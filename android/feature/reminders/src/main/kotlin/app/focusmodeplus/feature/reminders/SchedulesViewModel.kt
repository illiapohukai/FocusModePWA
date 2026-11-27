package app.focusmodeplus.feature.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import app.focusmodeplus.core.domain.notification.FocusChannel
import app.focusmodeplus.core.domain.notification.NotificationCapability
import app.focusmodeplus.core.domain.protection.AppSelectionReconciler
import app.focusmodeplus.core.domain.repository.ScheduleRepository
import app.focusmodeplus.core.domain.schedule.ScheduleCalculator
import app.focusmodeplus.core.domain.schedule.ScheduleValidation
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.EntityIds
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ProtectionPermissionState
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.ScheduleZone
import app.focusmodeplus.core.protection.ProtectionCapabilityOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class ScheduleRow(val schedule: Schedule, val window: String, val days: String, val next: String?)

data class SchedulesUiState(
    val rows: List<ScheduleRow> = emptyList(),
    /** Honest delivery statement shown above the list. */
    val delivery: String = "",
    val deliverable: Boolean = true,
    val loading: Boolean = true,
)

internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val NEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM · HH:mm", Locale.GERMAN)
internal val DAY_SHORT = mapOf(
    DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi", DayOfWeek.THURSDAY to "Do",
    DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val schedules: ScheduleRepository,
    protection: ProtectionCapabilityOwner,
    private val clock: FocusClock,
) : ViewModel() {

    val uiState: StateFlow<SchedulesUiState> = combine(schedules.observeSchedules(), protection.state) { list, p ->
        val canNotify = p?.notifications?.canDeliver(FocusChannel.REMINDERS) ?: true
        val exact = p?.snapshot?.exactAlarm == ProtectionPermissionState.GRANTED
        SchedulesUiState(
            rows = list.map { s ->
                val next = ScheduleCalculator.nextReminderOccurrence(s, clock.now(), clock.zone())
                ScheduleRow(
                    s, "${TIME.format(s.start)}–${TIME.format(s.end)}",
                    DayOfWeek.entries.filter { it in s.weekdays }.joinToString(" ") { DAY_SHORT.getValue(it) },
                    next?.let { NEXT.format(ZonedDateTime.ofInstant(it.reminderAt(s.leadTimeMinutes), clock.zone())) },
                )
            },
            // No delivery claim before the capability owner has read the platform state.
            delivery = when {
                p == null -> ""
                !canNotify -> p?.notifications?.let { NotificationCapability.deliveryLabel(it.deliveryFor(FocusChannel.REMINDERS)) } + " – Erinnerungen werden nicht angezeigt."
                exact -> "Erinnerungen werden pünktlich zugestellt."
                else -> "Erinnerungen kommen ungefähr pünktlich, meist innerhalb weniger Minuten. Akku-Optimierung kann sie verzögern."
            },
            deliverable = canNotify,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesUiState())

    fun setEnabled(schedule: Schedule, enabled: Boolean) = viewModelScope.launch {
        schedules.upsert(schedule.copy(enabled = enabled, updatedAt = clock.now()))
    }

    fun toggleSkipNext(schedule: Schedule) = viewModelScope.launch {
        val next = if (schedule.skipDate != null) schedule.copy(skipDate = null) else ScheduleCalculator.skipNext(schedule, clock.now(), clock.zone())
        schedules.upsert(next.copy(updatedAt = clock.now()))
    }

    fun delete(schedule: Schedule) = viewModelScope.launch { schedules.delete(schedule.id) }
}

data class ScheduleEditorState(
    val id: String? = null,
    val label: String = "",
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    val start: LocalTime = LocalTime.of(16, 0),
    val end: LocalTime = LocalTime.of(16, 45),
    val mode: ProtectionMode = ProtectionMode.STANDARD,
    val leadTime: Int = 10,
    val useSelectedApps: Boolean = true,
    val selectedAppCount: Int = 0,
    val enabled: Boolean = true,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val schedules: ScheduleRepository,
    private val protection: ProtectionCapabilityOwner,
    private val clock: FocusClock,
) : ViewModel() {
    private val editId = runCatching { savedState.toRoute<ScheduleEditorDestination>().scheduleId }.getOrNull()?.takeIf(EntityIds::isSafe)
    private val _state = MutableStateFlow(ScheduleEditorState())
    val state: StateFlow<ScheduleEditorState> = _state
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _events
    private var existing: Schedule? = null

    init {
        viewModelScope.launch {
            val p = protection.state.filterNotNull().first()
            val count = AppSelectionReconciler.watchablePackages(p.selectedApps).size
            existing = editId?.let { schedules.getSchedule(ScheduleId(it)) }
            _state.value = existing?.let { s ->
                ScheduleEditorState(s.id.value, s.label, s.weekdays, s.start, s.end, s.protectionMode, s.leadTimeMinutes, s.appPackages.isNotEmpty(), count, s.enabled)
            } ?: ScheduleEditorState(selectedAppCount = count)
        }
    }

    fun update(f: (ScheduleEditorState) -> ScheduleEditorState) = _state.update { f(it).copy(error = null) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val p = protection.state.filterNotNull().first()
            val now = clock.now()
            val schedule = Schedule(
                id = existing?.id ?: EntityIds.newScheduleId(),
                label = s.label.trim().take(60),
                weekdays = s.weekdays,
                start = s.start,
                end = s.end,
                zone = existing?.zone ?: ScheduleZone.FollowDevice,
                protectionMode = s.mode,
                appPackages = if (s.useSelectedApps) AppSelectionReconciler.watchablePackages(p.selectedApps) else emptySet(),
                leadTimeMinutes = s.leadTime,
                skipDate = existing?.skipDate,
                enabled = s.enabled,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            when (val v = ScheduleCalculator.validate(schedule)) {
                is ScheduleValidation.Invalid -> _state.update { it.copy(error = v.message) }
                ScheduleValidation.Valid -> { schedules.upsert(schedule); _events.emit(Unit) }
            }
        }
    }
}
