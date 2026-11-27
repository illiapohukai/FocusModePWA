package app.focusmodeplus.core.testing

import app.focusmodeplus.core.domain.account.AccountChooserHost
import app.focusmodeplus.core.domain.account.AccountRepository
import app.focusmodeplus.core.domain.sync.SyncScheduler
import app.focusmodeplus.core.model.AccountDeletionResult
import app.focusmodeplus.core.model.AccountResult
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountUser
import kotlinx.coroutines.flow.MutableStateFlow

/** Clearly fake test identities. Never real people, real addresses or real photos. */
object FakeUsers {
    val ALICE = AccountUser(uid = "aliceUid0001", displayName = "Test Nutzerin A", email = "nutzerin.a@example.test", photoUrl = null)
    val BOB = AccountUser(uid = "bobUid000002", displayName = "Test Nutzer B", email = "nutzer.b@example.test", photoUrl = null)
    val LONG = AccountUser(
        uid = "longUid00003",
        displayName = "Maximiliane Alexandra Testperson-Beispielname von Musterhausen",
        email = "sehr.lange.test.adresse.fuer.umbrueche.und.kuerzung@subdomain.example.test",
        photoUrl = null,
    )
}

/**
 * Fake account owner. No Google, no Firebase, no network: the next result of each call is set
 * by the test. Signing in emits [AccountState.SignedIn] exactly like the real owner.
 */
class FakeAccountRepository(
    override val configured: Boolean = true,
    initial: AccountState = if (configured) AccountState.SignInAvailable else AccountState.FirebaseUnconfigured,
) : AccountRepository {
    override val accountState = MutableStateFlow(initial)

    var nextSignIn: AccountResult = AccountResult.Success(FakeUsers.ALICE)
    var nextReauth: AccountResult? = null
    var nextRefresh: AccountResult? = null
    var nextDelete: AccountDeletionResult = AccountDeletionResult.Deleted(localDataErased = false)
    var signOuts = 0
    var signIns = 0
    var deletes = 0
    var refreshes = 0
    var guestChosen = false

    fun signInAs(user: AccountUser) { accountState.value = AccountState.SignedIn(user) }

    override suspend fun beginGoogleSignIn(host: AccountChooserHost): AccountResult {
        if (!configured) return AccountResult.Unavailable
        signIns++
        accountState.value = AccountState.SigningIn
        val r = nextSignIn
        accountState.value = when (r) {
            is AccountResult.Success -> AccountState.SignedIn(r.user)
            is AccountResult.Failure -> r.state
            AccountResult.Unavailable -> AccountState.FirebaseUnconfigured
        }
        return r
    }

    override suspend fun refreshAccount(): AccountResult {
        refreshes++
        val user = accountState.value.user ?: return AccountResult.Failure(accountState.value)
        val r = nextRefresh ?: return AccountResult.Success(user)
        if (r is AccountResult.Failure) accountState.value = r.state
        return r
    }

    override suspend fun signOut(): AccountResult {
        signOuts++
        accountState.value = if (configured) AccountState.Guest else AccountState.FirebaseUnconfigured
        return AccountResult.Success(FakeUsers.ALICE)
    }

    override suspend fun reauthenticate(host: AccountChooserHost): AccountResult {
        val user = accountState.value.user ?: return AccountResult.Failure(AccountState.SignInAvailable)
        return nextReauth ?: AccountResult.Success(user)
    }

    override suspend fun deleteAccount(): AccountDeletionResult {
        deletes++
        val r = nextDelete
        if (r is AccountDeletionResult.Deleted) accountState.value = AccountState.Guest
        return r
    }

    override suspend fun continueAsGuest() {
        guestChosen = true
        if (accountState.value == AccountState.SignInAvailable) accountState.value = AccountState.Guest
    }
}

/** Records scheduling instead of using WorkManager. */
class FakeSyncScheduler : SyncScheduler {
    val scheduled = mutableListOf<String>()
    var cancels = 0
    override fun schedule(uid: String, delayMs: Long) { scheduled += uid }
    override fun cancel() { cancels++ }
}

object FakeHost : AccountChooserHost {
    override fun platformHandle(): Any = this
}

/** A Session owner with nothing running (for data-deletion paths in tests outside :core:session). */
class IdleSessionController : app.focusmodeplus.core.domain.session.FocusSessionController {
    var abandoned = 0
    override val state = MutableStateFlow<app.focusmodeplus.core.domain.session.SessionState>(app.focusmodeplus.core.domain.session.SessionState.Idle())
    override suspend fun restore(trigger: app.focusmodeplus.core.domain.session.RestoreTrigger) = Unit
    override suspend fun startQuick(request: app.focusmodeplus.core.domain.session.QuickFocusRequest): app.focusmodeplus.core.domain.session.StartResult = error("unused")
    override suspend fun startMissionStep(missionId: app.focusmodeplus.core.model.MissionId, stepId: app.focusmodeplus.core.model.MissionStepId, mode: app.focusmodeplus.core.model.ProtectionMode): app.focusmodeplus.core.domain.session.StartResult = error("unused")
    override suspend fun endByUser(note: app.focusmodeplus.core.model.EndNote?) = app.focusmodeplus.core.domain.session.FinishResult.NoActiveSession
    override suspend fun cancel(note: app.focusmodeplus.core.model.EndNote?) = app.focusmodeplus.core.domain.session.FinishResult.NoActiveSession
    override suspend fun emergencyExit() = app.focusmodeplus.core.domain.session.FinishResult.NoActiveSession
    override suspend fun resumeRecovered() = false
    override suspend fun saveRecovered() = app.focusmodeplus.core.domain.session.FinishResult.NoActiveSession
    override suspend fun discardRecovered() = false
    override suspend fun submitMissionReview(review: app.focusmodeplus.core.model.MissionReview) = false
    override suspend fun abandonForDataDeletion() { abandoned++ }
    override suspend fun onRuntimeTick() = Unit
    override fun read(): app.focusmodeplus.core.domain.session.ElapsedReading? = null
}

class NoopReminderScheduler : app.focusmodeplus.core.domain.platform.ReminderScheduler {
    override suspend fun sync(reason: app.focusmodeplus.core.domain.platform.ReminderSyncReason) = Unit
    override suspend fun deliver(key: String) = Unit
}

/** Scriptable sync owner for ViewModel and UI tests (the real engine is tested in :core:data). */
class FakeSyncRepository(phase: app.focusmodeplus.core.model.SyncPhase = app.focusmodeplus.core.model.SyncPhase.SIGNED_OUT) :
    app.focusmodeplus.core.domain.sync.SyncRepository {
    override val syncState = MutableStateFlow(app.focusmodeplus.core.model.SyncState(phase = phase))
    override val decision = MutableStateFlow<app.focusmodeplus.core.model.AccountDataDecision?>(null)
    var preview = app.focusmodeplus.core.model.MigrationPreview(
        missions = 2, missionSteps = 7, sessions = 18,
        historyFrom = java.time.LocalDate.parse("2026-10-02"), historyTo = java.time.LocalDate.parse("2026-11-25"),
        focusMinutes = 512, remoteMissions = 0, remoteSessions = 0,
    )
    var nextResult: app.focusmodeplus.core.model.SyncResult = app.focusmodeplus.core.model.SyncResult.Success()
    /** Suspends every result until completed (duplicate-tap tests). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    private suspend fun result(call: String): app.focusmodeplus.core.model.SyncResult {
        calls += call
        gate?.await()
        return nextResult
    }

    override suspend fun prepareMigration(uid: String) = preview.also { calls += "prepare" }
    override suspend fun migrateLocalData(uid: String) = result("migrate:$uid")
    override suspend fun initialSync(uid: String) = result("initial:$uid")
    override suspend fun adoptCloudData(uid: String) = result("adopt:$uid")
    override suspend fun uploadPendingMutations() = result("upload")
    override suspend fun downloadRemoteChanges() = result("download")
    override suspend fun synchronize() = result("sync")
    override suspend fun unlinkAccount() = result("unlink")
    override fun postponeDecision() { calls += "postpone"; decision.value = null }
    override suspend fun retryParked() { calls += "retryParked" }
}
