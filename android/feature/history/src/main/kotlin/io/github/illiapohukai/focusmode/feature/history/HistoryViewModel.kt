package io.github.illiapohukai.focusmode.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.history.HistoryFilter
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.LocalProgress
import io.github.illiapohukai.focusmode.core.model.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HistoryRow(val sessionId: String, val title: String, val timeRange: String, val minutes: String, val badge: OutcomeBadge, val mission: Boolean)
data class HistorySection(val label: String, val focusLabel: String, val rows: List<HistoryRow>)

data class PeriodSummary(val countedMinutes: Int = 0, val sessions: Int = 0, val completed: Int = 0, val partial: Int = 0, val cancelled: Int = 0)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val sections: List<HistorySection> = emptyList(),
    val progress: LocalProgress = LocalProgress.EMPTY,
    val totalCount: Int = 0,
    /** The selected period (PWA "Fokus-Bilanz"): counted minutes, Sessions and outcomes. */
    val period: PeriodSummary = PeriodSummary(),
    val loading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(history: SessionHistoryRepository, private val clock: FocusClock) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.TODAY)

    val uiState: StateFlow<HistoryUiState> = combine(history.observeResults(), filter) { results, f ->
        val today = clock.today()
        val zone = clock.zone()
        val filtered = HistoryQueries.filter(results, f, today)
        val badges = filtered.map(HistoryQueries::badge)
        HistoryUiState(
            filter = f,
            period = PeriodSummary(
                countedMinutes = filtered.filter(ProgressCalculator::isCounted).sumOf { it.actualMinutes },
                sessions = filtered.size,
                completed = badges.count { it == OutcomeBadge.COMPLETED },
                partial = badges.count { it == OutcomeBadge.PARTIAL },
                cancelled = badges.count { it == OutcomeBadge.CANCELLED || it == OutcomeBadge.INTERRUPTED },
            ),
            sections = HistoryQueries.groupByDay(filtered).map { day ->
                HistorySection(
                    label = dayLabel(day.date, today),
                    focusLabel = HistoryQueries.formatMinutes(day.focusMinutes) + " Fokus",
                    rows = day.results.map {
                        HistoryRow(
                            it.sessionId.value, it.title,
                            TIME.format(ZonedDateTime.ofInstant(it.startedAt, zone)) + "–" + TIME.format(ZonedDateTime.ofInstant(it.finishedAt, zone)),
                            HistoryQueries.formatMinutes(it.actualMinutes), HistoryQueries.badge(it), it.type == SessionType.MISSION,
                        )
                    },
                )
            },
            progress = ProgressCalculator.compute(results, today),
            totalCount = results.size,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(f: HistoryFilter) { filter.value = f }

    private fun dayLabel(date: LocalDate, today: LocalDate) = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        else -> DAY.format(date)
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    }
}
