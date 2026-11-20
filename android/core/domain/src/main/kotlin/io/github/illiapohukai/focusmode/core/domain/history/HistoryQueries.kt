package io.github.illiapohukai.focusmode.core.domain.history

import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import java.time.LocalDate

/** PWA History filters. */
enum class HistoryFilter(val label: String) { TODAY("Heute"), WEEK("Woche"), ALL("Alle") }

data class HistoryDay(val date: LocalDate, val results: List<FocusSessionResult>) {
    val focusMinutes: Int get() = results.filter(ProgressCalculator::isCounted).sumOf { it.actualMinutes }
}

/** Display classification (PWA `classifySessionOutcomeV173`). */
enum class OutcomeBadge(val label: String) {
    COMPLETED("Abgeschlossen"),
    PARTIAL("Teilweise"),
    CANCELLED("Abgebrochen"),
    INTERRUPTED("Unterbrochen"),
}

object HistoryQueries {
    fun filter(results: List<FocusSessionResult>, filter: HistoryFilter, today: LocalDate): List<FocusSessionResult> {
        val weekStart = ProgressCalculator.weekStart(today)
        return results.filter {
            when (filter) {
                HistoryFilter.TODAY -> it.localDate == today
                HistoryFilter.WEEK -> !it.localDate.isBefore(weekStart)
                HistoryFilter.ALL -> true
            }
        }.sortedByDescending { it.finishedAt }
    }

    /** Groups by local day, newest day first; each day newest record first. */
    fun groupByDay(results: List<FocusSessionResult>): List<HistoryDay> =
        results.sortedByDescending { it.finishedAt }
            .groupBy { it.localDate }
            .map { (date, items) -> HistoryDay(date, items) }
            .sortedByDescending { it.date }

    fun badge(r: FocusSessionResult): OutcomeBadge = when {
        r.status == SessionStatus.CANCELLED -> OutcomeBadge.CANCELLED
        r.status == SessionStatus.INTERRUPTED -> OutcomeBadge.INTERRUPTED
        r.outcome == SessionOutcome.PARTIAL -> OutcomeBadge.PARTIAL
        r.outcome == SessionOutcome.CANCELED -> OutcomeBadge.CANCELLED
        else -> OutcomeBadge.COMPLETED
    }

    /** PWA `formatHistoryDuration`. */
    fun formatMinutes(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        return when {
            m == 0 -> "0 Min."
            m < 60 -> "$m Min."
            m % 60 == 0 -> "${m / 60} Std."
            else -> "${m / 60} Std. ${m % 60} Min."
        }
    }
}
