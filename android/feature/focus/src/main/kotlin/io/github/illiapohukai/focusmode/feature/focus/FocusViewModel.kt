package io.github.illiapohukai.focusmode.feature.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.protection.AppSelectionReconciler
import io.github.illiapohukai.focusmode.core.domain.repository.DistractionLog
import io.github.illiapohukai.focusmode.core.domain.session.EndBarrier
import io.github.illiapohukai.focusmode.core.domain.session.FinishResult
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.FreezeCause
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.ProtectionCapability
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FocusPhase { LOADING, RUNNING, FROZEN, ENDED }

data class EndSheetState(val barrier: EndBarrier, val secondsWaited: Int, val note: EndNote?, val countedMinutes: Int) {
    val canConfirm: Boolean get() = barrier.canConfirm(secondsWaited, note)
    val secondsLeft: Int get() = (barrier.delaySeconds - secondsWaited).coerceAtLeast(0)
}

data class FocusUiState(
    val phase: FocusPhase = FocusPhase.LOADING,
    val timerText: String = "",
    /** Minute granularity for accessibility (no per-second announcements). */
    val timerDescription: String = "",
    val progress: Float = 0f,
    val title: String = "",
    /** Session header line (PWA `#activeTaskLabel`): the Quick Focus task or the Mission step title. */
    val headline: String = "",
    val task: String = "",
    val modeLabel: String = "",
    val stepLabel: String? = null,
    val protectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val monitoringLabel: String? = null,
    val frozenElapsedMinutes: Int = 0,
    val plannedMinutes: Int = 0,
    val frozenByRestart: Boolean = false,
    val endSheet: EndSheetState? = null,
)

sealed interface FocusEvent {
    data class ShowResult(val sessionId: String) : FocusEvent
    data object Leave : FocusEvent
    data class Message(val text: String) : FocusEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FocusViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val controller: FocusSessionController,
    private val clock: FocusClock,
    protection: ProtectionCapabilityOwner,
    distractions: DistractionLog,
) : ViewModel() {

    private val openEnd = runCatching { savedState.toRoute<FocusDestination>().openEnd }.getOrDefault(false)
    private val endSheet = MutableStateFlow<EndSheetState?>(null)
    private val _events = MutableSharedFlow<FocusEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FocusEvent> = _events
    private var barrierJob: Job? = null
    private var observedSessionId: String? = null
    private var routed = false

    /** One Session end produces exactly one navigation, whichever path observes it first. */
    private suspend fun routeOnce(event: FocusEvent) {
        if (routed) return
        routed = true
        _events.emit(event)
    }

    /**
     * Display-only ticker; it never writes. Aligned to whole seconds of the remaining time, and it
     * ticks only while a Session is running (Recovery and loading need no per-second work).
     */
    private val ticker = controller.state.map { it is SessionState.Running }.distinctUntilChanged().flatMapLatest { running ->
        if (!running) flowOf(Unit) else flow {
            while (true) {
                emit(Unit)
                val remaining = controller.read()?.remainingMs ?: 1_000L
                delay(((remaining - 1) % 1_000L + 1).coerceIn(50L, 1_000L))
            }
        }
    }

    private val distractionCount = controller.state.map { it.activeSession?.id }.flatMapLatest { id ->
        if (id == null) flowOf(0) else distractions.observeCount(id)
    }

    val uiState: StateFlow<FocusUiState> = combine(controller.state, ticker, endSheet, protection.state, distractionCount) { state, _, sheet, p, count ->
        when (state) {
            is SessionState.Running -> running(state.session, sheet, p, count)
            is SessionState.AwaitingResume -> frozen(state.session, state.cause)
            SessionState.Loading -> FocusUiState()
            else -> FocusUiState(phase = FocusPhase.ENDED)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        viewModelScope.launch {
            controller.state.collect { state ->
                when (state) {
                    is SessionState.Running -> {
                        observedSessionId = state.session.id.value
                        if (openEnd && endSheet.value == null) requestEnd()
                    }
                    is SessionState.AwaitingResume -> observedSessionId = state.session.id.value
                    is SessionState.AwaitingReview -> routeOnce(FocusEvent.ShowResult(state.result.sessionId.value))
                    is SessionState.Idle -> {
                        val last = state.lastFinalized?.value
                        if (last != null && (observedSessionId == null || observedSessionId == last)) routeOnce(FocusEvent.ShowResult(last))
                        else routeOnce(FocusEvent.Leave)
                    }
                    SessionState.Loading -> Unit
                }
            }
        }
    }

    private fun running(s: FocusSession, sheet: EndSheetState?, p: io.github.illiapohukai.focusmode.core.protection.ProtectionState?, count: Int): FocusUiState {
        val r = SessionTimeline.read(s, clock)
        val totalSeconds = ((r.remainingMs + 999) / 1000).toInt()
        val minutesLeft = r.remainingMinutesCeil
        val monitoring = if (s.protectionMode == ProtectionMode.PROTECTED && p != null) {
            when (p.overview.usageMonitoring.capability) {
                ProtectionCapability.READY_FOR_USAGE_MONITORING ->
                    "Ablenkungen überwachen · " + (if (count == 1) "1 Hinweis" else "$count Hinweise") +
                        " · ${AppSelectionReconciler.watchablePackages(p.selectedApps).size} Apps"
                else -> p.overview.usageMonitoring.title
            }
        } else null
        return FocusUiState(
            phase = FocusPhase.RUNNING,
            timerText = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60),
            timerDescription = if (minutesLeft <= 1) "Noch weniger als eine Minute" else "Noch $minutesLeft Minuten",
            progress = (r.elapsedMs.toFloat() / r.plannedMs).coerceIn(0f, 1f),
            title = s.title,
            headline = s.title.ifBlank { s.task.text },
            task = s.task.text,
            modeLabel = if (s.type == SessionType.MISSION) s.modeLabel else "Kurzer Fokus",
            stepLabel = if (s.type == SessionType.MISSION) "Schritt ${s.progressCurrent} von ${s.progressTotal}" else null,
            protectionMode = s.protectionMode,
            monitoringLabel = monitoring,
            plannedMinutes = s.plannedMinutes,
            endSheet = sheet?.copy(countedMinutes = r.countedMinutes),
        )
    }

    private fun frozen(s: FocusSession, cause: FreezeCause): FocusUiState {
        val r = SessionTimeline.read(s, clock)
        return FocusUiState(
            phase = FocusPhase.FROZEN, title = s.title, task = s.task.text,
            modeLabel = if (s.type == SessionType.MISSION) "Focus Mission · Schritt ${s.progressCurrent}" else "Kurzer Fokus",
            frozenElapsedMinutes = r.countedMinutes, plannedMinutes = s.plannedMinutes,
            frozenByRestart = cause == FreezeCause.DEVICE_RESTARTED, protectionMode = s.protectionMode,
        )
    }

    fun requestEnd() {
        val session = (controller.state.value as? SessionState.Running)?.session ?: return
        endSheet.value = EndSheetState(EndBarrier.forMode(session.protectionMode), 0, null, controller.read()?.countedMinutes ?: 0)
        barrierJob?.cancel()
        barrierJob = viewModelScope.launch {
            while (true) {
                val current = endSheet.value ?: break
                if (current.secondsWaited >= current.barrier.delaySeconds) break
                delay(1_000)
                endSheet.update { it?.copy(secondsWaited = it.secondsWaited + 1) }
            }
        }
    }

    fun selectNote(note: EndNote) = endSheet.update { it?.copy(note = note) }

    fun dismissEnd() {
        barrierJob?.cancel()
        endSheet.value = null
    }

    fun confirmEnd() {
        val sheet = endSheet.value ?: return
        if (!sheet.canConfirm) return
        finish { controller.endByUser(sheet.note) }
    }

    /** Ends without counting the time (still behind the same barrier). */
    fun discard() {
        val sheet = endSheet.value ?: return
        if (!sheet.canConfirm) return
        finish { controller.cancel(sheet.note) }
    }

    /** Always available, no delay, no reason: the user is never trapped. */
    fun emergencyExit() = finish { controller.emergencyExit() }

    private fun finish(block: suspend () -> FinishResult) {
        viewModelScope.launch {
            dismissEnd()
            when (val r = block()) {
                is FinishResult.Finalized -> routeOnce(FocusEvent.ShowResult(r.result.sessionId.value))
                is FinishResult.Rejected -> _events.emit(FocusEvent.Message("Die Sitzung konnte nicht beendet werden."))
                FinishResult.NoActiveSession -> routeOnce(FocusEvent.Leave)
            }
        }
    }

    fun resume() = viewModelScope.launch { controller.resumeRecovered() }
    fun saveRecovered() = finish { controller.saveRecovered() }
    fun discardRecovered() = viewModelScope.launch { if (controller.discardRecovered()) routeOnce(FocusEvent.Leave) }
}
