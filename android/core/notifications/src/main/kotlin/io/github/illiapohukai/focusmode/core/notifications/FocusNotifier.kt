package io.github.illiapohukai.focusmode.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.domain.deeplink.DeepLinkTarget
import io.github.illiapohukai.focusmode.core.domain.deeplink.DeepLinks
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.notification.FocusChannel
import io.github.illiapohukai.focusmode.core.domain.notification.NotificationCopy
import io.github.illiapohukai.focusmode.core.domain.notification.NotificationText
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderNotifications
import io.github.illiapohukai.focusmode.core.domain.platform.SessionNotifications
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object NotificationIds {
    const val ACTIVE_SESSION = 1001
    const val SESSION_FINISHED = 1002
    const val SESSION_FROZEN = 1003
    const val DISTRACTION_HINT = 1004
    /** Stable id per reminder occurrence: re-posting the same event replaces, never duplicates. */
    fun reminder(key: String): Int = 20_000 + (key.hashCode() and 0x3FFFF)
}

/**
 * The single owner of every Focus Mode+ notification. It checks deliverability before posting,
 * honors the notification-detail privacy preference, and uses only immutable, explicit
 * PendingIntents into the app's own launcher activity carrying an opaque deep link.
 */
@Singleton
class FocusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: NotificationChannels,
    private val capability: NotificationCapabilityReader,
    preferences: PreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
) : SessionNotifications, ReminderNotifications {

    @Volatile private var prefs = AppPreferences()

    init {
        scope.launch { preferences.preferences.collect { prefs = it } }
    }

    private val manager get() = NotificationManagerCompat.from(context)

    fun canPost(channel: FocusChannel): Boolean =
        capability.read(prefs.notificationsEnabled, prefs.notificationPermissionRequests, shouldShowRationale = false).canDeliver(channel)

    // ------------------------------------------------------------------ active Session (FGS)

    /** Ongoing notification of the running Session. Updated at minute cadence, never per second. */
    fun activeSessionNotification(session: FocusSession, remainingMinutes: Int): Notification {
        channels.ensureCreated()
        val text = NotificationCopy.activeSession(remainingMinutes, session.task.text, prefs.notificationDetail)
        return base(FocusChannel.ACTIVE_SESSION, text, prefs.notificationDetail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(deepLink(DeepLinkTarget.ActiveSession, REQ_SESSION))
            .addAction(0, "Beenden …", deepLink(DeepLinkTarget.EndSession, REQ_END))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Shown only for the instant a foreground service starts after its Session already ended. */
    fun endingNotification(): Notification =
        base(FocusChannel.ACTIVE_SESSION, NotificationText("Focus Mode+", "Fokus wird beendet"), NotificationDetailLevel.GENERIC)
            .setSilent(true)
            .build()

    /** Fallback when the foreground service may not start (e.g. restored from the background). */
    fun postActiveSession(session: FocusSession, remainingMinutes: Int) {
        post(FocusChannel.ACTIVE_SESSION, NotificationIds.ACTIVE_SESSION, activeSessionNotification(session, remainingMinutes))
    }

    fun cancelActiveSession() = manager.cancel(NotificationIds.ACTIVE_SESSION)

    // ------------------------------------------------------------------ SessionNotifications

    override fun showFinished(result: FocusSessionResult) {
        val text = NotificationCopy.sessionFinished(result.actualMinutes, result.task, prefs.notificationDetail)
        val n = base(FocusChannel.SESSION_FINISHED, text, prefs.notificationDetail)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(deepLink(DeepLinkTarget.Result(result.sessionId.value), REQ_RESULT))
            .build()
        post(FocusChannel.SESSION_FINISHED, NotificationIds.SESSION_FINISHED, n)
    }

    override fun showFrozen(session: FocusSession) {
        val n = base(FocusChannel.SESSION_FINISHED, NotificationCopy.sessionFrozen(), NotificationDetailLevel.GENERIC)
            .setAutoCancel(true)
            .setContentIntent(deepLink(DeepLinkTarget.ActiveSession, REQ_SESSION))
            .build()
        post(FocusChannel.SESSION_FINISHED, NotificationIds.SESSION_FROZEN, n)
    }

    override fun clearSessionNotifications() {
        manager.cancel(NotificationIds.SESSION_FROZEN)
        manager.cancel(NotificationIds.DISTRACTION_HINT)
        manager.cancel(NotificationIds.ACTIVE_SESSION)
    }

    // ------------------------------------------------------------------ ReminderNotifications

    override fun showReminder(reminder: Reminder): Boolean {
        val channel = if (reminder.type == ReminderType.MISSION_DEADLINE || reminder.type == ReminderType.PREPARATION_START) FocusChannel.MISSION_DEADLINES else FocusChannel.REMINDERS
        if (!canPost(channel)) return false
        val target = DeepLinks.parse(reminder.deepLink)
        val text = NotificationText(reminder.title, reminder.body, reminder.title, genericBody(reminder.type))
        val builder = base(channel, text, prefs.notificationDetail)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(deepLink(target, REQ_REMINDER_BASE + (NotificationIds.reminder(reminder.key) and 0xFFF)))
        if (reminder.type == ReminderType.SCHEDULED_FOCUS) {
            builder.addAction(0, "Fokus vorbereiten", deepLink(target, REQ_REMINDER_ACTION_BASE + (NotificationIds.reminder(reminder.key) and 0xFFF)))
        }
        return post(channel, NotificationIds.reminder(reminder.key), builder.build())
    }

    override fun showDistractionHint(remainingMinutes: Int): Boolean {
        if (!canPost(FocusChannel.PROTECTION)) return false
        val n = base(FocusChannel.PROTECTION, NotificationCopy.distractionHint(remainingMinutes), NotificationDetailLevel.GENERIC)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60_000L)
            .setContentIntent(deepLink(DeepLinkTarget.ActiveSession, REQ_SESSION))
            .build()
        return post(FocusChannel.PROTECTION, NotificationIds.DISTRACTION_HINT, n)
    }

    // ------------------------------------------------------------------ helpers

    private fun genericBody(type: ReminderType): String = when (type) {
        ReminderType.SCHEDULED_FOCUS -> "Dein geplanter Fokus beginnt bald."
        ReminderType.MISSION_DEADLINE -> "Eine Mission hat bald ihre Deadline."
        ReminderType.PREPARATION_START -> "Der erste Schritt ist bereit."
        ReminderType.DAILY_PLANNING -> "Zeit für deinen Plan."
    }

    /**
     * Lock-screen privacy: FULL_TASK shows details only when the device allows sensitive content
     * (generic public version otherwise); GENERIC never contains task text; HIDDEN is secret.
     */
    private fun base(channel: FocusChannel, text: NotificationText, detail: NotificationDetailLevel): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(ACCENT)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setLocalOnly(true)
        when {
            detail == NotificationDetailLevel.HIDDEN || text.publicTitle == null -> builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
            text.publicTitle == text.title && text.publicBody == text.body -> builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            else -> builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
                NotificationCompat.Builder(context, channel.id)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setColor(ACCENT)
                    .setContentTitle(text.publicTitle)
                    .setContentText(text.publicBody)
                    .build(),
            )
        }
        return builder
    }

    @SuppressLint("MissingPermission")
    private fun post(channel: FocusChannel, id: Int, notification: Notification): Boolean {
        channels.ensureCreated()
        if (!capability.permissionGranted()) return false
        if (channel != FocusChannel.ACTIVE_SESSION && channel != FocusChannel.SESSION_FINISHED && !prefs.notificationsEnabled) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            capability.runtimePermissionRequired
        ) return false
        manager.notify(id, notification)
        return true
    }

    /** Explicit intent to this app's launcher activity; immutable; carries only an opaque route. */
    fun deepLink(target: DeepLinkTarget, requestCode: Int): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent().setPackage(context.packageName)).apply {
            action = Intent.ACTION_VIEW
            removeCategory(Intent.CATEGORY_LAUNCHER)
            data = DeepLinks.build(target).toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val ACCENT = 0xFF6D28D9.toInt()
        const val REQ_SESSION = 1
        const val REQ_END = 2
        const val REQ_RESULT = 3
        const val REQ_REMINDER_BASE = 0x1000
        const val REQ_REMINDER_ACTION_BASE = 0x2000
    }
}
