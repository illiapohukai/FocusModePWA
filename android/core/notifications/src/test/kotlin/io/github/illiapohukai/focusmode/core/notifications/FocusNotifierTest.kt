package io.github.illiapohukai.focusmode.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.domain.notification.FocusChannel
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusRequest
import io.github.illiapohukai.focusmode.core.domain.session.SessionFactory
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderPrecision
import io.github.illiapohukai.focusmode.core.model.ReminderType
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class FocusNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val nm = app.getSystemService(NotificationManager::class.java)
    private val prefs = FakePreferencesRepository()
    private val channels = NotificationChannels(app)
    private val reader = NotificationCapabilityReader(app)
    private val notifier = FocusNotifier(app, channels, reader, prefs, CoroutineScope(Dispatchers.Unconfined))
    private val session = SessionFactory(FakeClock()).quick(QuickFocusRequest("Geheime Bewerbung", 25, ProtectionMode.STANDARD))
    private val reminder = Reminder("scheduled_focus:schedule_a:2026-11-19", ReminderType.SCHEDULED_FOCUS, "schedule_a",
        java.time.Instant.EPOCH, "Bereit für deinen Fokus?", "25 Minuten · Mathe", ReminderPrecision.INEXACT_ALARM, "focusmode://quickfocus?schedule=schedule_a")

    @Test fun allChannelsAreCreatedWithStableIds() {
        channels.ensureCreated()
        FocusChannel.entries.forEach { assertNotNull(nm.getNotificationChannel(it.id)) }
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(FocusChannel.ACTIVE_SESSION.id).importance)
    }

    @Test fun deniedPermissionMeansNothingIsPostedAndStateIsReported() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.showReminder(reminder))
        assertEquals(0, shadowOf(nm).allNotifications.size)
        assertEquals(ProtectionPermissionState.NOT_REQUESTED, reader.read(true, 0, false).permission)
        assertEquals(ProtectionPermissionState.PERMANENTLY_DENIED, reader.read(true, 2, false).permission)
    }

    @Test fun reminderIsPostedOnceWithDeepLink() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notifier.showReminder(reminder))
        assertTrue(notifier.showReminder(reminder)) // same occurrence id replaces, never duplicates
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
        val intent = shadowOf(posted.single().contentIntent).savedIntent
        assertEquals("focusmode://quickfocus?schedule=schedule_a", intent.dataString)
        assertEquals(app.packageName, intent.`package` ?: intent.component?.packageName)
    }

    @Test fun lockScreenNeverShowsTaskTextAndHiddenModeIsSecret() {
        val full = notifier.activeSessionNotification(session, 18)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, full.visibility)
        val publicText = full.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertEquals("Noch 18 Minuten", publicText)
        assertTrue(full.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Geheime Bewerbung"))
        assertTrue(full.flags and Notification.FLAG_ONGOING_EVENT != 0)

        prefs.state.value = AppPreferences(notificationDetail = NotificationDetailLevel.HIDDEN)
        val hidden = notifier.activeSessionNotification(session, 18)
        assertEquals(NotificationCompat.VISIBILITY_SECRET, hidden.visibility)
        assertFalse(hidden.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Geheime"))
    }

    @Test fun appSwitchOffBlocksRemindersButNotTheSessionNotification() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prefs.state.value = AppPreferences(notificationsEnabled = false)
        assertFalse(notifier.showReminder(reminder))
        notifier.postActiveSession(session, 10)
        assertEquals(1, shadowOf(nm).allNotifications.size)
        notifier.clearSessionNotifications()
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }
}
