package app.focusmodeplus.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import app.focusmodeplus.core.domain.history.FocusInsights
import app.focusmodeplus.core.domain.history.FocusInsightsSummary
import app.focusmodeplus.core.domain.history.HistoryFilter
import app.focusmodeplus.core.domain.history.HistoryFormat
import app.focusmodeplus.core.domain.history.HistoryQueries
import app.focusmodeplus.core.domain.history.OutcomeBadge
import app.focusmodeplus.core.domain.history.PeriodSummary
import app.focusmodeplus.core.domain.history.RhythmRange
import app.focusmodeplus.core.domain.history.RhythmSeries
import app.focusmodeplus.core.domain.mission.MissionThemes
import app.focusmodeplus.core.domain.progress.ProgressCalculator
import app.focusmodeplus.core.domain.repository.MissionRepository
import app.focusmodeplus.core.domain.repository.SessionHistoryRepository
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.LocalProgress
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionTheme
import app.focusmodeplus.core.model.SessionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/**
 * One History row. [missionContext] is "Mission-Titel · Schritt 02" for Mission Sessions (PWA
 * `history-meta`), resolved from the current Mission; it is null when the Mission was deleted.
 */
data class HistoryRow(
    val sessionId: String,
    val title: String,
    val timeRange: String,
    val minutes: String,
    val badge: OutcomeBadge,
    val mission: Boolean,
    val missionContext: String? = null,
    val theme: MissionTheme? = null,
) {
    val node get() = HistoryQueries.node(badge)
}

data class HistorySection(val label: String, val focusLabel: String, val rows: List<HistoryRow>)

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.TODAY,
    val sections: List<HistorySection> = emptyList(),
    val progress: LocalProgress = LocalProgress.EMPTY,
    val totalCount: Int = 0,
    /** The selected period (PWA "Fokus-Bilanz"): counted minutes, Sessions and outcomes. */
    val period: PeriodSummary = PeriodSummary(),
    /** PWA "Fokus-Rhythmus" (7 Tage / 4 Wochen); null only while loading. */
    val rhythm: RhythmSeries? = null,
    val loading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    history: SessionHistoryRepository,
    missions: MissionRepository,
    private val clock: FocusClock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val filter = savedState.getStateFlow(KEY_FILTER, HistoryFilter.TODAY.name)
    private val range = savedState.getStateFlow(KEY_RANGE, RhythmRange.DAYS_7.name)

    val uiState: StateFlow<HistoryUiState> = combine(history.observeResults(), missions.observeMissions(), filter, range) { results, all, f, r ->
        build(results, all, HistoryFilter.valueOf(f), RhythmRange.valueOf(r))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setFilter(f: HistoryFilter) { savedState[KEY_FILTER] = f.name }
    fun setRange(r: RhythmRange) { savedState[KEY_RANGE] = r.name }

    private fun build(results: List<FocusSessionResult>, all: List<Mission>, f: HistoryFilter, r: RhythmRange): HistoryUiState {
        val today = clock.today()
        val zone = clock.zone()
        val byId = all.associateBy { it.id }
        val themes = MissionThemes.resolve(all)
        val filtered = HistoryQueries.filter(results, f, today)
        return HistoryUiState(
            filter = f,
            period = HistoryQueries.periodSummary(filtered),
            sections = HistoryQueries.groupByDay(filtered).map { day ->
                HistorySection(
                    label = HistoryFormat.dayLabel(day.date, today),
                    focusLabel = HistoryQueries.formatMinutes(day.focusMinutes) + " Fokus",
                    rows = day.results.map { row(it, zone, byId, themes) },
                )
            },
            progress = ProgressCalculator.compute(results, today),
            totalCount = results.size,
            rhythm = FocusInsights.series(results, r, today),
            loading = false,
        )
    }

    private fun row(it: FocusSessionResult, zone: ZoneId, byId: Map<MissionId, Mission>, themes: Map<MissionId, MissionTheme>) = HistoryRow(
        sessionId = it.sessionId.value,
        title = it.title,
        timeRange = HistoryFormat.timeRange(it, zone),
        minutes = HistoryFormat.minutes(it),
        badge = HistoryQueries.badge(it),
        mission = it.type == SessionType.MISSION,
        missionContext = HistoryFormat.missionContext(it, byId),
        theme = it.missionLink?.let { l -> themes[l.missionId] },
    )

    private companion object {
        const val KEY_FILTER = "history_filter"
        const val KEY_RANGE = "history_rhythm_range"
    }
}

data class InsightsUiState(
    val loading: Boolean = true,
    val summary: FocusInsightsSummary? = null,
    val rhythm: RhythmSeries? = null,
)

/** "Dein Fokusverlauf" (PWA `app-screen-stats`): long-term local insights from the History owner. */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    history: SessionHistoryRepository,
    private val clock: FocusClock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** The PWA stats chart opens on 4 Wochen (`data-default-range="4w"`). */
    private val range = savedState.getStateFlow(KEY_RANGE, RhythmRange.WEEKS_4.name)

    val uiState: StateFlow<InsightsUiState> = combine(history.observeResults(), range) { results, r ->
        val today = clock.today()
        InsightsUiState(loading = false, summary = FocusInsights.summary(results, today), rhythm = FocusInsights.series(results, RhythmRange.valueOf(r), today))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setRange(r: RhythmRange) { savedState[KEY_RANGE] = r.name }

    private companion object { const val KEY_RANGE = "insights_rhythm_range" }
}
