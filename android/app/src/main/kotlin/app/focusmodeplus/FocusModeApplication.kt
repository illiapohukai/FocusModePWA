package app.focusmodeplus

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import app.focusmodeplus.core.data.RestoreGuard
import app.focusmodeplus.core.domain.di.ApplicationScope
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.domain.platform.ReminderScheduler
import app.focusmodeplus.core.domain.platform.ReminderSyncReason
import app.focusmodeplus.core.domain.repository.DistractionLog
import app.focusmodeplus.core.domain.session.FocusSessionController
import app.focusmodeplus.core.domain.session.RestoreTrigger
import app.focusmodeplus.core.domain.sync.SyncEngine
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.notifications.NotificationChannels
import app.focusmodeplus.core.reminders.ReminderSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltAndroidApp
class FocusModeApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var restoreGuard: RestoreGuard
    @Inject lateinit var controller: FocusSessionController
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var reminderSync: ReminderSyncCoordinator
    @Inject lateinit var distractions: DistractionLog
    @Inject lateinit var clock: FocusClock
    @Inject lateinit var missionGeneration: MissionGenerationOwner
    @Inject lateinit var sync: SyncEngine
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        channels.ensureCreated()
        appScope.launch {
            restoreGuard.check()
            // Recovery: re-derive the Session from durable timestamps (cold start / process death).
            controller.restore(RestoreTrigger.APP_START)
            // A generation request cannot survive process death; its draft is marked interrupted (never re-sent).
            missionGeneration.reconcile()
            reminders.sync(ReminderSyncReason.APP_START)
            distractions.purgeOlderThan(clock.now().minus(30, ChronoUnit.DAYS))
        }
        reminderSync.start(appScope)
        // v21: account-aware synchronization (does nothing in guest mode or unconfigured builds).
        sync.start(appScope)
    }
}
