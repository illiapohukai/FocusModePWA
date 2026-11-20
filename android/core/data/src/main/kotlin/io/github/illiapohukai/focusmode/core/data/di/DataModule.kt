package io.github.illiapohukai.focusmode.core.data.di

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
import io.github.illiapohukai.focusmode.core.data.SystemFocusClock
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.Migrations
import io.github.illiapohukai.focusmode.core.data.prefs.DataStorePreferencesRepository
import io.github.illiapohukai.focusmode.core.data.prefs.PreferencesSchemaMigration
import io.github.illiapohukai.focusmode.core.data.repository.RoomDistractionLog
import io.github.illiapohukai.focusmode.core.data.repository.RoomDndOverrideStore
import io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomReminderStore
import io.github.illiapohukai.focusmode.core.data.repository.RoomScheduleRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomSelectedAppRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomSessionRepository
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionStore
import io.github.illiapohukai.focusmode.core.domain.repository.DistractionLog
import io.github.illiapohukai.focusmode.core.domain.repository.DndOverrideStore
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ReminderStore
import io.github.illiapohukai.focusmode.core.domain.repository.ScheduleRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SelectedAppRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
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
    @Binds abstract fun backupImport(impl: io.github.illiapohukai.focusmode.core.data.backup.BackupImportRepository): io.github.illiapohukai.focusmode.core.domain.backup.BackupImportGateway
}
