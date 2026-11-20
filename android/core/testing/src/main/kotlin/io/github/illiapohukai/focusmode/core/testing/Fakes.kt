package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.StepCompletion
import io.github.illiapohukai.focusmode.core.domain.platform.DndApplyResult
import io.github.illiapohukai.focusmode.core.domain.platform.DoNotDisturbController
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.SessionEndAlarm
import io.github.illiapohukai.focusmode.core.domain.platform.SessionNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.SessionRuntime
import io.github.illiapohukai.focusmode.core.domain.platform.SystemSettingsNavigator
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionLoad
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionStore
import io.github.illiapohukai.focusmode.core.domain.repository.FinalizeOutcome
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ReminderStore
import io.github.illiapohukai.focusmode.core.domain.repository.ScheduleRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SelectedAppRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.model.ActivityRecord
import io.github.illiapohukai.focusmode.core.model.ActivityType
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.SelectedDistractingApp
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SettingsTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime

/**
 * In-memory Session store + History with the same contract as the Room implementation:
 * one active Session, idempotent finalization that also clears the active row.
 * [simulateProcessDeath] returns a fresh store over the same "disk".
 */
class FakeSessionStore(private val clock: FakeClock) : ActiveSessionStore, SessionHistoryRepository {
    var active: FocusSession? = null
    val results = MutableStateFlow<List<FocusSessionResult>>(emptyList())
    val activity = MutableStateFlow<List<ActivityRecord>>(emptyList())
    var saves = 0
    var missions: FakeMissionRepository? = null

    override suspend fun load(): ActiveSessionLoad = active?.let { ActiveSessionLoad.Loaded(it) } ?: ActiveSessionLoad.None
    override suspend fun save(session: FocusSession) {
        check(active == null || active!!.id == session.id) { "another_session_active" }
        active = session; saves++
    }
    override suspend fun clear(id: SessionId): Boolean = (active?.id == id).also { if (it) active = null }

    override fun observeResults(): Flow<List<FocusSessionResult>> = results
    override fun observeResult(id: SessionId): Flow<FocusSessionResult?> = results.map { l -> l.firstOrNull { it.sessionId == id } }
    override suspend fun getResult(id: SessionId) = results.value.firstOrNull { it.sessionId == id }
    override suspend fun finalize(result: FocusSessionResult): FinalizeOutcome {
        val existing = getResult(result.sessionId)
        if (active?.id == result.sessionId) active = null
        if (existing != null) return FinalizeOutcome(existing, false)
        results.value = (results.value + result).sortedByDescending { it.finishedAt }
        activity.value = activity.value + ActivityRecord(activity.value.size + 1L, ActivityType.SESSION_FINISHED, result.sessionId.value, result.finishedAt, result.localDate)
        return FinalizeOutcome(result, true)
    }
    override suspend fun applyMissionReview(id: SessionId, outcome: SessionOutcome, rating: Int?, completeStep: Boolean): FocusSessionResult? {
        val current = getResult(id) ?: return null
        val updated = current.copy(outcome = outcome, rating = rating, endReason = SessionEndReason.USER_REVIEWED)
        results.value = results.value.map { if (it.sessionId == id) updated else it }
        val link = current.missionLink
        if (completeStep && link != null) missions?.completeStep(link.missionId, link.stepId)
        return updated
    }
    override fun observeActivity(limit: Int): Flow<List<ActivityRecord>> = activity.map { it.reversed().take(limit) }
    override suspend fun pendingReview(): FocusSessionResult? =
        results.value.filter { it.endReason == SessionEndReason.AWAITING_USER_REVIEW }.maxByOrNull { it.finishedAt }
}

class FakeMissionRepository(private val clock: FakeClock) : MissionRepository {
    val missions = MutableStateFlow<List<Mission>>(emptyList())
    override fun observeMissions(): Flow<List<Mission>> = missions
    override fun observeMission(id: MissionId): Flow<Mission?> = missions.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getMission(id: MissionId) = missions.value.firstOrNull { it.id == id }
    override fun observePrimaryMission(): Flow<Mission?> = missions.map { l -> l.firstOrNull { it.status == MissionStatus.ACTIVE } }
    override suspend fun create(mission: Mission): MissionWriteResult {
        if (missions.value.any { it.id == mission.id }) return MissionWriteResult.Rejected("mission_id_duplicate")
        missions.value = missions.value + mission
        return MissionWriteResult.Ok(mission)
    }
    override suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult {
        val m = getMission(missionId) ?: return MissionWriteResult.Rejected("mission_not_found")
        return when (val c = MissionRules.completeStep(m, stepId, clock.now())) {
            is StepCompletion.Completed -> { missions.value = missions.value.map { if (it.id == missionId) c.mission else it }; MissionWriteResult.Ok(c.mission) }
            is StepCompletion.AlreadyCompleted -> MissionWriteResult.Ok(c.mission)
            StepCompletion.StepNotFound -> MissionWriteResult.Rejected("step_not_found")
        }
    }
    override suspend fun archive(id: MissionId) = MissionWriteResult.Rejected("unsupported_in_fake")
    override suspend fun delete(id: MissionId): MissionWriteResult {
        val m = getMission(id) ?: return MissionWriteResult.Rejected("mission_not_found")
        missions.value = missions.value - m
        return MissionWriteResult.Ok(m)
    }
}

class FakePreferencesRepository(initial: AppPreferences = AppPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<AppPreferences> = state
    override suspend fun current() = state.value
    private fun edit(f: (AppPreferences) -> AppPreferences) { state.value = f(state.value) }
    override suspend fun setDefaultQuickFocusMinutes(minutes: Int) = edit { it.copy(defaultQuickFocusMinutes = minutes) }
    override suspend fun setMotion(preference: MotionPreference) = edit { it.copy(motion = preference) }
    override suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it.copy(notificationsEnabled = enabled) }
    override suspend fun setNotificationDetail(level: NotificationDetailLevel) = edit { it.copy(notificationDetail = level) }
    override suspend fun setMissionRemindersEnabled(enabled: Boolean) = edit { it.copy(missionRemindersEnabled = enabled) }
    override suspend fun setDailyPlanning(enabled: Boolean, time: LocalTime) = edit { it.copy(dailyPlanningEnabled = enabled, dailyPlanningTime = time) }
    override suspend fun setDefaultProtectionMode(mode: ProtectionMode) = edit { it.copy(defaultProtectionMode = mode) }
    override suspend fun setUsageMonitoringEnabled(enabled: Boolean) = edit { it.copy(usageMonitoringEnabled = enabled) }
    override suspend fun setDoNotDisturbDuringFocus(enabled: Boolean) = edit { it.copy(doNotDisturbDuringFocus = enabled) }
    override suspend fun setOnboardingCompleted(done: Boolean) = edit { it.copy(onboardingCompleted = done) }
    override suspend fun setLastSelectedMission(id: String?) = edit { it.copy(lastSelectedMissionId = id) }
    override suspend fun incrementNotificationPermissionRequests() = edit { it.copy(notificationPermissionRequests = it.notificationPermissionRequests + 1) }
    override suspend fun setPwaImportEnabled(enabled: Boolean) = edit { it.copy(featureFlags = it.featureFlags.copy(pwaImportEnabled = enabled)) }
}

class FakeScheduleRepository : ScheduleRepository {
    val schedules = MutableStateFlow<List<Schedule>>(emptyList())
    override fun observeSchedules(): Flow<List<Schedule>> = schedules
    override suspend fun getSchedule(id: ScheduleId) = schedules.value.firstOrNull { it.id == id }
    override suspend fun getAll() = schedules.value
    override suspend fun upsert(schedule: Schedule) { schedules.value = schedules.value.filterNot { it.id == schedule.id } + schedule }
    override suspend fun delete(id: ScheduleId) { schedules.value = schedules.value.filterNot { it.id == id } }
}

class FakeSelectedAppRepository : SelectedAppRepository {
    val selected = MutableStateFlow<List<SelectedDistractingApp>>(emptyList())
    override fun observeSelected(): Flow<List<SelectedDistractingApp>> = selected
    override suspend fun getSelected() = selected.value
    override suspend fun setSelected(packageName: String, label: String, selected: Boolean) {
        this.selected.value = this.selected.value.filterNot { it.packageName == packageName } +
            (if (selected) listOf(SelectedDistractingApp(packageName, label, Instant.EPOCH)) else emptyList())
    }
    override suspend fun updateLabelSnapshot(packageName: String, label: String) {
        selected.value = selected.value.map { if (it.packageName == packageName) it.copy(labelSnapshot = label) else it }
    }
    override suspend fun clear() { selected.value = emptyList() }
}

class FakeReminderStore : ReminderStore {
    val reminders = MutableStateFlow<Map<String, Reminder>>(emptyMap())
    override fun observeUpcoming(limit: Int): Flow<List<Reminder>> =
        reminders.map { m -> m.values.filter { it.deliveredAt == null }.sortedBy { it.triggerAt }.take(limit) }
    override suspend fun getAll() = reminders.value.values.toList()
    override suspend fun get(key: String) = reminders.value[key]
    override suspend fun upsert(reminder: Reminder) { reminders.value = reminders.value + (reminder.key to reminder) }
    override suspend fun delete(key: String) { reminders.value = reminders.value - key }
    override suspend fun markDelivered(key: String, at: Instant): Boolean {
        val r = reminders.value[key] ?: return false
        if (r.deliveredAt != null) return false
        reminders.value = reminders.value + (key to r.copy(deliveredAt = at))
        return true
    }
}

/** Records platform side effects so tests can assert on them. */
class FakePlatform : SessionRuntime, SessionEndAlarm, SessionNotifications, ReminderNotifications, DoNotDisturbController, SystemSettingsNavigator {
    var runtimeRunning = false
    var alarmAt: Long? = null
    val finishedShown = mutableListOf<FocusSessionResult>()
    val frozenShown = mutableListOf<FocusSession>()
    var cleared = 0
    val reminders = mutableListOf<Reminder>()
    var hints = 0
    var notificationsDeliverable = true
    var dndAccess = false
    var dndApplied = false
    var dndRestores = 0
    val openedSettings = mutableListOf<SettingsTarget>()

    override fun ensureRunning(session: FocusSession) { runtimeRunning = true }
    override fun stop() { runtimeRunning = false }
    override fun schedule(session: FocusSession, endRealtimeMs: Long) { alarmAt = endRealtimeMs }
    override fun cancel() { alarmAt = null }
    override fun showFinished(result: FocusSessionResult) { finishedShown += result }
    override fun showFrozen(session: FocusSession) { frozenShown += session }
    override fun clearSessionNotifications() { cleared++ }
    override fun showReminder(reminder: Reminder): Boolean = notificationsDeliverable.also { if (it) reminders += reminder }
    override fun showDistractionHint(remainingMinutes: Int): Boolean = notificationsDeliverable.also { if (it) hints++ }
    override fun isSupported() = true
    override fun hasAccess() = dndAccess
    override suspend fun applyForSession(sessionId: SessionId): DndApplyResult =
        if (!dndAccess) DndApplyResult.NoAccess else { dndApplied = true; DndApplyResult.Applied }
    override suspend fun restoreIfChanged(): Boolean { dndRestores++; val was = dndApplied; dndApplied = false; return was }
    override fun open(target: SettingsTarget): Boolean { openedSettings += target; return true }
}

/** Combines two flows for tests without importing coroutine operators everywhere. */
fun <A, B, R> Flow<A>.with(other: Flow<B>, f: (A, B) -> R): Flow<R> = combine(this, other, f)
