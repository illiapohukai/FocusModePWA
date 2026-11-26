package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationResult
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRepository
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterialStore
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Live view of the one generation request (the draft phase is the durable view). */
sealed interface GenerationRun {
    data object Idle : GenerationRun
    data class Running(val requestId: String) : GenerationRun
    /** The plan is fully validated and stored in the draft (phase BRIEFING). */
    data class Succeeded(val requestId: String) : GenerationRun
    data class Failed(val requestId: String?, val error: GenerationError, val serverRequestId: String?) : GenerationRun
    data class Cancelled(val requestId: String?) : GenerationRun
}

sealed interface GenerationStart {
    data class Started(val requestId: String) : GenerationStart
    data class Rejected(val error: GenerationError) : GenerationStart
}

/**
 * The one Mission generation owner (PWA `analyzeGoal` / `cancelMissionAnalysis`). It validates
 * the draft, sends exactly one Worker request per explicit tap, and writes the outcome into the
 * draft only while that request is still the current one: every start and cancel advances a
 * token, so a late response after "Abbrechen" (or after a newer request) is dropped. Nothing is
 * retried automatically; a Mission is created only by [accept].
 *
 * Draft edits also go through here, serialized with the request outcome, so a form edit can
 * never overwrite a plan that arrived at the same moment.
 */
@Singleton
class MissionGenerationOwner @Inject constructor(
    private val missions: MissionRepository,
    private val generator: MissionGenerationRepository,
    private val material: MissionMaterialStore,
    private val preferences: PreferencesRepository,
    private val network: NetworkStatus,
    private val clock: FocusClock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var token = 0L
    private var job: Job? = null
    private val _run = MutableStateFlow<GenerationRun>(GenerationRun.Idle)
    val run: StateFlow<GenerationRun> = _run.asStateFlow()

    /** Returns the current draft, creating an empty one if none exists ("resume draft"). */
    suspend fun openDraft(): MissionDraft = mutex.withLock {
        reconcileLocked()
        missions.getDraft() ?: MissionDraft.new(clock.now()).also { missions.saveDraft(it) }
    }

    /**
     * After process death a draft can still say GENERATING although no request exists any more.
     * The result of that request is unknown, so it is marked interrupted (never re-sent).
     */
    suspend fun reconcile() = mutex.withLock { reconcileLocked() }

    private suspend fun reconcileLocked() {
        if (job?.isActive == true) return
        val draft = missions.getDraft() ?: return
        if (draft.phase == MissionDraftPhase.GENERATING) {
            missions.saveDraftIf(draft.copy(phase = MissionDraftPhase.FAILED, error = GenerationError.Interrupted, updatedAt = clock.now()), MissionDraftPhase.GENERATING)
        }
    }

    /**
     * Applies a form edit. While a request runs the form is read-only. Any input change makes an
     * existing Briefing plan stale, so the plan is dropped and a new plan needs an explicit tap.
     */
    suspend fun edit(transform: (MissionDraft) -> MissionDraft): MissionDraft? = mutex.withLock {
        val current = missions.getDraft() ?: MissionDraft.new(clock.now())
        if (current.phase == MissionDraftPhase.GENERATING) return@withLock null
        val edited = transform(current)
        if (edited == current) return@withLock current
        val inputsChanged = edited.goalText != current.goalText || edited.type != current.type || edited.confidence != current.confidence ||
            edited.schedule != current.schedule || edited.attachment != current.attachment
        val next = edited.copy(
            draftId = current.draftId, missionId = current.missionId, createdAt = current.createdAt, updatedAt = clock.now(),
            phase = if (inputsChanged) MissionDraftPhase.EDITING else current.phase,
            plan = if (inputsChanged) null else current.plan,
            error = if (inputsChanged) null else current.error,
            errorRequestId = if (inputsChanged) null else current.errorRequestId,
        )
        missions.saveDraft(next)
        next
    }

    /** Checks everything that can be checked locally before a paid request is sent. */
    suspend fun localError(draft: MissionDraft): GenerationError? {
        val goal = draft.goalText.trim()
        if (goal.isEmpty() && draft.attachment == null) return GenerationError.EmptyInput
        if (goal.length > MissionGenerationRequest.MAX_GOAL_CHARS) return GenerationError.GoalTooLong
        if (draft.type == null || draft.confidence == null) return GenerationError.InvalidRequest
        if (!MissionSchedulePlanner.validate(draft.schedule, clock.today()).valid) return GenerationError.ScheduleInvalid
        val active = missions.observeMissions().first().count { it.status == MissionStatus.ACTIVE }
        if (!MissionRules.canCreateActive(active)) return GenerationError.MissionLimitReached
        return null
    }

    /** "Mission planen" / "Erneut versuchen": one explicit request. */
    suspend fun start(): GenerationStart = mutex.withLock {
        if (job?.isActive == true) return@withLock GenerationStart.Rejected(GenerationError.AlreadyInFlight)
        // A cancelled request may still be unwinding inside the client; give it a moment to release.
        job?.let { previous -> withTimeoutOrNull(2_000) { previous.join() } }
        reconcileLocked()
        val draft = missions.getDraft() ?: return@withLock GenerationStart.Rejected(GenerationError.EmptyInput)
        val local = localError(draft) ?: if (!network.online.value) GenerationError.Offline else null
        if (local != null) {
            missions.saveDraft(draft.copy(phase = MissionDraftPhase.EDITING, error = local, errorRequestId = null, updatedAt = clock.now()))
            _run.value = GenerationRun.Failed(null, local, null)
            return@withLock GenerationStart.Rejected(local)
        }
        val photo = if (draft.attachment != null) {
            material.load() ?: run {
                val err = GenerationError.ImageProcessingFailed
                missions.saveDraft(draft.copy(attachment = null, phase = MissionDraftPhase.EDITING, error = err, updatedAt = clock.now()))
                return@withLock GenerationStart.Rejected(err)
            }
        } else null
        val request = MissionGenerationRequest(
            goalText = draft.goalText.trim(),
            type = draft.type!!,
            confidence = draft.confidence!!,
            schedule = MissionSchedulePlanner.payload(draft.schedule, clock.today(), clock.zone()),
            material = photo,
        )
        val requestId = "gen_" + UUID.randomUUID()
        val myToken = ++token
        missions.saveDraft(draft.copy(phase = MissionDraftPhase.GENERATING, error = null, errorRequestId = null, plan = null, updatedAt = clock.now()))
        _run.value = GenerationRun.Running(requestId)
        job = scope.launch { execute(myToken, requestId, draft.draftId, request) }
        GenerationStart.Started(requestId)
    }

    private suspend fun execute(myToken: Long, requestId: String, draftId: String, request: MissionGenerationRequest) {
        val result = try {
            generator.generate(request)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            GenerationResult.Failure(GenerationError.Unknown(null))
        }
        mutex.withLock {
            if (myToken != token) return // cancelled or superseded: a late response is dropped
            val current = missions.getDraft()
            if (current == null || current.draftId != draftId) {
                _run.value = GenerationRun.Cancelled(requestId)
                return
            }
            val now = clock.now()
            when (result) {
                is GenerationResult.Success -> {
                    val stored = missions.saveDraftIf(current.copy(phase = MissionDraftPhase.BRIEFING, plan = result.plan, error = null, errorRequestId = null, updatedAt = now), MissionDraftPhase.GENERATING)
                    _run.value = if (stored) GenerationRun.Succeeded(requestId) else GenerationRun.Cancelled(requestId)
                }
                is GenerationResult.Failure -> {
                    missions.saveDraftIf(current.copy(phase = MissionDraftPhase.FAILED, error = result.error, errorRequestId = result.serverRequestId, updatedAt = now), MissionDraftPhase.GENERATING)
                    _run.value = GenerationRun.Failed(requestId, result.error, result.serverRequestId)
                }
            }
            job = null
        }
    }

    /**
     * "Abbrechen": invalidates the request (a late answer is ignored), cancels the HTTP call,
     * keeps every input and returns the draft to editing. Never retries.
     */
    suspend fun cancel() = mutex.withLock {
        val running = (_run.value as? GenerationRun.Running)?.requestId
        token++
        job?.cancel()
        val draft = missions.getDraft()
        if (draft != null && draft.phase == MissionDraftPhase.GENERATING) {
            missions.saveDraftIf(draft.copy(phase = MissionDraftPhase.EDITING, error = null, errorRequestId = null, updatedAt = clock.now()), MissionDraftPhase.GENERATING)
        }
        _run.value = GenerationRun.Cancelled(running)
    }

    /** "Mission übernehmen": creates exactly one Mission from the validated Briefing. */
    suspend fun accept(): MissionWriteResult = mutex.withLock {
        val draft = missions.getDraft() ?: return@withLock MissionWriteResult.Rejected("draft_missing")
        val result = missions.acceptDraft(draft.draftId)
        if (result is MissionWriteResult.Ok) {
            material.clear()
            preferences.markHomeOnboardingDone()
            preferences.setLastSelectedMission(result.mission.id.value)
            _run.value = GenerationRun.Idle
        }
        result
    }

    /** Deletes the draft (and its processed photo). A running request is cancelled first. */
    suspend fun discard() = mutex.withLock {
        token++
        job?.cancel()
        missions.discardDraft()
        material.clear()
        _run.value = GenerationRun.Idle
    }

    /** Clears a shown error without touching the inputs (PWA `clearMissionError`). */
    suspend fun dismissError() = mutex.withLock {
        val draft = missions.getDraft() ?: return@withLock
        if (draft.error != null && draft.phase != MissionDraftPhase.GENERATING) {
            missions.saveDraft(draft.copy(error = null, errorRequestId = null, phase = if (draft.plan != null) MissionDraftPhase.BRIEFING else MissionDraftPhase.EDITING, updatedAt = clock.now()))
        }
        if (_run.value is GenerationRun.Failed) _run.value = GenerationRun.Idle
    }
}
