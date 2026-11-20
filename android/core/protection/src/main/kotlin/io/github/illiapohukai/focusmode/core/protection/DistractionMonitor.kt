package io.github.illiapohukai.focusmode.core.protection

import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway
import io.github.illiapohukai.focusmode.core.domain.repository.DistractionLog
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.SessionId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Soft protection during an active *protected* Session only. Interval-based (every
 * [POLL_INTERVAL_MS] while the screen is on), never outside a Session, never continuous. It records
 * an aggregated attempt count and shows a gentle, rate-limited Focus hint. It does not block apps.
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
            val now = clock.now().toEpochMilli()
            if (!screen.isInteractive()) { windowStart = now; continue }
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
