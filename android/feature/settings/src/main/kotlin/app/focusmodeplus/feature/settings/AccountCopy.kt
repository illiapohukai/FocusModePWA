package app.focusmodeplus.feature.settings

import app.focusmodeplus.core.designsystem.component.PillTone
import app.focusmodeplus.core.model.AccountErrorKind
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.SyncFailure
import app.focusmodeplus.core.model.SyncPhase
import app.focusmodeplus.core.model.SyncSkipReason
import app.focusmodeplus.core.model.SyncState

/** Concise German copy for the account section (v21). No raw errors, ids, tokens or project names. */
object AccountCopy {
    const val UNCONFIGURED = "Cloud-Synchronisierung ist in diesem Build noch nicht eingerichtet."
    const val GUEST_TITLE = "Lokaler Gastmodus"
    const val NO_LEAGUE_POINTS = "Dadurch entstehen keine verifizierten Liga-Punkte."
    const val LINK_HEADLINE = "Lokaler Fortschritt wird mit diesem Konto verbunden."

    val BENEFITS = listOf(
        "Missionen und Verlauf zwischen deinen Geräten synchronisieren",
        "Auf einem neuen Gerät wiederherstellen",
        "Cloud-Backup zusätzlich zum lokalen Backup",
        "Vorbereitung auf zukünftige Liga-Funktionen",
    )

    /** Title and body for account states that need an explanation; null for normal states. */
    fun accountMessage(state: AccountState): Pair<String, String>? = when (state) {
        AccountState.SignInCanceled -> "Anmeldung abgebrochen" to "Es wurde nichts geändert. Du kannst es jederzeit erneut versuchen."
        AccountState.CredentialUnavailable -> "Kein Google-Konto gefunden" to "Füge in den Android-Einstellungen ein Google-Konto hinzu und versuche es dann erneut."
        AccountState.NetworkUnavailable -> "Keine Verbindung" to "Für die Anmeldung brauchst du eine Internetverbindung. Focus Mode+ funktioniert offline weiter."
        AccountState.AccountDisabled -> "Konto deaktiviert" to "Dieses Konto ist deaktiviert. Deine Daten bleiben auf diesem Gerät und funktionieren weiter."
        AccountState.AccountDeletedRemotely -> "Konto nicht mehr verfügbar" to "Dieses Konto wurde gelöscht oder ist nicht mehr verfügbar. Deine lokalen Daten bleiben auf diesem Gerät."
        is AccountState.ReauthenticationRequired -> "Anmeldung abgelaufen" to "Melde dich erneut an, damit die Synchronisierung weiterläuft. Deine Daten bleiben erhalten."
        is AccountState.PermissionDenied -> "Zugriff verweigert" to "Die Cloud hat den Zugriff abgelehnt. Die Synchronisierung pausiert; deine Daten bleiben auf diesem Gerät."
        is AccountState.Error -> "Anmeldung nicht möglich" to when (state.kind) {
            AccountErrorKind.INVALID_CREDENTIAL -> "Google hat die Anmeldung nicht bestätigt. Bitte versuche es erneut."
            AccountErrorKind.PROVIDER_UNAVAILABLE -> "Die Google-Anmeldung ist auf diesem Gerät nicht verfügbar."
            AccountErrorKind.ACCOUNT_MISMATCH -> "Bitte wähle dasselbe Google-Konto, mit dem du angemeldet bist."
            AccountErrorKind.TOO_MANY_REQUESTS -> "Zu viele Versuche. Bitte warte einen Moment."
            AccountErrorKind.UNKNOWN -> "Bitte versuche es später erneut."
        }
        else -> null
    }

    fun syncFailure(f: SyncFailure): String = when (f) {
        SyncFailure.OFFLINE -> "Offline. Änderungen werden synchronisiert, sobald du wieder verbunden bist."
        SyncFailure.UNAUTHENTICATED -> "Deine Anmeldung ist abgelaufen. Melde dich erneut an."
        SyncFailure.PERMISSION_DENIED -> "Die Cloud hat den Zugriff verweigert. Melde dich erneut an."
        SyncFailure.QUOTA -> "Der Cloud-Dienst ist gerade ausgelastet. Ein neuer Versuch folgt automatisch."
        SyncFailure.INVALID_DATA -> "Einige Einträge konnten nicht synchronisiert werden. Sie bleiben auf diesem Gerät."
        SyncFailure.SERVER, SyncFailure.UNKNOWN -> "Die Synchronisierung ist fehlgeschlagen. Ein neuer Versuch folgt automatisch."
    }

    fun migrationPending(f: SyncFailure): String = when (f) {
        SyncFailure.OFFLINE -> "Keine Verbindung. Die Übernahme wird automatisch fortgesetzt; deine lokalen Daten bleiben unverändert."
        else -> "Die Übernahme ist noch nicht bestätigt und wird automatisch fortgesetzt. Deine lokalen Daten bleiben unverändert."
    }

    fun skipped(r: SyncSkipReason): String = when (r) {
        SyncSkipReason.UNAVAILABLE -> UNCONFIGURED
        SyncSkipReason.NOT_SIGNED_IN -> "Melde dich an, um zu synchronisieren."
        SyncSkipReason.NOT_LINKED -> "Deine lokalen Daten sind noch nicht mit dem Konto verbunden."
        SyncSkipReason.ACCOUNT_MISMATCH -> "Die Daten auf diesem Gerät gehören zu einem anderen Konto."
        SyncSkipReason.DELETION_PENDING -> "Die Kontolöschung ist noch nicht abgeschlossen."
        SyncSkipReason.OFFLINE -> "Offline. Bitte versuche es mit Verbindung erneut."
        SyncSkipReason.ACTIVE_SESSION -> "Beende zuerst die laufende Fokus-Sitzung."
        SyncSkipReason.ACCOUNT_CHANGED -> "Das Konto wurde gewechselt. Es wurde nichts übertragen."
    }

    fun phase(sync: SyncState): Pair<String, PillTone> = when (sync.phase) {
        SyncPhase.UNAVAILABLE -> "Nicht eingerichtet" to PillTone.NEUTRAL
        SyncPhase.SIGNED_OUT -> "Nicht synchronisiert" to PillTone.NEUTRAL
        SyncPhase.PAUSED -> "Pausiert" to PillTone.NEUTRAL
        SyncPhase.DECISION_REQUIRED -> "Entscheidung offen" to PillTone.WARNING
        SyncPhase.ACCOUNT_MISMATCH -> "Anderes Konto" to PillTone.WARNING
        SyncPhase.IDLE -> if (sync.pendingChanges > 0) "Änderungen ausstehend" to PillTone.ACCENT else "Synchronisiert" to PillTone.READY
        SyncPhase.SYNCING -> "Wird synchronisiert …" to PillTone.ACCENT
        SyncPhase.MIGRATING -> "Datenübernahme läuft" to PillTone.ACCENT
        SyncPhase.OFFLINE -> "Offline" to PillTone.NEUTRAL
        SyncPhase.FAILED -> "Fehler" to PillTone.WARNING
        SyncPhase.DELETING -> "Löschung offen" to PillTone.WARNING
    }

    /** Settings hero chip: where the data lives right now. */
    fun storageChip(sync: SyncState): String = when (sync.phase) {
        SyncPhase.IDLE, SyncPhase.SYNCING, SyncPhase.OFFLINE, SyncPhase.FAILED, SyncPhase.MIGRATING -> "Mit Konto synchronisiert"
        else -> "Lokal gespeichert"
    }

    fun changes(n: Int): String = if (n == 1) "1 Änderung" else "$n Änderungen"
}
