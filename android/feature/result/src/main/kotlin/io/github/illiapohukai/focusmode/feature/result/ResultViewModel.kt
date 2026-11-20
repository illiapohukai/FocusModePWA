package io.github.illiapohukai.focusmode.feature.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.LocalStreak
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ResultUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val headline: String = "",
    val eyebrow: String = "",
    val minutes: Int = 0,
    val plannedMinutes: Int = 0,
    val task: String = "",
    val timeRange: String = "",
    val badge: OutcomeBadge = OutcomeBadge.COMPLETED,
    val countsAsFocus: Boolean = true,
    val streak: LocalStreak = LocalStreak(0, 0, false),
    val imported: Boolean = false,
    val endNote: String? = null,
    val awaitingReview: Boolean = false,
    val reviewSaving: Boolean = false,
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    history: SessionHistoryRepository,
    private val controller: FocusSessionController,
    private val clock: FocusClock,
) : ViewModel() {

    private val rawId = runCatching { savedState.toRoute<ResultDestination>().sessionId }.getOrNull()
    private val sessionId = rawId?.takeIf(EntityIds::isSafe)?.let(::SessionId)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<ResultUiState> = combine(
        sessionId?.let(history::observeResult) ?: flowOf(null),
        history.observeResults(),
        controller.state,
        saving,
    ) { result, all, state, isSaving ->
        if (sessionId == null) return@combine ResultUiState(loading = false, notFound = true)
        if (result == null) return@combine ResultUiState(loading = false, notFound = true)
        val zone = clock.zone()
        val range = TIME.format(ZonedDateTime.ofInstant(result.startedAt, zone)) + "–" + TIME.format(ZonedDateTime.ofInstant(result.finishedAt, zone))
        val badge = HistoryQueries.badge(result)
        val review = state is SessionState.AwaitingReview && state.result.sessionId == sessionId
        ResultUiState(
            loading = false,
            headline = when {
                review -> "Wie lief dein Schritt?"
                !result.countsAsFocus -> "Sitzung nicht gezählt"
                result.outcome == SessionOutcome.COMPLETED -> "Fokus abgeschlossen"
                else -> "Fokuszeit gespeichert"
            },
            eyebrow = badge.label,
            minutes = result.actualMinutes,
            plannedMinutes = result.plannedMinutes,
            task = result.task,
            timeRange = range + " · " + DAY.format(result.localDate),
            badge = badge,
            countsAsFocus = result.countsAsFocus,
            streak = ProgressCalculator.compute(all, clock.today()).streak,
            imported = result.origin == DataOrigin.IMPORTED_PWA,
            endNote = result.endNote?.label,
            awaitingReview = review,
            reviewSaving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState())

    fun submitReview(stepCompleted: Boolean, rating: Int?) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            controller.submitMissionReview(stepCompleted, rating)
            saving.value = false
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
    }
}
