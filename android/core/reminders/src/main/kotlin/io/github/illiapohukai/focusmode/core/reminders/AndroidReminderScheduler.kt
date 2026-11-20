package io.github.illiapohukai.focusmode.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderScheduler
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderSyncReason
import io.github.illiapohukai.focusmode.core.domain.reminder.PlanningInputs
import io.github.illiapohukai.focusmode.core.domain.reminder.ReminderDiffer
import io.github.illiapohukai.focusmode.core.domain.reminder.ReminderKeys
import io.github.illiapohukai.focusmode.core.domain.reminder.ReminderPlanner
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.ReminderStore
import io.github.illiapohukai.focusmode.core.domain.repository.ScheduleRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderPrecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Delivery backends, behind an interface so the scheduler logic is testable without Android. */
interface ReminderBackend {
    fun enqueueDeferrable(reminder: Reminder, delayMs: Long)
    fun scheduleAlarm(reminder: Reminder, exact: Boolean)
    fun cancel(key: String)
}

/**
 * The one reminder scheduler. `sync` computes the desired occurrences (pure planner), diffs them
 * with the stored rows and only (re)schedules the differences under stable unique names, so
 * repeated syncs (boot, time change, data change) never duplicate a reminder. `deliver` marks an
 * occurrence delivered atomically before posting, so an occurrence is shown at most once.
 */
@Singleton
class AndroidReminderScheduler @Inject constructor(
    private val store: ReminderStore,
    private val schedules: ScheduleRepository,
    private val missions: MissionRepository,
    private val preferences: PreferencesRepository,
    private val clock: FocusClock,
    private val exact: ExactAlarmCapability,
    private val backend: ReminderBackend,
    private val notifications: ReminderNotifications,
) : ReminderScheduler {

    private val mutex = Mutex()

    private suspend fun desired(): List<Reminder> = ReminderPlanner.plan(
        PlanningInputs(
            now = clock.now(),
            deviceZone = clock.zone(),
            schedules = schedules.getAll(),
            missions = missions.observeMissions().first(),
            preferences = preferences.current(),
            exactAlarmsAvailable = exact.canScheduleExactAlarms(),
        ),
    )

    override suspend fun sync(reason: ReminderSyncReason) = mutex.withLock {
        val stored = store.getAll()
        val desired = desired()
        val diff = ReminderDiffer.diff(stored, desired)
        diff.toCancel.forEach { backend.cancel(it.key); store.delete(it.key) }
        diff.toSchedule.forEach { r ->
            store.upsert(r.copy(deliveredAt = null))
            when (r.precision) {
                ReminderPrecision.DEFERRABLE -> backend.enqueueDeferrable(r, Duration.between(clock.now(), r.triggerAt).toMillis().coerceAtLeast(0))
                ReminderPrecision.EXACT_ALARM -> backend.scheduleAlarm(r, exact = true)
                ReminderPrecision.INEXACT_ALARM -> backend.scheduleAlarm(r, exact = false)
            }
        }
        // Keep the table small: delivered occurrences older than a week are dropped.
        val cutoff = clock.now().minus(Duration.ofDays(7))
        stored.filter { it.deliveredAt != null && it.deliveredAt!!.isBefore(cutoff) }.forEach { store.delete(it.key) }
    }

    override suspend fun deliver(key: String) {
        if (!ReminderKeys.isValid(key)) return
        mutex.withLock {
            val reminder = store.get(key) ?: return@withLock
            // Still wanted? (schedule disabled, Mission finished, preference turned off -> skip.)
            if (desired().none { it.key == key } && reminder.triggerAt.isAfter(clock.now().plusSeconds(60))) return@withLock
            if (!store.markDelivered(key, clock.now())) return@withLock
            notifications.showReminder(reminder)
        }
        sync(ReminderSyncReason.DELIVERED) // plan the next occurrence
    }
}

@Singleton
class AndroidReminderBackend @Inject constructor(@ApplicationContext private val context: Context) : ReminderBackend {
    private val workManager get() = WorkManager.getInstance(context)

    override fun enqueueDeferrable(reminder: Reminder, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY to reminder.key))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(ReminderKeys.workName(reminder.key), ExistingWorkPolicy.REPLACE, request)
    }

    override fun scheduleAlarm(reminder: Reminder, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = reminder.triggerAt.toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact && exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(reminder.key))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(reminder.key))
    }

    override fun cancel(key: String) {
        workManager.cancelUniqueWork(ReminderKeys.workName(key))
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(key))
    }

    /** One PendingIntent per occurrence: the key is the intent data, so re-scheduling replaces. */
    private fun alarmIntent(key: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_REMINDER).setData(Uri.Builder().scheme(SCHEME).opaquePart(key).build()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val TAG = "focus-reminders"
        const val ACTION_REMINDER = "io.github.illiapohukai.focusmode.action.REMINDER"
        const val SCHEME = "focusmode-reminder"
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY) ?: return Result.success()
        scheduler.deliver(key)
        return Result.success()
    }

    companion object { const val KEY = "reminder_key" }
}

/** Non-exported receiver for Scheduled Focus alarms. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderBackend.ACTION_REMINDER) return
        val key = intent.data?.schemeSpecificPart ?: return
        val pending = goAsync()
        scope.launch { try { scheduler.deliver(key) } finally { pending.finish() } }
    }
}
