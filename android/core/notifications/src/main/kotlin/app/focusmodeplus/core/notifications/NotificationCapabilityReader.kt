package app.focusmodeplus.core.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import app.focusmodeplus.core.domain.notification.FocusChannel
import app.focusmodeplus.core.domain.notification.NotificationCapability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads what the OS currently allows. Never cached as truth: callers re-read on resume, after a
 * permission dialog and before posting.
 */
@Singleton
class NotificationCapabilityReader @Inject constructor(@ApplicationContext private val context: Context) {

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val runtimePermissionRequired: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun permissionGranted(): Boolean = !runtimePermissionRequired ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun read(appPreferenceEnabled: Boolean, requestCount: Int, shouldShowRationale: Boolean): NotificationCapability {
        val manager = context.getSystemService(NotificationManager::class.java)
        val blocked = FocusChannel.entries.filter { channel ->
            manager?.getNotificationChannel(channel.id)?.importance == NotificationManager.IMPORTANCE_NONE
        }.toSet()
        return NotificationCapability(
            permission = NotificationCapability.resolvePermission(runtimePermissionRequired, permissionGranted(), requestCount, shouldShowRationale),
            osEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            blockedChannels = blocked,
            appPreferenceEnabled = appPreferenceEnabled,
        )
    }
}
