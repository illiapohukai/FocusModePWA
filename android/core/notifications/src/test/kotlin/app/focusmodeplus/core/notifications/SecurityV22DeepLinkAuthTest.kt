package app.focusmodeplus.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.deeplink.DeepLinkTarget
import app.focusmodeplus.core.domain.deeplink.DeepLinks
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Reminder
import app.focusmodeplus.core.model.ReminderPrecision
import app.focusmodeplus.core.model.ReminderType
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * V22-F06: the exported launcher activity accepted `focusmode://` navigation from any installed
 * app. Routes are now tagged by the app's own notifications and verified on arrival.
 */
@RunWith(AndroidJUnit4::class)
class SecurityV22DeepLinkAuthTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val keyFile = File(app.noBackupFilesDir, DeepLinkAuthenticator.KEY_FILE)
    private lateinit var auth: DeepLinkAuthenticator

    @Before fun setUp() {
        keyFile.delete()
        auth = DeepLinkAuthenticator(keyFile)
    }

    private fun view(route: String, tag: String? = null) = Intent(Intent.ACTION_VIEW, Uri.parse(route))
        .setComponent(ComponentName(app.packageName, "app.focusmodeplus.MainActivity"))
        .apply { if (tag != null) putExtra(DeepLinkAuthenticator.EXTRA_TAG, tag) }

    @Test fun aNotificationRouteNavigatesAfterAProcessRestart() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = FocusNotifier(app, NotificationChannels(app), NotificationCapabilityReader(app), FakePreferencesRepository(), CoroutineScope(Dispatchers.Unconfined), auth)
        val result: FocusSessionResult = Fixtures.result("session_auth_1")
        notifier.showFinished(result)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        val intent = shadowOf(posted.contentIntent).savedIntent
        // Same key file, new instance: like a cold start before the user taps the notification.
        val restarted = DeepLinkAuthenticator(keyFile)
        assertEquals(DeepLinkTarget.Result("session_auth_1"), restarted.routeFrom(intent))
        assertTrue(keyFile.isFile)
        assertEquals(32, keyFile.readBytes().size)
    }

    @Test fun aForeignCallerWithoutAValidTagNeverNavigates() {
        val routes = listOf("focusmode://session/end", "focusmode://mission/mission_a", "focusmode://settings/permissions", "focusmode://result/session_a")
        routes.forEach { route ->
            assertNull(route, auth.routeFrom(view(route)))
            assertNull(route, auth.routeFrom(view(route, tag = "")))
            assertNull(route, auth.routeFrom(view(route, tag = "x".repeat(43))))
            assertNull(route, auth.routeFrom(view(route, tag = auth.sign(route) + "A")))
        }
    }

    @Test fun aTagIsBoundToItsRouteAndToThisInstall() {
        val history = "focusmode://history"
        val tag = auth.sign(history)
        assertEquals(DeepLinkTarget.History, auth.routeFrom(view(history, tag)))
        // Replaying a captured tag on another route fails.
        assertNull(auth.routeFrom(view("focusmode://session/end", tag)))
        assertNull(auth.routeFrom(view("focusmode://history?x=1", tag)))
        // Another install (another key) cannot forge tags for this one.
        val other = DeepLinkAuthenticator(File(app.noBackupFilesDir, "other_install.key").apply { delete() })
        assertNotEquals(tag, other.sign(history))
        assertNull(auth.routeFrom(view(history, other.sign(history))))
    }

    @Test fun onlyViewIntentsWithTheAppSchemeAreConsidered() {
        val route = "focusmode://missions"
        val tag = auth.sign(route)
        assertNull(auth.routeFrom(null))
        assertNull(auth.routeFrom(Intent(Intent.ACTION_MAIN).setData(Uri.parse(route)).putExtra(DeepLinkAuthenticator.EXTRA_TAG, tag)))
        assertNull(auth.routeFrom(Intent(Intent.ACTION_VIEW)))
        val https = "https://example.invalid/missions"
        assertNull(auth.routeFrom(view(https, auth.sign(https))))
        // Wrong extra type and unrelated extras do not crash the launch.
        assertNull(auth.routeFrom(view(route).putExtra(DeepLinkAuthenticator.EXTRA_TAG, 42)))
        assertEquals(DeepLinkTarget.Missions, auth.routeFrom(view(route, tag).putExtra("android.intent.extra.REFERRER", Uri.parse("android-app://evil"))))
    }

    @Test fun aSignedButHostileRouteIsStillValidatedByTheParser() {
        // Even a correctly tagged route goes through the strict parser (defense in depth).
        listOf("focusmode://mission/..%2Fx", "focusmode://account/delete", "focusmode://" + "a".repeat(450)).forEach { r ->
            assertEquals(DeepLinkTarget.Home, auth.routeFrom(view(r, auth.sign(r))))
        }
    }

    @Test fun reminderNotificationsCarryTaggedRoutes() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = FocusNotifier(app, NotificationChannels(app), NotificationCapabilityReader(app), FakePreferencesRepository(), CoroutineScope(Dispatchers.Unconfined), auth)
        val reminder = Reminder("scheduled_focus:schedule_a:2026-11-19", ReminderType.SCHEDULED_FOCUS, "schedule_a", java.time.Instant.EPOCH,
            "Bereit?", "25 Minuten", ReminderPrecision.INEXACT_ALARM, DeepLinks.build(DeepLinkTarget.QuickFocus("schedule_a")))
        assertTrue(notifier.showReminder(reminder))
        val n = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(DeepLinkTarget.QuickFocus("schedule_a"), auth.routeFrom(shadowOf(n.contentIntent).savedIntent))
        assertEquals(1, n.actions?.size)
        n.actions?.forEach { a -> assertEquals(DeepLinkTarget.QuickFocus("schedule_a"), auth.routeFrom(shadowOf(a.actionIntent).savedIntent)) }
        assertTrue(shadowOf(n.contentIntent).flags and android.app.PendingIntent.FLAG_IMMUTABLE != 0)
    }
}
