package app.focusmodeplus.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.focusmodeplus.core.data.SystemFocusClock
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.Migrations
import app.focusmodeplus.core.data.prefs.DataStorePreferencesRepository
import app.focusmodeplus.core.data.prefs.PreferencesSchemaMigration
import app.focusmodeplus.core.data.repository.RoomDistractionLog
import app.focusmodeplus.core.data.repository.RoomDndOverrideStore
import app.focusmodeplus.core.data.repository.RoomMissionRepository
import app.focusmodeplus.core.data.repository.RoomReminderStore
import app.focusmodeplus.core.data.repository.RoomScheduleRepository
import app.focusmodeplus.core.data.repository.RoomSelectedAppRepository
import app.focusmodeplus.core.data.repository.RoomSessionRepository
import app.focusmodeplus.core.domain.di.ApplicationScope
import app.focusmodeplus.core.domain.repository.ActiveSessionStore
import app.focusmodeplus.core.domain.repository.DistractionLog
import app.focusmodeplus.core.domain.repository.DndOverrideStore
import app.focusmodeplus.core.domain.repository.MissionRepository
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.repository.ReminderStore
import app.focusmodeplus.core.domain.repository.ScheduleRepository
import app.focusmodeplus.core.domain.repository.SelectedAppRepository
import app.focusmodeplus.core.domain.repository.SessionHistoryRepository
import app.focusmodeplus.core.domain.time.FocusClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {

    /** No destructive fallback: data loss on upgrade is impossible by construction. */
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): FocusDatabase =
        Room.databaseBuilder(context, FocusDatabase::class.java, FocusDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            migrations = listOf(PreferencesSchemaMigration),
            scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("focus_preferences") },
        )

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds abstract fun clock(impl: SystemFocusClock): FocusClock
    @Binds abstract fun activeSessionStore(impl: RoomSessionRepository): ActiveSessionStore
    @Binds abstract fun history(impl: RoomSessionRepository): SessionHistoryRepository
    @Binds abstract fun missions(impl: RoomMissionRepository): MissionRepository
    @Binds abstract fun schedules(impl: RoomScheduleRepository): ScheduleRepository
    @Binds abstract fun selectedApps(impl: RoomSelectedAppRepository): SelectedAppRepository
    @Binds abstract fun reminders(impl: RoomReminderStore): ReminderStore
    @Binds abstract fun distractions(impl: RoomDistractionLog): DistractionLog
    @Binds abstract fun dndOverride(impl: RoomDndOverrideStore): DndOverrideStore
    @Binds abstract fun preferences(impl: DataStorePreferencesRepository): PreferencesRepository
    @Binds abstract fun backupImport(impl: app.focusmodeplus.core.data.backup.BackupImportRepository): app.focusmodeplus.core.domain.backup.BackupImportGateway
    @Binds abstract fun backupSnapshot(impl: app.focusmodeplus.core.data.backup.RoomBackupSnapshotSource): app.focusmodeplus.core.domain.backup.BackupSnapshotSource
    @Binds abstract fun personalData(impl: app.focusmodeplus.core.data.backup.RoomPersonalDataStore): app.focusmodeplus.core.domain.backup.PersonalDataStore
    @Binds abstract fun syncLocalStore(impl: app.focusmodeplus.core.data.sync.RoomSyncLocalStore): app.focusmodeplus.core.domain.sync.SyncLocalStore
}
