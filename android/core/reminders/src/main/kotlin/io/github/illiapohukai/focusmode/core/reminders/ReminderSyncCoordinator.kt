package io.github.illiapohukai.focusmode.core.reminders

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderScheduler
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderSyncReason
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ScheduleRepository
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
