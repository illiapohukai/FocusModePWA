package app.focusmodeplus.core.account

import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import app.focusmodeplus.core.domain.account.AccountChooserHost
import app.focusmodeplus.core.domain.account.AccountRepository
import app.focusmodeplus.core.model.AccountDeletionResult
import app.focusmodeplus.core.model.AccountErrorKind
import app.focusmodeplus.core.model.AccountResult
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two small, non-sensitive flags (never backed up: the backup rules include only the database and
 * the preferences DataStore). After an explicit sign-out nothing signs in again automatically,
 * and a guest who chose "Ohne Konto fortfahren" is not asked again.
 */
@Singleton
class AccountLocalMeta @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("focus_account_meta", Context.MODE_PRIVATE)
    val guestChosen: Boolean get() = prefs.getBoolean(KEY_GUEST, false)
    val explicitSignOut: Boolean get() = prefs.getBoolean(KEY_SIGNED_OUT, false)
    fun setGuestChosen(v: Boolean) = prefs.edit { putBoolean(KEY_GUEST, v) }
    fun setExplicitSignOut(v: Boolean) = prefs.edit { putBoolean(KEY_SIGNED_OUT, v) }

    private companion object {
        const val KEY_GUEST = "guest_chosen"
        const val KEY_SIGNED_OUT = "explicit_sign_out"
    }
}

/**
 * The one account owner on configured builds: Sign in with Google through Android Credential
 * Manager (`GetSignInWithGoogleOption`, the explicit button flow that always shows the account
 * chooser), authenticated with Firebase Authentication. The legacy Google Sign-In API is not
 * used. The ID token lives only for the duration of one call; nothing here logs, stores or
 * returns a token, an email or an exception message.
 */
@Singleton
class FirebaseAccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: FirebaseRuntime,
    private val meta: AccountLocalMeta,
) : AccountRepository {
    override val configured: Boolean = true

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(checkNotNull(runtime.app)) }
    private val mutex = Mutex()
    private val state = MutableStateFlow(auth.currentUser?.toAccountUser()?.let { AccountState.SignedIn(it) } ?: resting())
    override val accountState: StateFlow<AccountState> = state.asStateFlow()

    init {
        auth.addAuthStateListener { a ->
            val user = a.currentUser?.toAccountUser()
            val current = state.value
            state.value = when {
                user == null && current.user != null -> resting() // signed out elsewhere (or deleted)
                user != null && current.user?.uid != user.uid && current !is AccountState.SigningIn -> AccountState.SignedIn(user)
                else -> current
            }
        }
    }

    private fun resting(): AccountState = if (meta.guestChosen || meta.explicitSignOut) AccountState.Guest else AccountState.SignInAvailable

    override suspend fun beginGoogleSignIn(host: AccountChooserHost): AccountResult = mutex.withLock {
        val activity = host.platformHandle() as? Context ?: return@withLock fail(AccountState.Error(AccountErrorKind.PROVIDER_UNAVAILABLE))
        state.value = AccountState.SigningIn
        try {
            val idToken = googleIdToken(activity)
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            val user = result.user?.toAccountUser() ?: return@withLock fail(AccountState.Error(AccountErrorKind.UNKNOWN))
            meta.setExplicitSignOut(false)
            state.value = AccountState.SignedIn(user)
            AccountResult.Success(user)
        } catch (e: CancellationException) {
            state.value = auth.currentUser?.toAccountUser()?.let { AccountState.SignedIn(it) } ?: resting()
            throw e
        } catch (e: Exception) {
            fail(AccountErrorMapper.signIn(e))
        }
    }

    private fun fail(s: AccountState): AccountResult.Failure {
        state.value = s
        return AccountResult.Failure(s)
    }

    /** One Credential Manager request; the token is used immediately and never stored. */
    private suspend fun googleIdToken(activity: Context): String {
        val clientId = runtime.serverClientId ?: throw UnexpectedCredentialException()
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw UnexpectedCredentialException()
    }

    override suspend fun refreshAccount(): AccountResult = mutex.withLock {
        val firebaseUser = auth.currentUser ?: return@withLock AccountResult.Failure(state.value)
        val user = firebaseUser.toAccountUser()
        state.value = AccountState.Refreshing(user)
        try {
            firebaseUser.reload().await()
            firebaseUser.getIdToken(true).await()
            val fresh = (auth.currentUser ?: firebaseUser).toAccountUser()
            state.value = AccountState.SignedIn(fresh)
            AccountResult.Success(fresh)
        } catch (e: CancellationException) {
            state.value = AccountState.SignedIn(user)
            throw e
        } catch (e: Exception) {
            when (val s = AccountErrorMapper.refresh(e, user)) {
                AccountState.AccountDisabled, AccountState.AccountDeletedRemotely -> {
                    auth.signOut() // local only; data on this device stays
                    state.value = s
                    AccountResult.Failure(s)
                }
                AccountState.NetworkUnavailable -> {
                    state.value = AccountState.SignedIn(user) // offline is not a sign-out
                    AccountResult.Failure(s)
                }
                is AccountState.SignedIn -> { state.value = s; AccountResult.Success(user) }
                else -> { state.value = s; AccountResult.Failure(s) }
            }
        }
    }

    override suspend fun signOut(): AccountResult = mutex.withLock {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        meta.setExplicitSignOut(true)
        state.value = AccountState.Guest
        AccountResult.Success(AccountUser("", null, null, null))
    }

    override suspend fun reauthenticate(host: AccountChooserHost): AccountResult = mutex.withLock {
        val firebaseUser = auth.currentUser ?: return@withLock AccountResult.Failure(AccountState.SignInAvailable)
        val activity = host.platformHandle() as? Context ?: return@withLock AccountResult.Failure(AccountState.Error(AccountErrorKind.PROVIDER_UNAVAILABLE))
        try {
            val idToken = googleIdToken(activity)
            firebaseUser.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
            AccountResult.Success(firebaseUser.toAccountUser())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AccountResult.Failure(AccountErrorMapper.reauth(e)) // the signed-in state stays as it is
        }
    }

    override suspend fun deleteAccount(): AccountDeletionResult = mutex.withLock {
        val firebaseUser = auth.currentUser ?: return@withLock AccountDeletionResult.NotSignedIn
        try {
            firebaseUser.delete().await()
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            meta.setExplicitSignOut(true)
            state.value = AccountState.Guest
            AccountDeletionResult.Deleted(localDataErased = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            AccountDeletionResult.ReauthenticationRequired
        } catch (_: FirebaseNetworkException) {
            AccountDeletionResult.NetworkUnavailable
        } catch (_: Exception) {
            AccountDeletionResult.Failed
        }
    }

    override suspend fun continueAsGuest() {
        meta.setGuestChosen(true)
        if (state.value == AccountState.SignInAvailable) state.value = AccountState.Guest
    }

    private fun FirebaseUser.toAccountUser() = AccountUser(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() },
        email = email?.takeIf { it.isNotBlank() },
        // Only an HTTPS Google profile image is ever displayed.
        photoUrl = photoUrl?.toString()?.takeIf { it.startsWith("https://") },
    )
}

/** Unconfigured builds: guest mode only. No Firebase class is loaded, no network call is made. */
object UnconfiguredAccountRepository : AccountRepository {
    override val configured = false
    override val accountState: StateFlow<AccountState> = MutableStateFlow(AccountState.FirebaseUnconfigured)
    override suspend fun beginGoogleSignIn(host: AccountChooserHost) = AccountResult.Unavailable
    override suspend fun refreshAccount() = AccountResult.Unavailable
    override suspend fun signOut() = AccountResult.Unavailable
    override suspend fun reauthenticate(host: AccountChooserHost) = AccountResult.Unavailable
    override suspend fun deleteAccount() = AccountDeletionResult.Unavailable
    override suspend fun continueAsGuest() = Unit
}
