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

/** PWA "Fokus-Bilanz" of one period: counted minutes, all Sessions and their outcomes. */
data class PeriodSummary(val countedMinutes: Int = 0, val sessions: Int = 0, val completed: Int = 0, val partial: Int = 0, val cancelled: Int = 0)

/** Outcome node shape shared by History and Home Activity; [fill] is the filled share of the node. */
enum class OutcomeNode(val fill: Float) { FILLED(1f), HALF(0.5f), OPEN(0f) }

/** Display classification (PWA `classifySessionOutcomeV173`). */
enum class OutcomeBadge(val label: String) {
    COMPLETED("Abgeschlossen"),
    PARTIAL("Teilweise"),
    CANCELLED("Abgebrochen"),
    INTERRUPTED("Unterbrochen"),
}

object HistoryQueries {
    /**
     * The one History order: newest end first; a tie on the same instant falls back to the
     * Session id (descending), exactly like the History DAO, so the order never depends on input order.
     */
    val ORDER: Comparator<FocusSessionResult> = compareByDescending<FocusSessionResult> { it.finishedAt }.thenByDescending { it.sessionId.value }

    fun ordered(results: List<FocusSessionResult>): List<FocusSessionResult> = results.sortedWith(ORDER)

    fun filter(results: List<FocusSessionResult>, filter: HistoryFilter, today: LocalDate): List<FocusSessionResult> {
        val weekStart = ProgressCalculator.weekStart(today)
        return results.filter {
            when (filter) {
                HistoryFilter.TODAY -> it.localDate == today
                HistoryFilter.WEEK -> !it.localDate.isBefore(weekStart)
                HistoryFilter.ALL -> true
            }
        }.sortedWith(ORDER)
    }

    /** Groups by local day, newest day first; each day newest record first. */
    fun groupByDay(results: List<FocusSessionResult>): List<HistoryDay> =
        results.sortedWith(ORDER)
            .groupBy { it.localDate }
            .map { (date, items) -> HistoryDay(date, items) }
            .sortedByDescending { it.date }

    /** PWA `renderSessionHistory` overview counts for an already filtered period. */
    fun periodSummary(filtered: List<FocusSessionResult>): PeriodSummary {
        val badges = filtered.map(::badge)
        return PeriodSummary(
            countedMinutes = filtered.filter(ProgressCalculator::isCounted).sumOf { it.actualMinutes },
            sessions = filtered.size,
            completed = badges.count { it == OutcomeBadge.COMPLETED },
            partial = badges.count { it == OutcomeBadge.PARTIAL },
            cancelled = badges.count { it == OutcomeBadge.CANCELLED || it == OutcomeBadge.INTERRUPTED },
        )
    }

    /** Outcome node shape (PWA Activity): filled, half-filled or open. Never colour alone. */
    fun node(badge: OutcomeBadge): OutcomeNode = when (badge) {
        OutcomeBadge.COMPLETED -> OutcomeNode.FILLED
        OutcomeBadge.PARTIAL -> OutcomeNode.HALF
        OutcomeBadge.CANCELLED, OutcomeBadge.INTERRUPTED -> OutcomeNode.OPEN
    }

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
