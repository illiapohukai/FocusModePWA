package app.focusmodeplus.core.reminders

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.focusmodeplus.core.domain.platform.ReminderScheduler
import app.focusmodeplus.core.domain.platform.ReminderSyncReason
import app.focusmodeplus.core.domain.repository.MissionRepository
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Re-syncs reminders whenever their inputs change (schedules, Missions, reminder settings). */
@Singleton
class ReminderSyncCoordinator @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val schedules: ScheduleRepository,
    private val missions: MissionRepository,
    private val preferences: PreferencesRepository,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                schedules.observeSchedules(),
                missions.observeMissions(),
                preferences.preferences.map { Triple(it.missionRemindersEnabled, it.dailyPlanningEnabled, it.dailyPlanningTime) }.distinctUntilChanged(),
            ) { s, m, p -> Triple(s, m, p) }
                .debounce(500)
                .collect { scheduler.sync(ReminderSyncReason.DATA_CHANGED) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {
    @Binds abstract fun scheduler(impl: AndroidReminderScheduler): ReminderScheduler
    @Binds abstract fun backend(impl: AndroidReminderBackend): ReminderBackend
}
