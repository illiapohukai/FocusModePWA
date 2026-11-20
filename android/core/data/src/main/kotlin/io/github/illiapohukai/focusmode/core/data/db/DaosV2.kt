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
