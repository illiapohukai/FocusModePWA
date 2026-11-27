package app.focusmodeplus.core.domain.history

import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** PWA `focus-insights.js` semantics on local calendar days (`buildSeries`, `computeInsights`). */
class FocusInsightsTest {
    // Thursday, 2026-11-26.
    private val today = LocalDate.parse("2026-11-26")

    private fun r(id: String, date: LocalDate, minutes: Int, mission: Boolean = false, outcome: SessionOutcome = SessionOutcome.COMPLETED, counts: Boolean = true): FocusSessionResult {
        val base = Fixtures.result(id, date, minutes, outcome = outcome, counts = counts, status = if (outcome == SessionOutcome.CANCELED) SessionStatus.CANCELLED else SessionStatus.COMPLETED)
        return if (!mission) base else base.copy(type = SessionType.MISSION, missionLink = MissionLink(MissionId("mission_a"), MissionStepId("step_a1")))
    }

    @Test fun noDataGivesEmptyBucketsAndNoInsights() {
        val s = FocusInsights.series(emptyList(), RhythmRange.DAYS_7, today)
        assertEquals(7, s.buckets.size)
        assertEquals(today, s.buckets.last().start)
        assertEquals(-1, s.bestIndex)
        assertEquals(0, s.total)
        assertNull(s.trendPercent)
        assertFalse(s.showsAverage)
        assertEquals("In den letzten 7 Tagen noch keine Fokuszeit.", FocusInsights.emptyLabel(s.range))
        val summary = FocusInsights.summary(emptyList(), today)
        assertFalse(summary.hasData)
        assertNull(summary.completionRate)
        assertEquals(28, summary.recentDays.size)
        assertTrue(summary.recentDays.last().isToday)
    }

    @Test fun oneSessionFillsTodayAndIsTheBestBucket() {
        val s = FocusInsights.series(listOf(r("a", today, 25)), RhythmRange.DAYS_7, today)
        assertEquals(6, s.bestIndex)
        assertEquals(25, s.buckets[6].total)
        assertEquals(25, s.buckets[6].quick)
        assertEquals(1, s.count)
        assertFalse("average needs two active buckets", s.showsAverage)
        assertEquals("Heute", FocusInsights.bucketLabel(s, 6))
        assertEquals("Mi", FocusInsights.bucketLabel(s, 5))
        assertEquals("Do., 26. Nov.", FocusInsights.bucketTitle(s, s.buckets[6]))
    }

    @Test fun sevenDaySeriesSplitsMissionAndQuickCountsOnlyCountedFocusAndComputesTrend() {
        val results = listOf(
            r("a", today, 20, mission = true),
            r("b", today, 10),
            r("c", today.minusDays(2), 30),
            r("x", today.minusDays(1), 0, outcome = SessionOutcome.CANCELED, counts = false), // not counted
            r("p", today.minusDays(9), 25), // previous 7-day period
        )
        val s = FocusInsights.series(results, RhythmRange.DAYS_7, today)
        assertEquals(60, s.total)
        assertEquals(20, s.mission)
        assertEquals(40, s.quick)
        assertEquals(3, s.count)
        // 30 today vs 30 two days ago: the earlier bucket wins the tie (PWA strict ">").
        assertEquals(4, s.bestIndex)
        assertEquals(30, s.buckets[4].total)
        assertTrue(s.showsAverage)
        assertEquals(25, s.previousTotal)
        assertEquals(140, s.trendPercent) // round((60-25)/25*100)
        assertEquals("+140 % ggü. vorherigen 7 Tagen", FocusInsights.trendLabel(s))
    }

    @Test fun bestBucketTieGoesToTheEarlierBucket() {
        val s = FocusInsights.series(listOf(r("a", today.minusDays(3), 25), r("b", today, 25)), RhythmRange.DAYS_7, today)
        assertEquals(3, s.bestIndex)
    }

    @Test fun fourWeekSeriesUsesMondayWeeksEndingThisWeek() {
        val monday = LocalDate.parse("2026-11-23")
        val results = listOf(r("a", monday, 40), r("b", monday.minusDays(1), 15), r("c", monday.minusDays(21), 10), r("old", monday.minusDays(28), 99))
        val s = FocusInsights.series(results, RhythmRange.WEEKS_4, today)
        assertEquals(listOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-16"), monday), s.buckets.map { it.start })
        assertEquals(listOf(10, 0, 15, 40), s.buckets.map { it.total })
        assertEquals(99, s.previousTotal)
        assertEquals("KW 48", FocusInsights.bucketLabel(s, 3))
        assertEquals("KW 48 · 23.11.–29.11.", FocusInsights.bucketTitle(s, s.buckets[3]))
    }

    @Test fun groupingUsesTheStoredLocalDayNotTheDeviceZone() {
        // Finished 23:30 in Berlin on the 25th = 22:30 UTC; it stays on the 25th even if read elsewhere.
        val late = r("late", LocalDate.parse("2026-11-25"), 25).copy(finishedAt = Instant.parse("2026-11-25T22:30:00Z"))
        val s = FocusInsights.series(listOf(late), RhythmRange.DAYS_7, today)
        assertEquals(25, s.buckets[5].total)
        assertEquals(0, s.buckets[6].total)
    }

    @Test fun summaryMatchesPwaComputeInsights() {
        val results = listOf(
            r("a", today, 25),
            r("b", today, 10, outcome = SessionOutcome.PARTIAL),
            r("c", today.minusDays(1), 50, mission = true),
            r("d", today.minusDays(2), 0, outcome = SessionOutcome.CANCELED, counts = false),
            r("e", today.minusDays(10), 30),
        )
        val s = FocusInsights.summary(results, today)
        assertEquals(5, s.sessionCount)
        assertEquals(4, s.countedCount)
        assertEquals(115, s.totalMinutes)
        assertEquals(29, s.averageMinutes) // round(115 / 4) = 28.75 -> 29
        assertEquals(60, s.completionRate) // 3 of 5 "Abgeschlossen"
        assertEquals(35, s.todayMinutes)
        assertEquals(PeriodTotal(today.minusDays(1), 50), s.bestDay)
        assertEquals(LocalDate.parse("2026-11-23"), s.bestWeek?.start)
        assertEquals(85, s.bestWeek?.minutes)
        assertEquals(50, s.longestSession?.minutes)
        assertEquals(SessionType.MISSION, s.longestSession?.type)
        assertEquals(2, s.streak.current)
        assertTrue(s.streak.todayActive)
        assertEquals(3, s.activeDays28)
        assertEquals(TypeTotal(1, 50), s.mission)
        assertEquals(TypeTotal(3, 65), s.quick)
        assertEquals(3, s.levelOf(s.recentDays.single { it.date == today.minusDays(1) }))
        assertEquals(0, s.levelOf(s.recentDays.single { it.date == today.minusDays(2) }))
    }

    @Test fun longHistoryKeepsOnlyRealDataAndTiesShowTheMostRecent() {
        val results = (0 until 400).map { i -> r("s$i", today.minusDays(i.toLong()), 20) }
        val s = FocusInsights.summary(results, today)
        assertEquals(400, s.countedCount)
        assertEquals(today, s.bestDay?.start) // all days equal: the most recent one is shown
        assertEquals(400, s.streak.current)
        assertEquals(28, s.activeDays28)
        val weeks = FocusInsights.series(results, RhythmRange.WEEKS_4, today)
        assertEquals(4 * 7 * 20 - 3 * 20, weeks.total) // this week has only Mon–Thu so far
    }
}
