package io.github.illiapohukai.focusmode.core.domain.progress

import io.github.illiapohukai.focusmode.core.domain.history.HistoryFilter
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.TrustLevel
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressCalculatorTest {
    private val today = LocalDate.parse("2026-11-19") // Thursday
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun streakCountsFromTodayWhenTodayIsActive() {
        val s = ProgressCalculator.streak(setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5)), today)
        assertEquals(3, s.current)
        assertEquals(3, s.best)
        assertTrue(s.todayActive)
        assertEquals(TrustLevel.LOCAL_DERIVED, s.trust)
    }

    @Test fun unfinishedTodayDoesNotBreakTheStreak() {
        val s = ProgressCalculator.streak(setOf(today.minusDays(1), today.minusDays(2)), today)
        assertEquals(2, s.current)
        assertFalse(s.todayActive)
    }

    @Test fun gapResetsCurrentButKeepsBest() {
        val days = setOf(d("2026-11-01"), d("2026-11-02"), d("2026-11-03"), d("2026-11-04"), today.minusDays(2))
        val s = ProgressCalculator.streak(days, today)
        assertEquals(0, s.current)
        assertEquals(4, s.best)
    }

    @Test fun progressCountsOnlyCountedFocusAndUsesMondayWeek() {
        val results = listOf(
            Fixtures.result("a", today, 25),
            Fixtures.result("b", today, 10, outcome = SessionOutcome.PARTIAL),
            Fixtures.result("c", today, 0, outcome = SessionOutcome.CANCELED, status = SessionStatus.CANCELLED, counts = false),
            Fixtures.result("d", d("2026-11-17"), 30), // Tuesday, same week
            Fixtures.result("e", d("2026-11-16"), 15), // Monday, same week
            Fixtures.result("f", d("2026-11-15"), 45), // Sunday, previous week
            Fixtures.result("g", today, 20, counts = false),
        )
        val p = ProgressCalculator.compute(results, today)
        assertEquals(35, p.todayMinutes)
        assertEquals(2, p.todaySessions)
        assertEquals(80, p.weekMinutes)
        assertEquals(4, p.weekSessions)
        assertEquals(125, p.totalMinutes)
        assertEquals(1, p.streak.current) // 17th breaks the chain to 19th
    }

    @Test fun historyFilterGroupingAndBadges() {
        val results = listOf(
            Fixtures.result("a", today, 25, finishedAt = java.time.Instant.parse("2026-11-19T08:00:00Z")),
            Fixtures.result("b", today, 5, outcome = SessionOutcome.PARTIAL, finishedAt = java.time.Instant.parse("2026-11-19T12:00:00Z")),
            Fixtures.result("c", d("2026-11-10"), 25),
        )
        assertEquals(listOf("b", "a"), HistoryQueries.filter(results, HistoryFilter.TODAY, today).map { it.sessionId.value })
        assertEquals(2, HistoryQueries.filter(results, HistoryFilter.WEEK, today).size)
        val days = HistoryQueries.groupByDay(results)
        assertEquals(listOf(today, d("2026-11-10")), days.map { it.date })
        assertEquals(30, days.first().focusMinutes)
        assertEquals(OutcomeBadge.PARTIAL, HistoryQueries.badge(results[1]))
        assertEquals(OutcomeBadge.CANCELLED, HistoryQueries.badge(Fixtures.result(status = SessionStatus.CANCELLED, outcome = SessionOutcome.CANCELED)))
        assertEquals("1 Std. 5 Min.", HistoryQueries.formatMinutes(65))
        assertEquals("0 Min.", HistoryQueries.formatMinutes(0))
    }
}
