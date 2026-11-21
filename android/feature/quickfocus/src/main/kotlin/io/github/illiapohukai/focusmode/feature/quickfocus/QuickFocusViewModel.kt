package io.github.illiapohukai.focusmode.feature.quickfocus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.protection.AppSelectionReconciler
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ScheduleRepository
import io.github.illiapohukai.focusmode.core.domain.schedule.ScheduleCalculator
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusDuration
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusRequest
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionValidationError
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.FocusTask
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotificationPrompt { NONE, ASK, DENIED }

data class QuickFocusUiState(
    /** The one authoritative selected duration (5–60, step 5). */
    val minutes: Int = 25,
    val task: String = "",
    val mode: ProtectionMode = ProtectionMode.STANDARD,
    val availableModes: Set<ProtectionMode> = setOf(ProtectionMode.STANDARD, ProtectionMode.PROTECTED),
    val protectionSummary: String = "",
    val selectedAppLabels: List<String> = emptyList(),
    /** Package name and label of the installed selected apps (real icons in the summary). */
    val selectedApps: List<Pair<String, String>> = emptyList(),
    /** Protected mode with Usage Access ready: the only state the summary shows in green. */
    val protectionReady: Boolean = false,
    val notificationPrompt: NotificationPrompt = NotificationPrompt.NONE,
    val scheduleLabel: String? = null,
    val starting: Boolean = false,
    val error: String? = null,
) {
    val readout: String get() = QuickFocusDuration.staticReadout(minutes)
}

sealed interface QuickFocusEvent { data object Started : QuickFocusEvent; data object AlreadyRunning : QuickFocusEvent }

@HiltViewModel
class QuickFocusViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val controller: FocusSessionController,
    private val preferences: PreferencesRepository,
    private val protection: ProtectionCapabilityOwner,
    private val schedules: ScheduleRepository,
    private val clock: FocusClock,
) : ViewModel() {

    private val scheduleId: String? = runCatching { savedState.toRoute<QuickFocusDestination>().scheduleId }.getOrNull()
    private val form = MutableStateFlow(QuickFocusUiState())
    private val _events = MutableSharedFlow<QuickFocusEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<QuickFocusEvent> = _events

    init {
        viewModelScope.launch {
            val prefs = preferences.current()
            val schedule = scheduleId?.let { schedules.getSchedule(ScheduleId(it)) }
            form.update {
                it.copy(
                    minutes = schedule?.let { s -> ScheduleCalculator.suggestedSessionMinutes(s, clock.now(), clock.zone()) } ?: prefs.defaultQuickFocusMinutes,
                    task = schedule?.label.orEmpty(),
                    mode = schedule?.protectionMode ?: prefs.defaultProtectionMode,
                    scheduleLabel = schedule?.label,
                )
            }
            if (controller.state.value.activeSession != null) _events.emit(QuickFocusEvent.AlreadyRunning)
        }
        protection.refresh()
    }

    val uiState: StateFlow<QuickFocusUiState> = combine(form, protection.state) { f, p ->
        if (p == null) return@combine f
        val apps = p.selectedApps.filter { it.selection.packageName in AppSelectionReconciler.watchablePackages(p.selectedApps) }
        f.copy(
            availableModes = p.overview.availableModes,
            protectionSummary = p.overview.summaryFor(f.mode, apps.size),
            selectedAppLabels = apps.map { it.displayLabel },
            selectedApps = apps.map { it.selection.packageName to it.displayLabel },
            protectionReady = f.mode == ProtectionMode.PROTECTED &&
                p.overview.usageMonitoring.capability == io.github.illiapohukai.focusmode.core.model.ProtectionCapability.READY_FOR_USAGE_MONITORING,
            notificationPrompt = when (p.notifications.permission) {
                ProtectionPermissionState.NOT_REQUESTED -> NotificationPrompt.ASK
                ProtectionPermissionState.DENIED, ProtectionPermissionState.PERMANENTLY_DENIED -> NotificationPrompt.DENIED
                else -> if (!p.notifications.osEnabled) NotificationPrompt.DENIED else NotificationPrompt.NONE
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickFocusUiState())

    fun setMinutes(minutes: Int) = form.update { it.copy(minutes = QuickFocusDuration.normalize(minutes)) }
    fun step(delta: Int) = form.update { it.copy(minutes = QuickFocusDuration.normalize(it.minutes + delta * QuickFocusDuration.STEP)) }
    fun setTask(text: String) = form.update { it.copy(task = text.take(FocusTask.MAX_INPUT_LENGTH)) }
    fun setMode(mode: ProtectionMode) = form.update { it.copy(mode = mode) }

    fun onNotificationPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        viewModelScope.launch {
            preferences.incrementNotificationPermissionRequests()
            protection.refresh(shouldShowRationale)
        }
    }

    fun refresh(shouldShowRationale: Boolean?) = protection.refresh(shouldShowRationale)

    fun start() {
        val f = form.value
        if (f.starting) return // one launch per tap (PWA quickLaunchPending)
        form.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            val result = controller.startQuick(QuickFocusRequest(f.task, f.minutes, f.mode, scheduleId?.let(::ScheduleId)))
            when (result) {
                is StartResult.Started -> _events.emit(QuickFocusEvent.Started)
                is StartResult.Rejected -> {
                    if (result.error == SessionValidationError.SessionAlreadyActive && controller.state.first() is SessionState.Running) {
                        _events.emit(QuickFocusEvent.AlreadyRunning)
                    }
                    form.update { it.copy(error = "Die Fokus-Sitzung konnte nicht gestartet werden.") }
                }
            }
            form.update { it.copy(starting = false) }
        }
    }
}
