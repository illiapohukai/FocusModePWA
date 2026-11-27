package app.focusmodeplus.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MissionDao {
    @Transaction @Query("SELECT * FROM missions ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<MissionWithSteps>>

    @Transaction @Query("SELECT * FROM missions ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<MissionWithSteps>

    @Transaction @Query("SELECT * FROM missions WHERE id = :id")
    fun observe(id: String): Flow<MissionWithSteps?>

    @Transaction @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun get(id: String): MissionWithSteps?

    @Query("SELECT COUNT(*) FROM missions WHERE status = 'active'")
    suspend fun countActive(): Int

    @Query("SELECT id FROM missions")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(mission: MissionEntity)

    @Update
    suspend fun update(mission: MissionEntity)

    @Upsert
    suspend fun upsertSteps(steps: List<MissionStepEntity>)

    /** ABORT: a step id already used by any Mission is never silently overwritten. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSteps(steps: List<MissionStepEntity>)

    @Query("SELECT COUNT(*) FROM mission_steps WHERE id IN (:ids)")
    suspend fun countStepIds(ids: List<String>): Int

    @Query("SELECT id FROM mission_steps WHERE id IN (:ids)")
    suspend fun existingStepIds(ids: List<String>): List<String>

    @Query("DELETE FROM mission_steps WHERE mission_id = :missionId AND status != 'completed'")
    suspend fun deleteOpenSteps(missionId: String): Int

    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM active_session WHERE slot = 0")
    suspend fun getActive(): ActiveSessionEntity?

    @Query("SELECT * FROM active_session WHERE slot = 0")
    fun observeActive(): Flow<ActiveSessionEntity?>

    /** ABORT: a second active Session cannot be inserted while one exists. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActive(entity: ActiveSessionEntity)

    @Update
    suspend fun updateActive(entity: ActiveSessionEntity): Int

    @Query("DELETE FROM active_session WHERE session_id = :sessionId")
    suspend fun deleteActive(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM active_session WHERE mission_id = :missionId")
    suspend fun activeCountForMission(missionId: String): Int

    /** IGNORE: finalizing the same Session twice keeps the first record (returns -1). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResult(entity: SessionResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertResults(entities: List<SessionResultEntity>): List<Long>

    @Query("SELECT * FROM session_results ORDER BY finished_at DESC, session_id DESC")
    fun observeResults(): Flow<List<SessionResultEntity>>

    @Query("SELECT * FROM session_results ORDER BY finished_at DESC, session_id DESC")
    suspend fun allResults(): List<SessionResultEntity>

    @Query("SELECT * FROM session_results WHERE session_id = :id")
    fun observeResult(id: String): Flow<SessionResultEntity?>

    @Query("SELECT * FROM session_results WHERE session_id = :id")
    suspend fun getResult(id: String): SessionResultEntity?

    @Query("SELECT session_id FROM session_results")
    suspend fun allResultIds(): List<String>

    @Query("SELECT * FROM session_results WHERE end_reason = 'awaiting_user_review' ORDER BY finished_at DESC LIMIT 1")
    suspend fun pendingReview(): SessionResultEntity?

    @Query("UPDATE session_results SET outcome = :outcome, rating = :rating, end_reason = :reason, review_note = :note WHERE session_id = :id")
    suspend fun updateReview(id: String, outcome: String, rating: Int?, reason: String, note: String?): Int

    @Query("SELECT COUNT(*) FROM session_results WHERE type = 'mission'")
    suspend fun missionResultCount(): Int
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ActivityEntity): Long

    @Query("SELECT * FROM activity_log ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE delivered_at IS NULL ORDER BY trigger_at ASC LIMIT :limit")
    fun observeUpcoming(limit: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE `key` = :key")
    suspend fun get(key: String): ReminderEntity?

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Returns 1 only for the first delivery of an occurrence (duplicate-delivery guard). */
    @Query("UPDATE reminders SET delivered_at = :at WHERE `key` = :key AND delivered_at IS NULL")
    suspend fun markDelivered(key: String, at: Instant): Int

    @Query("DELETE FROM reminders WHERE delivered_at IS NOT NULL AND delivered_at < :cutoff")
    suspend fun purgeDelivered(cutoff: Instant): Int
}

@Dao
interface SelectedAppDao {
    @Query("SELECT * FROM selected_apps ORDER BY label_snapshot COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SelectedAppEntity>>

    @Query("SELECT * FROM selected_apps ORDER BY label_snapshot COLLATE NOCASE ASC")
    suspend fun getAll(): List<SelectedAppEntity>

    @Upsert
    suspend fun upsert(entity: SelectedAppEntity)

    @Query("DELETE FROM selected_apps WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE selected_apps SET label_snapshot = :label WHERE package_name = :packageName")
    suspend fun updateLabel(packageName: String, label: String)

    @Query("DELETE FROM selected_apps")
    suspend fun clear()
}
