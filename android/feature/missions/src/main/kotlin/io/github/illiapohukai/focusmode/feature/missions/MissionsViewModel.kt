package io.github.illiapohukai.focusmode.feature.missions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.mission.StepState
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionValidationError
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissionListItem(val mission: Mission, val theme: MissionTheme)

data class MissionsUiState(
    val active: List<MissionListItem> = emptyList(),
    val finished: List<MissionListItem> = emptyList(),
    val importAvailable: Boolean = false,
    /** A generation draft exists (form, failed request or an unaccepted Briefing). */
    val draftWaiting: Boolean = false,
    val loading: Boolean = true,
) {
    val limitReached: Boolean get() = !MissionRules.canCreateActive(active.size)
}

@HiltViewModel
class MissionsViewModel @Inject constructor(missions: MissionRepository, preferences: PreferencesRepository) : ViewModel() {
    val uiState: StateFlow<MissionsUiState> = combine(missions.observeMissions(), preferences.preferences, missions.observeDraft()) { all, prefs, draft ->
        val themes = MissionThemes.resolve(all)
        val items = all.map { MissionListItem(it, themes[it.id] ?: MissionTheme.AUBERGINE) }
        MissionsUiState(
            active = items.filter { it.mission.status == MissionStatus.ACTIVE }
                .sortedWith(compareBy<MissionListItem>({ it.mission.id.value != prefs.lastSelectedMissionId }, { it.mission.createdAt })),
            finished = items.filter { it.mission.status != MissionStatus.ACTIVE }.sortedByDescending { it.mission.updatedAt },
            importAvailable = prefs.featureFlags.pwaImportEnabled,
            draftWaiting = draft != null && (draft.hasInput || draft.phase == MissionDraftPhase.BRIEFING),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionsUiState())
}

data class MissionDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val item: MissionListItem? = null,
    val stepStates: Map<MissionStepId, StepState> = emptyMap(),
    val canStartCurrent: Boolean = false,
    /** Another Session (Quick Focus or another step) is running: starting is not possible now. */
    val otherSessionActive: Boolean = false,
    val reviewSessionId: String? = null,
    val activeCount: Int = 0,
    val busy: Boolean = false,
) {
    val mission: Mission? get() = item?.mission
}

sealed interface MissionDetailEvent {
    data object OpenSession : MissionDetailEvent
    data class OpenResult(val sessionId: String) : MissionDetailEvent
    data object Closed : MissionDetailEvent
    data class Message(val text: String) : MissionDetailEvent
}

/**
 * Mission Plan. Progress, the current step and every step state come from the same rules the
 * Session owner and Home use; this ViewModel keeps no Mission copy of its own.
 */
@HiltViewModel
class MissionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val missions: MissionRepository,
    private val controller: FocusSessionController,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    private val id = runCatching { savedState.toRoute<MissionDetailDestination>().missionId }.getOrNull()?.takeIf(EntityIds::isSafe)?.let(::MissionId)
    private val _events = MutableSharedFlow<MissionDetailEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<MissionDetailEvent> = _events
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<MissionDetailUiState> = combine(
        if (id == null) flowOf(null) else missions.observeMission(id),
        missions.observeMissions(),
        controller.state,
        busy,
    ) { mission, all, session, isBusy ->
        if (mission == null) return@combine MissionDetailUiState(loading = false, notFound = true)
        val themes = MissionThemes.resolve(all)
        val running = session.activeSession?.missionLink
        val review = (session as? SessionState.AwaitingReview)?.result
        MissionDetailUiState(
            loading = false,
            item = MissionListItem(mission, themes[mission.id] ?: MissionTheme.AUBERGINE),
            stepStates = MissionRules.stepStates(mission, running, review?.missionLink),
            canStartCurrent = session.activeSession == null && review == null && mission.currentStepId?.let { MissionRules.canStartStep(mission, it) } == true,
            otherSessionActive = session.activeSession != null && running?.missionId != mission.id,
            reviewSessionId = review?.sessionId?.value?.takeIf { review.missionLink?.missionId == mission.id },
            activeCount = all.count { it.status == MissionStatus.ACTIVE },
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionDetailUiState())

    /** Starts the current step as a real Mission Session through the one Session owner. */
    fun startCurrentStep() {
        val mission = uiState.value.mission ?: return
        val step = mission.currentStepId ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val mode = preferences.current().defaultProtectionMode
            when (val r = controller.startMissionStep(mission.id, step, mode)) {
                is StartResult.Started -> _events.emit(MissionDetailEvent.OpenSession)
                is StartResult.Rejected -> _events.emit(
                    MissionDetailEvent.Message(
                        when (r.error) {
                            SessionValidationError.SessionAlreadyActive -> "Beende zuerst deine aktuelle Fokus-Sitzung."
                            else -> "Dieser Schritt kann gerade nicht gestartet werden."
                        },
                    ),
                )
            }
            busy.value = false
        }
    }

    fun openSession() { viewModelScope.launch { _events.emit(MissionDetailEvent.OpenSession) } }
    fun openReview() { uiState.value.reviewSessionId?.let { s -> viewModelScope.launch { _events.emit(MissionDetailEvent.OpenResult(s)) } } }

    fun archive() = write(close = false) { missions.archive(it) }
    fun restore() = write(close = false) { missions.restore(it) }
    fun delete() = write(close = true) { missions.delete(it) }

    private fun write(close: Boolean, block: suspend (MissionId) -> MissionWriteResult) {
        val missionId = id ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            when (val r = block(missionId)) {
                is MissionWriteResult.Ok -> if (close) _events.emit(MissionDetailEvent.Closed)
                is MissionWriteResult.Rejected -> _events.emit(
                    MissionDetailEvent.Message(
                        when (r.reason) {
                            "active_session_exists" -> "Diese Mission hat eine laufende Sitzung. Beende sie zuerst."
                            "active_mission_limit_reached" -> "Du hast bereits zwei aktive Missionen. Schließe eine ab oder archiviere sie."
                            else -> "Das hat nicht geklappt. Bitte versuche es erneut."
                        },
                    ),
                )
            }
            busy.value = false
        }
    }
}
