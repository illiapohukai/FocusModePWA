package io.github.illiapohukai.focusmode.feature.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.domain.mission.AdaptationRun
import io.github.illiapohukai.focusmode.core.domain.mission.MissionAdaptationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.LocalStreak
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionReview
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionType
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

/** The Mission this Session belongs to, read from the one Mission owner (same progress rules everywhere). */
data class MissionContext(
    val missionId: String,
    val title: String,
    val theme: MissionTheme,
    val stepTitle: String,
    /** 1-based position of the reviewed step, or 0 when the step no longer exists. */
    val stepNumber: Int,
    val total: Int,
    val completed: Int,
    val percent: Int,
    /** The reviewed step is already completed (after an "Abgeschlossen" review). */
    val stepDone: Boolean,
    val nextTitle: String?,
    val nextMinutes: Int?,
    val missionCompleted: Boolean,
    val missionActive: Boolean,
)

sealed interface AdaptationUi {
    data object Running : AdaptationUi
    data class Adapted(val message: String, val summary: String) : AdaptationUi
    data class Failed(val offline: Boolean) : AdaptationUi
}

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
    val isMission: Boolean = false,
    val mission: MissionContext? = null,
    val selectedOutcome: SessionOutcome = SessionOutcome.PARTIAL,
    val rating: Int = 3,
    val note: String = "",
    val adaptation: AdaptationUi? = null,
    val reviewNote: String? = null,
) {
    /** PWA: adaptation is offered when this Mission would still have open steps after the result. */
    val canOfferAdaptation: Boolean
        get() = mission != null && mission.missionActive && !(selectedOutcome == SessionOutcome.COMPLETED && !mission.stepDone && mission.completed + 1 >= mission.total)
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    history: SessionHistoryRepository,
    missions: MissionRepository,
    private val controller: FocusSessionController,
    private val adaptationOwner: MissionAdaptationOwner,
    private val clock: FocusClock,
) : ViewModel() {

    private val rawId = runCatching { savedState.toRoute<ResultDestination>().sessionId }.getOrNull()
    private val sessionId = rawId?.takeIf(EntityIds::isSafe)?.let(::SessionId)
    private val saving = MutableStateFlow(false)
    private val form = MutableStateFlow(
        ReviewForm(
            outcome = savedState.get<String>(KEY_OUTCOME)?.let(SessionOutcome::fromWire),
            rating = savedState.get<Int>(KEY_RATING) ?: 3,
            note = savedState.get<String>(KEY_NOTE) ?: "",
            adaptRequested = savedState.get<Boolean>(KEY_ADAPT) ?: false,
        ),
    )

    private data class ReviewForm(val outcome: SessionOutcome?, val rating: Int, val note: String, val adaptRequested: Boolean)

    private val sources = combine(
        sessionId?.let(history::observeResult) ?: flowOf(null),
        history.observeResults(),
        controller.state,
        missions.observeMissions(),
    ) { result, all, state, allMissions -> Sources(result, all, state, allMissions) }

    private data class Sources(
        val result: io.github.illiapohukai.focusmode.core.model.FocusSessionResult?,
        val all: List<io.github.illiapohukai.focusmode.core.model.FocusSessionResult>,
        val state: SessionState,
        val missions: List<Mission>,
    )

    val uiState: StateFlow<ResultUiState> = combine(sources, saving, form, adaptationOwner.run) { src, isSaving, f, run ->
        val result = src.result
        if (sessionId == null || result == null) return@combine ResultUiState(loading = false, notFound = true)
        val zone = clock.zone()
        val range = TIME.format(ZonedDateTime.ofInstant(result.startedAt, zone)) + "–" + TIME.format(ZonedDateTime.ofInstant(result.finishedAt, zone))
        val badge = HistoryQueries.badge(result)
        val review = src.state is SessionState.AwaitingReview && src.state.result.sessionId == sessionId
        val defaultOutcome = if (result.outcome == SessionOutcome.COMPLETED) SessionOutcome.COMPLETED else SessionOutcome.PARTIAL
        val mission = result.missionLink?.let { link -> src.missions.firstOrNull { it.id == link.missionId } }?.let { m ->
            val themes = MissionThemes.resolve(src.missions)
            val step = m.steps.firstOrNull { it.id == result.missionLink!!.stepId }
            val next = m.currentStep?.takeIf { it.id != step?.id || step.status != MissionStepStatus.COMPLETED }
            MissionContext(
                missionId = m.id.value,
                title = m.title,
                theme = themes[m.id] ?: MissionTheme.AUBERGINE,
                stepTitle = step?.title ?: result.title,
                stepNumber = step?.let { m.steps.indexOf(it) + 1 } ?: 0,
                total = m.steps.size,
                completed = m.completedStepCount,
                percent = m.progressPercent,
                stepDone = step?.status == MissionStepStatus.COMPLETED,
                nextTitle = next?.title,
                nextMinutes = next?.durationMinutes,
                missionCompleted = m.status == MissionStatus.COMPLETED,
                missionActive = m.status == MissionStatus.ACTIVE,
            )
        }
        val adaptation = when (run) {
            is AdaptationRun.Running -> if (run.sessionId == sessionId) AdaptationUi.Running else null
            is AdaptationRun.Adapted -> if (run.sessionId == sessionId) AdaptationUi.Adapted(run.message, run.summary) else null
            is AdaptationRun.Failed -> if (run.sessionId == sessionId) AdaptationUi.Failed(run.error == GenerationError.Offline) else null
            AdaptationRun.Idle -> null
        }?.takeIf { f.adaptRequested }
        ResultUiState(
            loading = false,
            headline = when {
                review -> "Wie weit bist du gekommen?"
                !result.countsAsFocus -> "Sitzung nicht gezählt"
                result.outcome == SessionOutcome.COMPLETED -> if (result.type == SessionType.MISSION) "Schritt erledigt" else "Fokus abgeschlossen"
                else -> "Fokuszeit gespeichert"
            },
            eyebrow = badge.label,
            minutes = result.actualMinutes,
            plannedMinutes = result.plannedMinutes,
            task = result.task,
            timeRange = range + " · " + DAY.format(result.localDate),
            badge = badge,
            countsAsFocus = result.countsAsFocus,
            streak = ProgressCalculator.compute(src.all, clock.today()).streak,
            imported = result.origin == DataOrigin.IMPORTED_PWA,
            endNote = result.endNote?.label,
            awaitingReview = review,
            reviewSaving = isSaving,
            isMission = result.type == SessionType.MISSION,
            mission = mission,
            selectedOutcome = f.outcome ?: defaultOutcome,
            rating = f.rating,
            note = f.note,
            adaptation = adaptation,
            reviewNote = result.reviewNote,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultUiState())

    fun selectOutcome(outcome: SessionOutcome) = updateForm { it.copy(outcome = outcome) }
    fun setRating(rating: Int) = updateForm { it.copy(rating = rating.coerceIn(MissionReview.RATINGS)) }
    fun setNote(note: String) = updateForm { it.copy(note = note.take(MissionReview.NOTE_MAX)) }

    /** "Ohne Anpassung fortfahren": commits the result exactly once; the plan stays unchanged. */
    fun submit() = commit(adapt = false)

    /** "Plan mit AI anpassen": commits the result first (never lost), then asks for an adaptation. */
    fun submitAndAdapt() = commit(adapt = true)

    private fun commit(adapt: Boolean) {
        if (saving.value) return
        val s = uiState.value
        saving.value = true
        viewModelScope.launch {
            val ok = !s.awaitingReview || controller.submitMissionReview(MissionReview(s.selectedOutcome, s.rating, s.note))
            if (ok && adapt && sessionId != null) {
                updateForm { it.copy(adaptRequested = true) }
                adaptationOwner.adapt(sessionId)
            }
            saving.value = false
        }
    }

    /** Legacy two-outcome entry point (kept for the v18 review contract). */
    fun submitReview(stepCompleted: Boolean, rating: Int?) {
        selectOutcome(if (stepCompleted) SessionOutcome.COMPLETED else SessionOutcome.PARTIAL)
        rating?.let(::setRating)
        submit()
    }

    fun retryAdaptation() {
        val id = sessionId ?: return
        viewModelScope.launch { adaptationOwner.adapt(id) }
    }

    /** Continue without (further) adaptation: the committed result and the existing plan stay. */
    fun skipAdaptation() {
        viewModelScope.launch {
            adaptationOwner.cancel()
            updateForm { it.copy(adaptRequested = false) }
        }
    }

    private fun updateForm(f: (ReviewForm) -> ReviewForm) {
        val next = f(form.value)
        form.value = next
        savedState[KEY_OUTCOME] = next.outcome?.wire
        savedState[KEY_RATING] = next.rating
        savedState[KEY_NOTE] = next.note
        savedState[KEY_ADAPT] = next.adaptRequested
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
        const val KEY_OUTCOME = "review_outcome"
        const val KEY_RATING = "review_rating"
        const val KEY_NOTE = "review_note"
        const val KEY_ADAPT = "adapt_requested"
    }
}
