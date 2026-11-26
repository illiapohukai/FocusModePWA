package io.github.illiapohukai.focusmode.core.domain.account

import io.github.illiapohukai.focusmode.core.model.AccountDeletionResult
import io.github.illiapohukai.focusmode.core.model.AccountResult
import io.github.illiapohukai.focusmode.core.model.AccountState
import kotlinx.coroutines.flow.StateFlow

/**
 * Opaque handle to the foreground UI that may show the system account chooser (on Android: the
 * current Activity). Passed per call and never stored; the domain never touches it.
 */
fun interface AccountChooserHost {
    fun platformHandle(): Any
}

/**
 * The one account owner (v21): Sign in with Google through Android Credential Manager,
 * authenticated with Firebase Authentication. Google is the only provider; guest mode is local
 * use, not Firebase anonymous authentication. Implementations never expose tokens, raw
 * exceptions or project identifiers; Composables and ViewModels never call Firebase directly.
 */
interface AccountRepository {
    /** False when this build carries no Firebase configuration (guest mode only). */
    val configured: Boolean

    val accountState: StateFlow<AccountState>

    /** Shows the Google account chooser ("Mit Google anmelden") and signs in with Firebase. */
    suspend fun beginGoogleSignIn(host: AccountChooserHost): AccountResult

    /** Re-checks the signed-in account with Firebase (disabled, deleted, revoked credentials). */
    suspend fun refreshAccount(): AccountResult

    /** Signs out of Firebase and clears the Credential Manager state, so nothing signs in again automatically. */
    suspend fun signOut(): AccountResult

    /** Confirms the signed-in account with Google again (needed before account deletion). */
    suspend fun reauthenticate(host: AccountChooserHost): AccountResult

    /** Deletes the Firebase Authentication account of the signed-in user (data deletion happens before). */
    suspend fun deleteAccount(): AccountDeletionResult

    /** "Ohne Konto fortfahren": remembers the choice so guests are not asked again. */
    suspend fun continueAsGuest()
}

/**
 * Hook the full local-data deletion calls first: synchronization stops, the account is signed
 * out and the account binding goes with the local data, so a later sync can never re-download
 * or re-upload anything behind the user's back. Cloud data is not touched.
 */
interface LocalEraseAccountHook {
    /** [signOut]: also sign out of the account (plain local erase); false when the caller continues with an account. */
    suspend fun beforeLocalErase(signOut: Boolean)

    object None : LocalEraseAccountHook {
        override suspend fun beforeLocalErase(signOut: Boolean) = Unit
    }
}
