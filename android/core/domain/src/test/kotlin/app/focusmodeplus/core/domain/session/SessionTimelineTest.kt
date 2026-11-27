package app.focusmodeplus.core.domain.session

import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.SessionLifecycle
import app.focusmodeplus.core.testing.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimelineTest {
    private val clock = FakeClock()
    private val factory = SessionFactory(clock)
    private fun quick(minutes: Int = 25) = factory.quick(QuickFocusRequest("Lineare Funktionen", minutes, ProtectionMode.STANDARD))

    @Test fun remainingIsDerivedFromElapsedRealTimeIncludingSleepAndBackground() {
        val s = quick()
        clock.advanceMinutes(7) // background + device sleep: elapsedRealtime keeps counting
        val r = SessionTimeline.read(s, clock)
        assertTrue(r.reliable)
        assertEquals(7 * 60_000L, r.elapsedMs)
        assertEquals(18, r.remainingMinutesCeil)
    }

    @Test fun wallClockChangesDoNotAffectElapsedTime() {
        val s = quick()
        clock.advanceMinutes(5)
        clock.shiftWallClock(+3 * 3_600_000L) // user sets the clock 3 h ahead
        assertEquals(5 * 60_000L, SessionTimeline.read(s, clock).elapsedMs)
        clock.shiftWallClock(-6 * 3_600_000L)
        assertEquals(5 * 60_000L, SessionTimeline.read(s, clock).elapsedMs)
    }

    @Test fun elapsedIsCappedAtPlannedDuration() {
        val s = quick(5)
        clock.advanceMinutes(30)
        val r = SessionTimeline.read(s, clock)
        assertTrue(r.isElapsed)
        assertEquals(5 * 60_000L, r.elapsedMs)
        assertEquals(0L, r.remainingMs)
    }

    @Test fun completionInstantUsesMonotonicOvershootDespiteWallClockShift() {
        val s = quick(10)
        val expectedEnd = s.startedAt.plusSeconds(600)
        clock.advanceMinutes(15)
        assertEquals(expectedEnd, SessionTimeline.completionInstant(s, clock))
    }

    @Test fun rebootIsDetectedByBootCounterAndFreezesAtCheckpoint() {
        var s = quick()
        clock.advanceMinutes(12)
        s = SessionTimeline.checkpoint(s, clock)
        clock.advanceMinutes(1) // last minute after checkpoint is lost with the reboot
        clock.reboot()
        val r = SessionTimeline.read(s, clock)
        assertFalse(r.reliable)
        assertEquals(12 * 60_000L, r.elapsedMs)
        val frozen = SessionTimeline.freeze(s, clock)
        assertEquals(SessionLifecycle.AWAITING_RESUME, frozen.lifecycle)
        assertEquals(12 * 60_000L, SessionTimeline.read(frozen, clock).elapsedMs)
    }

    @Test fun rebootWithoutBootCounterIsDetectedByWallMonotonicMismatch() {
        clock.boot = null
        val s = quick()
        clock.advanceMinutes(3)
        // Reboot: monotonic restarts small, then the device stays up long enough that nowRt > startRt.
        clock.wall = clock.wall.plusSeconds(3_600)
        clock.realtimeMs = s.timing.segmentStartRealtimeMs!! + 2 * 60_000L
        assertFalse(SessionTimeline.read(s, clock).reliable)
    }

    @Test fun resumeContinuesFromExactBankedElapsedTime() {
        var s = quick()
        clock.advanceMinutes(9)
        s = SessionTimeline.freeze(SessionTimeline.checkpoint(s, clock), clock)
        clock.advanceMinutes(60) // time while frozen never counts
        s = SessionTimeline.resume(s, clock)
        clock.advanceMinutes(2)
        assertEquals(11 * 60_000L, SessionTimeline.read(s, clock).elapsedMs)
        assertEquals(s.timing.segmentStartRealtimeMs!! + (25 - 9) * 60_000L, SessionTimeline.endRealtimeMs(s))
    }

    @Test fun checkpointIsNoOpWhenUnreliable() {
        val s = quick()
        clock.reboot()
        assertEquals(s, SessionTimeline.checkpoint(s, clock))
    }
}
