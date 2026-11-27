package app.focusmodeplus.core.domain.session

import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.FocusTask
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    @Test fun quickDurationContractMatchesPwa() {
        assertTrue(QuickFocusDuration.isValid(5))
        assertTrue(QuickFocusDuration.isValid(60))
        assertFalse(QuickFocusDuration.isValid(0))
        assertFalse(QuickFocusDuration.isValid(65))
        assertFalse(QuickFocusDuration.isValid(27))
        assertEquals(25, QuickFocusDuration.normalize(27))
        assertEquals(5, QuickFocusDuration.normalize(-10))
        assertEquals(60, QuickFocusDuration.normalize(500))
        assertEquals("05:00", QuickFocusDuration.staticReadout(5))
        assertEquals("25:00", QuickFocusDuration.staticReadout(25))
    }

    @Test fun quickSessionUsesTaskAsTitleAndDefaultsEmptyTask() {
        val s = SessionFactory(FakeClock()).quick(QuickFocusRequest("   ", 25, ProtectionMode.STANDARD))
        assertEquals(FocusTask.DEFAULT, s.task.text)
        assertEquals(FocusTask.DEFAULT, s.title)
        assertEquals(SessionType.QUICK, s.type)
        assertNull(s.missionLink)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidQuickDurationIsRejected() {
        SessionFactory(FakeClock()).quick(QuickFocusRequest("x", 7, ProtectionMode.STANDARD))
    }

    @Test fun taskStripsControlCharacters() {
        assertEquals("a b", FocusTask.of("a\u0000b").text)
    }

    @Test fun protectedEndBarrierNeedsDelayAndReasonButEmergencyExitIsAlwaysThere() {
        val b = EndBarrier.forMode(ProtectionMode.PROTECTED)
        assertTrue(b.emergencyExitAvailable)
        assertFalse(b.canConfirm(3, EndNote.NEED_BREAK))
        assertFalse(b.canConfirm(10, null))
        assertTrue(b.canConfirm(10, EndNote.NEED_BREAK))
        assertTrue(EndBarrier.forMode(ProtectionMode.STANDARD).canConfirm(0, null))
    }
}
