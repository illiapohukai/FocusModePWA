package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraft
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
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
import io.github.illiapohukai.focusmode.core.domain.repository.MissionProgressEntry
import io.github.illiapohukai.focusmode.core.domain.repository.ReviewApplication
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
import io.github.illiapohukai.focusmode.core.model.MissionReview
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
    override suspend fun applyMissionReview(id: SessionId, review: MissionReview): ReviewApplication? {
        val current = getResult(id) ?: return null
        val link = current.missionLink
        val repo = missions
        if (current.endReason != SessionEndReason.AWAITING_USER_REVIEW) {
            return ReviewApplication(current, link?.let { repo?.getMission(it.missionId) }, applied = false)
        }
        val updated = current.copy(outcome = review.outcome, rating = review.rating, endReason = SessionEndReason.USER_REVIEWED, reviewNote = review.note)
        results.value = results.value.map { if (it.sessionId == id) updated else it }
        if (link != null && repo != null) repo.applyReview(updated, link, review)
        return ReviewApplication(updated, link?.let { repo?.getMission(it.missionId) }, applied = true)
    }
    override fun observeActivity(limit: Int): Flow<List<ActivityRecord>> = activity.map { it.reversed().take(limit) }
    override suspend fun pendingReview(): FocusSessionResult? =
        results.value.filter { it.endReason == SessionEndReason.AWAITING_USER_REVIEW }.maxByOrNull { it.finishedAt }
}

class FakeMissionRepository(private val clock: FakeClock) : MissionRepository {
    val missions = MutableStateFlow<List<Mission>>(emptyList())
    val draft = MutableStateFlow<MissionDraft?>(null)
    val progress = MutableStateFlow<List<MissionProgressEntry>>(emptyList())
    var acceptCalls = 0
    override fun observeMissions(): Flow<List<Mission>> = missions
    override fun observeMission(id: MissionId): Flow<Mission?> = missions.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun getMission(id: MissionId) = missions.value.firstOrNull { it.id == id }
    override fun observePrimaryMission(): Flow<Mission?> = missions.map { l -> l.firstOrNull { it.status == MissionStatus.ACTIVE } }
    override suspend fun create(mission: Mission): MissionWriteResult {
        if (missions.value.any { it.id == mission.id }) return MissionWriteResult.Rejected("mission_id_duplicate")
        if (mission.status == MissionStatus.ACTIVE && !MissionRules.canCreateActive(missions.value.count { it.status == MissionStatus.ACTIVE })) {
            return MissionWriteResult.Rejected("active_mission_limit_reached")
        }
        missions.value = missions.value + mission
        return MissionWriteResult.Ok(mission)
    }
    private fun put(m: Mission) { missions.value = missions.value.map { if (it.id == m.id) m else it } }
    override suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult {
        val m = getMission(missionId) ?: return MissionWriteResult.Rejected("mission_not_found")
        return when (val c = MissionRules.completeStep(m, stepId, clock.now())) {
            is StepCompletion.Completed -> { put(c.mission); MissionWriteResult.Ok(c.mission) }
            is StepCompletion.AlreadyCompleted -> MissionWriteResult.Ok(c.mission)
            StepCompletion.StepNotFound -> MissionWriteResult.Rejected("step_not_found")
        }
    }
    /** Same rules as the Room transaction in RoomSessionRepository.applyMissionReview. */
    fun applyReview(result: FocusSessionResult, link: io.github.illiapohukai.focusmode.core.model.MissionLink, review: MissionReview) {
        val m = missions.value.firstOrNull { it.id == link.missionId } ?: return
        if (progress.value.any { it.sessionId == result.sessionId }) return
        progress.value = progress.value + MissionProgressEntry(result.sessionId, m.id, link.stepId, review.outcome, review.rating, result.actualMinutes, result.plannedMinutes, clock.now())
        val next = if (review.completesStep) (MissionRules.completeStep(m, link.stepId, clock.now()) as? StepCompletion.Completed)?.mission
        else MissionRules.keepStepCurrent(m, link.stepId, clock.now())
        next?.let(::put)
    }
    override suspend fun archive(id: MissionId): MissionWriteResult {
        val m = getMission(id) ?: return MissionWriteResult.Rejected("mission_not_found")
        val a = MissionRules.archive(m, clock.now()); put(a); return MissionWriteResult.Ok(a)
    }
    override suspend fun restore(id: MissionId): MissionWriteResult {
        val m = getMission(id) ?: return MissionWriteResult.Rejected("mission_not_found")
        val r = MissionRules.restore(m, clock.now()) ?: return MissionWriteResult.Rejected("mission_not_archived")
        if (r.status == MissionStatus.ACTIVE && !MissionRules.canCreateActive(missions.value.count { it.status == MissionStatus.ACTIVE })) {
            return MissionWriteResult.Rejected("active_mission_limit_reached")
        }
        put(r); return MissionWriteResult.Ok(r)
    }
    override suspend fun delete(id: MissionId): MissionWriteResult {
        val m = getMission(id) ?: return MissionWriteResult.Rejected("mission_not_found")
        missions.value = missions.value - m
        progress.value = progress.value.filterNot { it.missionId == id }
        return MissionWriteResult.Ok(m)
    }
    override fun observeDraft(): Flow<MissionDraft?> = draft
    override suspend fun getDraft(): MissionDraft? = draft.value
    override suspend fun saveDraft(draft: MissionDraft) { this.draft.value = draft }
    override suspend fun saveDraftIf(draft: MissionDraft, expectedPhase: MissionDraftPhase): Boolean {
        val current = this.draft.value ?: return false
        if (current.draftId != draft.draftId || current.phase != expectedPhase) return false
        this.draft.value = draft
        return true
    }
    override suspend fun discardDraft() { draft.value = null }
    override suspend fun acceptDraft(draftId: String): MissionWriteResult {
        acceptCalls++
        val d = draft.value
        if (d == null || d.draftId != draftId) return MissionWriteResult.Rejected("draft_missing")
        getMission(d.missionId)?.let { draft.value = null; return MissionWriteResult.Ok(it) }
        val plan = d.plan ?: return MissionWriteResult.Rejected("plan_missing")
        if (d.phase != MissionDraftPhase.BRIEFING) return MissionWriteResult.Rejected("plan_missing")
        val r = create(MissionRules.missionFromPlan(d, plan, clock.now()))
        if (r is MissionWriteResult.Ok) draft.value = null
        return r
    }
    override suspend fun replaceRemainingSteps(id: MissionId, steps: List<AdaptedStep>, expectedUpdatedAt: Instant): MissionWriteResult {
        val m = getMission(id) ?: return MissionWriteResult.Rejected("mission_not_found")
        if (m.updatedAt != expectedUpdatedAt) return MissionWriteResult.Rejected("mission_changed")
        val u = MissionRules.replaceRemainingSteps(m, steps, clock.now()) ?: return MissionWriteResult.Rejected("adaptation_invalid")
        put(u); return MissionWriteResult.Ok(u)
    }
    override suspend fun progressHistory(id: MissionId): List<MissionProgressEntry> = progress.value.filter { it.missionId == id }
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
    override suspend fun markHomeOnboardingDone() = edit { it.copy(homeOnboardingDone = true) }
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
