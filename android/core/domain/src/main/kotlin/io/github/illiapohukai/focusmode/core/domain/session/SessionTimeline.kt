package io.github.illiapohukai.focusmode.core.domain.session

import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import io.github.illiapohukai.focusmode.core.model.SessionTiming
import java.time.Instant
import kotlin.math.abs

/**
 * Elapsed time of a Session.
 * @property reliable false when the monotonic base was lost (reboot): [elapsedMs] is then the
 * frozen checkpoint and the Session must await the user's decision.
 */
data class ElapsedReading(val elapsedMs: Long, val plannedMs: Long, val reliable: Boolean) {
    val remainingMs: Long get() = (plannedMs - elapsedMs).coerceAtLeast(0)
    val isElapsed: Boolean get() = elapsedMs >= plannedMs
    val countedMinutes: Int get() = (elapsedMs / 60_000L).toInt()
    /** Remaining whole minutes for display, rounded up (24:59 -> 25). */
    val remainingMinutesCeil: Int get() = ((remainingMs + 59_999L) / 60_000L).toInt()
}

/**
 * Pure timing rules of the single active Session. Remaining time is always derived from durable
 * timestamps and the monotonic clock, never from a tick counter.
 */
object SessionTimeline {
    /** Tolerated drift between wall and monotonic deltas when the boot counter is unknown. */
    private const val UNKNOWN_BOOT_TOLERANCE_MS = 10 * 60_000L

    fun read(session: FocusSession, clock: FocusClock): ElapsedReading {
        val planned = session.plannedMs
        val t = session.timing
        val frozen = t.checkpointElapsedMs.coerceIn(0, planned)
        if (session.lifecycle != SessionLifecycle.RUNNING) return ElapsedReading(frozen, planned, true)
        val raw = rawRunningElapsed(t, clock) ?: return ElapsedReading(frozen, planned, false)
        return ElapsedReading(raw.coerceIn(0, planned), planned, true)
    }

    /** Uncapped elapsed of a running segment, or null when the monotonic base is not trustworthy. */
    private fun rawRunningElapsed(t: SessionTiming, clock: FocusClock): Long? {
        val startRt = t.segmentStartRealtimeMs ?: return null
        val nowRt = clock.elapsedRealtimeMs()
        if (nowRt < startRt) return null
        val storedBoot = t.segmentBootCount
        val currentBoot = clock.bootCount()
        if (storedBoot != null && currentBoot != null) {
            if (storedBoot != currentBoot) return null
        } else {
            // Without a boot counter, cross-check against the wall clock to catch a reboot.
            val startWall = t.segmentStartWall ?: return null
            val wallDelta = clock.now().toEpochMilli() - startWall.toEpochMilli()
            if (abs(wallDelta - (nowRt - startRt)) > UNKNOWN_BOOT_TOLERANCE_MS) return null
        }
        return t.accumulatedMs + (nowRt - startRt)
    }

    /**
     * Wall instant at which the planned duration was reached, computed from the monotonic clock
     * so a wall-clock change during the Session does not distort History.
     */
    fun completionInstant(session: FocusSession, clock: FocusClock): Instant {
        val raw = rawRunningElapsed(session.timing, clock)
        val now = clock.now()
        if (raw == null) return now
        val overshoot = (raw - session.plannedMs).coerceAtLeast(0)
        return now.minusMillis(overshoot)
    }

    /** Monotonic timestamp at which the running segment reaches the planned duration. */
    fun endRealtimeMs(session: FocusSession): Long? {
        if (session.lifecycle != SessionLifecycle.RUNNING) return null
        val start = session.timing.segmentStartRealtimeMs ?: return null
        return start + (session.plannedMs - session.timing.accumulatedMs).coerceAtLeast(0)
    }

    fun newRunningTiming(clock: FocusClock, accumulatedMs: Long = 0): SessionTiming {
        val now = clock.now()
        return SessionTiming(
            accumulatedMs = accumulatedMs,
            segmentStartWall = now,
            segmentStartRealtimeMs = clock.elapsedRealtimeMs(),
            segmentBootCount = clock.bootCount(),
            checkpointElapsedMs = accumulatedMs,
            checkpointWall = now,
        )
    }

    /** Coarse durable checkpoint (minute cadence). Returns the session unchanged if unreliable. */
    fun checkpoint(session: FocusSession, clock: FocusClock): FocusSession {
        val reading = read(session, clock)
        if (!reading.reliable || session.lifecycle != SessionLifecycle.RUNNING) return session
        return session.copy(timing = session.timing.copy(checkpointElapsedMs = reading.elapsedMs, checkpointWall = clock.now()))
    }

    /** Freeze at the last checkpoint (monotonic base lost). */
    fun freeze(session: FocusSession, clock: FocusClock): FocusSession {
        val frozen = session.timing.checkpointElapsedMs.coerceIn(0, session.plannedMs)
        return session.copy(
            lifecycle = SessionLifecycle.AWAITING_RESUME,
            timing = session.timing.copy(
                accumulatedMs = frozen,
                segmentStartWall = null,
                segmentStartRealtimeMs = null,
                segmentBootCount = null,
                checkpointElapsedMs = frozen,
                checkpointWall = clock.now(),
            ),
        )
    }

    /** Resume a frozen Session from its exact banked elapsed time. */
    fun resume(session: FocusSession, clock: FocusClock): FocusSession {
        val banked = session.timing.checkpointElapsedMs.coerceIn(0, session.plannedMs)
        return session.copy(lifecycle = SessionLifecycle.RUNNING, timing = newRunningTiming(clock, banked))
    }
}
