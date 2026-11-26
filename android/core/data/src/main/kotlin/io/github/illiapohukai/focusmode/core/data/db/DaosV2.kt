package io.github.illiapohukai.focusmode.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ScheduleDao {
    @Transaction @Query("SELECT * FROM focus_schedules ORDER BY start_minute ASC, label ASC")
    fun observeAll(): Flow<List<ScheduleWithApps>>

    @Transaction @Query("SELECT * FROM focus_schedules ORDER BY start_minute ASC, label ASC")
    suspend fun getAll(): List<ScheduleWithApps>

    @Transaction @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun get(id: String): ScheduleWithApps?

    @Upsert suspend fun upsert(entity: ScheduleEntity)

    @Query("DELETE FROM schedule_apps WHERE schedule_id = :id")
    suspend fun clearApps(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApps(apps: List<ScheduleAppEntity>)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DistractionDao {
    @Query("SELECT * FROM session_distractions WHERE session_id = :sessionId AND package_name = :pkg")
    suspend fun get(sessionId: String, pkg: String): DistractionEntity?

    @Upsert suspend fun upsert(entity: DistractionEntity)

    @Query("SELECT COALESCE(SUM(attempts), 0) FROM session_distractions WHERE session_id = :sessionId")
    fun observeCount(sessionId: String): Flow<Int>

    @Query("DELETE FROM session_distractions WHERE last_at < :cutoff")
    suspend fun purgeOlderThan(cutoff: Instant): Int

    @Query("DELETE FROM session_distractions")
    suspend fun purgeAll(): Int
}

@Dao
interface DndOverrideDao {
    @Query("SELECT * FROM dnd_override WHERE slot = 0")
    suspend fun get(): DndOverrideEntity?

    @Upsert suspend fun put(entity: DndOverrideEntity)

    @Query("DELETE FROM dnd_override")
    suspend fun clear()
}

@Dao
interface MissionDraftDao {
    @Transaction @Query("SELECT * FROM mission_drafts WHERE slot = 0")
    fun observe(): Flow<MissionDraftWithSteps?>

    @Transaction @Query("SELECT * FROM mission_drafts WHERE slot = 0")
    suspend fun get(): MissionDraftWithSteps?

    @Upsert suspend fun upsert(entity: MissionDraftEntity)

    @Query("DELETE FROM mission_draft_steps WHERE draft_slot = 0")
    suspend fun clearSteps()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSteps(steps: List<MissionDraftStepEntity>)

    @Query("DELETE FROM mission_drafts")
    suspend fun clear()
}

@Dao
interface MissionProgressDao {
    /** IGNORE: a Session result already applied returns -1 and changes nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MissionProgressEntity): Long

    @Query("SELECT * FROM mission_progress WHERE session_id = :sessionId")
    suspend fun get(sessionId: String): MissionProgressEntity?

    @Query("SELECT * FROM mission_progress WHERE mission_id = :missionId ORDER BY applied_at ASC, session_id ASC")
    suspend fun forMission(missionId: String): List<MissionProgressEntity>

    @Query("SELECT * FROM mission_progress ORDER BY applied_at ASC, session_id ASC")
    suspend fun all(): List<MissionProgressEntity>
}

/**
 * Full personal-data deletion (v20). Children before parents so foreign keys hold at every
 * step; always called inside one transaction. Reminder rows are not deleted here: the reminder
 * scheduler cancels each scheduled occurrence first (see `PersonalDataEraser`).
 */
@Dao
interface PersonalDataDao {
    @Query("DELETE FROM active_session") suspend fun clearActiveSession()
    @Query("DELETE FROM mission_progress") suspend fun clearMissionProgress()
    @Query("DELETE FROM mission_draft_steps") suspend fun clearDraftSteps()
    @Query("DELETE FROM mission_drafts") suspend fun clearDrafts()
    @Query("DELETE FROM session_results") suspend fun clearHistory()
    @Query("DELETE FROM mission_steps") suspend fun clearMissionSteps()
    @Query("DELETE FROM missions") suspend fun clearMissions()
    @Query("DELETE FROM activity_log") suspend fun clearActivity()
    @Query("DELETE FROM selected_apps") suspend fun clearSelectedApps()
    @Query("DELETE FROM schedule_apps") suspend fun clearScheduleApps()
    @Query("DELETE FROM focus_schedules") suspend fun clearSchedules()
    @Query("DELETE FROM session_distractions") suspend fun clearDistractions()
    @Query("DELETE FROM dnd_override") suspend fun clearDndOverride()
    @Query("DELETE FROM reminders") suspend fun clearReminders()

    /** Rows that would survive a deletion; 0 after a complete one. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM missions) + (SELECT COUNT(*) FROM mission_steps) + (SELECT COUNT(*) FROM mission_drafts) + " +
            "(SELECT COUNT(*) FROM mission_progress) + (SELECT COUNT(*) FROM session_results) + (SELECT COUNT(*) FROM active_session) + " +
            "(SELECT COUNT(*) FROM activity_log) + (SELECT COUNT(*) FROM selected_apps) + (SELECT COUNT(*) FROM focus_schedules) + " +
            "(SELECT COUNT(*) FROM session_distractions) + (SELECT COUNT(*) FROM dnd_override) + (SELECT COUNT(*) FROM reminders)",
    )
    suspend fun remainingRows(): Int
}
