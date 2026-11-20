package io.github.illiapohukai.focusmode.core.session

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.SessionEndAlarm
import io.github.illiapohukai.focusmode.core.domain.platform.SessionRuntime
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import io.github.illiapohukai.focusmode.core.domain.session.SessionTimeline
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.notifications.FocusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts/stops the foreground service. If Android refuses a foreground start (the app is in the
 * background, e.g. restoring after an app update), the Session stays visible through the same
 * ongoing notification posted directly, and timing is unaffected.
 */
@Singleton
class ForegroundSessionRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: FocusNotifier,
    private val clock: FocusClock,
) : SessionRuntime {
    override fun ensureRunning(session: FocusSession) {
        val intent = Intent(context, FocusSessionService::class.java)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                notifier.postActiveSession(session, SessionTimeline.read(session, clock).remainingMinutesCeil)
            } else throw e
        }
    }

    override fun stop() {
        context.stopService(Intent(context, FocusSessionService::class.java))
        notifier.cancelActiveSession()
    }
}

/**
 * Wakes the Session owner at the planned end on the monotonic clock (immune to wall-clock
 * changes). Exact only where the platform already allows it; otherwise an inexact
 * allow-while-idle alarm (may arrive a few minutes late in Doze — timing of the record is still
 * exact because it is derived from timestamps).
 */
@Singleton
class AlarmSessionEndAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exact: ExactAlarmCapability,
) : SessionEndAlarm {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, SessionAlarmReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun schedule(session: FocusSession, endRealtimeMs: Long) {
        val am = alarmManager ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact.canScheduleExactAlarms() && exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endRealtimeMs, pendingIntent())
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endRealtimeMs, pendingIntent())
        }
    }

    override fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    companion object {
        const val REQUEST_CODE = 7001
        const val ACTION_END = "io.github.illiapohukai.focusmode.action.SESSION_END"
    }
}

/** Non-exported receiver for the Session end alarm only. */
@AndroidEntryPoint
class SessionAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: FocusSessionController
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmSessionEndAlarm.ACTION_END) return
        val pending = goAsync()
        scope.launch {
            try { controller.restore(RestoreTrigger.END_ALARM) } finally { pending.finish() }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {
    @Binds abstract fun controller(impl: FocusSessionManager): FocusSessionController
    @Binds abstract fun runtime(impl: ForegroundSessionRuntime): SessionRuntime
    @Binds abstract fun alarm(impl: AlarmSessionEndAlarm): SessionEndAlarm
}
