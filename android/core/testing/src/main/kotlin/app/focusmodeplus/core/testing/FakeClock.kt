package app.focusmodeplus.core.testing

import app.focusmodeplus.core.domain.time.FocusClock
import java.time.Instant
import java.time.ZoneId

/** Deterministic clock: wall time, monotonic time, boot counter and zone move independently. */
class FakeClock(
    var wall: Instant = Instant.parse("2026-11-19T09:00:00Z"),
    var realtimeMs: Long = 5_000_000L,
    var boot: Int? = 7,
    var zoneId: ZoneId = ZoneId.of("Europe/Berlin"),
) : FocusClock {
    override fun now(): Instant = wall
    override fun elapsedRealtimeMs(): Long = realtimeMs
    override fun bootCount(): Int? = boot
    override fun zone(): ZoneId = zoneId

    /** Real time passes (wall and monotonic together, as during sleep or background). */
    fun advanceMinutes(minutes: Long) = advanceMs(minutes * 60_000L)
    fun advanceMs(ms: Long) { wall = wall.plusMillis(ms); realtimeMs += ms }

    /** The user changes the wall clock; monotonic time is unaffected. */
    fun shiftWallClock(ms: Long) { wall = wall.plusMillis(ms) }

    /** Device reboot: monotonic time restarts and the boot counter increments. */
    fun reboot(downtimeMs: Long = 60_000L) {
        wall = wall.plusMillis(downtimeMs)
        realtimeMs = 20_000L
        boot = boot?.plus(1)
    }
}
