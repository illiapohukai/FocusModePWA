package io.github.illiapohukai.focusmode.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.account.AccountChooserHost
import io.github.illiapohukai.focusmode.core.domain.account.AccountLifecycle
import io.github.illiapohukai.focusmode.core.domain.account.AccountRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.sync.SyncRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.AccountDataDecision
import io.github.illiapohukai.focusmode.core.model.AccountDeletionResult
import io.github.illiapohukai.focusmode.core.model.AccountResult
import io.github.illiapohukai.focusmode.core.model.AccountState
import io.github.illiapohukai.focusmode.core.model.AccountSwitchChoice
import io.github.illiapohukai.focusmode.core.model.MigrationPreview
import io.github.illiapohukai.focusmode.core.model.SyncPhase
import io.github.illiapohukai.focusmode.core.model.SyncResult
import io.github.illiapohukai.focusmode.core.model.SyncState
import io.github.illiapohukai.focusmode.core.model.activeUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class AccountUiState(
    val loading: Boolean = true,
    val account: AccountState = AccountState.FirebaseUnconfigured,
    val sync: SyncState = SyncState(phase = SyncPhase.UNAVAILABLE),
    /** Shown before linking; nothing changes until the user confirms. */
    val preview: MigrationPreview? = null,
    val switch: AccountDataDecision.AccountSwitch? = null,
    /** "Später" collapses the linking card to one row for this screen visit. */
    val decisionPostponed: Boolean = false,
    val busy: Boolean = false,
    val feedback: DataFeedback? = null,
    val lastSyncLabel: String? = null,
    val localMissions: Int = 0,
    val localSessions: Int = 0,
) {
    val signedIn: Boolean get() = account.activeUid != null
    val configured: Boolean get() = account != AccountState.FirebaseUnconfigured
}

/**
 * Account section (v21). Talks only to the domain owners: [AccountLifecycle] for sign-in,
 * sign-out, switch and deletion, [SyncRepository] for state, linking and manual sync. It never
 * touches Firebase, never holds a listener and never keeps the Activity it is handed.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val sync: SyncRepository,
    private val lifecycle: AccountLifecycle,
    missions: MissionRepository,
    history: SessionHistoryRepository,
    private val clock: FocusClock,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<DataFeedback?>(null)
    private val preview = MutableStateFlow<MigrationPreview?>(null)
    private val postponed = MutableStateFlow(false)

    private data class Local(val missions: Int, val sessions: Int)
    private data class Ui(val busy: Boolean, val feedback: DataFeedback?, val preview: MigrationPreview?, val postponed: Boolean)

    val uiState: StateFlow<AccountUiState> = combine(
        accounts.accountState,
        sync.syncState,
        sync.decision,
        combine(busy, feedback, preview, postponed) { b, f, p, d -> Ui(b, f, p, d) },
        combine(missions.observeMissions(), history.observeResults()) { m, r -> Local(m.size, r.size) },
    ) { account, state, decision, ui, local ->
        AccountUiState(
            loading = false,
            account = account,
            sync = state,
            preview = (decision as? AccountDataDecision.LinkLocalData)?.preview ?: ui.preview,
            switch = decision as? AccountDataDecision.AccountSwitch,
            decisionPostponed = ui.postponed,
            busy = ui.busy,
            feedback = ui.feedback,
            lastSyncLabel = state.lastSuccessAt?.let { LAST.format(ZonedDateTime.ofInstant(it, clock.zone())) },
            localMissions = local.missions,
            localSessions = local.sessions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    init {
        viewModelScope.launch {
            sync.syncState.map { it.phase }.distinctUntilChanged().collect { if (it == SyncPhase.DECISION_REQUIRED) loadPreview() }
        }
    }

    private fun loadPreview() {
        val uid = accounts.accountState.value.activeUid ?: return
        viewModelScope.launch { preview.value = sync.prepareMigration(uid) }
    }

    /** One action at a time: a double tap never starts a second sign-in, migration or deletion. */
    private fun act(block: suspend () -> DataFeedback?) {
        if (busy.value) return
        busy.value = true
        feedback.value = null
        viewModelScope.launch {
            try { feedback.value = block() } finally { busy.value = false }
        }
    }

    fun signIn(host: AccountChooserHost) = act {
        postponed.value = false
        when (val r = lifecycle.signIn(host)) {
            is AccountResult.Success -> null
            is AccountResult.Failure -> null // the account state itself explains what happened
            AccountResult.Unavailable -> DataFeedback(false, "Nicht verfügbar", AccountCopy.UNCONFIGURED)
        }
    }

    fun continueAsGuest() = act { lifecycle.continueAsGuest(); null }

    fun syncNow() = act { syncFeedback(sync.synchronize()) }

    fun retryParked() = act { sync.retryParked(); syncFeedback(sync.synchronize()) }

    fun linkLocalData() = act {
        val uid = accounts.accountState.value.activeUid ?: return@act null
        when (val r = sync.migrateLocalData(uid)) {
            is SyncResult.Success -> DataFeedback(true, "Lokale Daten verbunden", "Dein lokaler Fortschritt ist jetzt mit diesem Konto verbunden. Deine Daten auf diesem Gerät bleiben erhalten.")
            is SyncResult.Failed -> DataFeedback(false, "Datenübernahme läuft weiter", AccountCopy.migrationPending(r.failure))
            is SyncResult.Skipped -> DataFeedback(false, "Nicht verbunden", AccountCopy.skipped(r.reason))
        }
    }

    fun useCloudData() = act {
        val uid = accounts.accountState.value.activeUid ?: return@act null
        when (val r = sync.adoptCloudData(uid)) {
            is SyncResult.Success -> DataFeedback(true, "Cloud-Daten übernommen", "Dieses Gerät zeigt jetzt die Missionen und den Verlauf deines Kontos.")
            is SyncResult.Failed -> DataFeedback(false, "Nicht übernommen", AccountCopy.syncFailure(r.failure))
            is SyncResult.Skipped -> DataFeedback(false, "Nicht übernommen", AccountCopy.skipped(r.reason))
        }
    }

    fun postpone() {
        sync.postponeDecision()
        postponed.value = true
    }

    fun expandDecision() {
        postponed.value = false
        loadPreview()
    }

    fun signOut(removeLocalData: Boolean) = act {
        val out = lifecycle.signOut(removeLocalData)
        DataFeedback(
            true, "Abgemeldet",
            if (out.localDataErased) "Lokale Daten wurden von diesem Gerät entfernt. Deine Cloud-Daten bleiben im Konto erhalten."
            else "Du nutzt Focus Mode+ jetzt im Gastmodus. Deine Cloud-Daten bleiben im Konto erhalten.",
        )
    }

    fun resolveSwitch(choice: AccountSwitchChoice) = act {
        when (val r = lifecycle.resolveAccountSwitch(choice)) {
            is SyncResult.Success -> when (choice) {
                AccountSwitchChoice.KEEP_SEPARATE -> DataFeedback(true, "Getrennt gehalten", "Das neue Konto wurde wieder abgemeldet. Die Daten auf diesem Gerät bleiben beim bisherigen Konto.")
                AccountSwitchChoice.DELETE_LOCAL -> DataFeedback(true, "Neu gestartet", "Lokale Daten wurden entfernt. Dieses Gerät nutzt jetzt die Daten des angemeldeten Kontos.")
                AccountSwitchChoice.USE_NEW_ACCOUNT_DATA -> DataFeedback(true, "Kontodaten übernommen", "Dieses Gerät zeigt jetzt die Daten des angemeldeten Kontos.")
            }
            is SyncResult.Failed -> DataFeedback(false, "Nicht abgeschlossen", AccountCopy.syncFailure(r.failure))
            is SyncResult.Skipped -> DataFeedback(false, "Nicht abgeschlossen", AccountCopy.skipped(r.reason))
        }
    }

    fun unlink() = act {
        sync.unlinkAccount()
        DataFeedback(true, "Verknüpfung gelöst", "Deine Daten bleiben als lokale Daten auf diesem Gerät. Es wird nichts mehr synchronisiert.")
    }

    fun deleteAccount(host: AccountChooserHost, alsoDeleteLocal: Boolean) = act {
        when (val r = lifecycle.deleteAccount(host, alsoDeleteLocal)) {
            is AccountDeletionResult.Deleted -> DataFeedback(
                true, "Konto gelöscht",
                if (r.localDataErased) "Dein Konto, deine Cloud-Daten und die lokalen Daten wurden gelöscht."
                else "Dein Konto und deine Cloud-Daten wurden gelöscht. Die Daten auf diesem Gerät bleiben lokal erhalten.",
            )
            AccountDeletionResult.Canceled -> DataFeedback(false, "Nicht gelöscht", "Die Bestätigung wurde abgebrochen. Es wurde nichts gelöscht.")
            AccountDeletionResult.ReauthenticationRequired -> DataFeedback(false, "Bestätigung erforderlich", "Bitte bestätige die Löschung erneut mit deinem Google-Konto.")
            AccountDeletionResult.NetworkUnavailable -> DataFeedback(false, "Keine Verbindung", "Die Löschung braucht eine Internetverbindung. Bereits Gelöschtes bleibt gelöscht; versuche es erneut.")
            AccountDeletionResult.Incomplete -> DataFeedback(false, "Löschung nicht abgeschlossen", "Einige Cloud-Daten konnten nicht gelöscht werden. Bitte versuche es erneut.")
            AccountDeletionResult.NotSignedIn -> DataFeedback(false, "Nicht angemeldet", "Melde dich an, um dein Konto zu löschen.")
            AccountDeletionResult.Unavailable -> DataFeedback(false, "Nicht verfügbar", AccountCopy.UNCONFIGURED)
            AccountDeletionResult.Failed -> DataFeedback(false, "Nicht gelöscht", "Das Konto konnte nicht gelöscht werden. Bitte versuche es später erneut.")
        }
    }

    fun dismissFeedback() { feedback.value = null }

    private fun syncFeedback(r: SyncResult): DataFeedback = when (r) {
        is SyncResult.Success -> DataFeedback(true, "Synchronisiert", if (r.conflicts > 0) "Alles ist aktuell. ${r.conflicts} gleichzeitige Änderung wurde nach festen Regeln zusammengeführt." else "Alles ist auf dem neuesten Stand.")
        is SyncResult.Failed -> DataFeedback(false, "Nicht synchronisiert", AccountCopy.syncFailure(r.failure))
        is SyncResult.Skipped -> DataFeedback(false, "Nicht synchronisiert", AccountCopy.skipped(r.reason))
    }

    private companion object {
        val LAST: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)
    }
}
