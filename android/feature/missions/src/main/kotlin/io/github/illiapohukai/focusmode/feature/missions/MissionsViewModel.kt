package io.github.illiapohukai.focusmode.feature.missions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissionListItem(val mission: Mission, val theme: MissionTheme)

data class MissionsUiState(
    val active: List<MissionListItem> = emptyList(),
    val finished: List<MissionListItem> = emptyList(),
    val importAvailable: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class MissionsViewModel @Inject constructor(missions: MissionRepository, preferences: PreferencesRepository) : ViewModel() {
    val uiState: StateFlow<MissionsUiState> = combine(missions.observeMissions(), preferences.preferences) { all, prefs ->
        val themes = MissionThemes.resolve(all)
        val items = all.map { MissionListItem(it, themes[it.id] ?: MissionTheme.AUBERGINE) }
        MissionsUiState(
            active = items.filter { it.mission.status == MissionStatus.ACTIVE },
            finished = items.filter { it.mission.status != MissionStatus.ACTIVE }.sortedByDescending { it.mission.updatedAt },
            importAvailable = prefs.featureFlags.pwaImportEnabled,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionsUiState())
}

data class MissionDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val item: MissionListItem? = null,
    val canStartCurrent: Boolean = false,
)

sealed interface MissionDetailEvent {
    data object OpenSession : MissionDetailEvent
    data object Closed : MissionDetailEvent
    data class Message(val text: String) : MissionDetailEvent
}

@HiltViewModel
class MissionDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val missions: MissionRepository,
    private val controller: FocusSessionController,
) : ViewModel() {
    private val id = runCatching { savedState.toRoute<MissionDetailDestination>().missionId }.getOrNull()?.takeIf(EntityIds::isSafe)?.let(::MissionId)
    private val _events = MutableSharedFlow<MissionDetailEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<MissionDetailEvent> = _events

    val uiState: StateFlow<MissionDetailUiState> = (if (id == null) flowOf(null) else missions.observeMission(id))
        .combine(missions.observeMissions().map(MissionThemes::resolve)) { mission, themes ->
            if (mission == null) MissionDetailUiState(loading = false, notFound = true)
            else MissionDetailUiState(
                loading = false,
                item = MissionListItem(mission, themes[mission.id] ?: MissionTheme.AUBERGINE),
                canStartCurrent = mission.currentStepId?.let { MissionRules.canStartStep(mission, it) } == true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionDetailUiState())

    fun startCurrentStep() {
        val mission = uiState.value.item?.mission ?: return
        val step = mission.currentStepId ?: return
        viewModelScope.launch {
            when (controller.startMissionStep(mission.id, step, io.github.illiapohukai.focusmode.core.model.ProtectionMode.STANDARD)) {
                is StartResult.Started -> _events.emit(MissionDetailEvent.OpenSession)
                is StartResult.Rejected -> _events.emit(MissionDetailEvent.Message("Beende zuerst deine aktuelle Fokus-Sitzung."))
            }
        }
    }

    fun archive() = write { missions.archive(it) }
    fun delete() = write { missions.delete(it) }

    private fun write(block: suspend (MissionId) -> MissionWriteResult) {
        val missionId = id ?: return
        viewModelScope.launch {
            when (val r = block(missionId)) {
                is MissionWriteResult.Ok -> _events.emit(MissionDetailEvent.Closed)
                is MissionWriteResult.Rejected -> _events.emit(
                    MissionDetailEvent.Message(if (r.reason == "active_session_exists") "Diese Mission hat eine laufende Sitzung." else "Das hat nicht geklappt."),
                )
            }
        }
    }
}
