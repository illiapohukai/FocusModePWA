package app.focusmodeplus.core.data.backup

import androidx.room.withTransaction
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.toModel
import app.focusmodeplus.core.domain.backup.BackupContent
import app.focusmodeplus.core.domain.backup.BackupSnapshotSource
import app.focusmodeplus.core.domain.backup.PersonalDataStore
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Reads Missions, progress entries and History in one transaction, so a backup is one consistent snapshot. */
@Singleton
class RoomBackupSnapshotSource @Inject constructor(
    private val db: FocusDatabase,
    private val preferences: PreferencesRepository,
) : BackupSnapshotSource {
    override suspend fun snapshot(): BackupContent {
        val (missions, progress, results) = db.withTransaction {
            Triple(
                db.missionDao().getAll().mapNotNull { it.toModel() },
                db.missionProgressDao().all().mapNotNull { it.toModel() },
                db.sessionDao().allResults().mapNotNull { it.toModel() },
            )
        }
        return BackupContent(missions, progress, results, preferences.current())
    }
}

/** Room side of full data deletion: one transaction, children before parents. */
@Singleton
class RoomPersonalDataStore @Inject constructor(private val db: FocusDatabase) : PersonalDataStore {
    override suspend fun wipeRecords() = db.withTransaction {
        val d = db.personalDataDao()
        d.clearActiveSession()
        d.clearMissionProgress()
        d.clearDraftSteps()
        d.clearDrafts()
        d.clearHistory()
        d.clearMissionSteps()
        d.clearMissions()
        d.clearActivity()
        d.clearSelectedApps()
        d.clearScheduleApps()
        d.clearSchedules()
        d.clearDistractions()
        d.clearDndOverride()
        // v21: the account binding, outbox and sync metadata go with the data; cloud data stays.
        d.clearSyncOutbox()
        d.clearSyncEntityStates()
        d.clearSyncTombstones()
        d.clearSyncConflicts()
        d.clearAccountBinding()
    }

    override suspend fun clearReminderRows() = db.personalDataDao().clearReminders()

    override suspend fun remainingRows(): Int = db.personalDataDao().remainingRows()
}
