package io.github.illiapohukai.focusmode.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarDayInfo
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarEventKind
import io.github.illiapohukai.focusmode.core.domain.mission.HomeMissionClassifier
import io.github.illiapohukai.focusmode.core.domain.mission.HomeMissionMode
import io.github.illiapohukai.focusmode.core.domain.mission.MissionCalendar
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.notification.FocusChannel
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.progress.ProgressCalculator
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ReminderStore
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.session.SessionValidationError
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.LocalProgress
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
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
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    /** PWA `mh-kicker`: HAUPTMISSION for the primary Mission, AKTIVE MISSION otherwise. */
    val primary: Boolean = true,
)

data class ActivityItem(val sessionId: String, val title: String, val time: String, val minutes: String, val badge: OutcomeBadge)

data class ReminderItem(val whenLabel: String, val title: String)

data class CalendarEventItem(val missionId: String, val title: String, val chip: String, val detail: String, val theme: MissionTheme, val kind: CalendarEventKind)

/** Home Mission calendar (PWA `homeMissionCalendar`), derived from the one calendar owner. */
data class HomeCalendar(
    val empty: Boolean = true,
    val month: YearMonth = YearMonth.of(2026, 1),
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val selected: LocalDate = LocalDate.of(2026, 1, 1),
    val days: List<CalendarDayInfo> = emptyList(),
    val nextLine: String = "Keine anstehende Frist",
    val selectedTitle: String = "",
    val selectedEvents: List<CalendarEventItem> = emptyList(),
    /** Jump target when the selected day is empty (PWA "Nächste Frist: …"). */
    val nextDeadline: Pair<LocalDate, String>? = null,
)

data class HomeUiState(
    val dateLabel: String = "",
    val online: Boolean = true,
    val banner: SessionBanner? = null,
    /** The primary Mission (first card). */
    val mission: MissionCard? = null,
    /** Active Missions in the repository (PWA hero states: none, one, several). */
    val activeMissionCount: Int = 0,
    /** Every active Mission, primary first (the carousel). */
    val missions: List<MissionCard> = listOfNotNull(mission),
    val mode: HomeMissionMode = when {
        missions.size >= 2 -> HomeMissionMode.TWO
        missions.size == 1 -> HomeMissionMode.ONE
        else -> HomeMissionMode.FIRST_TIME
    },
    val quickMinutes: Int = 25,
    val progress: LocalProgress = LocalProgress.EMPTY,
    val reminders: List<ReminderItem> = emptyList(),
    val remindersDeliverable: Boolean = true,
    val activity: List<ActivityItem> = emptyList(),
    val calendar: HomeCalendar = HomeCalendar(),
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
    private val preferences: PreferencesRepository,
    private val clock: FocusClock,
) : ViewModel() {

    private val ticker = flow { while (true) { emit(clock.now()); delay(15_000) } }
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events
    private val calendarView = MutableStateFlow<Pair<YearMonth, LocalDate>?>(null)
    private var starting = false

    private val sessionAndMissions = combine(controller.state, missions.observeMissions(), preferences.preferences, ticker) { state, all, prefs, _ ->
        Triple(state, all, prefs)
    }

    private val supporting = combine(reminders.observeUpcoming(3), protection.state, network.online, calendarView) { upcoming, protectionState, online, view ->
        Supporting(upcoming, protectionState?.notifications?.canDeliver(FocusChannel.REMINDERS) ?: true, online, view)
    }

    private data class Supporting(val upcoming: List<io.github.illiapohukai.focusmode.core.model.Reminder>, val deliverable: Boolean, val online: Boolean, val view: Pair<YearMonth, LocalDate>?)

    val uiState: StateFlow<HomeUiState> = combine(sessionAndMissions, history.observeResults(), supporting) { (state, all, prefs), results, sup ->
        val today = clock.today()
        val themes = MissionThemes.resolve(all)
        val active = all.filter { it.status == MissionStatus.ACTIVE }
            .sortedWith(compareBy<Mission>({ it.id.value != prefs.lastSelectedMissionId }, { it.createdAt }))
        val evidence = HomeMissionClassifier.hasMissionEvidence(all, results)
        if (HomeMissionClassifier.shouldMarkReturning(prefs.homeOnboardingDone, evidence)) {
            viewModelScope.launch { preferences.markHomeOnboardingDone() }
        }
        val cards = active.mapIndexed { i, m -> card(m, themes[m.id] ?: MissionTheme.AUBERGINE, primary = i == 0) }
        HomeUiState(
            dateLabel = DATE.format(ZonedDateTime.ofInstant(clock.now(), clock.zone())).uppercase(Locale.GERMAN),
            online = sup.online,
            banner = when (state) {
                is SessionState.Running -> SessionBanner.Running(SessionTimeline.read(state.session, clock).remainingMinutesCeil, state.session.task.text)
                is SessionState.AwaitingResume -> SessionBanner.Frozen
                is SessionState.AwaitingReview -> SessionBanner.Review(state.result.sessionId.value)
                else -> null
            },
            mission = cards.firstOrNull(),
            activeMissionCount = active.size,
            missions = cards,
            mode = HomeMissionClassifier.classify(all, prefs.homeOnboardingDone, evidence),
            progress = ProgressCalculator.compute(results, today),
            reminders = sup.upcoming.map { ReminderItem(whenLabel(it.triggerAt, today), it.title) },
            remindersDeliverable = sup.deliverable,
            activity = results.take(3).map {
                ActivityItem(
                    it.sessionId.value, it.title,
                    TIME.format(ZonedDateTime.ofInstant(it.finishedAt, clock.zone())) + " · " + dayLabel(it.localDate, today),
                    HistoryQueries.formatMinutes(it.actualMinutes), HistoryQueries.badge(it),
                )
            },
            calendar = calendar(all, results, themes, today, sup.view),
            loading = state is SessionState.Loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun card(m: Mission, theme: MissionTheme, primary: Boolean): MissionCard {
        val step = m.currentStep
        val deadline = MissionCalendar.deadlineOf(m)
        return MissionCard(
            missionId = m.id.value,
            title = m.title,
            subtitle = listOfNotNull(
                m.subject.takeIf { it.isNotBlank() },
                deadline?.let { "bis " + SHORT_DAY.format(it) } ?: m.deadlineText.takeIf { it.isNotBlank() && it != "Nicht angegeben" },
            ).joinToString(" · "),
            stepLabel = if (step != null) "SCHRITT %02d / %02d".format(m.steps.indexOf(step) + 1, m.steps.size) else "ALLE SCHRITTE ERLEDIGT",
            nextStepTitle = step?.title,
            nextStepTask = step?.task?.takeIf { it.isNotBlank() },
            stepMinutes = step?.durationMinutes,
            stepId = step?.id?.value,
            percent = m.progressPercent,
            theme = theme,
            completedSteps = m.completedStepCount,
            totalSteps = m.steps.size,
            primary = primary,
        )
    }

    private fun calendar(
        all: List<Mission>, results: List<io.github.illiapohukai.focusmode.core.model.FocusSessionResult>,
        themes: Map<MissionId, MissionTheme>, today: LocalDate, view: Pair<YearMonth, LocalDate>?,
    ): HomeCalendar {
        val events = MissionCalendar.events(all, results, themes, clock.zone())
        val visibleMissions = all.any { it.status != MissionStatus.ARCHIVED }
        if (!visibleMissions) return HomeCalendar(empty = true, month = YearMonth.from(today), today = today, selected = today)
        val byDate = MissionCalendar.byDate(events)
        val (month, selected) = view ?: (YearMonth.from(today) to today)
        val next = MissionCalendar.nextDeadline(events, today)
        val selectedEvents = byDate[selected].orEmpty().map { CalendarEventItem(it.missionId.value, it.title, MissionCalendar.chip(it), MissionCalendar.detail(it, today), it.theme, it.kind) }
        return HomeCalendar(
            empty = false,
            month = month,
            today = today,
            selected = selected,
            days = MissionCalendar.monthDays(month, byDate, today),
            nextLine = next?.let { "${MissionCalendar.deadlineLabel(it.missionType)} · ${it.title} · ${MissionCalendar.relative(it.date, today)}" } ?: "Keine anstehende Frist",
            selectedTitle = MissionCalendar.dayTitle(selected, today),
            selectedEvents = selectedEvents,
            nextDeadline = next?.takeIf { it.date != selected }?.let { it.date to "Nächste Frist: ${MissionCalendar.shortDate(it.date)} · ${it.title}" },
        )
    }

    fun selectDate(date: LocalDate) { calendarView.value = YearMonth.from(date) to date }

    fun shiftMonth(step: Long) {
        val today = clock.today()
        val current = calendarView.value?.first ?: YearMonth.from(today)
        val month = current.plusMonths(step)
        calendarView.value = month to if (YearMonth.from(today) == month) today else month.atDay(1)
    }

    fun calendarToday() { calendarView.value = null }

    /** Starts the Mission's current step through the one Session owner (user's default protection). */
    fun startMissionStep(missionId: String, stepId: String) {
        if (starting) return
        starting = true
        viewModelScope.launch {
            val mode = preferences.current().defaultProtectionMode
            when (val r = controller.startMissionStep(MissionId(missionId), MissionStepId(stepId), mode)) {
                is StartResult.Started -> {
                    preferences.setLastSelectedMission(missionId)
                    _events.emit(HomeEvent.OpenSession)
                }
                is StartResult.Rejected -> _events.emit(
                    HomeEvent.Message(if (r.error == SessionValidationError.SessionAlreadyActive) "Beende zuerst deine aktuelle Fokus-Sitzung." else "Dieser Schritt kann gerade nicht gestartet werden."),
                )
            }
            starting = false
        }
    }

    /** The carousel's visible card becomes the primary Mission (PWA selected/primary). */
    fun selectMission(missionId: String) {
        viewModelScope.launch { preferences.setLastSelectedMission(missionId) }
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

