package app.focusmodeplus.core.domain.account

import app.focusmodeplus.core.domain.backup.EraseOutcome
import app.focusmodeplus.core.domain.backup.PersonalDataEraser
import app.focusmodeplus.core.domain.sync.RemoteCollection
import app.focusmodeplus.core.domain.sync.RemotePath
import app.focusmodeplus.core.domain.sync.RemoteSchema
import app.focusmodeplus.core.domain.sync.RemoteSyncException
import app.focusmodeplus.core.domain.sync.RemoteSyncStore
import app.focusmodeplus.core.domain.sync.RemoteWrite
import app.focusmodeplus.core.domain.sync.SyncLocalStore
import app.focusmodeplus.core.domain.sync.SyncRepository
import app.focusmodeplus.core.domain.sync.SyncScheduler
import app.focusmodeplus.core.model.AccountDeletionResult
import app.focusmodeplus.core.model.AccountResult
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountSwitchChoice
import app.focusmodeplus.core.model.SyncFailure
import app.focusmodeplus.core.model.SyncResult
import app.focusmodeplus.core.model.SyncSkipReason
import app.focusmodeplus.core.model.activeUid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What sign-out did. Cloud data is never deleted by signing out. */
data class SignOutOutcome(val signedOut: Boolean, val localDataErased: Boolean)

/**
 * User-initiated account lifecycle (v21): sign-out, account switching and account deletion. It
 * orchestrates the one account owner ([AccountRepository]), the one sync owner
 * ([SyncRepository]) and the one data eraser; it never talks to Firebase itself except through
 * the remote store boundary for the account-data deletion. One mutex: a double tap never runs
 * two deletions or two switches.
 */
@Singleton
class AccountLifecycle @Inject constructor(
    private val accounts: AccountRepository,
    private val sync: SyncRepository,
    private val eraser: PersonalDataEraser,
    private val remote: RemoteSyncStore,
    private val local: SyncLocalStore,
    private val scheduler: SyncScheduler,
) {
    private val mutex = Mutex()

    suspend fun signIn(host: AccountChooserHost): AccountResult = accounts.beginGoogleSignIn(host)

    suspend fun continueAsGuest() = accounts.continueAsGuest()

    /**
     * Stops synchronization and signs out. Local data stays (bound to the account, so changes
     * upload at the next sign-in with the same account) unless [removeLocalData] is chosen.
     */
    suspend fun signOut(removeLocalData: Boolean): SignOutOutcome = mutex.withLock {
        scheduler.cancel()
        val result = accounts.signOut()
        var erased = false
        if (removeLocalData) erased = eraser.eraseAll(keepAccountSignedIn = true) == EraseOutcome.COMPLETE
        SignOutOutcome(result !is AccountResult.Failure, erased)
    }

    /** The user's answer when the local data belongs to a different account. Nothing is merged across accounts. */
    suspend fun resolveAccountSwitch(choice: AccountSwitchChoice): SyncResult = mutex.withLock {
        val uid = accounts.accountState.value.activeUid ?: return@withLock SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN)
        when (choice) {
            AccountSwitchChoice.KEEP_SEPARATE -> {
                sync.postponeDecision()
                accounts.signOut()
                SyncResult.Success()
            }
            AccountSwitchChoice.DELETE_LOCAL -> {
                if (local.hasActiveSession()) return@withLock SyncResult.Skipped(SyncSkipReason.ACTIVE_SESSION)
                eraser.eraseAll(keepAccountSignedIn = true)
                sync.initialSync(uid)
            }
            AccountSwitchChoice.USE_NEW_ACCOUNT_DATA -> sync.adoptCloudData(uid)
        }
    }

    /**
     * Account deletion: explicit confirmation happens in the UI; here the user confirms with
     * Google again (recent sign-in), synchronization stops, the account's Firestore documents are
     * deleted (children before parents, verified), then the Firebase account. A deployed
     * `onUserDeleted` function additionally removes anything left under `/users/{uid}`. Local
     * data is removed only when [alsoDeleteLocal] is chosen; otherwise it stays as guest data.
     */
    suspend fun deleteAccount(host: AccountChooserHost, alsoDeleteLocal: Boolean): AccountDeletionResult = mutex.withLock {
        if (!accounts.configured) return@withLock AccountDeletionResult.Unavailable
        val uid = accounts.accountState.value.activeUid ?: return@withLock AccountDeletionResult.NotSignedIn
        when (val r = accounts.reauthenticate(host)) {
            is AccountResult.Success -> if (r.user.uid != uid) return@withLock AccountDeletionResult.Failed
            is AccountResult.Failure -> return@withLock when (r.state) {
                AccountState.SignInCanceled -> AccountDeletionResult.Canceled
                AccountState.NetworkUnavailable -> AccountDeletionResult.NetworkUnavailable
                else -> AccountDeletionResult.ReauthenticationRequired
            }
            AccountResult.Unavailable -> return@withLock AccountDeletionResult.Unavailable
        }
        scheduler.cancel()
        local.binding()?.takeIf { it.uid == uid }?.let { local.markDeletionPending(uid) }
        try {
            deleteRemoteData(uid)
        } catch (e: RemoteSyncException) {
            return@withLock if (e.failure == SyncFailure.OFFLINE) AccountDeletionResult.NetworkUnavailable else AccountDeletionResult.Incomplete
        }
        when (val d = accounts.deleteAccount()) {
            is AccountDeletionResult.Deleted -> Unit
            else -> return@withLock d
        }
        if (alsoDeleteLocal) {
            eraser.eraseAll(keepAccountSignedIn = true)
        } else {
            sync.unlinkAccount()
        }
        AccountDeletionResult.Deleted(localDataErased = alsoDeleteLocal)
    }

    /** Deletes every document the app writes below `/users/{uid}`; throws when any remains. */
    private suspend fun deleteRemoteData(uid: String) {
        val paths = ArrayList<RemotePath>()
        remote.list(uid, RemoteCollection.MISSIONS).forEach { mission ->
            val id = (mission.path as RemotePath.Doc).id
            remote.list(uid, RemoteCollection.STEPS, missionId = id).forEach { paths += it.path }
            paths += mission.path
        }
        listOf(RemoteCollection.SESSIONS, RemoteCollection.TOMBSTONES, RemoteCollection.SETTINGS, RemoteCollection.SYNC_META).forEach { c ->
            remote.list(uid, c).forEach { paths += it.path }
        }
        paths.chunked(RemoteSchema.MAX_BATCH_WRITES).forEach { chunk -> remote.commit(uid, chunk.map { RemoteWrite.Remove(it) }) }
        remote.commit(uid, listOf(RemoteWrite.Remove(RemotePath.Profile)))
        val left = listOf(RemoteCollection.MISSIONS, RemoteCollection.SESSIONS, RemoteCollection.TOMBSTONES, RemoteCollection.SETTINGS, RemoteCollection.SYNC_META)
            .sumOf { remote.count(uid, it) }
        if (left > 0) throw RemoteSyncException(SyncFailure.SERVER)
    }
}
