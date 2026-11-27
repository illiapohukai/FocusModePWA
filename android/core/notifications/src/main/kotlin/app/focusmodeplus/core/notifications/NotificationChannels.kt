package app.focusmodeplus.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import app.focusmodeplus.core.domain.notification.FocusChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Creates the Focus Mode+ channels (idempotent; the user's later channel choices are kept). */
@Singleton
class NotificationChannels @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile private var created = false

    /**
     * Once per process: the minute-cadence Session notification calls this on every update, and a
     * repeated `createNotificationChannels` is a system-server round trip that changes nothing.
     */
    fun ensureCreated() {
        if (created) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(FocusChannel.entries.map(::channelFor))
        created = true
    }

    internal fun channelFor(channel: FocusChannel): NotificationChannel {
        val importance = when (channel) {
            FocusChannel.ACTIVE_SESSION -> NotificationManager.IMPORTANCE_LOW
            FocusChannel.PROTECTION -> NotificationManager.IMPORTANCE_DEFAULT
            FocusChannel.SESSION_FINISHED, FocusChannel.REMINDERS, FocusChannel.MISSION_DEADLINES -> NotificationManager.IMPORTANCE_DEFAULT
        }
        return NotificationChannel(channel.id, channel.title, importance).apply {
            description = channel.description
            setShowBadge(channel != FocusChannel.ACTIVE_SESSION)
            if (channel == FocusChannel.ACTIVE_SESSION || channel == FocusChannel.PROTECTION) {
                setSound(null, null)
                enableVibration(false)
            }
        }
    }
}
