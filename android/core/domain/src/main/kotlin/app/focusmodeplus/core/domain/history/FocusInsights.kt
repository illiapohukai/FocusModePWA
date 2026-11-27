package app.focusmodeplus.core.domain.history

import app.focusmodeplus.core.domain.progress.ProgressCalculator
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.LocalStreak
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.model.TrustLevel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields

/** PWA `FocusInsights.RANGES`: 7 days ending today, or 4 calendar weeks (Monday) ending this week. */
enum class RhythmRange(val label: String, val buckets: Int, val stepDays: Long, val periodLabel: String, val previousLabel: String) {
    DAYS_7("7 Tage", 7, 1, "Letzte 7 Tage", "vorherigen 7 Tagen"),
    WEEKS_4("4 Wochen", 4, 7, "Letzte 4 Wochen", "vorherigen 4 Wochen"),
}

/** One chart bucket: counted focus minutes split by Session type. [end] is exclusive. */
data class RhythmBucket(
    val start: LocalDate,
    val end: LocalDate,
    val total: Int = 0,
    val quick: Int = 0,
    val mission: Int = 0,
    val count: Int = 0,
) {
    internal fun plus(r: FocusSessionResult): RhythmBucket = if (r.type == SessionType.MISSION) {
        copy(total = total + r.actualMinutes, mission = mission + r.actualMinutes, count = count + 1)
    } else {
        copy(total = total + r.actualMinutes, quick = quick + r.actualMinutes, count = count + 1)
    }
}

data class RhythmSeries(
    val range: RhythmRange,
    val buckets: List<RhythmBucket>,
    val total: Int,
    val quick: Int,
    val mission: Int,
    val count: Int,
    /** Counted minutes in the equally long period before the chart (for the trend). */
    val previousTotal: Int,
    /** Highest non-empty bucket (earliest wins a tie, like the PWA); -1 when the period is empty. */
    val bestIndex: Int,
    val activeBuckets: Int,
) {
    /** PWA trend: only when both periods have focus. Rounded like `Math.round`. */
    val trendPercent: Int?
        get() = if (previousTotal > 0 && total > 0) Math.round((total - previousTotal) * 100.0 / previousTotal).toInt() else null

    /** The dashed average line is drawn only with at least two active buckets (PWA). */
    val showsAverage: Boolean get() = activeBuckets >= 2
    val average: Double get() = total.toDouble() / buckets.size
}

data class PeriodTotal(val start: LocalDate, val minutes: Int)
data class LongestSession(val minutes: Int, val date: LocalDate, val type: SessionType)
data class TypeTotal(val count: Int = 0, val minutes: Int = 0)
data class RecentDay(val date: LocalDate, val minutes: Int, val isToday: Boolean)

/** PWA `computeInsights` ("Dein Fokusverlauf"). Every value is derived from stored History. */
data class FocusInsightsSummary(
    val sessionCount: Int,
    val countedCount: Int,
    val totalMinutes: Int,
    val averageMinutes: Int,
    /** Share of all Sessions classified "Abgeschlossen", in percent; null without Sessions. */
    val completionRate: Int?,
    val todayMinutes: Int,
    val bestDay: PeriodTotal?,
    val bestWeek: PeriodTotal?,
    val longestSession: LongestSession?,
    val streak: LocalStreak,
    /** The last 28 days, oldest first, today last. */
    val recentDays: List<RecentDay>,
    val activeDays28: Int,
    val quick: TypeTotal,
    val mission: TypeTotal,
    val trust: TrustLevel = TrustLevel.LOCAL_DERIVED,
) {
    val hasData: Boolean get() = countedCount > 0

    /** PWA consistency-grid level 0–3 relative to the strongest of the 28 days. */
    fun levelOf(day: RecentDay): Int {
        val max = recentDays.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
        return when {
            day.minutes <= 0 -> 0
            day.minutes >= max * 0.66 -> 3
            day.minutes >= max * 0.33 -> 2
            else -> 1
        }
    }
}

/**
 * The single Focus aggregation owner for History, the Fokus-Rhythmus chart and "Dein Fokusverlauf"
 * (PWA `src/features/history/focus-insights.js`). Pure and deterministic: Sessions are grouped by
 * the local calendar day stored at finalization, so a timezone change never moves a Session to
 * another day. Only counted Sessions (≥ 1 minute, counting as focus) contribute minutes.
 */
object FocusInsights {

    fun series(results: List<FocusSessionResult>, range: RhythmRange, today: LocalDate): RhythmSeries {
        val lastStart = if (range == RhythmRange.WEEKS_4) ProgressCalculator.weekStart(today) else today
        val firstStart = lastStart.minusDays(range.stepDays * (range.buckets - 1))
        val buckets = MutableList(range.buckets) { i ->
            val start = firstStart.plusDays(range.stepDays * i)
            RhythmBucket(start, start.plusDays(range.stepDays))
        }
        val rangeEnd = buckets.last().end
        val previousStart = firstStart.minusDays(range.stepDays * range.buckets)
        var previous = 0
        results.filter(ProgressCalculator::isCounted).forEach { r ->
            val d = r.localDate
            when {
                !d.isBefore(firstStart) && d.isBefore(rangeEnd) -> {
                    val i = buckets.indexOfFirst { !d.isBefore(it.start) && d.isBefore(it.end) }
                    if (i >= 0) buckets[i] = buckets[i].plus(r)
                }
                !d.isBefore(previousStart) && d.isBefore(firstStart) -> previous += r.actualMinutes
            }
        }
        var best = -1
        buckets.forEachIndexed { i, b -> if (b.total > 0 && (best == -1 || b.total > buckets[best].total)) best = i }
        return RhythmSeries(
            range = range,
            buckets = buckets,
            total = buckets.sumOf { it.total },
            quick = buckets.sumOf { it.quick },
            mission = buckets.sumOf { it.mission },
            count = buckets.sumOf { it.count },
            previousTotal = previous,
            bestIndex = best,
            activeBuckets = buckets.count { it.total > 0 },
        )
    }

    /**
     * [results] in History order (newest first). Ties keep the PWA order: the first record seen
     * wins, so the most recent day, week or Session is shown when two are equally strong.
     */
    fun summary(results: List<FocusSessionResult>, today: LocalDate): FocusInsightsSummary {
        val ordered = HistoryQueries.ordered(results)
        val counted = ordered.filter(ProgressCalculator::isCounted)
        val days = LinkedHashMap<LocalDate, Int>()
        val weeks = LinkedHashMap<LocalDate, Int>()
        var longest: LongestSession? = null
        var quick = TypeTotal()
        var mission = TypeTotal()
        counted.forEach { r ->
            val m = r.actualMinutes
            days.merge(r.localDate, m, Int::plus)
            weeks.merge(ProgressCalculator.weekStart(r.localDate), m, Int::plus)
            if (longest == null || m > longest!!.minutes) longest = LongestSession(m, r.localDate, r.type)
            if (r.type == SessionType.MISSION) mission = TypeTotal(mission.count + 1, mission.minutes + m)
            else quick = TypeTotal(quick.count + 1, quick.minutes + m)
        }
        val total = counted.sumOf { it.actualMinutes }
        val recent = (27 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            RecentDay(d, days[d] ?: 0, back == 0)
        }
        val completed = ordered.count { HistoryQueries.badge(it) == OutcomeBadge.COMPLETED }
        return FocusInsightsSummary(
            sessionCount = ordered.size,
            countedCount = counted.size,
            totalMinutes = total,
            averageMinutes = if (counted.isEmpty()) 0 else Math.round(total.toDouble() / counted.size).toInt(),
            completionRate = if (ordered.isEmpty()) null else Math.round(completed * 100.0 / ordered.size).toInt(),
            todayMinutes = days[today] ?: 0,
            bestDay = pickMax(days),
            bestWeek = pickMax(weeks),
            longestSession = longest,
            streak = ProgressCalculator.streak(days.keys, today),
            recentDays = recent,
            activeDays28 = recent.count { it.minutes > 0 },
            quick = quick,
            mission = mission,
        )
    }

    private fun pickMax(map: Map<LocalDate, Int>): PeriodTotal? {
        var best: PeriodTotal? = null
        map.forEach { (start, minutes) -> if (best == null || minutes > best!!.minutes) best = PeriodTotal(start, minutes) }
        return best
    }

    // ---------------------------------------------------------------- PWA labels (locale-independent)

    private val WEEKDAY_SHORT = mapOf(
        DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi", DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
    )
    private val WEEKDAY_LONG = mapOf(
        DayOfWeek.MONDAY to "Montag", DayOfWeek.TUESDAY to "Dienstag", DayOfWeek.WEDNESDAY to "Mittwoch", DayOfWeek.THURSDAY to "Donnerstag",
        DayOfWeek.FRIDAY to "Freitag", DayOfWeek.SATURDAY to "Samstag", DayOfWeek.SUNDAY to "Sonntag",
    )
    private val MONTH_SHORT = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez.")

    fun isoWeek(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** PWA `formatDayShort`: "Mo., 3. März". */
    fun dayShort(date: LocalDate): String = "${WEEKDAY_SHORT.getValue(date.dayOfWeek)}., ${date.dayOfMonth}. ${MONTH_SHORT[date.monthValue - 1]}"

    /** PWA `formatDayLong`: "Montag, 3. März". */
    fun dayLong(date: LocalDate): String = "${WEEKDAY_LONG.getValue(date.dayOfWeek)}, ${date.dayOfMonth}. ${MONTH_SHORT[date.monthValue - 1]}"

    /** PWA `formatWeekRange`: "KW 12 · 16.3.–22.3.". */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return "KW ${isoWeek(start)} · ${start.dayOfMonth}.${start.monthValue}.–${end.dayOfMonth}.${end.monthValue}."
    }

    /** X-axis label: weekday short ("Heute" for the last day) or "KW n". */
    fun bucketLabel(series: RhythmSeries, index: Int): String {
        val b = series.buckets[index]
        return if (series.range == RhythmRange.WEEKS_4) "KW ${isoWeek(b.start)}"
        else if (index == series.buckets.lastIndex) "Heute" else WEEKDAY_SHORT.getValue(b.start.dayOfWeek)
    }

    fun bucketTitle(series: RhythmSeries, b: RhythmBucket): String =
        if (series.range == RhythmRange.WEEKS_4) weekRange(b.start) else dayShort(b.start)

    fun sessionsLabel(n: Int): String = if (n == 1) "1 Sitzung" else "$n Sitzungen"

    /** Spoken and tooltip description of one bucket (PWA `describeBucket`). */
    fun describeBucket(series: RhythmSeries, b: RhythmBucket): String =
        bucketTitle(series, b) + ": " + (if (b.total > 0) HistoryQueries.formatMinutes(b.total) else "keine Fokuszeit") + ", " + sessionsLabel(b.count)

    fun emptyLabel(range: RhythmRange): String =
        if (range == RhythmRange.WEEKS_4) "In den letzten 4 Wochen noch keine Fokuszeit." else "In den letzten 7 Tagen noch keine Fokuszeit."

    fun trendLabel(series: RhythmSeries): String? = series.trendPercent?.let { pct ->
        (if (pct > 0) "+" else "") + "$pct % ggü. ${series.range.previousLabel}"
    }
}
