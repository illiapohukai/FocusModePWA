package io.github.illiapohukai.focusmode.core.domain.session

import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizationPolicyTest {
    private val clock = FakeClock()
    private val quick = SessionFactory(clock).quick(QuickFocusRequest(null, 25, ProtectionMode.STANDARD))
    private val mission = SessionFactory(clock).mission(
        MissionLink(MissionId("mission_a"), MissionStepId("step_a")), "Kapitel 1", "Lesen", 25, "Mathe", 1, 3, ProtectionMode.STANDARD,
    )

    private fun decide(s: io.github.illiapohukai.focusmode.core.model.FocusSession, minutes: Double, trigger: FinalizeTrigger, note: EndNote? = null) =
        FinalizationPolicy.decide(s, (minutes * 60_000).toLong(), trigger, clock.now(), clock.zone(), note)

    private fun finalize(d: FinalizationDecision) = (d as FinalizationDecision.Finalize)

    @Test fun timerElapsedIsCompletedAndCounts() {
        val f = finalize(decide(quick, 25.0, FinalizeTrigger.TIMER_ELAPSED))
        assertEquals(SessionStatus.COMPLETED, f.result.status)
        assertEquals(SessionOutcome.COMPLETED, f.result.outcome)
        assertEquals(SessionEndReason.TIMER_FINISHED, f.result.endReason)
        assertEquals(25, f.result.actualMinutes)
        assertTrue(f.result.countsAsFocus)
        assertFalse(f.awaitsReview)
    }

    @Test fun timerTriggerBeforeEndIsRejected() {
        assertTrue(decide(quick, 24.9, FinalizeTrigger.TIMER_ELAPSED) is FinalizationDecision.Rejected)
    }

    @Test fun endingBelowOneMinuteIsCancelledAsEndedTooEarly() {
        val f = finalize(decide(quick, 0.98, FinalizeTrigger.USER_END))
        assertEquals(SessionStatus.CANCELLED, f.result.status)
        assertEquals(SessionOutcome.CANCELED, f.result.outcome)
        assertEquals(SessionEndReason.ENDED_TOO_EARLY, f.result.endReason)
        assertFalse(f.result.countsAsFocus)
    }

    @Test fun endingEarlyWithCountedTimeIsPartialAndCounts() {
        val f = finalize(decide(quick, 12.6, FinalizeTrigger.USER_END, EndNote.NEED_BREAK))
        assertEquals(SessionStatus.COMPLETED, f.result.status)
        assertEquals(SessionOutcome.PARTIAL, f.result.outcome)
        assertEquals(12, f.result.actualMinutes) // floor, like the PWA
        assertTrue(f.result.countsAsFocus)
        assertEquals(EndNote.NEED_BREAK, f.result.endNote)
    }

    @Test fun userCancelNeverCounts() {
        val f = finalize(decide(quick, 20.0, FinalizeTrigger.USER_CANCEL))
        assertEquals(SessionOutcome.CANCELED, f.result.outcome)
        assertEquals(SessionEndReason.USER_CANCELLED, f.result.endReason)
        assertFalse(f.result.countsAsFocus)
    }

    @Test fun emergencyExitKeepsCountedTime() {
        val f = finalize(decide(quick, 6.0, FinalizeTrigger.EMERGENCY_EXIT))
        assertEquals(SessionOutcome.PARTIAL, f.result.outcome)
        assertEquals(SessionEndReason.EMERGENCY_EXIT, f.result.endReason)
        assertTrue(f.result.countsAsFocus)
    }

    @Test fun recoverySaveBelowOneMinuteIsRejectedLikeThePwa() {
        assertTrue(decide(quick, 0.5, FinalizeTrigger.RECOVERY_SAVE) is FinalizationDecision.Rejected)
        assertEquals(SessionEndReason.RECOVERY_CONFIRMED, finalize(decide(quick, 3.0, FinalizeTrigger.RECOVERY_SAVE)).result.endReason)
    }

    @Test fun missionSessionsWithCountedTimeAwaitReview() {
        val f = finalize(decide(mission, 25.0, FinalizeTrigger.TIMER_ELAPSED))
        assertTrue(f.awaitsReview)
        assertEquals(SessionEndReason.AWAITING_USER_REVIEW, f.result.endReason)
        assertEquals(mission.missionLink, f.result.missionLink)
        assertFalse(finalize(decide(mission, 0.5, FinalizeTrigger.USER_END)).awaitsReview)
    }

    @Test fun resultKeepsSessionIdentityAndLocalDate() {
        val f = finalize(decide(quick, 25.0, FinalizeTrigger.TIMER_ELAPSED_WHILE_AWAY))
        assertEquals(quick.id, f.result.sessionId)
        assertEquals(SessionEndReason.TIMER_FINISHED_WHILE_AWAY, f.result.endReason)
        assertEquals(java.time.LocalDate.parse("2026-11-19"), f.result.localDate)
    }
}
