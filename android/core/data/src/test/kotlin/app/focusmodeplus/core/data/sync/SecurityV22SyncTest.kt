package app.focusmodeplus.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.domain.sync.RemoteCodec
import app.focusmodeplus.core.domain.sync.RemoteCollection
import app.focusmodeplus.core.domain.sync.RemoteDocument
import app.focusmodeplus.core.domain.sync.RemotePath
import app.focusmodeplus.core.domain.sync.SyncEngine
import app.focusmodeplus.core.model.AccountDataDecision
import app.focusmodeplus.core.model.BindingStatus
import app.focusmodeplus.core.model.MigrationStatus
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.SyncResult
import app.focusmodeplus.core.model.SyncSkipReason
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeRemoteSyncStore
import app.focusmodeplus.core.testing.FakeUsers
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * v22 security regression tests for account binding and synchronization (fake account provider,
 * real Room, Rules-enforcing remote stand-in; no Firebase project is contacted).
 *
 * V22-F01: a record that is valid on the device but outside the remote schema (for example a
 * Session from a device whose clock was before 2000, or from a hand-edited backup) used to be
 * parked forever, and migration verification waited for it forever: the account never became
 * active, cloud data never downloaded and every sync ended as a retryable failure.
 */
@RunWith(AndroidJUnit4::class)
class SecurityV22SyncTest {
    private val remote = FakeRemoteSyncStore()
    private val clock = FakeClock(wall = Instant.parse("2026-11-26T09:00:00Z"))
    private lateinit var a: SyncTestDevice
    private val extra = mutableListOf<SyncTestDevice>()
    private val alice = FakeUsers.ALICE.uid
    private val bob = FakeUsers.BOB.uid

    @Before fun setUp() { a = SyncTestDevice(remote, clock) }
    @After fun tearDown() { a.close(); extra.forEach { it.close() } }

    private fun device() = SyncTestDevice(remote, clock).also { extra += it }

    /** Locally valid, remotely invalid: the wall clock of the source device was in 1999. */
    private fun pre2000Session(id: String) = SyncTestDevice.quick(id, "2026-11-18").copy(
        startedAt = Instant.parse("1999-12-31T10:00:00Z"),
        finishedAt = Instant.parse("1999-12-31T10:25:00Z"),
    )

    @Test fun aRemotelyInvalidRecordNeverBlocksTheMigration() = runBlocking {
        assertTrue(a.missions.create(Fixtures.mission("mission_ok", steps = 2)) is MissionWriteResult.Ok)
        a.sessions.finalize(SyncTestDevice.quick("s_ok", "2026-11-20", 25))
        a.sessions.finalize(pre2000Session("s_clock_1999"))
        a.engine.start(a.scope)
        a.accounts.signInAs(FakeUsers.ALICE)
        val decision = withTimeout(10_000) { a.engine.decision.first { it != null } } as AccountDataDecision.LinkLocalData
        val result = a.engine.migrateLocalData(alice)
        assertTrue("migration must complete, was $result (binding ${a.store.binding()?.status})", result is SyncResult.Success)
        // The preview told the user before linking that one record cannot synchronize.
        assertEquals(1, decision.preview.notSyncable)
        val binding = a.store.binding()!!
        assertEquals(BindingStatus.ACTIVE, binding.status)
        assertNotNull(binding.migrationCompletedAt)
        assertEquals(MigrationStatus.COMPLETE, withTimeout(10_000) { a.engine.syncState.first { it.migration == MigrationStatus.COMPLETE } }.migration)
        // The valid records are in the cloud; the invalid one never left the device and was not deleted.
        assertNotNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.MISSIONS, "mission_ok")))
        assertNotNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_ok")))
        assertNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_clock_1999")))
        assertNotNull(a.sessions.getResult(app.focusmodeplus.core.model.SessionId("s_clock_1999")))
        // A later sync succeeds instead of failing forever.
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        a.engine.postponeDecision()
    }

    @Test fun aRemotelyInvalidRecordFinalizedAfterLinkingIsReportedNotRetriedForever() = runBlocking {
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.initialSync(alice) is SyncResult.Success)
        a.sessions.finalize(pre2000Session("s_late_1999"))
        // Parked as invalid data: a sync still succeeds and nothing is uploaded for it.
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_late_1999")))
        assertEquals(BindingStatus.ACTIVE, a.store.binding()!!.status)
    }

    // ------------------------------------------------------------------ V22-F02: remote Quick Session with a Mission link

    @Test fun aRemoteQuickSessionCarryingAMissionLinkIsNeverApplied() = runBlocking {
        val codec = RemoteCodec(alice)
        val quick = SyncTestDevice.quick("s_remote_quick", "2026-11-20", 25)
        val forged = codec.session(quick, "mutation_forged_01", clock.now())!!.toMutableMap().apply {
            put("missionId", "mission_victim")
            put("missionStepId", "mission_victim_step_0")
            put("serverUpdatedAt", 1_800_000_000_000L)
        }
        // Quick Focus never touches Missions: the decoder refuses the forged combination.
        assertNull(codec.decodeSession(RemoteDocument(RemotePath.Doc(RemoteCollection.SESSIONS, "s_remote_quick"), forged)))
        // A Mission Session with the same link stays valid.
        val mission = quick.copy(type = app.focusmodeplus.core.model.SessionType.MISSION, missionLink = MissionLink(MissionId("mission_victim"), MissionStepId("mission_victim_step_0")))
        val ok = codec.session(mission, "mutation_valid_01", clock.now())!!.toMutableMap().apply { put("serverUpdatedAt", 1_800_000_000_000L) }
        assertNotNull(codec.decodeSession(RemoteDocument(RemotePath.Doc(RemoteCollection.SESSIONS, "s_remote_quick"), ok)))
        // And the encoder never produces the forged shape from local data.
        assertNull(codec.session(quick.copy(missionLink = MissionLink(MissionId("mission_victim"), MissionStepId("mission_victim_step_0"))), "mutation_local_01", clock.now()))
    }

    // ------------------------------------------------------------------ cross-account isolation (attack scenarios)

    @Test fun accountAThenSignOutThenAccountBNeverSeesOrReceivesAccountAData() = runBlocking {
        assertTrue(a.missions.create(Fixtures.mission("mission_alice", steps = 2)) is MissionWriteResult.Ok)
        a.sessions.finalize(SyncTestDevice.quick("s_alice", "2026-11-20", 25))
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.migrateLocalData(alice) is SyncResult.Success)
        assertTrue(a.lifecycle().signOut(removeLocalData = false).signedOut)
        // Sign-out cancelled the background job; nothing remains scheduled for Alice afterwards.
        assertTrue(a.scheduler.cancels > 0)
        a.scheduler.scheduled.clear()

        // Bob signs in on the same device: the engine asks, never merges and never uploads Alice's data as Bob.
        a.accounts.signInAs(FakeUsers.BOB)
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.synchronize())
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.migrateLocalData(bob))
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.initialSync(bob))
        assertTrue(remote.allFor(bob).keys.none { it.contains("mission_alice") || it.contains("s_alice") })
        assertTrue(a.scheduler.scheduled.none { it == bob })
        // Alice's cloud copy is untouched by Bob's session on this device.
        assertNotNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_alice")))
    }

    @Test fun anOutboxEntryOfAnotherAccountIsNeverUploadedForTheSignedInAccount() = runBlocking {
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.initialSync(alice) is SyncResult.Success)
        a.sessions.finalize(SyncTestDevice.quick("s_pending_alice", "2026-11-21", 30))
        // Alice is signed out elsewhere before the pending change uploads; Bob signs in.
        a.accounts.signInAs(FakeUsers.BOB)
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.uploadPendingMutations())
        assertTrue(remote.allFor(bob).isEmpty())
        assertNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_pending_alice")))
    }

    @Test fun aReplayedMigrationConfirmationIsIdempotent() = runBlocking {
        assertTrue(a.missions.create(Fixtures.mission("mission_once", steps = 1)) is MissionWriteResult.Ok)
        a.accounts.signInAs(FakeUsers.ALICE)
        val first = a.engine.migrateLocalData(alice)
        val commits = remote.commits
        val replay = a.engine.migrateLocalData(alice)
        assertTrue(first is SyncResult.Success && replay is SyncResult.Success)
        assertEquals(1, remote.collection(alice, RemoteCollection.SYNC_META).size)
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size)
        // The replay re-runs a normal sync at most; it never re-creates the migration or the Mission.
        assertTrue(remote.commits - commits <= 1)
    }

    // ------------------------------------------------------------------ server-side injection (misconfigured Rules, defense in depth)

    /** Writes a document straight into the server stand-in, as if the Rules had let it through. */
    private fun inject(uid: String, collection: RemoteCollection, id: String, fields: Map<String, Any?>) {
        remote.docs["users/$uid/${collection.segment}/$id"] = fields + mapOf("serverUpdatedAt" to remote.serverTimeMs + 10_000)
    }

    @Test fun forgedServerDocumentsAreNeverAppliedEvenIfTheRulesFailed() = runBlocking {
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.initialSync(alice) is SyncResult.Success)
        val codec = RemoteCodec(alice)
        val base = codec.session(SyncTestDevice.quick("s_base", "2026-11-20", 25), "mutation_inject_01", clock.now())!!
        inject(alice, RemoteCollection.SESSIONS, "s_league", base + mapOf("id" to "s_league", "leaguePoints" to 999_999L))
        inject(alice, RemoteCollection.SESSIONS, "s_verified", base + mapOf("id" to "s_verified", "verifiedFocusMinutes" to 600L))
        inject(alice, RemoteCollection.SESSIONS, "s_unknown", base + mapOf("id" to "s_unknown", "adminNote" to mapOf("role" to "owner")))
        inject(alice, RemoteCollection.SESSIONS, "s_foreign", base + mapOf("id" to "s_foreign", "ownerUid" to bob))
        inject(alice, RemoteCollection.SESSIONS, "s_schema2", base + mapOf("id" to "s_schema2", "schemaVersion" to 2L))
        inject(alice, RemoteCollection.SESSIONS, "s_minutes", base + mapOf("id" to "s_minutes", "actualMinutes" to 100_000L))
        inject(alice, RemoteCollection.SESSIONS, "s_review", base + mapOf("id" to "s_review", "endReason" to "awaiting_user_review"))
        inject(alice, RemoteCollection.SESSIONS, "mismatch_doc", base + mapOf("id" to "s_other_id"))
        val m = codec.mission(app.focusmodeplus.core.domain.sync.MissionSnapshot(Fixtures.mission("mission_forged", steps = 1), emptyList()), 1, "mutation_inject_02")!!
        inject(alice, RemoteCollection.MISSIONS, "mission_forged", m + mapOf("syncVersion" to 0L))
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(0, a.count("session_results"))
        assertEquals(0, a.count("missions"))
    }

    @Test fun aRewrittenServerCopyNeverChangesOrDuplicatesAFinalizedSession() = runBlocking {
        a.sessions.finalize(SyncTestDevice.quick("s_final", "2026-11-20", 25))
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.migrateLocalData(alice) is SyncResult.Success)
        val stored = remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_final"))!!
        inject(alice, RemoteCollection.SESSIONS, "s_final", stored + mapOf("actualMinutes" to 1_440L, "plannedMinutes" to 1_440L, "countsAsFocus" to true))
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(1, a.count("session_results"))
        assertEquals(25, a.sessions.getResult(app.focusmodeplus.core.model.SessionId("s_final"))!!.actualMinutes)
    }

    @Test fun repeatedAccountSwitchingNeverLeaksOrWedges() = runBlocking {
        assertTrue(a.missions.create(Fixtures.mission("mission_alice", steps = 1)) is MissionWriteResult.Ok)
        a.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(a.engine.migrateLocalData(alice) is SyncResult.Success)
        repeat(20) {
            a.accounts.signInAs(FakeUsers.BOB)
            assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.synchronize())
            a.accounts.signInAs(FakeUsers.ALICE)
            assertTrue(a.engine.synchronize() is SyncResult.Success)
        }
        assertTrue(remote.allFor(bob).isEmpty())
        assertEquals(alice, a.store.binding()!!.uid)
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size)
    }

    @Test fun anotherDeviceCannotAttachItsLocalDataToTheWrongAccount() = runBlocking {
        val b = device()
        assertTrue(b.missions.create(Fixtures.mission("mission_device_b", steps = 1)) is MissionWriteResult.Ok)
        b.accounts.signInAs(FakeUsers.BOB)
        // A request for Alice while Bob is signed in is refused before anything is written.
        assertEquals(SyncResult.Skipped(SyncSkipReason.NOT_SIGNED_IN), b.engine.migrateLocalData(alice))
        assertTrue(remote.allFor(alice).isEmpty())
        assertNull(b.store.binding())
        assertFalse(SyncEngine.newId().isEmpty())
    }
}
