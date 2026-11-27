package app.focusmodeplus.core.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestoreException
import app.focusmodeplus.core.model.AccountErrorKind
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountUser
import app.focusmodeplus.core.model.SyncFailure

/** The account chooser returned something that is not a Google ID token credential. */
class UnexpectedCredentialException : Exception("unexpected_credential")

/**
 * Maps Credential Manager, Google ID and Firebase exceptions to explicit account states and
 * privacy-safe failure classes. Messages, tokens and stack traces never leave this mapping.
 */
object AccountErrorMapper {

    fun signIn(e: Throwable): AccountState = when (e) {
        is GetCredentialCancellationException -> AccountState.SignInCanceled
        is GetCredentialInterruptedException -> AccountState.SignInCanceled
        is NoCredentialException -> AccountState.CredentialUnavailable
        is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException -> AccountState.Error(AccountErrorKind.PROVIDER_UNAVAILABLE)
        is GoogleIdTokenParsingException, is UnexpectedCredentialException -> AccountState.Error(AccountErrorKind.INVALID_CREDENTIAL)
        is FirebaseNetworkException -> AccountState.NetworkUnavailable
        is FirebaseAuthInvalidUserException -> userState(e.errorCode)
        is FirebaseAuthInvalidCredentialsException -> AccountState.Error(AccountErrorKind.INVALID_CREDENTIAL)
        is FirebaseTooManyRequestsException -> AccountState.Error(AccountErrorKind.TOO_MANY_REQUESTS)
        else -> AccountState.Error(AccountErrorKind.UNKNOWN)
    }

    /** Reauthentication: the same account must be confirmed again. */
    fun reauth(e: Throwable): AccountState = when {
        e is FirebaseAuthInvalidUserException && e.errorCode == "ERROR_USER_MISMATCH" -> AccountState.Error(AccountErrorKind.ACCOUNT_MISMATCH)
        else -> signIn(e)
    }

    /** A refresh of a signed-in account: disabled, deleted and revoked credentials have states of their own. */
    fun refresh(e: Throwable, user: AccountUser): AccountState = when (e) {
        is FirebaseAuthInvalidUserException -> when (e.errorCode) {
            "ERROR_USER_DISABLED" -> AccountState.AccountDisabled
            "ERROR_USER_NOT_FOUND" -> AccountState.AccountDeletedRemotely
            else -> AccountState.ReauthenticationRequired(user) // ERROR_USER_TOKEN_EXPIRED, ERROR_INVALID_USER_TOKEN
        }
        is FirebaseAuthRecentLoginRequiredException -> AccountState.ReauthenticationRequired(user)
        is FirebaseNetworkException -> AccountState.NetworkUnavailable
        else -> AccountState.SignedIn(user)
    }

    private fun userState(code: String): AccountState = when (code) {
        "ERROR_USER_DISABLED" -> AccountState.AccountDisabled
        "ERROR_USER_NOT_FOUND" -> AccountState.AccountDeletedRemotely
        else -> AccountState.Error(AccountErrorKind.INVALID_CREDENTIAL)
    }

    fun firestore(code: FirebaseFirestoreException.Code): SyncFailure = when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED, FirebaseFirestoreException.Code.CANCELLED -> SyncFailure.OFFLINE
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> SyncFailure.UNAUTHENTICATED
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> SyncFailure.PERMISSION_DENIED
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> SyncFailure.QUOTA
        FirebaseFirestoreException.Code.INVALID_ARGUMENT, FirebaseFirestoreException.Code.FAILED_PRECONDITION, FirebaseFirestoreException.Code.OUT_OF_RANGE -> SyncFailure.INVALID_DATA
        else -> SyncFailure.SERVER
    }
}
