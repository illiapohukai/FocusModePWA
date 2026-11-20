package io.github.illiapohukai.focusmode.core.domain.progress

import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.LocalProgress
import io.github.illiapohukai.focusmode.core.model.LocalStreak
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Local derived statistics (PWA `focus-insights.js`). Computed from local History only; the
 * result is LOCAL_DERIVED and must never be presented as verified or competitive.
 */
object ProgressCalculator {

    /** PWA `countedFocus`: at least one minute and not explicitly excluded. */
    fun isCounted(r: FocusSessionResult): Boolean = r.actualMinutes >= 1 && r.countsAsFocus

    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun compute(results: List<FocusSessionResult>, today: LocalDate): LocalProgress {
        val counted = results.filter(::isCounted)
        val weekStart = weekStart(today)
        val todayCounted = counted.filter { it.localDate == today }
        val weekCounted = counted.filter { !it.localDate.isBefore(weekStart) && !it.localDate.isAfter(today) }
        return LocalProgress(
            todayMinutes = todayCounted.sumOf { it.actualMinutes },
            todaySessions = todayCounted.size,
            weekMinutes = weekCounted.sumOf { it.actualMinutes },
            weekSessions = weekCounted.size,
            totalMinutes = counted.sumOf { it.actualMinutes },
            countedSessions = counted.size,
            streak = streak(counted.map { it.localDate }.toSet(), today),
        )
    }

    /**
     * Current streak counts back from today when today already has focus, otherwise from
     * yesterday (an unfinished today does not break the streak). Best streak is the longest run.
     */
    fun streak(activeDays: Set<LocalDate>, today: LocalDate): LocalStreak {
        var current = 0
        var cursor = if (today in activeDays) today else today.minusDays(1)
        while (cursor in activeDays) {
            current++
            cursor = cursor.minusDays(1)
        }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in activeDays.sorted()) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return LocalStreak(current = current, best = best, todayActive = today in activeDays)
    }
}
