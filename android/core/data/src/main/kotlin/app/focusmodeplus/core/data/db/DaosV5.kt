package app.focusmodeplus.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** v21 synchronization metadata. Only the sync store and the outbox writer use it. */
@Dao
interface SyncDao {
    // ---- binding
    @Query("SELECT * FROM account_binding WHERE slot = 0")
    suspend fun binding(): AccountBindingEntity?

    @Query("SELECT * FROM account_binding WHERE slot = 0")
    fun observeBinding(): Flow<AccountBindingEntity?>

    @Upsert suspend fun putBinding(entity: AccountBindingEntity)

    @Query("DELETE FROM account_binding") suspend fun clearBinding()

    // ---- outbox
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(entity: SyncOutboxEntity)

    @Query("SELECT COALESCE(MAX(local_version), 0) FROM sync_outbox")
    suspend fun maxLocalVersion(): Long

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state = 'pending' AND account_uid = (SELECT uid FROM account_binding WHERE slot = 0)")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state = 'parked' AND account_uid = (SELECT uid FROM account_binding WHERE slot = 0)")
    fun observeParkedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE account_uid = :uid")
    suspend fun countForAccount(uid: String): Int

    /** v22: entries that can still upload; a record parked as invalid data never will. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE account_uid = :uid AND NOT (state = 'parked' AND last_failure = :invalid)")
    suspend fun countBlockingForAccount(uid: String, invalid: String): Int

    @Query("SELECT * FROM sync_outbox WHERE account_uid = :uid AND state = 'pending' AND next_attempt_at <= :now ORDER BY local_version ASC LIMIT :limit")
    suspend fun due(uid: String, now: Instant, limit: Int): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE account_uid = :uid AND entity_type = :type AND entity_id = :id AND state = 'pending' ORDER BY local_version ASC")
    suspend fun outboxFor(uid: String, type: String, id: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox ORDER BY local_version ASC")
    suspend fun allOutbox(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE mutation_id IN (:ids)")
    suspend fun deleteOutbox(ids: List<String>): Int

    @Query("UPDATE sync_outbox SET retry_count = retry_count + 1, last_failure = :failure, next_attempt_at = :nextAt, state = :state WHERE mutation_id IN (:ids)")
    suspend fun markFailed(ids: List<String>, failure: String, nextAt: Instant, state: String)

    @Query("UPDATE sync_outbox SET state = 'pending', retry_count = 0, next_attempt_at = :now WHERE state = 'parked' AND account_uid = :uid")
    suspend fun retryParked(uid: String, now: Instant)

    @Query("DELETE FROM sync_outbox") suspend fun clearOutbox()

    // ---- entity state
    @Query("SELECT * FROM sync_entity_state WHERE entity_type = :type AND entity_id = :id")
    suspend fun entityState(type: String, id: String): SyncEntityStateEntity?

    @Upsert suspend fun putEntityStates(entities: List<SyncEntityStateEntity>)

    @Query("DELETE FROM sync_entity_state") suspend fun clearEntityStates()

    // ---- tombstones
    @Query("SELECT * FROM sync_tombstones WHERE entity_type = :type AND entity_id = :id")
    suspend fun tombstone(type: String, id: String): SyncTombstoneEntity?

    @Upsert suspend fun putTombstone(entity: SyncTombstoneEntity)

    @Query("DELETE FROM sync_tombstones") suspend fun clearTombstones()

    // ---- conflicts
    @Insert suspend fun insertConflict(entity: SyncConflictEntity)

    @Query("SELECT COUNT(*) FROM sync_conflicts") suspend fun conflictCount(): Int

    @Query("DELETE FROM sync_conflicts") suspend fun clearConflicts()

    // ---- local data summary (synchronized categories only)
    @Query("SELECT EXISTS(SELECT 1 FROM missions) OR EXISTS(SELECT 1 FROM session_results)")
    fun observeHasLocalData(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM mission_steps") suspend fun stepCount(): Int

    @Query("SELECT session_id FROM session_results WHERE end_reason != 'awaiting_user_review'")
    suspend fun finalizedSessionIds(): List<String>

    @Query("SELECT COUNT(*) FROM session_results WHERE end_reason = 'awaiting_user_review'")
    suspend fun awaitingReviewCount(): Int

    @Query("SELECT MIN(local_date) FROM session_results WHERE end_reason != 'awaiting_user_review'")
    suspend fun historyFrom(): String?

    @Query("SELECT MAX(local_date) FROM session_results WHERE end_reason != 'awaiting_user_review'")
    suspend fun historyTo(): String?

    @Query("SELECT COALESCE(SUM(actual_minutes), 0) FROM session_results WHERE counts_as_focus = 1 AND end_reason != 'awaiting_user_review'")
    suspend fun focusMinutes(): Int

    @Query("DELETE FROM mission_steps WHERE mission_id = :missionId")
    suspend fun deleteSteps(missionId: String)

    @Query("SELECT mission_id FROM mission_steps WHERE id IN (:ids) AND mission_id != :missionId")
    suspend fun stepIdsOwnedByOthers(ids: List<String>, missionId: String): List<String>
}
