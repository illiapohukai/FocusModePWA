package io.github.illiapohukai.focusmode.core.domain.repository

import io.github.illiapohukai.focusmode.core.model.ActivityRecord
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.SelectedDistractingApp
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalTime

sealed interface ActiveSessionLoad {
    data object None : ActiveSessionLoad
    data class Loaded(val session: FocusSession) : ActiveSessionLoad
    /** The stored row failed validation; it was quarantined (not silently reused). */
    data class Corrupt(val reason: String) : ActiveSessionLoad
}

/** Durable store of the one active Session. Written only by the Session owner. */
interface ActiveSessionStore {
    suspend fun load(): ActiveSessionLoad
    suspend fun save(session: FocusSession)
    /** Clears the active Session only when [id] matches (PWA `clearActive(expectedId)`). */
    suspend fun clear(id: SessionId): Boolean
}

data class FinalizeOutcome(val result: FocusSessionResult, val created: Boolean)

/** The one History owner. Records are append-only snapshots; one per Session id. */
interface SessionHistoryRepository {
    fun observeResults(): Flow<List<FocusSessionResult>>
    fun observeResult(id: SessionId): Flow<FocusSessionResult?>
    suspend fun getResult(id: SessionId): FocusSessionResult?

    /**
     * One transaction: insert the History record unless it already exists, clear the active
     * Session row and append one Activity entry. Calling it twice for the same Session returns
     * the existing record with `created = false`.
     */
    suspend fun finalize(result: FocusSessionResult): FinalizeOutcome

    /** Mission review: update the outcome and (optionally) complete the step atomically. */
    suspend fun applyMissionReview(id: SessionId, outcome: SessionOutcome, rating: Int?, completeStep: Boolean): FocusSessionResult?

    fun observeActivity(limit: Int): Flow<List<ActivityRecord>>

    /** Most recent Mission Session still awaiting its step review (durable across process death). */
    suspend fun pendingReview(): FocusSessionResult?
}

/** Local distraction feedback of protected Sessions. Minimal retention, never uploaded. */
interface DistractionLog {
    suspend fun record(sessionId: SessionId, packageName: String, at: Instant)
    fun observeCount(sessionId: SessionId): Flow<Int>
    suspend fun purgeOlderThan(cutoff: Instant): Int
    suspend fun purgeAll(): Int
}

sealed interface MissionWriteResult {
    data class Ok(val mission: Mission) : MissionWriteResult
    data class Rejected(val reason: String) : MissionWriteResult
}

/** The one Mission owner. */
interface MissionRepository {
    fun observeMissions(): Flow<List<Mission>>
    fun observeMission(id: MissionId): Flow<Mission?>
    suspend fun getMission(id: MissionId): Mission?
    /** Selected Mission if active, else the first active one (PWA primary/selected). */
    fun observePrimaryMission(): Flow<Mission?>
    suspend fun create(mission: Mission): MissionWriteResult
    suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult
    suspend fun archive(id: MissionId): MissionWriteResult
    suspend fun delete(id: MissionId): MissionWriteResult
}

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    suspend fun getSchedule(id: ScheduleId): Schedule?
    suspend fun getAll(): List<Schedule>
    suspend fun upsert(schedule: Schedule)
    suspend fun delete(id: ScheduleId)
}

interface SelectedAppRepository {
    fun observeSelected(): Flow<List<SelectedDistractingApp>>
    suspend fun getSelected(): List<SelectedDistractingApp>
    suspend fun setSelected(packageName: String, label: String, selected: Boolean)
    suspend fun updateLabelSnapshot(packageName: String, label: String)
    suspend fun clear()
}

/** Persisted reminder occurrences (used only by the reminder scheduler). */
interface ReminderStore {
    fun observeUpcoming(limit: Int): Flow<List<Reminder>>
    suspend fun getAll(): List<Reminder>
    suspend fun get(key: String): Reminder?
    suspend fun upsert(reminder: Reminder)
    suspend fun delete(key: String)
    suspend fun markDelivered(key: String, at: Instant): Boolean
}

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun current(): AppPreferences
    suspend fun setDefaultQuickFocusMinutes(minutes: Int)
    suspend fun setMotion(preference: MotionPreference)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotificationDetail(level: NotificationDetailLevel)
    suspend fun setMissionRemindersEnabled(enabled: Boolean)
    suspend fun setDailyPlanning(enabled: Boolean, time: LocalTime)
    suspend fun setDefaultProtectionMode(mode: ProtectionMode)
    suspend fun setUsageMonitoringEnabled(enabled: Boolean)
    suspend fun setDoNotDisturbDuringFocus(enabled: Boolean)
    suspend fun setOnboardingCompleted(done: Boolean)
    suspend fun setLastSelectedMission(id: String?)
    suspend fun incrementNotificationPermissionRequests()
    suspend fun setPwaImportEnabled(enabled: Boolean)
}

/** Durable record of a DND change made by Focus Mode+ (restored after the Session or a crash). */
data class DndOverride(val sessionId: SessionId, val priorFilter: Int, val appliedFilter: Int, val appliedAt: Instant)

interface DndOverrideStore {
    suspend fun get(): DndOverride?
    suspend fun put(override: DndOverride)
    suspend fun clear()
}
