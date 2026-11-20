package io.github.illiapohukai.focusmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.illiapohukai.focusmode.core.data.RestoreGuard
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderScheduler
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderSyncReason
import io.github.illiapohukai.focusmode.core.domain.session.FocusSessionController
import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Non-exported receiver for protected system broadcasts: reboot, app update, time and time-zone
 * changes. Reminders are re-planned (unique names, no duplicates) and the Session is recovered.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: FocusSessionController
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var restoreGuard: RestoreGuard
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val (trigger, reason) = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> RestoreTrigger.BOOT_COMPLETED to ReminderSyncReason.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> RestoreTrigger.PACKAGE_REPLACED to ReminderSyncReason.PACKAGE_REPLACED
            Intent.ACTION_TIMEZONE_CHANGED -> RestoreTrigger.TIME_CHANGED to ReminderSyncReason.TIMEZONE_CHANGED
            Intent.ACTION_TIME_CHANGED -> RestoreTrigger.TIME_CHANGED to ReminderSyncReason.TIME_CHANGED
            else -> return
        }
        val pending = goAsync()
        scope.launch {
            try {
                restoreGuard.check()
                controller.restore(trigger)
                reminders.sync(reason)
            } finally {
                pending.finish()
            }
        }
    }
}
