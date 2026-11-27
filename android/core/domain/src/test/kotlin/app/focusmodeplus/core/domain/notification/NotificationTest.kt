package app.focusmodeplus.core.domain.notification

import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.ProtectionPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTest {
    @Test fun activeSessionCopyMatchesTemplate() {
        val t = NotificationCopy.activeSession(18, "Lineare Funktionen", NotificationDetailLevel.GENERIC)
        assertEquals("Fokus läuft", t.title)
        assertEquals("Noch 18 Minuten", t.body)
        assertEquals("Noch 1 Minute", NotificationCopy.activeSession(1, "x", NotificationDetailLevel.GENERIC).body)
    }

    @Test fun lockScreenPublicVersionNeverContainsTaskText() {
        val task = "Geheime Bewerbung schreiben"
        val full = NotificationCopy.activeSession(18, task, NotificationDetailLevel.FULL_TASK)
        assertTrue(full.body.contains(task))
        assertFalse(full.publicBody!!.contains(task))
        assertFalse(full.publicTitle!!.contains(task))
        val finished = NotificationCopy.sessionFinished(25, task, NotificationDetailLevel.FULL_TASK)
        assertFalse(finished.publicBody!!.contains(task))
        val hidden = NotificationCopy.activeSession(18, task, NotificationDetailLevel.HIDDEN)
        assertFalse(hidden.body.contains(task))
        assertNull(hidden.publicBody)
        val deadline = NotificationCopy.deadline(2, "Mathe-Klausur", 3)
        assertFalse(deadline.publicBody!!.contains("Mathe"))
    }

    @Test fun distractionHintNeverNamesTheApp() {
        val t = NotificationCopy.distractionHint(12)
        assertEquals("Noch 12 Minuten – zurück zu deiner Aufgabe?", t.body)
    }

    @Test fun permissionStateResolution() {
        fun r(req: Boolean, granted: Boolean, count: Int, rationale: Boolean) =
            NotificationCapability.resolvePermission(req, granted, count, rationale)
        assertEquals(ProtectionPermissionState.NOT_REQUIRED, r(false, true, 0, false))
        assertEquals(ProtectionPermissionState.GRANTED, r(true, true, 1, false))
        assertEquals(ProtectionPermissionState.NOT_REQUESTED, r(true, false, 0, false))
        assertEquals(ProtectionPermissionState.DENIED, r(true, false, 1, true))
        assertEquals(ProtectionPermissionState.PERMANENTLY_DENIED, r(true, false, 2, false))
    }

    @Test fun deliveryMatrix() {
        fun cap(p: ProtectionPermissionState = ProtectionPermissionState.GRANTED, os: Boolean = true, blocked: Set<FocusChannel> = emptySet(), app: Boolean = true) =
            NotificationCapability(p, os, blocked, app)
        assertEquals(NotificationDelivery.DELIVERABLE, cap().deliveryFor(FocusChannel.REMINDERS))
        assertEquals(NotificationDelivery.PERMISSION_DENIED, cap(ProtectionPermissionState.DENIED).deliveryFor(FocusChannel.REMINDERS))
        assertEquals(NotificationDelivery.PERMISSION_PERMANENTLY_DENIED, cap(ProtectionPermissionState.PERMANENTLY_DENIED).deliveryFor(FocusChannel.ACTIVE_SESSION))
        assertEquals(NotificationDelivery.DISABLED_BY_OS, cap(os = false).deliveryFor(FocusChannel.REMINDERS))
        assertEquals(NotificationDelivery.CHANNEL_DISABLED, cap(blocked = setOf(FocusChannel.REMINDERS)).deliveryFor(FocusChannel.REMINDERS))
        assertEquals(NotificationDelivery.DISABLED_IN_APP, cap(app = false).deliveryFor(FocusChannel.REMINDERS))
        // The ongoing Session notification belongs to the foreground service, not the app switch.
        assertEquals(NotificationDelivery.DELIVERABLE, cap(app = false).deliveryFor(FocusChannel.ACTIVE_SESSION))
    }
}
