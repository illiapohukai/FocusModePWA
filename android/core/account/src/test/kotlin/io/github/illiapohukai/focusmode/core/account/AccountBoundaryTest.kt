package io.github.illiapohukai.focusmode.core.account

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import io.github.illiapohukai.focusmode.core.domain.sync.RemoteSyncStore
import io.github.illiapohukai.focusmode.core.domain.sync.ServerTimestamp
import io.github.illiapohukai.focusmode.core.model.AccountDeletionResult
import io.github.illiapohukai.focusmode.core.model.AccountErrorKind
import io.github.illiapohukai.focusmode.core.model.AccountResult
import io.github.illiapohukai.focusmode.core.model.AccountState
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import io.github.illiapohukai.focusmode.core.testing.FakeHost
import io.github.illiapohukai.focusmode.core.testing.FakeUsers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/** The Firebase boundary: explicit failure states, safe value conversion, and an unconfigured build that never touches Firebase. */
@RunWith(AndroidJUnit4::class)
class AccountBoundaryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun signInFailuresMapToTheirOwnStates() {
        assertEquals(AccountState.SignInCanceled, AccountErrorMapper.signIn(GetCredentialCancellationException("x")))
        assertEquals(AccountState.SignInCanceled, AccountErrorMapper.signIn(GetCredentialInterruptedException("x")))
        assertEquals(AccountState.CredentialUnavailable, AccountErrorMapper.signIn(NoCredentialException("x")))
        assertEquals(AccountState.Error(AccountErrorKind.PROVIDER_UNAVAILABLE), AccountErrorMapper.signIn(GetCredentialProviderConfigurationException("x")))
        assertEquals(AccountState.Error(AccountErrorKind.INVALID_CREDENTIAL), AccountErrorMapper.signIn(UnexpectedCredentialException()))
        assertEquals(AccountState.Error(AccountErrorKind.INVALID_CREDENTIAL), AccountErrorMapper.signIn(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "x")))
        assertEquals(AccountState.NetworkUnavailable, AccountErrorMapper.signIn(FirebaseNetworkException("x")))
        assertEquals(AccountState.AccountDisabled, AccountErrorMapper.signIn(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x")))
        assertEquals(AccountState.Error(AccountErrorKind.TOO_MANY_REQUESTS), AccountErrorMapper.signIn(FirebaseTooManyRequestsException("x")))
        assertEquals(AccountState.Error(AccountErrorKind.UNKNOWN), AccountErrorMapper.signIn(GetCredentialUnknownException("x")))
        assertEquals(AccountState.Error(AccountErrorKind.UNKNOWN), AccountErrorMapper.signIn(IllegalStateException("token=secret")))
    }

    @Test fun refreshDistinguishesDisabledDeletedRevokedAndOffline() {
        val u = FakeUsers.ALICE
        assertEquals(AccountState.AccountDisabled, AccountErrorMapper.refresh(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x"), u))
        assertEquals(AccountState.AccountDeletedRemotely, AccountErrorMapper.refresh(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x"), u))
        assertEquals(AccountState.ReauthenticationRequired(u), AccountErrorMapper.refresh(FirebaseAuthInvalidUserException("ERROR_USER_TOKEN_EXPIRED", "x"), u))
        assertEquals(AccountState.ReauthenticationRequired(u), AccountErrorMapper.refresh(FirebaseAuthInvalidUserException("ERROR_INVALID_USER_TOKEN", "x"), u))
        assertEquals(AccountState.NetworkUnavailable, AccountErrorMapper.refresh(FirebaseNetworkException("x"), u))
        assertEquals(AccountState.Error(AccountErrorKind.ACCOUNT_MISMATCH), AccountErrorMapper.reauth(FirebaseAuthInvalidUserException("ERROR_USER_MISMATCH", "x")))
    }

    @Test fun firestoreCodesBecomePrivacySafeFailureClasses() {
        assertEquals(SyncFailure.OFFLINE, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.UNAVAILABLE))
        assertEquals(SyncFailure.OFFLINE, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))
        assertEquals(SyncFailure.PERMISSION_DENIED, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(SyncFailure.UNAUTHENTICATED, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.UNAUTHENTICATED))
        assertEquals(SyncFailure.QUOTA, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))
        assertEquals(SyncFailure.INVALID_DATA, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.INVALID_ARGUMENT))
        assertEquals(SyncFailure.SERVER, AccountErrorMapper.firestore(FirebaseFirestoreException.Code.INTERNAL))
    }

    @Test fun valuesConvertAtTheFirestoreBoundary() {
        val out = FirestoreValues.toFirestore(mapOf("a" to 5, "t" to ServerTimestamp, "l" to listOf(1, "x"), "m" to mapOf("n" to 2), "z" to null))
        assertEquals(5L, out["a"])
        assertEquals(FieldValue.serverTimestamp(), out["t"])
        assertEquals(listOf(1L, "x"), out["l"])
        assertEquals(mapOf("n" to 2L), out["m"])
        assertNull(out["z"])
        val back = FirestoreValues.fromFirestore(mapOf("serverUpdatedAt" to Timestamp(1_800_000_000L, 123_000_000), "n" to 3L))
        assertEquals(1_800_000_000_123L, back["serverUpdatedAt"])
        assertEquals(1_800_000_000_123L, FirestoreValues.timestamp(1_800_000_000_123L).let { it.seconds * 1000 + it.nanoseconds / 1_000_000 })
    }

    @Test fun anUnconfiguredBuildNeverInstantiatesFirebase() = runBlocking {
        val runtime = FirebaseRuntime(context, FirebaseBuildConfig.UNCONFIGURED, AppCheckProviderSource { error("App Check must not be touched") })
        assertFalse(runtime.available)
        val never = Provider<FirebaseAccountRepository> { error("Firebase must not be created") }
        val neverStore = Provider<FirestoreRemoteSyncStore> { error("Firestore must not be created") }
        val accounts = AccountProvidesModule.accountRepository(runtime, never)
        assertSame(UnconfiguredAccountRepository, accounts)
        assertSame(RemoteSyncStore.Unavailable, AccountProvidesModule.remoteSyncStore(runtime, neverStore))
        assertFalse(accounts.configured)
        assertEquals(AccountState.FirebaseUnconfigured, accounts.accountState.value)
        assertEquals(AccountResult.Unavailable, accounts.beginGoogleSignIn(FakeHost))
        assertEquals(AccountDeletionResult.Unavailable, accounts.deleteAccount())
        // A google-services.json without a web client id is not usable for Sign in with Google either.
        assertFalse(FirebaseBuildConfig(configured = true, serverClientId = null).usable)
        assertFalse(FirebaseRuntime(context, FirebaseBuildConfig(true, " "), AppCheckProviderSource { null }).available)
        assertTrue(FirebaseBuildConfig(true, "000-web.apps.googleusercontent.com").usable)
    }
}
