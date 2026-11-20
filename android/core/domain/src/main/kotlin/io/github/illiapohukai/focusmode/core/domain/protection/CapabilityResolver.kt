package io.github.illiapohukai.focusmode.core.domain.protection

import io.github.illiapohukai.focusmode.core.model.CapabilityAssessment
import io.github.illiapohukai.focusmode.core.model.ManagedDeviceStatus
import io.github.illiapohukai.focusmode.core.model.PermissionKind
import io.github.illiapohukai.focusmode.core.model.PermissionSnapshot
import io.github.illiapohukai.focusmode.core.model.ProtectionCapability
import io.github.illiapohukai.focusmode.core.model.ProtectionCapability.*
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState as PS
import io.github.illiapohukai.focusmode.core.model.SettingsTarget

data class ProtectionInputs(
    val snapshot: PermissionSnapshot,
    val selectedAppCount: Int,
    val usageMonitoringEnabled: Boolean,
    val doNotDisturbEnabled: Boolean,
    /** Set while a protected Session runs and Usage Access was revoked mid-session. */
    val usageRevokedDuringSession: Boolean = false,
)

/** One row of the Settings permission center. */
data class PermissionRow(
    val kind: PermissionKind,
    val title: String,
    val purpose: String,
    val dataAccessed: String,
    val leavesDevice: String,
    val state: ProtectionPermissionState,
    val stateLabel: String,
    val actionLabel: String?,
    val settingsTarget: SettingsTarget?,
    /** True when the app may show the system permission dialog itself (notifications only). */
    val requestInApp: Boolean,
    val limitation: String,
)

data class ProtectionOverview(
    val softProtection: CapabilityAssessment,
    val usageMonitoring: CapabilityAssessment,
    val doNotDisturb: CapabilityAssessment,
    val managed: CapabilityAssessment,
    val accessibility: CapabilityAssessment,
    val availableModes: Set<ProtectionMode>,
    val permissionRows: List<PermissionRow>,
) {
    /** Honest one-line status for the Quick Focus "Fokus-Schutz" row. */
    fun summaryFor(mode: ProtectionMode, selectedAppCount: Int): String {
        val apps = when (selectedAppCount) {
            0 -> "Keine Apps ausgewählt"
            1 -> "1 App ausgewählt"
            else -> "$selectedAppCount Apps ausgewählt"
        }
        return when (mode) {
            ProtectionMode.STANDARD -> when {
                softProtection.capability == READY_FOR_SOFT_PROTECTION -> "Standard-Fokus · Fokus-Hinweis aktiv"
                softProtection.permission == ProtectionPermissionState.NOT_REQUESTED -> "Standard-Fokus · Fokus-Hinweis noch nicht erlaubt"
                else -> "Standard-Fokus · Benachrichtigungen deaktiviert"
            }
            ProtectionMode.PROTECTED -> when (usageMonitoring.capability) {
                READY_FOR_USAGE_MONITORING -> "Geschützter Fokus · Ablenkungen überwachen · $apps"
                USAGE_ACCESS_NEEDED -> "Geschützter Fokus · Nutzungszugriff erforderlich"
                NOT_CONFIGURED -> "Geschützter Fokus · App-Auswahl vorbereitet · $apps"
                else -> "Geschützter Fokus · Beenden mit Bedenkzeit"
            }
            ProtectionMode.STRICT_MANAGED -> "Strikte Sperre auf diesem Gerät nicht verfügbar"
        }
    }
}

/**
 * Pure resolution of what protection the platform really offers right now. The Strict mode is
 * only available with a real managed enforcement (never on an ordinary consumer phone).
 */
object CapabilityResolver {

    fun resolve(inputs: ProtectionInputs): ProtectionOverview {
        val s = inputs.snapshot
        val soft = softProtection(s.notifications)
        val usage = usageMonitoring(inputs)
        val dnd = doNotDisturb(s.doNotDisturb, inputs.doNotDisturbEnabled)
        val managed = managed(s.managedDevice)
        val modes = buildSet {
            add(ProtectionMode.STANDARD)
            add(ProtectionMode.PROTECTED)
            if (managed.capability == READY_FOR_MANAGED_BLOCKING) add(ProtectionMode.STRICT_MANAGED)
        }
        return ProtectionOverview(soft, usage, dnd, managed, ACCESSIBILITY, modes, permissionRows(s))
    }

    private fun granted(state: ProtectionPermissionState) = state == PS.GRANTED || state == PS.NOT_REQUIRED

    private fun softProtection(notifications: ProtectionPermissionState) =
        if (granted(notifications)) CapabilityAssessment(
            READY_FOR_SOFT_PROTECTION, "Fokus-Schutz vorbereitet",
            "Während der Sitzung zeigt eine dauerhafte Benachrichtigung die verbleibende Zeit.",
            null, null, notifications,
            "Andere Apps bleiben nutzbar. Fokus Mode+ blockiert keine Apps.",
        ) else CapabilityAssessment(
            NOTIFICATION_PERMISSION_NEEDED, "Benachrichtigungen deaktiviert",
            "Der Timer läuft trotzdem, aber ohne Hinweis in der Benachrichtigungsleiste.",
            if (notifications == PS.NOT_REQUESTED) "Benachrichtigungen erlauben" else "In den Einstellungen erlauben",
            SettingsTarget.APP_NOTIFICATION_SETTINGS, notifications,
            "Ohne Benachrichtigung siehst du die Restzeit nur in der App.",
        )

    private fun usageMonitoring(inputs: ProtectionInputs): CapabilityAssessment {
        val state = inputs.snapshot.usageAccess
        return when {
            state == PS.UNSUPPORTED -> CapabilityAssessment(
                UNSUPPORTED, "Nutzungszugriff nicht verfügbar",
                "Dieses Gerät bietet keinen Nutzungszugriff an.", null, null, state,
                "Ablenkungen können auf diesem Gerät nicht erkannt werden.",
            )
            inputs.usageRevokedDuringSession -> CapabilityAssessment(
                TEMPORARILY_UNAVAILABLE, "Überwachung pausiert",
                "Der Nutzungszugriff wurde während der Sitzung entzogen. Die Sitzung läuft normal weiter.",
                "Nutzungszugriff erneut erlauben", SettingsTarget.USAGE_ACCESS_SETTINGS, state,
                "Bis zur erneuten Freigabe gibt es keine Fokus-Hinweise.",
            )
            !granted(state) -> CapabilityAssessment(
                USAGE_ACCESS_NEEDED, "Nutzungszugriff erforderlich",
                "Damit Focus Mode+ bemerkt, wenn du während einer geschützten Sitzung eine ausgewählte App öffnest.",
                "Nutzungszugriff erlauben", SettingsTarget.USAGE_ACCESS_SETTINGS, state,
                "Es werden nur ausgewählte Apps und nur während einer geschützten Sitzung geprüft.",
            )
            inputs.selectedAppCount == 0 -> CapabilityAssessment(
                NOT_CONFIGURED, "App-Auswahl vorbereitet",
                "Wähle ablenkende Apps aus, damit Focus Mode+ dich bei ihnen an deinen Fokus erinnert.",
                "Apps auswählen", null, state,
                "Ohne ausgewählte Apps gibt es keine Fokus-Hinweise.",
            )
            !inputs.usageMonitoringEnabled -> CapabilityAssessment(
                NOT_CONFIGURED, "Ablenkungen überwachen ist aus",
                "Der Nutzungszugriff ist erlaubt, die Überwachung ist in Focus Mode+ ausgeschaltet.",
                "Überwachung einschalten", null, state,
                "Fokus-Hinweise erscheinen erst nach dem Einschalten.",
            )
            else -> CapabilityAssessment(
                READY_FOR_USAGE_MONITORING, "Ablenkungen überwachen",
                "Öffnest du während einer geschützten Sitzung eine ausgewählte App, erscheint ein Fokus-Hinweis.",
                null, SettingsTarget.USAGE_ACCESS_SETTINGS, state,
                "Die App wird nicht blockiert. Erkennung kann je nach Gerät einige Sekunden dauern.",
            )
        }
    }

    private fun doNotDisturb(state: ProtectionPermissionState, enabled: Boolean) = when {
        state == PS.UNSUPPORTED -> CapabilityAssessment(
            UNSUPPORTED, "Nicht stören nicht verfügbar", "Dieses Gerät erlaubt Apps keinen Zugriff auf Nicht stören.",
            null, null, state, "Benachrichtigungen anderer Apps bleiben unverändert.",
        )
        !granted(state) -> CapabilityAssessment(
            DO_NOT_DISTURB_ACCESS_NEEDED, "Systemzugriff erforderlich",
            "Optional: Focus Mode+ kann während der Sitzung Nicht stören aktivieren und danach wiederherstellen.",
            "Zugriff auf Nicht stören erlauben", SettingsTarget.DND_ACCESS_SETTINGS, state,
            "Nicht stören dämpft Benachrichtigungen, blockiert aber keine Apps.",
        )
        else -> CapabilityAssessment(
            if (enabled) READY_FOR_SOFT_PROTECTION else NOT_CONFIGURED,
            if (enabled) "Nicht stören während des Fokus" else "Nicht stören verfügbar",
            "Deine Ausnahmen (z. B. Wecker, wichtige Kontakte) bleiben erhalten. Nach der Sitzung wird der vorherige Zustand wiederhergestellt.",
            if (enabled) null else "Für Fokus-Sitzungen einschalten", SettingsTarget.DND_ACCESS_SETTINGS, state,
            "Nicht stören dämpft Benachrichtigungen, blockiert aber keine Apps.",
        )
    }

    private fun managed(status: ManagedDeviceStatus) = when (status) {
        ManagedDeviceStatus.NOT_MANAGED -> CapabilityAssessment(
            MANAGED_DEVICE_REQUIRED, "Verwaltetes Gerät erforderlich",
            "Eine strikte App-Sperre ist unter Android nur auf verwalteten Geräten möglich (z. B. Schul- oder Firmengeräte).",
            null, null, ProtectionPermissionState.UNSUPPORTED,
            "Strikte Sperre auf diesem Gerät nicht verfügbar.",
        )
        ManagedDeviceStatus.OWNER_DETECTED_NOT_SUPPORTED -> CapabilityAssessment(
            TEMPORARILY_UNAVAILABLE, "Strikter Modus noch nicht unterstützt",
            "Dieses Gerät ist verwaltet, aber diese Version enthält noch keine strikte Sperre.",
            null, null, ProtectionPermissionState.UNSUPPORTED,
            "Strikte Sperre auf diesem Gerät nicht verfügbar.",
        )
        ManagedDeviceStatus.MANAGED_ENFORCEMENT_READY -> CapabilityAssessment(
            READY_FOR_MANAGED_BLOCKING, "Strikte Sperre verfügbar",
            "Die Geräteverwaltung erlaubt eine strikte Sperre ausgewählter Apps.",
            null, null, ProtectionPermissionState.GRANTED,
            "Nur auf verwalteten Geräten.",
        )
    }

    private val ACCESSIBILITY = CapabilityAssessment(
        ACCESSIBILITY_NOT_AVAILABLE, "Bedienungshilfen werden nicht genutzt",
        "Focus Mode+ verwendet bewusst keinen Bedienungshilfen-Dienst, um Apps zu überwachen oder zu sperren.",
        null, null, ProtectionPermissionState.UNSUPPORTED,
        "Das schützt deine Bildschirminhalte und hält die App fair überprüfbar.",
    )

    fun stateLabel(state: ProtectionPermissionState): String = when (state) {
        PS.GRANTED -> "Erlaubt"
        PS.NOT_REQUIRED -> "Nicht erforderlich"
        PS.NOT_REQUESTED -> "Nicht angefragt"
        PS.DENIED -> "Abgelehnt"
        PS.PERMANENTLY_DENIED -> "In den Einstellungen deaktiviert"
        PS.UNSUPPORTED -> "Nicht verfügbar"
    }

    private fun permissionRows(s: PermissionSnapshot): List<PermissionRow> = listOf(
        PermissionRow(
            PermissionKind.NOTIFICATIONS, "Benachrichtigungen",
            "Zeigt die laufende Sitzung, das Sitzungsende und Erinnerungen, die du eingerichtet hast.",
            "Keine Daten – nur Anzeige von Focus-Mode+-Hinweisen.", "Nein, alles bleibt auf dem Gerät.",
            s.notifications, stateLabel(s.notifications),
            when (s.notifications) { PS.NOT_REQUESTED, PS.DENIED -> "Erlauben"; PS.PERMANENTLY_DENIED -> "Einstellungen öffnen"; else -> "Einstellungen" },
            SettingsTarget.APP_NOTIFICATION_SETTINGS,
            requestInApp = s.notifications == PS.NOT_REQUESTED || s.notifications == PS.DENIED,
            "Ohne Erlaubnis läuft der Timer weiter, aber Erinnerungen erscheinen nicht.",
        ),
        PermissionRow(
            PermissionKind.USAGE_ACCESS, "Nutzungszugriff",
            "Erkennt während einer geschützten Sitzung, ob du eine ausgewählte ablenkende App öffnest.",
            "Nur Vordergrund-Ereignisse der ausgewählten Apps während der Sitzung.", "Nein, die Daten verlassen das Gerät nie.",
            s.usageAccess, stateLabel(s.usageAccess),
            if (s.usageAccess == PS.UNSUPPORTED) null else if (granted(s.usageAccess)) "Zugriff verwalten" else "Einstellungen öffnen",
            SettingsTarget.USAGE_ACCESS_SETTINGS.takeIf { s.usageAccess != PS.UNSUPPORTED }, false,
            "Apps werden nicht blockiert. Manche Hersteller melden Ereignisse verzögert.",
        ),
        PermissionRow(
            PermissionKind.DO_NOT_DISTURB, "Nicht stören",
            "Aktiviert auf Wunsch Nicht stören für die Dauer deiner Sitzung und stellt danach den vorherigen Zustand wieder her.",
            "Nur der Nicht-stören-Status. Keine Inhalte anderer Benachrichtigungen.", "Nein.",
            s.doNotDisturb, stateLabel(s.doNotDisturb),
            if (s.doNotDisturb == PS.UNSUPPORTED) null else if (granted(s.doNotDisturb)) "Zugriff verwalten" else "Einstellungen öffnen",
            SettingsTarget.DND_ACCESS_SETTINGS.takeIf { s.doNotDisturb != PS.UNSUPPORTED }, false,
            "Nicht stören blockiert keine Apps.",
        ),
        PermissionRow(
            PermissionKind.EXACT_ALARM, "Exakte Erinnerungen",
            "Pünktliche Zustellung von Erinnerungen zu geplantem Fokus.",
            "Keine Daten.", "Nein.",
            s.exactAlarm, if (granted(s.exactAlarm)) "Verfügbar" else "Nicht angefordert",
            null, null, false,
            if (granted(s.exactAlarm)) "Erinnerungen werden pünktlich zugestellt."
            else "Focus Mode+ fordert keine exakten Wecker an. Erinnerungen kommen ungefähr pünktlich, meist innerhalb weniger Minuten.",
        ),
        PermissionRow(
            PermissionKind.BATTERY_OPTIMIZATION, "Akku-Optimierung",
            "Strenge Akku-Optimierung kann Erinnerungen verzögern.",
            "Keine Daten.", "Nein.",
            when (s.batteryOptimizationIgnored) { true -> PS.GRANTED; false -> PS.DENIED; null -> PS.UNSUPPORTED },
            when (s.batteryOptimizationIgnored) { true -> "Nicht eingeschränkt"; false -> "Optimiert (Standard)"; null -> "Unbekannt" },
            "Einstellungen öffnen", SettingsTarget.BATTERY_OPTIMIZATION_SETTINGS, false,
            "Eine Ausnahme ist nicht nötig. Manche Hersteller verzögern Erinnerungen trotzdem.",
        ),
        PermissionRow(
            PermissionKind.MANAGED_DEVICE, "Verwaltetes Gerät",
            "Nur verwaltete Geräte erlauben eine strikte Sperre von Apps.",
            "Keine Daten.", "Nein.",
            if (s.managedDevice == ManagedDeviceStatus.MANAGED_ENFORCEMENT_READY) PS.GRANTED else PS.UNSUPPORTED,
            when (s.managedDevice) {
                ManagedDeviceStatus.NOT_MANAGED -> "Nicht verwaltet"
                ManagedDeviceStatus.OWNER_DETECTED_NOT_SUPPORTED -> "Verwaltet – noch nicht unterstützt"
                ManagedDeviceStatus.MANAGED_ENFORCEMENT_READY -> "Verwaltet"
            },
            null, null, false,
            "Strikte Sperre auf diesem Gerät nicht verfügbar. Focus Mode+ richtet niemals selbst eine Geräteverwaltung ein.",
        ),
        PermissionRow(
            PermissionKind.ACCESSIBILITY, "Bedienungshilfen",
            "Wird nicht verwendet.",
            "Keine.", "Nein.",
            PS.UNSUPPORTED, "Nicht verwendet", null, null, false,
            "Focus Mode+ liest keine Bildschirminhalte und steuert keine anderen Apps.",
        ),
    )

    /** Effective mode for a Session: falls back to the strongest mode the platform supports. */
    fun effectiveMode(requested: ProtectionMode, overview: ProtectionOverview): ProtectionMode =
        if (requested in overview.availableModes) requested else ProtectionMode.PROTECTED.takeIf { requested == ProtectionMode.STRICT_MANAGED } ?: ProtectionMode.STANDARD
}
