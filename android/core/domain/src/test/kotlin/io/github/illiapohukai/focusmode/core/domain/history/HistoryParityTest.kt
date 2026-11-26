package io.github.illiapohukai.focusmode.core.domain.history

import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryParityTest {
    private val today = LocalDate.parse("2026-11-26")
    private val at = Instant.parse("2026-11-26T09:00:00Z")

    @Test fun sameTimestampTieIsOrderedBySessionIdLikeTheDao() {
        val a = Fixtures.result("session_a", today, 25, finishedAt = at)
        val b = Fixtures.result("session_b", today, 25, finishedAt = at)
        val older = Fixtures.result("session_z", today, 25, finishedAt = at.minusSeconds(60))
        assertEquals(listOf("session_b", "session_a", "session_z"), HistoryQueries.ordered(listOf(a, older, b)).map { it.sessionId.value })
        assertEquals(HistoryQueries.ordered(listOf(b, a, older)), HistoryQueries.ordered(listOf(older, a, b)))
        assertEquals(listOf("session_b", "session_a", "session_z"), HistoryQueries.filter(listOf(older, a, b), HistoryFilter.TODAY, today).map { it.sessionId.value })
    }

    @Test fun weekFilterStartsOnMonday() {
        val sunday = Fixtures.result("sun", LocalDate.parse("2026-11-22"), 25)
        val monday = Fixtures.result("mon", LocalDate.parse("2026-11-23"), 25)
        assertEquals(listOf("mon"), HistoryQueries.filter(listOf(sunday, monday), HistoryFilter.WEEK, today).map { it.sessionId.value })
        assertEquals(2, HistoryQueries.filter(listOf(sunday, monday), HistoryFilter.ALL, today).size)
    }

    @Test fun outcomesUseNodeShapesNotOnlyColour() {
        val completed = Fixtures.result("c", today, 25)
        val partial = Fixtures.result("p", today, 10, outcome = SessionOutcome.PARTIAL)
        val cancelled = Fixtures.result("x", today, 0, outcome = SessionOutcome.CANCELED, counts = false, status = SessionStatus.CANCELLED)
        val interrupted = Fixtures.result("i", today, 3, status = SessionStatus.INTERRUPTED, outcome = SessionOutcome.CANCELED, counts = false)
        assertEquals(OutcomeNode.FILLED, HistoryQueries.node(HistoryQueries.badge(completed)))
        assertEquals(OutcomeNode.HALF, HistoryQueries.node(HistoryQueries.badge(partial)))
        assertEquals(OutcomeNode.OPEN, HistoryQueries.node(HistoryQueries.badge(cancelled)))
        assertEquals(OutcomeNode.OPEN, HistoryQueries.node(HistoryQueries.badge(interrupted)))
        assertEquals("Unterbrochen", HistoryQueries.badge(interrupted).label)
        assertEquals(PeriodSummary(countedMinutes = 35, sessions = 4, completed = 1, partial = 1, cancelled = 2), HistoryQueries.periodSummary(listOf(completed, partial, cancelled, interrupted)))
    }

    @Test fun missionRowsNameTheirMissionAndStep() {
        val mission = Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3)
        val step = mission.steps[1]
        val r = Fixtures.result("m", today, 25).copy(type = SessionType.MISSION, missionLink = MissionLink(mission.id, step.id))
        val byId = mapOf(mission.id to mission)
        assertEquals("Biologie-Klausur · Schritt 02", HistoryFormat.missionContext(r, byId))
        assertEquals("Biologie-Klausur", HistoryFormat.activityContext(r, byId))
        // Deleted Mission: History keeps the Session with a neutral label.
        assertEquals("Focus Mission", HistoryFormat.missionContext(r, emptyMap()))
        val replaced = r.copy(missionLink = MissionLink(MissionId("mission_bio"), MissionStepId("gone_step")))
        assertEquals("Biologie-Klausur", HistoryFormat.missionContext(replaced, byId))
        assertEquals("Quick Focus", HistoryFormat.activityContext(Fixtures.result("q", today, 25), byId))
    }

    @Test fun dayLabelsAndBelowOneMinute() {
        assertEquals("Heute", HistoryFormat.dayLabel(today, today))
        assertEquals("Gestern", HistoryFormat.activityDayLabel(today.minusDays(1), today))
        assertEquals("Mo., 23. Nov.", HistoryFormat.activityDayLabel(LocalDate.parse("2026-11-23"), today))
        assertEquals("Montag, 23. Nov.", HistoryFormat.dayLabel(LocalDate.parse("2026-11-23"), today))
        assertEquals("< 1 Min.", HistoryFormat.minutes(Fixtures.result("z", today, 0, counts = false)))
        assertEquals("1 Std. 5 Min.", HistoryFormat.minutes(Fixtures.result("h", today, 65, planned = 90)))
    }
}
