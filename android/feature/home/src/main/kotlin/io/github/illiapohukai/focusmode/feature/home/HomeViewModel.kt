package io.github.illiapohukai.focusmode.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.notification.FocusChannel
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ReminderStore
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.LocalProgress
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

sealed interface SessionBanner {
    data class Running(val remainingMinutes: Int, val task: String) : SessionBanner
    data object Frozen : SessionBanner
    data class Review(val sessionId: String) : SessionBanner
}

data class MissionCard(
    val missionId: String,
    val title: String,
    val subtitle: String,
    val stepLabel: String,
    val nextStepTitle: String?,
    val nextStepTask: String?,
    val stepMinutes: Int?,
    val stepId: String?,
    val percent: Int,
    val theme: MissionTheme,
)

data class ActivityItem(val sessionId: String, val title: String, val time: String, val minutes: String, val badge: OutcomeBadge)

data class ReminderItem(val whenLabel: String, val title: String)

data class HomeUiState(
    val dateLabel: String = "",
    val online: Boolean = true,
    val banner: SessionBanner? = null,
    val mission: MissionCard? = null,
    val quickMinutes: Int = 25,
    val progress: LocalProgress = LocalProgress.EMPTY,
    val reminders: List<ReminderItem> = emptyList(),
    val remindersDeliverable: Boolean = true,
    val activity: List<ActivityItem> = emptyList(),
    val loading: Boolean = true,
)

sealed interface HomeEvent {
    data object OpenSession : HomeEvent
    data class Message(val text: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val controller: FocusSessionController,
    history: SessionHistoryRepository,
    missions: MissionRepository,
    reminders: ReminderStore,
    protection: ProtectionCapabilityOwner,
    network: NetworkStatus,
    private val clock: FocusClock,
) : ViewModel() {

    private val ticker = flow { while (true) { emit(clock.now()); delay(15_000) } }
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events

    private val sessionAndMission = combine(controller.state, missions.observePrimaryMission(), missions.observeMissions(), ticker) { state, primary, all, _ ->
        Triple(state, primary, all)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        sessionAndMission,
        history.observeResults(),
        reminders.observeUpcoming(3),
        protection.state,
        network.online,
    ) { (state, primary, all), results, upcoming, protectionState, online ->
        val today = clock.today()
        val themes = MissionThemes.resolve(all)
        HomeUiState(
            dateLabel = DATE.format(ZonedDateTime.ofInstant(clock.now(), clock.zone())).uppercase(Locale.GERMAN),
            online = online,
            banner = when (state) {
                is SessionState.Running -> SessionBanner.Running(SessionTimeline.read(state.session, clock).remainingMinutesCeil, state.session.task.text)
                is SessionState.AwaitingResume -> SessionBanner.Frozen
                is SessionState.AwaitingReview -> SessionBanner.Review(state.result.sessionId.value)
                else -> null
            },
            mission = primary?.let { m ->
                val step = m.currentStep
                MissionCard(
                    missionId = m.id.value,
                    title = m.title,
                    subtitle = listOf(m.subject, m.deadlineText.takeIf { it != "Nicht angegeben" }).filterNotNull().joinToString(" · "),
                    stepLabel = if (step != null) "SCHRITT %02d / %02d".format(m.steps.indexOf(step) + 1, m.steps.size) else "ALLE SCHRITTE ERLEDIGT",
                    nextStepTitle = step?.title,
                    nextStepTask = step?.task?.takeIf { it.isNotBlank() },
                    stepMinutes = step?.durationMinutes,
                    stepId = step?.id?.value,
                    percent = m.progressPercent,
                    theme = themes[m.id] ?: MissionTheme.AUBERGINE,
                )
            },
            progress = ProgressCalculator.compute(results, today),
            reminders = upcoming.map { ReminderItem(whenLabel(it.triggerAt, today), it.title) },
            remindersDeliverable = protectionState?.notifications?.canDeliver(FocusChannel.REMINDERS) ?: true,
            activity = results.take(3).map {
                ActivityItem(
                    it.sessionId.value, it.title,
                    TIME.format(ZonedDateTime.ofInstant(it.finishedAt, clock.zone())) + " · " + dayLabel(it.localDate, today),
                    HistoryQueries.formatMinutes(it.actualMinutes), HistoryQueries.badge(it),
                )
            },
            loading = state is SessionState.Loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun startMissionStep(missionId: String, stepId: String) {
        viewModelScope.launch {
            when (val r = controller.startMissionStep(MissionId(missionId), MissionStepId(stepId), io.github.illiapohukai.focusmode.core.model.ProtectionMode.STANDARD)) {
                is StartResult.Started -> _events.emit(HomeEvent.OpenSession)
                is StartResult.Rejected -> _events.emit(HomeEvent.Message("Dieser Schritt kann gerade nicht gestartet werden."))
            }
        }
    }

    private fun whenLabel(at: java.time.Instant, today: LocalDate): String {
        val zdt = ZonedDateTime.ofInstant(at, clock.zone())
        return dayLabel(zdt.toLocalDate(), today) + " · " + TIME.format(zdt)
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Heute"
        today.minusDays(1) -> "Gestern"
        today.plusDays(1) -> "Morgen"
        else -> SHORT_DAY.format(date)
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
    }
}
