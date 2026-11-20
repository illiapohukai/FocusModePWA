package io.github.illiapohukai.focusmode.core.session

import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.platform.DoNotDisturbController
import io.github.illiapohukai.focusmode.core.domain.platform.SessionEndAlarm
import io.github.illiapohukai.focusmode.core.domain.platform.SessionNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.SessionRuntime
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionLoad
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionStore
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.session.ElapsedReading
import io.github.illiapohukai.focusmode.core.domain.session.FinalizationDecision
import io.github.illiapohukai.focusmode.core.domain.session.FinalizationPolicy
import io.github.illiapohukai.focusmode.core.domain.session.FinalizeTrigger
import io.github.illiapohukai.focusmode.core.domain.session.FinishResult
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.FreezeCause
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusDuration
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusRequest
import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import io.github.illiapohukai.focusmode.core.domain.session.SessionFactory
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.session.SessionValidationError
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single authoritative Focus Session owner. Every mutation is serialized by one mutex, so a
 * timer end, a notification action and a user tap racing each other still finalize exactly once
 * (and the History insert is idempotent on top of that).
 *
 * Timing never comes from an in-memory ticker: the state is re-derived from the durable record
 * and the monotonic clock on every restore, tick, alarm and user action.
 */
@Singleton
class FocusSessionManager @Inject constructor(
    private val store: ActiveSessionStore,
    private val history: SessionHistoryRepository,
    private val missions: MissionRepository,
    private val preferences: PreferencesRepository,
    private val clock: FocusClock,
    private val runtime: SessionRuntime,
    private val alarm: SessionEndAlarm,
    private val notifications: SessionNotifications,
    private val dnd: DoNotDisturbController,
) : FocusSessionController {

    private val mutex = Mutex()
    private val factory = SessionFactory(clock)
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override fun read(): ElapsedReading? = _state.value.activeSession?.let { SessionTimeline.read(it, clock) }

    override suspend fun restore(trigger: RestoreTrigger) = mutex.withLock {
        when (val load = store.load()) {
            ActiveSessionLoad.None, is ActiveSessionLoad.Corrupt -> {
                stopRuntime()
                dnd.restoreIfChanged()
                val review = history.pendingReview()
                _state.value = if (review != null) SessionState.AwaitingReview(review) else SessionState.Idle(lastFinalizedOrNull())
            }
            is ActiveSessionLoad.Loaded -> restoreLoaded(load.session, trigger)
        }
    }

    private fun lastFinalizedOrNull() = (_state.value as? SessionState.Idle)?.lastFinalized

    private suspend fun restoreLoaded(session: FocusSession, trigger: RestoreTrigger) {
        when (session.lifecycle) {
            SessionLifecycle.RUNNING -> {
                val reading = SessionTimeline.read(session, clock)
                when {
                    !reading.reliable -> {
                        // Monotonic base lost (reboot): freeze at the last checkpoint, like the PWA after a reload.
                        val frozen = SessionTimeline.freeze(session, clock)
                        store.save(frozen)
                        stopRuntime()
                        dnd.restoreIfChanged()
                        _state.value = SessionState.AwaitingResume(frozen, FreezeCause.DEVICE_RESTARTED)
                        notifications.showFrozen(frozen)
                    }
                    reading.isElapsed -> {
                        val away = trigger == RestoreTrigger.APP_START || trigger == RestoreTrigger.BOOT_COMPLETED || trigger == RestoreTrigger.PACKAGE_REPLACED
                        finalizeLocked(session, reading, if (away) FinalizeTrigger.TIMER_ELAPSED_WHILE_AWAY else FinalizeTrigger.TIMER_ELAPSED, null)
                    }
                    else -> {
                        _state.value = SessionState.Running(session)
                        startRuntime(session)
                    }
                }
            }
            SessionLifecycle.AWAITING_RESUME -> {
                stopRuntime()
                _state.value = SessionState.AwaitingResume(session, FreezeCause.STORED_FROZEN)
            }
            SessionLifecycle.AWAITING_REVIEW -> {
                // Legacy shape; reviews are tracked through History. Drop the stale active row.
                store.clear(session.id)
                _state.value = history.pendingReview()?.let(SessionState::AwaitingReview) ?: SessionState.Idle()
            }
        }
    }

    override suspend fun startQuick(request: QuickFocusRequest): StartResult = mutex.withLock {
        if (_state.value.activeSession != null) return StartResult.Rejected(SessionValidationError.SessionAlreadyActive)
        if (!QuickFocusDuration.isValid(request.minutes)) return StartResult.Rejected(SessionValidationError.DurationInvalid)
        if (request.protectionMode == ProtectionMode.STRICT_MANAGED) return StartResult.Rejected(SessionValidationError.ModeUnavailable)
        val session = factory.quick(request)
        begin(session)
        preferences.setDefaultQuickFocusMinutes(request.minutes)
        StartResult.Started(session)
    }

    override suspend fun startMissionStep(missionId: MissionId, stepId: MissionStepId, mode: ProtectionMode): StartResult = mutex.withLock {
        if (_state.value.activeSession != null) return StartResult.Rejected(SessionValidationError.SessionAlreadyActive)
        if (mode == ProtectionMode.STRICT_MANAGED) return StartResult.Rejected(SessionValidationError.ModeUnavailable)
        val mission = missions.getMission(missionId) ?: return StartResult.Rejected(SessionValidationError.MissionNotFound)
        if (!MissionRules.canStartStep(mission, stepId)) return StartResult.Rejected(SessionValidationError.StepNotStartable)
        val step = mission.steps.first { it.id == stepId }
        val session = factory.mission(
            link = MissionLink(missionId, stepId),
            title = step.title,
            task = step.task.ifBlank { step.reason },
            minutes = step.durationMinutes,
            modeLabel = mission.subject,
            progressCurrent = mission.steps.indexOf(step) + 1,
            progressTotal = mission.steps.size,
            protectionMode = mode,
        )
        begin(session)
        preferences.setLastSelectedMission(missionId.value)
        StartResult.Started(session)
    }

    /** One durable write at start; the foreground service and end alarm make it survive. */
    private suspend fun begin(session: FocusSession) {
        store.save(session)
        _state.value = SessionState.Running(session)
        notifications.clearSessionNotifications()
        startRuntime(session)
        if (preferences.current().doNotDisturbDuringFocus) dnd.applyForSession(session.id)
    }

    override suspend fun endByUser(note: EndNote?): FinishResult = finishRunning(FinalizeTrigger.USER_END, note)
    override suspend fun cancel(note: EndNote?): FinishResult = finishRunning(FinalizeTrigger.USER_CANCEL, note)
    override suspend fun emergencyExit(): FinishResult = finishRunning(FinalizeTrigger.EMERGENCY_EXIT, EndNote.EMERGENCY)

    private suspend fun finishRunning(trigger: FinalizeTrigger, note: EndNote?): FinishResult = mutex.withLock {
        val session = (_state.value as? SessionState.Running)?.session ?: return FinishResult.NoActiveSession
        finalizeLocked(session, SessionTimeline.read(session, clock), trigger, note)
    }

    override suspend fun resumeRecovered(): Boolean = mutex.withLock {
        val frozen = (_state.value as? SessionState.AwaitingResume)?.session ?: return false
        val resumed = SessionTimeline.resume(frozen, clock)
        store.save(resumed)
        _state.value = SessionState.Running(resumed)
        notifications.clearSessionNotifications()
        startRuntime(resumed)
        if (preferences.current().doNotDisturbDuringFocus) dnd.applyForSession(resumed.id)
        true
    }

    override suspend fun saveRecovered(): FinishResult = mutex.withLock {
        val frozen = (_state.value as? SessionState.AwaitingResume)?.session ?: return FinishResult.NoActiveSession
        finalizeLocked(frozen, SessionTimeline.read(frozen, clock), FinalizeTrigger.RECOVERY_SAVE, null)
    }

    /** PWA discard: the frozen Session is dropped without a History record. */
    override suspend fun discardRecovered(): Boolean = mutex.withLock {
        val frozen = (_state.value as? SessionState.AwaitingResume)?.session ?: return false
        store.clear(frozen.id)
        stopRuntime()
        notifications.clearSessionNotifications()
        _state.value = SessionState.Idle()
        true
    }

    override suspend fun submitMissionReview(stepCompleted: Boolean, rating: Int?): Boolean = mutex.withLock {
        val pending = (_state.value as? SessionState.AwaitingReview)?.result ?: return false
        val outcome = if (stepCompleted) SessionOutcome.COMPLETED else SessionOutcome.PARTIAL
        history.applyMissionReview(pending.sessionId, outcome, rating, completeStep = stepCompleted) ?: return false
        _state.value = SessionState.Idle(lastFinalized = pending.sessionId)
        true
    }

    override suspend fun onRuntimeTick() = mutex.withLock {
        val session = (_state.value as? SessionState.Running)?.session ?: return@withLock
        val reading = SessionTimeline.read(session, clock)
        when {
            !reading.reliable -> Unit // cannot happen inside one boot; restore() handles reboots
            reading.isElapsed -> finalizeLocked(session, reading, FinalizeTrigger.TIMER_ELAPSED, null)
            reading.elapsedMs - session.timing.checkpointElapsedMs >= CHECKPOINT_INTERVAL_MS -> {
                val checkpointed = SessionTimeline.checkpoint(session, clock)
                store.save(checkpointed)
                _state.value = SessionState.Running(checkpointed)
            }
        }
    }

    private suspend fun finalizeLocked(session: FocusSession, reading: ElapsedReading, trigger: FinalizeTrigger, note: EndNote?): FinishResult {
        val finishedAt = if (reading.isElapsed && session.lifecycle == SessionLifecycle.RUNNING) SessionTimeline.completionInstant(session, clock) else clock.now()
        return when (val decision = FinalizationPolicy.decide(session, reading.elapsedMs, trigger, finishedAt, clock.zone(), note)) {
            is FinalizationDecision.Rejected -> FinishResult.Rejected(decision.reason)
            is FinalizationDecision.Finalize -> {
                val outcome = history.finalize(decision.result) // one transaction; idempotent per Session id
                stopRuntime()
                dnd.restoreIfChanged()
                notifications.clearSessionNotifications()
                if (trigger == FinalizeTrigger.TIMER_ELAPSED || trigger == FinalizeTrigger.TIMER_ELAPSED_WHILE_AWAY) {
                    notifications.showFinished(outcome.result)
                }
                _state.value = if (decision.awaitsReview) SessionState.AwaitingReview(outcome.result) else SessionState.Idle(lastFinalized = session.id)
                FinishResult.Finalized(outcome.result, decision.awaitsReview)
            }
        }
    }

    private fun startRuntime(session: FocusSession) {
        runtime.ensureRunning(session)
        SessionTimeline.endRealtimeMs(session)?.let { alarm.schedule(session, it) }
    }

    private fun stopRuntime() {
        runtime.stop()
        alarm.cancel()
    }

    companion object {
        /** Coarse durable checkpoint cadence (only matters after a reboot). Never per second. */
        const val CHECKPOINT_INTERVAL_MS = 60_000L
    }
}
