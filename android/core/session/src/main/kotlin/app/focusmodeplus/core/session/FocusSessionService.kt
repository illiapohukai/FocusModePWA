package app.focusmodeplus.core.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import app.focusmodeplus.core.domain.session.SessionState
import app.focusmodeplus.core.domain.session.SessionTimeline
import app.focusmodeplus.core.domain.time.FocusClock
import app.focusmodeplus.core.model.FocusSession
import app.focusmodeplus.core.notifications.FocusNotifier
import app.focusmodeplus.core.notifications.NotificationIds
import app.focusmodeplus.core.protection.DistractionMonitorOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Visible foreground service of a running Session (type `specialUse`: a user-started Focus timer
 * with an ongoing countdown notification). It is never started without a running Session and
 * stops itself as soon as the Session ends. It owns no timing state: every update re-reads the
 * Session owner, so killing the service never loses time.
 */
@AndroidEntryPoint
class FocusSessionService : LifecycleService() {

    @Inject lateinit var manager: FocusSessionManager
    @Inject lateinit var notifier: FocusNotifier
    @Inject lateinit var clock: FocusClock
    @Inject lateinit var monitor: DistractionMonitorOwner

    private var started = false
    private var loop: Job? = null

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lifecycleScope.launch { manager.onRuntimeTick(); render() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            manager.state.collectLatest { state ->
                if (state is SessionState.Running) onRunning(state.session) else shutdown()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val session = (manager.state.value as? SessionState.Running)?.session
        promote(session) // always satisfy the startForeground contract first
        if (session == null) shutdown()
        return START_NOT_STICKY
    }

    private fun promote(session: FocusSession?) {
        val notification = if (session != null) {
            notifier.activeSessionNotification(session, SessionTimeline.read(session, clock).remainingMinutesCeil)
        } else notifier.endingNotification()
        ServiceCompat.startForeground(this, NotificationIds.ACTIVE_SESSION, notification, foregroundType())
        started = true
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

    private fun onRunning(session: FocusSession) {
        if (!started) promote(session)
        if (loop?.isActive != true) {
            loop = lifecycleScope.launch {
                while (isActive) {
                    manager.onRuntimeTick()
                    val current = (manager.state.value as? SessionState.Running)?.session ?: break
                    render(current)
                    // Wake at the next whole-minute boundary of the remaining time (minute cadence).
                    val remainingMs = SessionTimeline.read(current, clock).remainingMs
                    delay(((remainingMs - 1) % 60_000L + 1).coerceIn(1_000L, 60_000L))
                }
            }
        }
        // One monitor job per protected Session (a no-op for the same Session); it runs off the main thread.
        monitor.start(lifecycleScope, session) { manager.read()?.remainingMinutesCeil ?: 1 }
    }

    private fun render(session: FocusSession? = (manager.state.value as? SessionState.Running)?.session) {
        session ?: return
        val remaining = SessionTimeline.read(session, clock).remainingMinutesCeil
        ServiceCompat.startForeground(this, NotificationIds.ACTIVE_SESSION, notifier.activeSessionNotification(session, remaining), foregroundType())
    }

    private fun shutdown() {
        loop?.cancel()
        monitor.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitor.stop()
        unregisterReceiver(screenOn)
        super.onDestroy()
    }
}
