package app.focusmodeplus.core.domain.notification

import app.focusmodeplus.core.model.ProtectionPermissionState

/** Focus Mode+ notification channels (ids are stable; changing one needs a migration). */
enum class FocusChannel(val id: String, val title: String, val description: String) {
    ACTIVE_SESSION("focus_active", "Aktive Fokus-Sitzung", "Zeigt die laufende Sitzung und die verbleibende Zeit."),
    SESSION_FINISHED("focus_finished", "Fokus beendet", "Meldet, wenn deine geplante Fokuszeit vorbei ist."),
    REMINDERS("reminders", "Erinnerungen", "Geplanter Fokus und optionale Tagesplanung."),
    MISSION_DEADLINES("mission_deadlines", "Missions-Deadlines", "Vorbereitungsstart und nahende Deadlines deiner Missionen."),
    PROTECTION("protection_status", "Fokus-Schutz", "Kurze Fokus-Hinweise während einer geschützten Sitzung."),
}

enum class NotificationDelivery {
    DELIVERABLE,
    PERMISSION_NOT_REQUESTED,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    DISABLED_BY_OS,
    CHANNEL_DISABLED,
    DISABLED_IN_APP,
}

/** What the OS and the user currently allow. Re-read on every resume; never cached as truth. */
data class NotificationCapability(
    val permission: ProtectionPermissionState,
    val osEnabled: Boolean,
    val blockedChannels: Set<FocusChannel>,
    val appPreferenceEnabled: Boolean,
) {
    /**
     * The app-level switch governs reminders and hints; the ongoing Session notification belongs
     * to the foreground service and follows only the system permission.
     */
    fun deliveryFor(channel: FocusChannel): NotificationDelivery = when {
        channel != FocusChannel.ACTIVE_SESSION && channel != FocusChannel.SESSION_FINISHED && !appPreferenceEnabled ->
            NotificationDelivery.DISABLED_IN_APP
        permission == ProtectionPermissionState.NOT_REQUESTED -> NotificationDelivery.PERMISSION_NOT_REQUESTED
        permission == ProtectionPermissionState.DENIED -> NotificationDelivery.PERMISSION_DENIED
        permission == ProtectionPermissionState.PERMANENTLY_DENIED -> NotificationDelivery.PERMISSION_PERMANENTLY_DENIED
        !osEnabled -> NotificationDelivery.DISABLED_BY_OS
        channel in blockedChannels -> NotificationDelivery.CHANNEL_DISABLED
        else -> NotificationDelivery.DELIVERABLE
    }

    fun canDeliver(channel: FocusChannel): Boolean = deliveryFor(channel) == NotificationDelivery.DELIVERABLE

    companion object {
        /**
         * Notification permission state. Android 13+ only reports granted/denied; "permanently
         * denied" is inferred: denied after at least one request with no rationale to show.
         */
        fun resolvePermission(
            runtimePermissionRequired: Boolean,
            granted: Boolean,
            requestCount: Int,
            shouldShowRationale: Boolean,
        ): ProtectionPermissionState = when {
            !runtimePermissionRequired -> ProtectionPermissionState.NOT_REQUIRED
            granted -> ProtectionPermissionState.GRANTED
            requestCount <= 0 -> ProtectionPermissionState.NOT_REQUESTED
            shouldShowRationale -> ProtectionPermissionState.DENIED
            else -> ProtectionPermissionState.PERMANENTLY_DENIED
        }

        fun deliveryLabel(delivery: NotificationDelivery): String = when (delivery) {
            NotificationDelivery.DELIVERABLE -> "Benachrichtigungen aktiv"
            NotificationDelivery.PERMISSION_NOT_REQUESTED -> "Benachrichtigungen noch nicht erlaubt"
            NotificationDelivery.PERMISSION_DENIED,
            NotificationDelivery.PERMISSION_PERMANENTLY_DENIED,
            NotificationDelivery.DISABLED_BY_OS -> "Benachrichtigungen deaktiviert"
            NotificationDelivery.CHANNEL_DISABLED -> "Diese Kategorie ist in den Systemeinstellungen aus"
            NotificationDelivery.DISABLED_IN_APP -> "In Focus Mode+ ausgeschaltet"
        }
    }
}
