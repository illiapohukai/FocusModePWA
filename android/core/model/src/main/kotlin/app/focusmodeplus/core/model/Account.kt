package app.focusmodeplus.core.model

/**
 * A signed-in Google account as the app sees it (v21). [uid] is the Firebase UID, the only owner
 * key of account data; the email is display-only and never used as a database key. Tokens are
 * never part of this model.
 */
data class AccountUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

/** Recoverable failure classes that do not have a state of their own. */
enum class AccountErrorKind {
    /** Google returned a credential Firebase did not accept (malformed or rejected ID token). */
    INVALID_CREDENTIAL,
    /** Google sign-in is not usable on this device (no Play services provider, misconfigured build). */
    PROVIDER_UNAVAILABLE,
    /** Reauthentication picked a different Google account than the signed-in one. */
    ACCOUNT_MISMATCH,
    /** Too many attempts; Firebase asked to wait. */
    TOO_MANY_REQUESTS,
    UNKNOWN,
}

/**
 * Explicit, immutable account states (v21). Guest mode is local application use; it is not
 * Firebase anonymous authentication. Every recoverable state offers a next action in the UI.
 */
sealed interface AccountState {
    /** "Mit Google anmelden" is possible from this state. */
    val canSignIn: Boolean get() = false
    /** The account behind the state, when one is known. */
    val user: AccountUser? get() = null

    /** This build has no Firebase configuration: guest mode only, no network calls for accounts. */
    data object FirebaseUnconfigured : AccountState

    /** Local guest mode after an explicit choice ("Ohne Konto fortfahren" or sign-out). */
    data object Guest : AccountState { override val canSignIn: Boolean get() = true }

    /** Not signed in and no choice made yet: the account benefits may be shown once, in Settings. */
    data object SignInAvailable : AccountState { override val canSignIn: Boolean get() = true }

    /** The system account chooser or Firebase sign-in is running. */
    data object SigningIn : AccountState

    data class SignedIn(override val user: AccountUser) : AccountState

    /** The signed-in account is being re-checked with Firebase (token refresh, reload). */
    data class Refreshing(override val user: AccountUser) : AccountState

    /** The credential expired or was revoked; synchronization pauses until the user signs in again. */
    data class ReauthenticationRequired(override val user: AccountUser) : AccountState {
        override val canSignIn: Boolean get() = true
    }

    /** A sign-in attempt failed because there was no connection. */
    data object NetworkUnavailable : AccountState { override val canSignIn: Boolean get() = true }

    /** No Google account is available on the device for "Sign in with Google". */
    data object CredentialUnavailable : AccountState { override val canSignIn: Boolean get() = true }

    /** The user closed the account chooser. Nothing changed. */
    data object SignInCanceled : AccountState { override val canSignIn: Boolean get() = true }

    /** Firebase reports the account as disabled. The app continues locally. */
    data object AccountDisabled : AccountState { override val canSignIn: Boolean get() = true }

    /** The account no longer exists (deleted elsewhere). Local data stays on this device. */
    data object AccountDeletedRemotely : AccountState { override val canSignIn: Boolean get() = true }

    /** Cloud Firestore refused access for the signed-in account (rules or App Check enforcement). */
    data class PermissionDenied(override val user: AccountUser) : AccountState

    data class Error(val kind: AccountErrorKind) : AccountState { override val canSignIn: Boolean get() = true }
}

/** The uid that may synchronize right now: only a signed-in (or refreshing) account. */
val AccountState.activeUid: String?
    get() = when (this) {
        is AccountState.SignedIn -> user.uid
        is AccountState.Refreshing -> user.uid
        else -> null
    }

sealed interface AccountResult {
    data class Success(val user: AccountUser) : AccountResult
    /** The attempt ended in [state] (canceled, no credential, offline, …). */
    data class Failure(val state: AccountState) : AccountResult
    /** Firebase is not configured in this build. */
    data object Unavailable : AccountResult
}

sealed interface AccountDeletionResult {
    /** Remote account data and the Firebase account were deleted. */
    data class Deleted(val localDataErased: Boolean) : AccountDeletionResult
    /** Firebase requires a recent sign-in; the user must confirm with Google again. */
    data object ReauthenticationRequired : AccountDeletionResult
    data object Canceled : AccountDeletionResult
    data object NetworkUnavailable : AccountDeletionResult
    /** Some remote documents could not be deleted; the deletion stays pending and can be retried. */
    data object Incomplete : AccountDeletionResult
    data object NotSignedIn : AccountDeletionResult
    data object Unavailable : AccountDeletionResult
    data object Failed : AccountDeletionResult
}
