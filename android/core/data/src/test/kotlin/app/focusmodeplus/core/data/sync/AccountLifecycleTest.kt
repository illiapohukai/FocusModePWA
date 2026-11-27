package app.focusmodeplus.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.domain.sync.RemoteCollection
import app.focusmodeplus.core.model.AccountDataDecision
import app.focusmodeplus.core.model.AccountDeletionResult
import app.focusmodeplus.core.model.AccountResult
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountSwitchChoice
import app.focusmodeplus.core.model.BindingStatus
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.SyncPhase
import app.focusmodeplus.core.model.SyncResult
import app.focusmodeplus.core.model.SyncSkipReason
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeHost
import app.focusmodeplus.core.testing.FakeRemoteSyncStore
import app.focusmodeplus.core.testing.FakeUsers
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Sign-out, account switching and account deletion: no data ever crosses from one account to another. */
@RunWith(AndroidJUnit4::class)
class AccountLifecycleTest {
    private val remote = FakeRemoteSyncStore()
    private val clock = FakeClock(wall = Instant.parse("2026-11-26T09:00:00Z"))
    private lateinit var dev: SyncTestDevice
    private val others = mutableListOf<SyncTestDevice>()
    private val alice = FakeUsers.ALICE.uid
    private val bob = FakeUsers.BOB.uid

    @Before fun setUp() { dev = SyncTestDevice(remote, clock) }
    @After fun tearDown() { dev.close(); others.forEach { it.close() } }

    /** Bob's cloud data, written by another device of Bob's. */
    private suspend fun bobHasCloudData() {
        val other = SyncTestDevice(remote, clock).also { others += it }
        other.missions.create(Fixtures.mission("mission_bob", title = "Bobs Projekt", steps = 2))
        other.sessions.finalize(SyncTestDevice.quick("s_bob", "2026-11-22", 30))
        other.accounts.signInAs(FakeUsers.BOB)
        assertTrue(other.engine.migrateLocalData(bob) is SyncResult.Success)
    }

    private suspend fun aliceLinked() {
        dev.missions.create(Fixtures.mission("mission_alice", title = "Alices Klausur", steps = 3))
        dev.sessions.finalize(SyncTestDevice.quick("s_alice", "2026-11-21", 25))
        dev.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(dev.engine.migrateLocalData(alice) is SyncResult.Success)
    }

    private suspend fun localMissionIds() = dev.missions.observeMissions().first().map { it.id.value }.toSet()

    @Test fun signOutKeepsLocalAndCloudDataAndStopsSync() = runBlocking {
        aliceLinked()
        dev.missions.rename(MissionId("mission_alice"), "Offline weiter") // pending change
        val cancelsBefore = dev.scheduler.cancels
        val out = dev.lifecycle().signOut(removeLocalData = false)
        assertTrue(out.signedOut)
        assertEquals(AccountState.Guest, dev.accounts.accountState.value)
        assertTrue(dev.scheduler.cancels > cancelsBefore)
        assertEquals(1, dev.count("missions"))
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size) // cloud data is not deleted
        assertEquals(alice, dev.store.binding()!!.uid) // changes upload at the next sign-in with Alice
        assertEquals(SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN), dev.engine.synchronize())
        // Same account again: the pending change uploads.
        dev.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(dev.engine.synchronize() is SyncResult.Success)
        assertEquals("Offline weiter", remote.collection(alice, RemoteCollection.MISSIONS).values.single()["title"])
    }

    @Test fun signOutWithRemovalThenAnotherAccountShowsOnlyThatAccount() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        val out = dev.lifecycle().signOut(removeLocalData = true)
        assertTrue(out.localDataErased)
        assertNull(dev.store.binding())
        assertEquals(0, dev.count("missions"))
        dev.accounts.signInAs(FakeUsers.BOB)
        assertTrue(dev.engine.initialSync(bob) is SyncResult.Success)
        assertEquals(setOf("mission_bob"), localMissionIds())
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size) // Alice's cloud data untouched
    }

    @Test fun anotherAccountIsAskedAndNothingIsReassigned() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        dev.lifecycle().signOut(removeLocalData = false)
        dev.missions.rename(MissionId("mission_alice"), "Noch nicht synchronisiert")
        dev.engine.start(dev.scope)
        dev.accounts.signInAs(FakeUsers.BOB)
        val decision = withTimeout(10_000) { dev.engine.decision.first { it != null } } as AccountDataDecision.AccountSwitch
        assertEquals(1, decision.pendingChangesOfPreviousAccount)
        assertEquals(SyncPhase.ACCOUNT_MISMATCH, withTimeout(10_000) { dev.engine.syncState.first { it.phase == SyncPhase.ACCOUNT_MISMATCH } }.phase)
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), dev.engine.synchronize())
        // Bob's cloud holds only Bob's Mission; Alice's data was not attached to Bob.
        assertEquals(setOf("users/$bob/missions/mission_bob"), remote.collection(bob, RemoteCollection.MISSIONS).keys)

        // "Getrennt halten": Bob is signed out again; the data stays with Alice.
        assertTrue(dev.lifecycle().resolveAccountSwitch(AccountSwitchChoice.KEEP_SEPARATE) is SyncResult.Success)
        assertEquals(AccountState.Guest, dev.accounts.accountState.value)
        assertEquals(alice, dev.store.binding()!!.uid)
        assertEquals(setOf("mission_alice"), localMissionIds())
    }

    @Test fun usingTheNewAccountsDataReplacesLocalDataWithoutLeakage() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        dev.lifecycle().signOut(removeLocalData = false)
        dev.accounts.signInAs(FakeUsers.BOB)
        assertTrue(dev.lifecycle().resolveAccountSwitch(AccountSwitchChoice.USE_NEW_ACCOUNT_DATA) is SyncResult.Success)
        assertEquals(setOf("mission_bob"), localMissionIds())
        assertEquals(setOf("s_bob"), dev.sessions.observeResults().first().map { it.sessionId.value }.toSet())
        val binding = dev.store.binding()!!
        assertEquals(bob, binding.uid)
        assertEquals(BindingStatus.ACTIVE, binding.status)
        assertEquals(0, dev.count("sync_outbox"))
        // Nothing of Alice reached Bob's cloud, and Alice's cloud is unchanged.
        assertTrue(remote.allFor(bob).keys.none { it.contains("alice") })
        assertEquals(setOf("users/$alice/missions/mission_alice"), remote.collection(alice, RemoteCollection.MISSIONS).keys)
    }

    @Test fun deletingLocalDataOnSwitchContinuesWithTheNewAccount() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        dev.lifecycle().signOut(removeLocalData = false)
        dev.accounts.signInAs(FakeUsers.BOB)
        assertTrue(dev.lifecycle().resolveAccountSwitch(AccountSwitchChoice.DELETE_LOCAL) is SyncResult.Success)
        assertEquals(setOf("mission_bob"), localMissionIds())
        assertEquals(bob, dev.store.binding()!!.uid)
        assertTrue(dev.accounts.accountState.value is AccountState.SignedIn)
    }

    @Test fun replacingDataIsRefusedWhileASessionRuns() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        val m = dev.missions.getMission(MissionId("mission_alice"))!!
        dev.sessions.save(app.focusmodeplus.core.domain.session.SessionFactory(clock).mission(
            app.focusmodeplus.core.model.MissionLink(m.id, m.steps.first().id), "Schritt", "Aufgabe", 25, m.subject, 1, 3,
            app.focusmodeplus.core.model.ProtectionMode.STANDARD,
        ))
        dev.accounts.signInAs(FakeUsers.BOB)
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACTIVE_SESSION), dev.lifecycle().resolveAccountSwitch(AccountSwitchChoice.USE_NEW_ACCOUNT_DATA))
        assertEquals(setOf("mission_alice"), localMissionIds())
    }

    @Test fun aRemotelyDeletedAccountKeepsLocalDataAndCanBeUnlinked() = runBlocking {
        aliceLinked()
        dev.accounts.nextRefresh = AccountResult.Failure(AccountState.AccountDeletedRemotely)
        dev.accounts.refreshAccount()
        assertEquals(AccountState.AccountDeletedRemotely, dev.accounts.accountState.value)
        assertEquals(SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN), dev.engine.synchronize())
        assertEquals(1, dev.count("missions"))
        assertTrue(dev.engine.unlinkAccount() is SyncResult.Success)
        assertNull(dev.store.binding())
        assertEquals(0, dev.count("sync_outbox"))
        assertEquals(1, dev.count("missions")) // stays as guest data
    }

    @Test fun revokedCredentialsPauseSyncUntilReauthentication() = runBlocking {
        aliceLinked()
        dev.accounts.nextRefresh = AccountResult.Failure(AccountState.ReauthenticationRequired(FakeUsers.ALICE))
        dev.accounts.refreshAccount()
        dev.missions.rename(MissionId("mission_alice"), "Wartet")
        assertEquals(SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN), dev.engine.synchronize())
        dev.accounts.nextRefresh = null
        dev.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(dev.engine.synchronize() is SyncResult.Success)
        assertEquals("Wartet", remote.collection(alice, RemoteCollection.MISSIONS).values.single()["title"])
    }

    @Test fun accountDeletionRemovesOnlyTheOwnersCloudData() = runBlocking {
        aliceLinked()
        bobHasCloudData()
        val result = dev.lifecycle().deleteAccount(FakeHost, alsoDeleteLocal = false)
        assertEquals(AccountDeletionResult.Deleted(localDataErased = false), result)
        assertTrue(remote.allFor(alice).isEmpty())
        assertTrue(remote.allFor(bob).isNotEmpty())
        assertEquals(1, dev.accounts.deletes)
        assertNull(dev.store.binding())
        assertEquals(1, dev.count("missions")) // local data stays as guest data unless chosen otherwise
    }

    @Test fun accountDeletionCanAlsoEraseLocalData() = runBlocking {
        aliceLinked()
        assertEquals(AccountDeletionResult.Deleted(localDataErased = true), dev.lifecycle().deleteAccount(FakeHost, alsoDeleteLocal = true))
        assertEquals(0, dev.count("missions"))
        assertEquals(0, dev.count("session_results"))
        assertTrue(remote.allFor(alice).isEmpty())
    }

    @Test fun accountDeletionNeedsARecentSignInAndChangesNothingWhenCanceled() = runBlocking {
        aliceLinked()
        dev.accounts.nextReauth = AccountResult.Failure(AccountState.SignInCanceled)
        assertEquals(AccountDeletionResult.Canceled, dev.lifecycle().deleteAccount(FakeHost, alsoDeleteLocal = true))
        assertTrue(remote.allFor(alice).isNotEmpty())
        assertEquals(0, dev.accounts.deletes)
        assertEquals(BindingStatus.ACTIVE, dev.store.binding()!!.status)
        assertEquals(1, dev.count("missions"))
    }

    @Test fun interruptedAccountDeletionStaysPendingAndCanBeRetried() = runBlocking {
        aliceLinked()
        remote.offline = true
        assertEquals(AccountDeletionResult.NetworkUnavailable, dev.lifecycle().deleteAccount(FakeHost, alsoDeleteLocal = false))
        assertEquals(BindingStatus.DELETION_PENDING, dev.store.binding()!!.status)
        // Nothing uploads while the deletion is pending.
        dev.missions.create(Fixtures.mission("mission_late", steps = 1))
        remote.offline = false
        assertEquals(SyncResult.Skipped(SyncSkipReason.DELETION_PENDING), dev.engine.synchronize())
        assertEquals(AccountDeletionResult.Deleted(false), dev.lifecycle().deleteAccount(FakeHost, alsoDeleteLocal = false))
        assertTrue(remote.allFor(alice).isEmpty())
    }

    @Test fun fullLocalEraseSignsOutAndNeverRedownloads() = runBlocking {
        aliceLinked()
        dev.eraser().eraseAll()
        assertEquals(AccountState.Guest, dev.accounts.accountState.value)
        assertNull(dev.store.binding())
        assertEquals(0, dev.count("missions"))
        assertEquals(0, dev.count("sync_outbox"))
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size) // the account's cloud copy stays
        assertNotNull(remote.collection(alice, RemoteCollection.SESSIONS).values.firstOrNull())
        assertTrue(dev.missions.create(Fixtures.mission("mission_after", steps = 1)) is MissionWriteResult.Ok)
        assertEquals(0, dev.count("sync_outbox")) // guest again: nothing is recorded
    }
}
