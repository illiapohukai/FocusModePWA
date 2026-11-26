package io.github.illiapohukai.focusmode.feature.missions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.MaterialPreparation
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterialStore
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarDayInfo
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarEvent
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarEventKind
import io.github.illiapohukai.focusmode.core.domain.mission.DraftAttachment
import io.github.illiapohukai.focusmode.core.domain.mission.GenerationStart
import io.github.illiapohukai.focusmode.core.domain.mission.MissionCalendar
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraft
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.MissionSchedulePlanner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.mission.ScheduleDraft
import io.github.illiapohukai.focusmode.core.domain.mission.ScheduleValidation
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class PlannerTarget { DEADLINE, START }

data class PlannerRow(val title: String, val chip: String, val detail: String, val theme: MissionTheme?, val kind: CalendarEventKind?)

data class PlannerUi(
    val expanded: Boolean = true,
    val target: PlannerTarget = PlannerTarget.DEADLINE,
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDayInfo> = emptyList(),
    val focused: LocalDate? = null,
    val detailTitle: String? = null,
    val rows: List<PlannerRow> = emptyList(),
    /** The inspected day that can still be chosen ("Als Frist wählen"). */
    val pick: LocalDate? = null,
    val previousEnabled: Boolean = false,
    val hint: String? = null,
)

data class CreateUiState(
    val loading: Boolean = true,
    val goalText: String = "",
    val type: MissionType? = null,
    val confidence: GenerationConfidence? = null,
    val schedule: ScheduleDraft = ScheduleDraft(),
    val validation: ScheduleValidation = ScheduleValidation(true),
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val planner: PlannerUi = PlannerUi(),
    val deadlineSummary: String? = null,
    val recommendedMode: ScheduleStartMode = ScheduleStartMode.IMMEDIATE,
    val startPreviews: Map<ScheduleStartMode, LocalDate?> = emptyMap(),
    val rhythmOpen: Boolean = false,
    val attachment: DraftAttachment? = null,
    val preparingPhoto: Boolean = false,
    val photoMessage: String? = null,
    val error: GenerationError? = null,
    val errorRequestId: String? = null,
    val briefingReady: Boolean = false,
    val generating: Boolean = false,
    val activeCount: Int = 0,
    val online: Boolean = true,
    val starting: Boolean = false,
) {
    val limitReached: Boolean get() = !MissionRules.canCreateActive(activeCount)
    val goalTooLong: Boolean get() = goalText.trim().length > io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRequest.MAX_GOAL_CHARS
    /** PWA `updateMissionPlanButton`: type, confidence, valid schedule, no request in flight. */
    val canPlan: Boolean
        get() = !loading && !generating && !starting && !preparingPhoto && type != null && confidence != null && validation.valid &&
            (goalText.isNotBlank() || attachment != null) && !goalTooLong && !limitReached
}

sealed interface CreateEvent {
    data object Generating : CreateEvent
    data object ShowBriefing : CreateEvent
    data class Message(val text: String) : CreateEvent
}

/**
 * Mission Creation (PWA `app-screen-goal`). Every input lives in the one Room draft (written
 * through [MissionGenerationOwner]), so it survives backgrounding, process death, a denied
 * picker, a failed or cancelled request. The goal text is also mirrored in [SavedStateHandle]
 * so typing is never lost between debounced writes.
 */
@HiltViewModel
class MissionCreateViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val owner: MissionGenerationOwner,
    missions: MissionRepository,
    private val material: MissionMaterialStore,
    network: NetworkStatus,
    private val clock: FocusClock,
) : ViewModel() {

    private val _events = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CreateEvent> = _events

    private val goal = MutableStateFlow(savedState.get<String>(KEY_GOAL) ?: "")
    private val local = MutableStateFlow(
        LocalUi(
            expanded = savedState.get<Boolean>(KEY_EXPANDED) ?: true,
            target = savedState.get<String>(KEY_TARGET)?.let { runCatching { PlannerTarget.valueOf(it) }.getOrNull() } ?: PlannerTarget.DEADLINE,
            month = savedState.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(clock.today()),
            inspected = savedState.get<String>(KEY_INSPECT)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            rhythmOpen = savedState.get<Boolean>(KEY_RHYTHM) ?: false,
        ),
    )
    private var goalWrite: Job? = null
    private var loaded = false

    private data class LocalUi(
        val expanded: Boolean, val target: PlannerTarget, val month: YearMonth, val inspected: LocalDate?, val rhythmOpen: Boolean,
        val preparing: Boolean = false, val photoMessage: String? = null, val starting: Boolean = false, val loaded: Boolean = false,
    )

    init {
        viewModelScope.launch {
            val draft = owner.openDraft()
            loaded = true
            // A process restart mid-typing keeps the newer text from SavedStateHandle and writes it.
            if (savedState.get<String>(KEY_GOAL) == null) setGoalLocal(draft.goalText)
            else if (goal.value != draft.goalText) persistGoal(goal.value)
            local.value = local.value.copy(loaded = true)
        }
    }

    val uiState: StateFlow<CreateUiState> = combine(missions.observeDraft(), missions.observeMissions(), network.online, goal, local) { draft, all, online, text, ui ->
        if (draft == null || !ui.loaded) return@combine CreateUiState(loading = true, goalText = text)
        val today = clock.today()
        val schedule = draft.schedule
        val themes = MissionThemes.resolve(all)
        val events = MissionCalendar.events(all, emptyList(), themes, clock.zone())
        val byDate = MissionCalendar.plannerEvents(events)
        val activeDeadlines = events.filter { it.kind == CalendarEventKind.DEADLINE && it.status == MissionStatus.ACTIVE }
        CreateUiState(
            loading = false,
            goalText = text,
            type = draft.type,
            confidence = draft.confidence,
            schedule = schedule,
            validation = MissionSchedulePlanner.validate(schedule, today),
            today = today,
            planner = planner(ui, schedule, byDate, today),
            deadlineSummary = deadlineSummary(schedule, activeDeadlines, today, ui.expanded),
            recommendedMode = MissionSchedulePlanner.recommendedStartMode(schedule.deadline, today).let { if (it == ScheduleStartMode.CUSTOM) ScheduleStartMode.TWO_WEEKS_BEFORE else it },
            startPreviews = ScheduleStartMode.entries.associateWith { MissionSchedulePlanner.previewStart(schedule, it, today) },
            rhythmOpen = ui.rhythmOpen,
            attachment = draft.attachment,
            preparingPhoto = ui.preparing,
            photoMessage = ui.photoMessage,
            error = draft.error,
            errorRequestId = draft.errorRequestId,
            briefingReady = draft.phase == MissionDraftPhase.BRIEFING && draft.plan != null,
            generating = draft.phase == MissionDraftPhase.GENERATING,
            activeCount = all.count { it.status == MissionStatus.ACTIVE },
            online = online,
            starting = ui.starting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateUiState(goalText = goal.value))

    // ---------------------------------------------------------------- form

    fun onGoalChange(text: String) {
        setGoalLocal(text)
        goalWrite?.cancel()
        goalWrite = viewModelScope.launch { delay(350); persistGoal(text) }
    }

    private fun setGoalLocal(text: String) {
        goal.value = text
        savedState[KEY_GOAL] = text
    }

    private suspend fun persistGoal(text: String) {
        if (loaded) owner.edit { it.copy(goalText = text) }
    }

    fun selectType(type: MissionType) = edit { it.copy(type = type) }
    fun selectConfidence(c: GenerationConfidence) = edit { it.copy(confidence = c) }
    fun selectStartMode(mode: ScheduleStartMode) {
        edit { it.copy(schedule = MissionSchedulePlanner.selectStartMode(it.schedule, mode, clock.today())) }
        if (mode == ScheduleStartMode.CUSTOM) openPlanner(PlannerTarget.START)
    }
    fun toggleWeekday(day: DayOfWeek) = edit { it.copy(schedule = MissionSchedulePlanner.toggleWeekday(it.schedule, day)) }
    fun weekdayPreset(days: Set<DayOfWeek>) = edit { it.copy(schedule = it.schedule.copy(preferredWeekdays = days)) }
    fun selectSessionMinutes(minutes: Int?) = edit { it.copy(schedule = MissionSchedulePlanner.selectSessionMinutes(it.schedule, minutes)) }
    fun toggleRhythm() = updateLocal { it.copy(rhythmOpen = !it.rhythmOpen) }

    fun clearDeadline() {
        edit { it.copy(schedule = MissionSchedulePlanner.clearDeadline(it.schedule)) }
        updateLocal { it.copy(expanded = true, target = PlannerTarget.DEADLINE, inspected = null) }
    }

    private fun edit(transform: (MissionDraft) -> MissionDraft) {
        if (!loaded) return // a tap before the stored draft is loaded must never overwrite it
        viewModelScope.launch {
            // Pending text is written together with the edit, so nothing typed is lost.
            goalWrite?.cancel()
            val text = goal.value
            owner.edit { transform(it).copy(goalText = text) }
        }
    }

    // ---------------------------------------------------------------- planner

    fun togglePlanner() = updateLocal { s ->
        val schedule = uiState.value.schedule
        if (s.expanded) s.copy(expanded = false, inspected = null, target = PlannerTarget.DEADLINE)
        else s.copy(expanded = true, month = YearMonth.from(schedule.deadline ?: clock.today()))
    }

    fun openPlanner(target: PlannerTarget) {
        val schedule = uiState.value.schedule
        val anchor = (if (target == PlannerTarget.START) schedule.preparationStart else schedule.deadline) ?: clock.today()
        updateLocal { it.copy(expanded = true, target = target, inspected = null, month = YearMonth.from(anchor)) }
    }

    fun plannerDone() = updateLocal { it.copy(target = PlannerTarget.DEADLINE, inspected = null, expanded = false) }

    fun shiftMonth(step: Long) = updateLocal { s ->
        val next = s.month.plusMonths(step)
        if (next.isBefore(YearMonth.from(clock.today()))) s else s.copy(month = next, inspected = null)
    }

    /**
     * PWA planner tap: a day that already holds Mission dates opens its details first (it is
     * never chosen by accident); an empty selectable day is chosen directly.
     */
    fun onDay(date: LocalDate) {
        val state = uiState.value
        val info = state.planner.days.firstOrNull { it.date == date } ?: return
        if (info.events.isNotEmpty()) {
            updateLocal { it.copy(inspected = date) }
            return
        }
        pick(date)
    }

    fun pick(date: LocalDate) {
        val target = local.value.target
        val today = clock.today()
        val schedule = uiState.value.schedule
        if (!MissionCalendar.selectable(date, today, target == PlannerTarget.START, schedule.deadline)) return
        edit { d ->
            val next = if (target == PlannerTarget.START) MissionSchedulePlanner.selectPreparationStart(d.schedule, date, today)
            else MissionSchedulePlanner.selectDeadline(d.schedule, date, today)
            if (next == null) d else d.copy(schedule = next)
        }
        updateLocal { it.copy(inspected = null, target = PlannerTarget.DEADLINE, expanded = false) }
    }

    private fun planner(ui: LocalUi, schedule: ScheduleDraft, byDate: Map<LocalDate, List<CalendarEvent>>, today: LocalDate): PlannerUi {
        val target = if (ui.target == PlannerTarget.START && schedule.deadline == null) PlannerTarget.DEADLINE else ui.target
        val choosingStart = target == PlannerTarget.START
        val start = if (schedule.deadline != null) schedule.preparationStart ?: today else null
        val days = MissionCalendar.monthDays(ui.month, byDate, today, { MissionCalendar.selectable(it, today, choosingStart, schedule.deadline) }) { date ->
            buildList {
                if (date == schedule.deadline) add("Frist der neuen Mission")
                if (start != null && date == start && start != schedule.deadline) add("Vorbereitungsstart der neuen Mission")
            }
        }
        val key = ui.inspected ?: if (choosingStart) start else schedule.deadline
        val rows = mutableListOf<PlannerRow>()
        if (key != null) {
            if (key == schedule.deadline) rows += PlannerRow("Neue Mission", "Frist", MissionCalendar.relative(key, today), null, null)
            if (start != null && key == start && start != schedule.deadline) rows += PlannerRow("Neue Mission", "Vorbereitung", "Start", null, null)
            byDate[key].orEmpty().forEach { rows += PlannerRow(it.title, MissionCalendar.chip(it), MissionCalendar.detail(it, today), it.theme, it.kind) }
        }
        val current = if (choosingStart) start else schedule.deadline
        val pick = key?.takeIf { it != current && MissionCalendar.selectable(it, today, choosingStart, schedule.deadline) && ui.inspected != null }
        return PlannerUi(
            expanded = ui.expanded,
            target = target,
            month = ui.month,
            days = days,
            focused = key,
            detailTitle = key?.let { MissionCalendar.dayTitle(it, today) },
            rows = rows,
            pick = pick,
            previousEnabled = ui.month.isAfter(YearMonth.from(today)),
            hint = if (key == null) "Tippe auf einen Tag, um die Frist festzulegen." else null,
        )
    }

    /** PWA `syncPlannerSummary`: the deadline and whether it meets existing work. */
    private fun deadlineSummary(s: ScheduleDraft, deadlines: List<CalendarEvent>, today: LocalDate, expanded: Boolean): String? {
        val end = s.deadline ?: return if (!expanded) "Kalender öffnen" else null
        val start = s.preparationStart ?: today
        val same = deadlines.filter { it.date == end }
        val within = deadlines.filter { !it.date.isBefore(start) && it.date.isBefore(end) }
        var text = MissionCalendar.relative(end, today).replaceFirstChar { it.uppercase() }
        text += when {
            same.isNotEmpty() -> " · Gleicher Tag wie „${same.first().title}“"
            within.size == 1 -> " · 1 weitere Frist im Zeitraum"
            within.size > 1 -> " · ${within.size} weitere Fristen im Zeitraum"
            else -> ""
        }
        return text
    }

    // ---------------------------------------------------------------- photo

    /** System photo picker result (a granted `content://` Uri), or null when the user backed out. */
    fun onPhotoPicked(uri: String?) {
        if (uri == null) return // cancelled or declined: the draft stays as it is
        updateLocal { it.copy(preparing = true, photoMessage = null) }
        viewModelScope.launch {
            when (val r = material.prepare(uri)) {
                is MaterialPreparation.Ready -> {
                    edit { it.copy(attachment = r.attachment) }
                    updateLocal { it.copy(preparing = false) }
                }
                is MaterialPreparation.Rejected -> updateLocal { it.copy(preparing = false, photoMessage = r.message) }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            material.clear()
            edit { it.copy(attachment = null) }
            updateLocal { it.copy(photoMessage = null) }
        }
    }

    // ---------------------------------------------------------------- generation

    /** "Mission planen": exactly one request per tap; a double tap cannot start a second one. */
    fun plan() {
        val state = uiState.value
        if (!state.canPlan || local.value.starting) return
        updateLocal { it.copy(starting = true) }
        viewModelScope.launch {
            goalWrite?.cancel()
            persistGoal(goal.value)
            when (val r = owner.start()) {
                is GenerationStart.Started -> _events.emit(CreateEvent.Generating)
                is GenerationStart.Rejected -> if (r.error == GenerationError.AlreadyInFlight) _events.emit(CreateEvent.Generating)
            }
            updateLocal { it.copy(starting = false) }
        }
    }

    fun openBriefing() {
        viewModelScope.launch { _events.emit(CreateEvent.ShowBriefing) }
    }

    fun dismissError() {
        viewModelScope.launch { owner.dismissError() }
    }

    fun dismissPhotoMessage() = updateLocal { it.copy(photoMessage = null) }

    private fun updateLocal(f: (LocalUi) -> LocalUi) {
        val next = f(local.value)
        local.value = next
        savedState[KEY_EXPANDED] = next.expanded
        savedState[KEY_TARGET] = next.target.name
        savedState[KEY_MONTH] = next.month.toString()
        savedState[KEY_INSPECT] = next.inspected?.toString()
        savedState[KEY_RHYTHM] = next.rhythmOpen
    }

    private companion object {
        const val KEY_GOAL = "goal"
        const val KEY_EXPANDED = "planner_expanded"
        const val KEY_TARGET = "planner_target"
        const val KEY_MONTH = "planner_month"
        const val KEY_INSPECT = "planner_inspect"
        const val KEY_RHYTHM = "rhythm_open"
    }
}
