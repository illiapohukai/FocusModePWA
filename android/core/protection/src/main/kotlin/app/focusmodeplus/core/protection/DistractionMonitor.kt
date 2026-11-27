package app.focusmodeplus.core.protection

import app.focusmodeplus.core.domain.platform.ReminderNotifications
import app.focusmodeplus.core.domain.platform.UsageAccessGateway
import app.focusmodeplus.core.domain.protection.AppSelectionReconciler
import app.focusmodeplus.core.domain.repository.DistractionLog
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.FocusSession
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ProtectionPermissionState
import app.focusmodeplus.core.model.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Soft protection during an active *protected* Session only. Interval-based (every
 * [POLL_INTERVAL_MS] while the screen is on), never outside a Session, never continuous. It records
 * an aggregated attempt count and shows a gentle, rate-limited Focus hint. It does not block apps.
 *
 * While the screen is off it does not wake up at all: it suspends until the screen is on again
 * ([ScreenState.awaitInteractive]), because no app can be in the foreground meanwhile.
 */
@Singleton
class DistractionMonitor @Inject constructor(
    private val usage: UsageAccessGateway,
    private val log: DistractionLog,
    private val notifications: ReminderNotifications,
    private val clock: FocusClock,
    private val screen: ScreenState,
) {
    sealed interface Stop {
        data object Cancelled : Stop
        data object NothingToWatch : Stop
        data object AccessRevoked : Stop
    }

    suspend fun run(sessionId: SessionId, watched: Set<String>, remainingMinutes: () -> Int): Stop {
        if (watched.isEmpty()) return Stop.NothingToWatch
        log.purgeOlderThan(clock.now().minus(RETENTION_DAYS, ChronoUnit.DAYS))
        var windowStart = clock.now().toEpochMilli()
        var lastHint = Long.MIN_VALUE / 2
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            if (!screen.isInteractive()) {
                screen.awaitInteractive()
                windowStart = clock.now().toEpochMilli()
                continue
            }
            val now = clock.now().toEpochMilli()
            if (!usage.hasAccess()) return Stop.AccessRevoked
            val events = usage.foregroundEvents(windowStart, now, watched)
            windowStart = now
            events.forEach { (pkg, at) -> log.record(sessionId, pkg, Instant.ofEpochMilli(at)) }
            if (events.isNotEmpty() && now - lastHint >= HINT_INTERVAL_MS) {
                notifications.showDistractionHint(remainingMinutes())
                lastHint = now
            }
        }
        return Stop.Cancelled
    }

    companion object {
        const val POLL_INTERVAL_MS = 20_000L
        const val HINT_INTERVAL_MS = 3 * 60_000L
        const val RETENTION_DAYS = 30L
    }
}

/** What one protected Session's monitor watches. */
data class MonitorPlan(val sessionId: SessionId, val watched: Set<String>)

object MonitorPolicy {
    /**
     * Null when there is nothing to monitor: not a protected Session, monitoring switched off,
     * no Usage Access, or no installed selected app. Usage Access is never assumed.
     */
    fun plan(session: FocusSession, state: ProtectionState): MonitorPlan? {
        if (session.protectionMode != ProtectionMode.PROTECTED) return null
        if (!state.preferences.usageMonitoringEnabled) return null
        if (state.snapshot.usageAccess != ProtectionPermissionState.GRANTED) return null
        val watched = AppSelectionReconciler.watchablePackages(state.selectedApps)
        return if (watched.isEmpty()) null else MonitorPlan(session.id, watched)
    }
}

/**
 * The single owner of the distraction-monitor job: at most one job, and only for a running
 * *protected* Session. Repeated calls for the same Session (every minute checkpoint re-emits the
 * running Session) are no-ops; a different Session replaces the job; [stop] cancels it.
 *
 * The job runs on [dispatcher] (never the main thread: Usage Access, AppOps and notification
 * calls are binder transactions). It re-plans only when the relevant protection state changes,
 * so a revoked permission stops polling until access is granted again, without restart loops.
 */
@Singleton
class DistractionMonitorOwner internal constructor(
    private val monitor: DistractionMonitor,
    private val states: Flow<ProtectionState?>,
    private val markRevoked: (Boolean) -> Unit,
    private val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor(monitor: DistractionMonitor, protection: ProtectionCapabilityOwner) :
        this(monitor, protection.state, protection::markUsageRevoked, Dispatchers.Default)

    private var job: Job? = null
    private var sessionId: SessionId? = null

    /** The Session whose monitor job is alive, if any. */
    val monitoredSessionId: SessionId?
        @Synchronized get() = sessionId.takeIf { job?.isActive == true }

    /** Returns true only when a new job was started. */
    @Synchronized
    fun start(scope: CoroutineScope, session: FocusSession, remainingMinutes: () -> Int): Boolean {
        if (session.protectionMode != ProtectionMode.PROTECTED) {
            stopLocked()
            return false
        }
        if (sessionId == session.id && job?.isActive == true) return false
        stopLocked()
        sessionId = session.id
        job = scope.launch(dispatcher) {
            states.filterNotNull()
                .map { MonitorPolicy.plan(session, it) }
                .distinctUntilChanged()
                .collectLatest { plan ->
                    if (plan == null) return@collectLatest
                    if (monitor.run(plan.sessionId, plan.watched, remainingMinutes) == DistractionMonitor.Stop.AccessRevoked) {
                        markRevoked(true)
                    }
                }
        }
        return true
    }

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        val owned = job ?: return
        owned.cancel()
        job = null
        sessionId = null
        markRevoked(false) // "revoked during this Session" belongs to the Session that just ended
    }
}
