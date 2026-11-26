package io.github.illiapohukai.focusmode.core.domain.repository

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraft
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.model.ActivityRecord
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionReview
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

/**
 * Outcome of applying one Mission review. [applied] is false when this Session's result had
 * already been applied (the PWA `duplicatePrevented` case): nothing changed the second time.
 */
data class ReviewApplication(val result: FocusSessionResult, val mission: Mission?, val applied: Boolean)

/** One applied Mission Session result (PWA `mission.progress.history`), Mission-scoped. */
data class MissionProgressEntry(
    val sessionId: SessionId,
    val missionId: MissionId,
    val stepId: MissionStepId,
    val outcome: SessionOutcome,
    val rating: Int?,
    val actualMinutes: Int,
    val plannedMinutes: Int,
    val appliedAt: Instant,
)

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

    /**
     * Mission review, one transaction: updates the History record's outcome, rating and note,
     * records the Mission progress entry (primary key = Session id, so a result is applied once)
     * and moves the Mission: "Abgeschlossen" completes the step (and the Mission after the last
     * step); "Teilweise" and "Abgebrochen" keep the step current. Returns null for an unknown Session.
     */
    suspend fun applyMissionReview(id: SessionId, review: MissionReview): ReviewApplication?

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

/**
 * The one Mission owner (persistence): Missions, steps, the generation draft and the Mission
 * progress entries. Every multi-row change is one Room transaction. Composables and ViewModels
 * never touch DAOs; the AI request itself is coordinated by `MissionGenerationOwner`.
 */
interface MissionRepository {
    fun observeMissions(): Flow<List<Mission>>
    fun observeMission(id: MissionId): Flow<Mission?>
    suspend fun getMission(id: MissionId): Mission?
    /** Selected Mission if active, else the first active one (PWA primary/selected). */
    fun observePrimaryMission(): Flow<Mission?>
    /** Validated insert (safe ids, unique ids, active limit). Used by acceptance and test seeding. */
    suspend fun create(mission: Mission): MissionWriteResult
    suspend fun completeStep(missionId: MissionId, stepId: MissionStepId): MissionWriteResult
    suspend fun archive(id: MissionId): MissionWriteResult
    /** Archived → active again (limit permitting) at the remembered step (PWA `restoreMissionV116`). */
    suspend fun restore(id: MissionId): MissionWriteResult
    /** Deletes the Mission, its steps and progress entries; History keeps its Sessions. */
    suspend fun delete(id: MissionId): MissionWriteResult
    /**
     * PWA `renameMissionV116`: changes the title only (cleaned, 1–80 characters). The id, and with
     * it the identity colour, steps, progress, History links and reminder keys, never change.
     */
    suspend fun rename(id: MissionId, title: String): MissionWriteResult

    // ---- generation draft (single slot) ----
    fun observeDraft(): Flow<MissionDraft?>
    suspend fun getDraft(): MissionDraft?
    suspend fun saveDraft(draft: MissionDraft)
    /**
     * Writes [draft] only if the stored draft is still [draft]'s draft id in [expectedPhase].
     * A late network result for a cancelled or replaced request can therefore never land.
     */
    suspend fun saveDraftIf(draft: MissionDraft, expectedPhase: MissionDraftPhase): Boolean
    suspend fun discardDraft()
    /**
     * "Mission übernehmen": one transaction creates the Mission from the validated Briefing plan
     * and deletes the draft. A second call for the same draft returns the existing Mission; the
     * active-Mission limit is checked inside the transaction.
     */
    suspend fun acceptDraft(draftId: String): MissionWriteResult

    // ---- adaptation and progress ----
    /**
     * Replaces the open steps with [steps] only if the Mission is unchanged since
     * [expectedUpdatedAt] (PWA `missionUpdatedAt`). Completed steps and History never change.
     */
    suspend fun replaceRemainingSteps(id: MissionId, steps: List<AdaptedStep>, expectedUpdatedAt: Instant): MissionWriteResult
    suspend fun progressHistory(id: MissionId): List<MissionProgressEntry>
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
    /** PWA `focus-mode-home-onboarding-v17-16`; never reset by ordinary data changes. */
    suspend fun markHomeOnboardingDone()
    suspend fun setLastSelectedMission(id: String?)
    suspend fun incrementNotificationPermissionRequests()
    suspend fun setPwaImportEnabled(enabled: Boolean)
    suspend fun setLastExportAt(at: java.time.Instant)
    /**
     * Full data deletion: every user preference returns to its default (including the one-way
     * Home onboarding mark). Technical metadata needed at startup stays: the schema version, the
     * install id (OS-restore detection), the notification-permission request counter (the system
     * remembers a denial) and development flags.
     */
    suspend fun resetUserPreferences()
}

/** Durable record of a DND change made by Focus Mode+ (restored after the Session or a crash). */
data class DndOverride(val sessionId: SessionId, val priorFilter: Int, val appliedFilter: Int, val appliedAt: Instant)

interface DndOverrideStore {
    suspend fun get(): DndOverride?
    suspend fun put(override: DndOverride)
    suspend fun clear()
}
