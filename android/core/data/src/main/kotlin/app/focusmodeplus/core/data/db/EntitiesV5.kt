package app.focusmodeplus.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/*
 * Schema version 5 (v21 Google Account & Secure Sync Foundation): account binding, outbox, the
 * server-confirmed entity versions, tombstones and resolved conflicts. No token, email, display
 * name or other credential is ever stored here; the Firebase UID is the only account reference.
 */

/** The one binding of the local dataset to an account (`slot` is always 0). Absent = guest data. */
@Entity(tableName = "account_binding")
data class AccountBindingEntity(
    @PrimaryKey val slot: Int = 0,
    val uid: String,
    val status: String,
    @ColumnInfo(name = "migration_id") val migrationId: String?,
    @ColumnInfo(name = "bound_at") val boundAt: Instant,
    @ColumnInfo(name = "migration_started_at") val migrationStartedAt: Instant?,
    @ColumnInfo(name = "migration_completed_at") val migrationCompletedAt: Instant?,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Instant?,
    @ColumnInfo(name = "last_failure") val lastFailure: String?,
    @ColumnInfo(name = "settings_fingerprint") val settingsFingerprint: String?,
    @ColumnInfo(name = "settings_remote_ms") val settingsRemoteMs: Long?,
    @ColumnInfo(name = "cursor_missions_ms") val cursorMissionsMs: Long?,
    @ColumnInfo(name = "cursor_sessions_ms") val cursorSessionsMs: Long?,
    @ColumnInfo(name = "cursor_tombstones_ms") val cursorTombstonesMs: Long?,
    @ColumnInfo(name = "conflicts_resolved", defaultValue = "0") val conflictsResolved: Int = 0,
)

/**
 * One queued change, written in the same Room transaction as the product change. The payload is a
 * reproducible entity reference (type + id): the current row is read at upload time.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["account_uid", "entity_type", "entity_id"]), Index("next_attempt_at")],
)
data class SyncOutboxEntity(
    @PrimaryKey @ColumnInfo(name = "mutation_id") val mutationId: String,
    @ColumnInfo(name = "account_uid") val accountUid: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "local_version") val localVersion: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "retry_count", defaultValue = "0") val retryCount: Int = 0,
    @ColumnInfo(name = "last_failure") val lastFailure: String? = null,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Instant,
    @ColumnInfo(defaultValue = "'pending'") val state: String = "pending",
)

/** The version the server last confirmed for one entity (optimistic concurrency base). */
@Entity(tableName = "sync_entity_state", primaryKeys = ["entity_type", "entity_id"])
data class SyncEntityStateEntity(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "account_uid") val accountUid: String,
    @ColumnInfo(name = "remote_version") val remoteVersion: Long,
    /** Step ids the server holds for a Mission, one per line (ids never contain line breaks). */
    @ColumnInfo(name = "remote_step_ids", defaultValue = "''") val remoteStepIds: String = "",
    @ColumnInfo(name = "last_mutation_id") val lastMutationId: String?,
)

/** A deleted entity (locally or on another device). A tombstoned Mission is never re-inserted by a download. */
@Entity(tableName = "sync_tombstones", primaryKeys = ["entity_type", "entity_id"])
data class SyncTombstoneEntity(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "account_uid") val accountUid: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant,
    /** "local" (this device deleted it) or "remote". */
    val origin: String,
)

/** A resolved conflict (privacy-safe: ids, time and the rule applied; never content). */
@Entity(tableName = "sync_conflicts", indices = [Index("detected_at")])
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "detected_at") val detectedAt: Instant,
    val resolution: String,
)
