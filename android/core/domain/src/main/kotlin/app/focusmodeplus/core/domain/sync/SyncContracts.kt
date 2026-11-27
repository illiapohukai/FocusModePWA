package app.focusmodeplus.core.domain.sync

import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.AccountDataDecision
import app.focusmodeplus.core.model.BindingStatus
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.MigrationPreview
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.SyncEntityType
import app.focusmodeplus.core.model.SyncFailure
import app.focusmodeplus.core.model.SyncOperation
import app.focusmodeplus.core.model.SyncResult
import app.focusmodeplus.core.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * The one synchronization owner (v21). Features read [syncState] and [decision]; only this owner
 * talks to the remote store. Composables never write Firestore; ViewModels never own listeners.
 */
interface SyncRepository {
    val syncState: StateFlow<SyncState>

    /** A decision only the user can make (link local data, account switch); null when none is open. */
    val decision: StateFlow<AccountDataDecision?>

    /** Local and (when reachable) cloud counts for the confirmation screen. Changes nothing. */
    suspend fun prepareMigration(uid: String): MigrationPreview

    /** "Lokale Daten mit diesem Konto verbinden": explicit, resumable, idempotent, verified. */
    suspend fun migrateLocalData(uid: String): SyncResult

    /** First download into a dataset that has no local data (new device, empty install). */
    suspend fun initialSync(uid: String): SyncResult

    /** "Cloud-Daten verwenden": replaces local synchronized data with the account's cloud data. */
    suspend fun adoptCloudData(uid: String): SyncResult

    /** Uploads due outbox mutations and the settings profile. */
    suspend fun uploadPendingMutations(): SyncResult

    suspend fun downloadRemoteChanges(): SyncResult

    /** Download, merge, upload; verifies and completes a running migration. Manual and background. */
    suspend fun synchronize(): SyncResult

    /** Removes the account binding, outbox and sync metadata. Local product data stays as guest data. */
    suspend fun unlinkAccount(): SyncResult

    /** "Später": no linking now; Settings keeps offering it. */
    fun postponeDecision()

    /** Re-queues parked mutations (manual retry after repeated failures). */
    suspend fun retryParked()
}

/** Background execution boundary (WorkManager on Android). One unique, account-aware job. */
interface SyncScheduler {
    /** Schedules the unique sync job for [uid] (network constraint, bounded backoff, coalesced). */
    fun schedule(uid: String, delayMs: Long = 0)
    /** Cancels the job (sign-out, account switch, deletion, local erase). */
    fun cancel()

    object None : SyncScheduler {
        override fun schedule(uid: String, delayMs: Long) = Unit
        override fun cancel() = Unit
    }
}

// ------------------------------------------------------------------------------------- local store

data class AccountBinding(
    val uid: String,
    val status: BindingStatus,
    val migrationId: String?,
    val boundAt: Instant,
    val migrationStartedAt: Instant? = null,
    val migrationCompletedAt: Instant? = null,
    val lastSyncAt: Instant? = null,
    val lastFailure: SyncFailure? = null,
    val settingsFingerprint: String? = null,
    val settingsRemoteMs: Long? = null,
    val cursorMissionsMs: Long? = null,
    val cursorSessionsMs: Long? = null,
    val cursorTombstonesMs: Long? = null,
    val conflictsResolved: Int = 0,
)

enum class OutboxState { PENDING, PARKED }

/**
 * One queued change. The payload is a reproducible entity reference: the current local entity is
 * read at upload time, so the outbox never duplicates Mission or History text.
 */
data class OutboxEntry(
    val mutationId: String,
    val accountUid: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val schemaVersion: Int,
    val localVersion: Long,
    val createdAt: Instant,
    val retryCount: Int,
    val lastFailure: SyncFailure?,
    val nextAttemptAt: Instant,
    val state: OutboxState,
)

/** What the server last confirmed for one entity (base version for optimistic concurrency). */
data class EntityState(
    val entityType: SyncEntityType,
    val entityId: String,
    val accountUid: String,
    val remoteVersion: Long,
    val remoteStepIds: List<String> = emptyList(),
    val lastMutationId: String? = null,
)

data class LocalDataSummary(
    val missions: Int,
    val missionSteps: Int,
    val sessions: Int,
    val awaitingReview: Int,
    val historyFrom: LocalDate?,
    val historyTo: LocalDate?,
    val focusMinutes: Int,
    val activeMissions: Int,
    val notSyncable: Int,
) {
    val isEmpty: Boolean get() = missions == 0 && sessions == 0 && awaitingReview == 0
}

/** Mission plus its progress entries, as uploaded in one document (steps go to the subcollection). */
data class MissionSnapshot(val mission: Mission, val progress: List<MissionProgressEntry>)

data class MigrationScope(val missionIds: Set<String>, val sessionIds: Set<String>)

data class RemoteMission(
    val snapshot: MissionSnapshot,
    val stepIds: List<String>,
    val syncVersion: Long,
    val deviceMutationId: String?,
    val serverUpdatedAtMs: Long?,
)

data class RemoteSession(val result: FocusSessionResult, val deviceMutationId: String?, val serverUpdatedAtMs: Long?)

data class RemoteTombstone(
    val entityType: SyncEntityType,
    val entityId: String,
    val deletedAt: Instant,
    val syncVersion: Long,
    val deviceMutationId: String?,
    val serverUpdatedAtMs: Long?,
)

data class RemoteSettings(
    val defaultQuickFocusMinutes: Int,
    val motion: app.focusmodeplus.core.model.MotionPreference,
    val notificationDetail: app.focusmodeplus.core.model.NotificationDetailLevel,
    val serverUpdatedAtMs: Long?,
)

data class RemoteChangeSet(
    val missions: List<RemoteMission> = emptyList(),
    val sessions: List<RemoteSession> = emptyList(),
    val tombstones: List<RemoteTombstone> = emptyList(),
) {
    val isEmpty: Boolean get() = missions.isEmpty() && sessions.isEmpty() && tombstones.isEmpty()
}

data class ApplyReport(
    val missionsApplied: Int = 0,
    val sessionsApplied: Int = 0,
    val tombstonesApplied: Int = 0,
    val conflicts: Int = 0,
    /** Remote deletions that wait because a running Session still uses the Mission. */
    val deferred: Int = 0,
    val archivedByLimit: Int = 0,
    /** False: the binding changed (sign-out, other account); nothing was written. */
    val accepted: Boolean = true,
)

/**
 * Room side of synchronization. Every method that writes runs in one Room transaction and checks
 * inside it that the dataset is still bound to the given uid, so a sign-out or account switch in
 * the middle of a sync can never write one account's data into another account's dataset.
 */
interface SyncLocalStore {
    fun observeBinding(): Flow<AccountBinding?>
    suspend fun binding(): AccountBinding?
    fun observePendingCount(): Flow<Int>
    fun observeParkedCount(): Flow<Int>
    /** True while Missions or History exist locally (synchronized categories only). */
    fun observeHasLocalData(): Flow<Boolean>

    suspend fun localSummary(): LocalDataSummary
    suspend fun hasActiveSession(): Boolean

    /** Binds the (empty or adopted) dataset to [uid] as ACTIVE. */
    suspend fun activate(uid: String, now: Instant)

    /**
     * One transaction: binding MIGRATING with [migrationId] and one UPSERT mutation for every local
     * Mission and finalized Session. Idempotent for the same uid and migration id. Returns false if
     * the dataset is bound to another account.
     */
    suspend fun beginMigration(uid: String, migrationId: String, now: Instant): Boolean
    suspend fun migrationScope(): MigrationScope
    /** Re-queues the given entities (verification found them missing remotely). */
    suspend fun requeue(uid: String, missionIds: Collection<String>, sessionIds: Collection<String>, now: Instant)
    suspend fun completeMigration(uid: String, migrationId: String, now: Instant): Boolean

    suspend fun markDeletionPending(uid: String): Boolean
    /** Removes binding, outbox, entity states, tombstones and conflicts in one transaction. */
    suspend fun clearAccountSyncState()

    suspend fun recordSuccess(uid: String, now: Instant)
    suspend fun recordFailure(uid: String, failure: SyncFailure)
    suspend fun recordSettings(uid: String, fingerprint: String, remoteMs: Long?)

    suspend fun dueMutations(uid: String, now: Instant, limit: Int): List<OutboxEntry>
    suspend fun pendingCount(uid: String): Int
    /**
     * v22 (V22-F01): outbox entries that can still be uploaded. An entry parked because the record
     * is invalid for the remote schema can never upload, so it must not hold a migration open.
     */
    suspend fun blockingCount(uid: String): Int = pendingCount(uid)
    /** Pending (not parked) mutations of one entity, regardless of their next attempt time. */
    suspend fun mutationsFor(uid: String, type: SyncEntityType, id: String): List<OutboxEntry>
    suspend fun loadMission(id: String): MissionSnapshot?
    suspend fun loadSession(id: String): FocusSessionResult?
    suspend fun entityState(type: SyncEntityType, id: String): EntityState?

    /** Deletes the covered outbox rows (by mutation id) and stores the confirmed entity states. */
    suspend fun markUploaded(uid: String, mutationIds: Collection<String>, states: Collection<EntityState>): Boolean
    suspend fun markFailed(mutationIds: Collection<String>, failure: SyncFailure, nextAttemptAt: Instant, park: Boolean)
    suspend fun retryParked(uid: String, now: Instant)

    /** Applies a download (merge per the conflict policy) and stores [cursors] in one transaction. */
    suspend fun applyRemote(uid: String, changes: RemoteChangeSet, now: Instant, cursors: SyncCursors? = null): ApplyReport

    /**
     * "Cloud-Daten verwenden": one transaction replaces local Missions, steps, progress, History
     * and Activity with [changes] and binds the dataset to [uid] as ACTIVE. Local-only data
     * (app selection, reminders, Geplanter Fokus, the draft) stays. Fails while a Session runs.
     */
    suspend fun replaceWithRemote(uid: String, changes: RemoteChangeSet, now: Instant, cursors: SyncCursors? = null): ApplyReport
}

data class SyncCursors(val missionsMs: Long?, val sessionsMs: Long?, val tombstonesMs: Long?)
