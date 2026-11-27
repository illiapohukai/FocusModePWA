package app.focusmodeplus.core.data.repository

import androidx.room.withTransaction
import app.focusmodeplus.core.data.db.DistractionEntity
import app.focusmodeplus.core.data.db.DndOverrideEntity
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.ScheduleAppEntity
import app.focusmodeplus.core.data.db.SelectedAppEntity
import app.focusmodeplus.core.data.db.toEntity
import app.focusmodeplus.core.data.db.toModel
import app.focusmodeplus.core.domain.protection.AppSelectionReconciler
import app.focusmodeplus.core.domain.repository.DistractionLog
import app.focusmodeplus.core.domain.repository.DndOverride
import app.focusmodeplus.core.domain.repository.DndOverrideStore
import app.focusmodeplus.core.domain.repository.ReminderStore
import app.focusmodeplus.core.domain.repository.ScheduleRepository
import app.focusmodeplus.core.domain.repository.SelectedAppRepository
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.Reminder
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.SelectedDistractingApp
import app.focusmodeplus.core.model.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomScheduleRepository @Inject constructor(private val db: FocusDatabase) : ScheduleRepository {
    private val dao get() = db.scheduleDao()
    override fun observeSchedules(): Flow<List<Schedule>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }
    override suspend fun getSchedule(id: ScheduleId): Schedule? = dao.get(id.value)?.toModel()
    override suspend fun getAll(): List<Schedule> = dao.getAll().mapNotNull { it.toModel() }
    override suspend fun upsert(schedule: Schedule) = db.withTransaction {
        dao.upsert(schedule.toEntity())
        dao.clearApps(schedule.id.value)
        dao.insertApps(schedule.appPackages.filter(AppSelectionReconciler::isPlausiblePackageName).map { ScheduleAppEntity(schedule.id.value, it) })
    }
    override suspend fun delete(id: ScheduleId) = dao.delete(id.value)
}

/** Selected distracting apps. Local only: this store has no network or analytics path. */
@Singleton
class RoomSelectedAppRepository @Inject constructor(private val db: FocusDatabase, private val clock: FocusClock) : SelectedAppRepository {
    private val dao get() = db.selectedAppDao()
    override fun observeSelected(): Flow<List<SelectedDistractingApp>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }
    override suspend fun getSelected(): List<SelectedDistractingApp> = dao.getAll().map { it.toModel() }
    override suspend fun setSelected(packageName: String, label: String, selected: Boolean) {
        if (!AppSelectionReconciler.isPlausiblePackageName(packageName)) return
        if (selected) dao.upsert(SelectedAppEntity(packageName, label.take(120), clock.now(), null)) else dao.delete(packageName)
    }
    override suspend fun updateLabelSnapshot(packageName: String, label: String) = dao.updateLabel(packageName, label.take(120))
    override suspend fun clear() = dao.clear()
}

@Singleton
class RoomReminderStore @Inject constructor(private val db: FocusDatabase) : ReminderStore {
    private val dao get() = db.reminderDao()
    override fun observeUpcoming(limit: Int): Flow<List<Reminder>> = dao.observeUpcoming(limit).map { rows -> rows.mapNotNull { it.toModel() } }
    override suspend fun getAll(): List<Reminder> = dao.getAll().mapNotNull { it.toModel() }
    override suspend fun get(key: String): Reminder? = dao.get(key)?.toModel()
    override suspend fun upsert(reminder: Reminder) = dao.upsert(reminder.toEntity())
    override suspend fun delete(key: String) = dao.delete(key)
    override suspend fun markDelivered(key: String, at: Instant): Boolean = dao.markDelivered(key, at) == 1
}

/** Aggregated distraction attempts per Session. Purged after [RETENTION_DAYS]; never uploaded. */
@Singleton
class RoomDistractionLog @Inject constructor(private val db: FocusDatabase) : DistractionLog {
    private val dao get() = db.distractionDao()
    override suspend fun record(sessionId: SessionId, packageName: String, at: Instant) = db.withTransaction {
        val current = dao.get(sessionId.value, packageName)
        dao.upsert(
            current?.copy(attempts = current.attempts + 1, lastAt = at)
                ?: DistractionEntity(sessionId.value, packageName, 1, at, at),
        )
    }
    override fun observeCount(sessionId: SessionId): Flow<Int> = dao.observeCount(sessionId.value)
    override suspend fun purgeOlderThan(cutoff: Instant): Int = dao.purgeOlderThan(cutoff)
    override suspend fun purgeAll(): Int = dao.purgeAll()

    companion object { const val RETENTION_DAYS = 30L }
}

@Singleton
class RoomDndOverrideStore @Inject constructor(private val db: FocusDatabase) : DndOverrideStore {
    private val dao get() = db.dndOverrideDao()
    override suspend fun get(): DndOverride? = dao.get()?.let { DndOverride(SessionId(it.sessionId), it.priorFilter, it.appliedFilter, it.appliedAt) }
    override suspend fun put(override: DndOverride) = dao.put(DndOverrideEntity(0, override.sessionId.value, override.priorFilter, override.appliedFilter, override.appliedAt))
    override suspend fun clear() = dao.clear()
}
