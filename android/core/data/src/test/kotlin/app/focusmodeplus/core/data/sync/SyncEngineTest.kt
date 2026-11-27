package app.focusmodeplus.core.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.ai.AdaptedStep
import app.focusmodeplus.core.domain.progress.ProgressCalculator
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.domain.session.QuickFocusRequest
import app.focusmodeplus.core.domain.session.SessionFactory
import app.focusmodeplus.core.domain.sync.RemoteCollection
import app.focusmodeplus.core.domain.sync.RemotePath
import app.focusmodeplus.core.domain.sync.RemoteSchema
import app.focusmodeplus.core.domain.sync.SyncEngine
import app.focusmodeplus.core.model.AccountDataDecision
import app.focusmodeplus.core.model.BindingStatus
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionReview
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SyncFailure
import app.focusmodeplus.core.model.SyncPhase
import app.focusmodeplus.core.model.SyncResult
import app.focusmodeplus.core.model.SyncSkipReason
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeRemoteSyncStore
import app.focusmodeplus.core.testing.FakeUsers
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * v21 synchronization on real Room databases against a server stand-in that enforces the
 * engine-relevant Firestore Rules. Every test is deterministic: no sleeps, only awaited states.
 */
@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private val remote = FakeRemoteSyncStore()
    private val clock = FakeClock(wall = Instant.parse("2026-11-26T09:00:00Z"))
    private lateinit var a: SyncTestDevice
    private val extra = mutableListOf<SyncTestDevice>()
    private val alice = FakeUsers.ALICE.uid
    private val bob = FakeUsers.BOB.uid

    @Before fun setUp() { a = SyncTestDevice(remote, clock) }
    @After fun tearDown() { a.close(); extra.forEach { it.close() } }

    private fun device() = SyncTestDevice(remote, clock).also { extra += it }

    private suspend fun SyncTestDevice.seed(missionId: String = "mission_bio") {
        assertTrue(missions.create(Fixtures.mission(missionId, title = "Biologie-Klausur", steps = 3, completed = 1)) is MissionWriteResult.Ok)
        sessions.finalize(SyncTestDevice.quick("s_${missionId}_1", "2026-11-18", 25))
        sessions.finalize(SyncTestDevice.quick("s_${missionId}_2", "2026-11-20", 40))
    }

    private suspend fun SyncTestDevice.linkAlice() {
        accounts.signInAs(FakeUsers.ALICE)
        assertTrue(engine.migrateLocalData(alice) is SyncResult.Success)
    }

    private fun missionDoc(uid: String, id: String) = remote.doc(uid, RemotePath.Doc(RemoteCollection.MISSIONS, id))

    // ------------------------------------------------------------------------------ guest and decision

    @Test fun guestDataIsNeverRecordedOrUploaded() = runBlocking {
        a.seed()
        assertEquals(0, a.count("sync_outbox"))
        a.accounts.signInAs(FakeUsers.ALICE)
        assertEquals(SyncResult.Skipped(SyncSkipReason.NOT_LINKED), a.engine.synchronize())
        assertTrue(remote.docs.isEmpty())
        assertNull(a.store.binding())
    }

    @Test fun signingInWithLocalDataAsksFirstAndShowsAPreview() = runBlocking {
        a.seed()
        a.sessions.finalize(SyncTestDevice.awaitingReview("s_review", "mission_bio", "mission_bio_step_1"))
        a.engine.start(a.scope)
        a.accounts.signInAs(FakeUsers.ALICE)
        val decision = withTimeout(10_000) { a.engine.decision.first { it != null } } as AccountDataDecision.LinkLocalData
        val p = decision.preview
        assertEquals(1, p.missions)
        assertEquals(3, p.missionSteps)
        assertEquals(2, p.sessions)
        assertEquals(1, p.awaitingReview)
        assertEquals(65, p.focusMinutes)
        assertEquals("2026-11-18", p.historyFrom.toString())
        assertEquals("2026-11-20", p.historyTo.toString())
        assertEquals(0, p.remoteMissions)
        // Nothing was attached or uploaded before the user decides.
        assertNull(a.store.binding())
        assertTrue(remote.docs.isEmpty())
        assertEquals(SyncPhase.DECISION_REQUIRED, withTimeout(10_000) { a.engine.syncState.first { it.phase == SyncPhase.DECISION_REQUIRED } }.phase)
        a.engine.postponeDecision()
        assertNull(a.engine.decision.value)
        assertNull(a.store.binding())
    }

    @Test fun emptyDeviceRestoresTheAccountWithoutAsking() = runBlocking {
        a.seed()
        a.linkAlice()
        val b = device()
        b.engine.start(b.scope)
        b.accounts.signInAs(FakeUsers.ALICE)
        withTimeout(10_000) { b.store.observeBinding().first { it?.status == BindingStatus.ACTIVE } }
        withTimeout(10_000) { b.missions.observeMissions().first { it.size == 1 } }
        assertNull(b.engine.decision.value)
        assertEquals(2, b.count("session_results"))
    }

    // ------------------------------------------------------------------------------ migration

    @Test fun migrationUploadsVerifiesAndKeepsLocalData() = runBlocking {
        a.seed()
        a.prefs.setMotion(MotionPreference.REDUCED)
        a.linkAlice()
        val binding = a.store.binding()!!
        assertEquals(BindingStatus.ACTIVE, binding.status)
        assertNotNull(binding.migrationCompletedAt)
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size)
        assertEquals(3, remote.collection(alice, RemoteCollection.STEPS, "mission_bio").size)
        assertEquals(2, remote.collection(alice, RemoteCollection.SESSIONS).size)
        assertEquals("complete", remote.doc(alice, RemotePath.Doc(RemoteCollection.SYNC_META, RemoteSchema.migrationDocId(binding.migrationId!!)))!!["status"])
        assertEquals("reduced", remote.doc(alice, RemotePath.Doc(RemoteCollection.SETTINGS, RemoteSchema.SETTINGS_DOC))!!["motion"])
        assertNotNull(remote.doc(alice, RemotePath.Profile))
        // Local data is never deleted by a migration.
        assertEquals(1, a.count("missions"))
        assertEquals(2, a.count("session_results"))
        assertEquals(0, a.count("sync_outbox"))
        // Nothing reserved or verified was written anywhere.
        remote.docs.values.forEach { doc -> RemoteSchema.RESERVED_SERVER_FIELDS.forEach { assertFalse(it in doc) } }
        assertTrue(remote.docs.values.none { "verification" in it })
    }

    @Test fun duplicateConfirmationTapsMigrateOnce() = runBlocking {
        a.seed()
        a.accounts.signInAs(FakeUsers.ALICE)
        coroutineScope { (1..3).map { async { a.engine.migrateLocalData(alice) } }.awaitAll() }
        assertEquals(BindingStatus.ACTIVE, a.store.binding()!!.status)
        assertEquals(1L, missionDoc(alice, "mission_bio")!!["syncVersion"])
        assertEquals(2, remote.collection(alice, RemoteCollection.SESSIONS).size)
        assertEquals(0, a.count("sync_outbox"))
    }

    @Test fun migrationSurvivesNetworkLossAndProcessDeath() = runBlocking {
        a.seed()
        a.accounts.signInAs(FakeUsers.ALICE)
        remote.offline = true
        assertEquals(SyncResult.Failed(SyncFailure.OFFLINE, retryable = true), a.engine.migrateLocalData(alice))
        val migrationId = a.store.binding()!!.migrationId
        assertEquals(BindingStatus.MIGRATING, a.store.binding()!!.status)
        assertEquals(3, a.count("sync_outbox")) // 1 Mission + 2 Sessions, enqueued in one transaction
        a.restartProcess()
        remote.offline = false
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        val b = a.store.binding()!!
        assertEquals(BindingStatus.ACTIVE, b.status)
        assertEquals(migrationId, b.migrationId)
        assertEquals(2, remote.collection(alice, RemoteCollection.SESSIONS).size)
        assertEquals(1L, missionDoc(alice, "mission_bio")!!["syncVersion"])
    }

    @Test fun migrationCompletesOnlyAfterTheServerHoldsEveryRecord() = runBlocking {
        a.seed()
        a.accounts.signInAs(FakeUsers.ALICE)
        remote.silentlyDrop = { it.endsWith("/sessions/s_mission_bio_2") }
        val first = a.engine.migrateLocalData(alice)
        assertTrue(first is SyncResult.Failed)
        assertEquals(BindingStatus.MIGRATING, a.store.binding()!!.status)
        assertEquals(1, a.count("sync_outbox")) // the missing Session was re-queued
        remote.silentlyDrop = { false }
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(BindingStatus.ACTIVE, a.store.binding()!!.status)
        assertEquals(2, remote.collection(alice, RemoteCollection.SESSIONS).size)
    }

    @Test fun aSecondDeviceCanLinkItsOwnLocalDataToTheSameAccount() = runBlocking {
        a.seed("mission_a")
        a.linkAlice()
        val b = device()
        b.seed("mission_b")
        b.accounts.signInAs(FakeUsers.ALICE)
        val preview = b.engine.prepareMigration(alice)
        assertEquals(1, preview.remoteMissions)
        assertTrue(b.engine.migrateLocalData(alice) is SyncResult.Success)
        // Both datasets are now in the account (merged, never overwritten), each migration recorded on its own.
        assertEquals(setOf("mission_a", "mission_b"), b.missions.observeMissions().first().map { it.id.value }.toSet())
        assertEquals(2, remote.collection(alice, RemoteCollection.SYNC_META).size)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(setOf("mission_a", "mission_b"), a.missions.observeMissions().first().map { it.id.value }.toSet())
        assertEquals(4, a.count("session_results"))
    }

    @Test fun migrationIsRefusedForADatasetOfAnotherAccount() = runBlocking {
        a.seed()
        a.linkAlice()
        a.accounts.signInAs(FakeUsers.BOB)
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.migrateLocalData(bob))
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_MISMATCH), a.engine.synchronize())
        assertTrue(remote.allFor(bob).isEmpty())
        assertEquals(alice, a.store.binding()!!.uid)
    }

    // ------------------------------------------------------------------------------ outbox

    @Test fun outboxMutationsAreWrittenInTheSameTransaction() = runBlocking {
        a.linkAlice()
        val m = Fixtures.mission("mission_new", steps = 2)
        assertTrue(a.missions.create(m) is MissionWriteResult.Ok)
        assertEquals(1, a.count("sync_outbox"))
        // A rejected write (duplicate id) rolls back the change and records nothing.
        assertTrue(a.missions.create(m) is MissionWriteResult.Rejected)
        assertEquals(1, a.count("sync_outbox"))
        assertTrue(a.missions.delete(m.id) is MissionWriteResult.Ok)
        assertEquals(2, a.count("sync_outbox"))
        assertEquals(1, a.count("sync_tombstones"))
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertNull(missionDoc(alice, "mission_new"))
        assertNotNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.TOMBSTONES, "mission_mission_new")))
    }

    @Test fun activeSessionsNeverSyncAndMissionSessionsWaitForTheirReview() = runBlocking {
        a.seed()
        a.linkAlice()
        val quick = SessionFactory(clock).quick(QuickFocusRequest("Konzentriert arbeiten", 25, ProtectionMode.STANDARD))
        a.sessions.save(quick)
        assertEquals(0, a.count("sync_outbox"))
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, quick.id.value)))
        assertTrue(remote.docs.keys.none { it.contains("active") })
        a.sessions.clear(quick.id)

        a.sessions.finalize(SyncTestDevice.awaitingReview("s_review", "mission_bio", "mission_bio_step_1"))
        assertEquals(0, a.count("sync_outbox"))
        a.sessions.applyMissionReview(SessionId("s_review"), MissionReview(SessionOutcome.COMPLETED, rating = 4, note = "Gut"))
        assertEquals(2, a.count("sync_outbox")) // the reviewed Session and its Mission
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        val session = remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_review"))!!
        assertEquals("user_reviewed", session["endReason"])
        assertEquals("Gut", session["reviewNote"])
        assertEquals(2L, missionDoc(alice, "mission_bio")!!["syncVersion"])
    }

    @Test fun lostAcknowledgementsNeverDuplicateOrBlock() = runBlocking {
        a.seed()
        a.linkAlice()
        a.missions.rename(MissionId("mission_bio"), "Bio-Klausur")
        remote.loseNextAck = true
        assertEquals(SyncResult.Failed(SyncFailure.OFFLINE, retryable = true), a.engine.synchronize())
        assertEquals(2L, missionDoc(alice, "mission_bio")!!["syncVersion"]) // applied on the server
        clock.advanceMinutes(10)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(0, a.count("sync_outbox"))
        assertEquals(2L, missionDoc(alice, "mission_bio")!!["syncVersion"])
        assertEquals("Bio-Klausur", missionDoc(alice, "mission_bio")!!["title"])
        assertEquals(1, remote.collection(alice, RemoteCollection.MISSIONS).size)
    }

    @Test fun manySessionsUploadInFewBatches() = runBlocking {
        a.linkAlice()
        val commitsBefore = remote.commits
        repeat(600) { i -> a.sessions.finalize(SyncTestDevice.quick("s_bulk_$i", "2026-11-1${i % 10}", 20)) }
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(600, remote.collection(alice, RemoteCollection.SESSIONS).size)
        assertTrue("no request per record", remote.commits - commitsBefore <= 4)
    }

    @Test fun repeatedFailuresAreBoundedAndParked() = runBlocking {
        a.linkAlice()
        a.sessions.finalize(SyncTestDevice.quick("s_fail", "2026-11-21"))
        remote.failure = SyncFailure.SERVER
        remote.failNextCommits = Int.MAX_VALUE
        repeat(SyncEngine.MAX_ATTEMPTS) {
            a.engine.synchronize()
            clock.advanceMs(SyncEngine.BACKOFF_MAX_MS + 1)
        }
        val commitsTried = remote.failNextCommits
        a.engine.synchronize()
        assertEquals("parked mutations are not retried automatically", commitsTried, remote.failNextCommits)
        assertEquals(1, withTimeout(5_000) { a.store.observeParkedCount().first() })
        remote.failNextCommits = 0
        a.engine.retryParked()
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertNotNull(remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_fail")))
    }

    // ------------------------------------------------------------------------------ download, deletion, conflicts

    @Test fun secondDeviceDownloadsMissionsStepsSessionsAndRecalculatesDerivedValues() = runBlocking {
        a.seed()
        a.sessions.finalize(SyncTestDevice.awaitingReview("s_review", "mission_bio", "mission_bio_step_1"))
        a.sessions.applyMissionReview(SessionId("s_review"), MissionReview(SessionOutcome.COMPLETED))
        a.linkAlice()
        val b = device()
        b.accounts.signInAs(FakeUsers.ALICE)
        assertTrue(b.engine.initialSync(alice) is SyncResult.Success)
        val mission = b.missions.getMission(MissionId("mission_bio"))!!
        assertEquals(3, mission.steps.size)
        assertEquals(2, mission.completedStepCount)
        assertEquals(1, b.missions.progressHistory(mission.id).size)
        assertEquals(3, b.count("session_results"))
        // Derived values come from the synchronized source records, never from uploaded totals.
        val aResults = a.sessions.observeResults().first()
        val bResults = b.sessions.observeResults().first()
        assertEquals(aResults.toSet(), bResults.toSet())
        assertEquals(ProgressCalculator.compute(aResults, clock.today()), ProgressCalculator.compute(bResults, clock.today()))
        assertTrue(remote.docs.values.none { doc -> doc.keys.any { it.contains("streak", ignoreCase = true) || it.contains("total", ignoreCase = true) } })
    }

    @Test fun aRemoteDeletionWinsAndIsNeverResurrected() = runBlocking {
        a.seed()
        a.linkAlice()
        val b = device()
        b.accounts.signInAs(FakeUsers.ALICE)
        b.engine.initialSync(alice)
        // Device B is offline and edits the Mission; device A deletes it meanwhile.
        b.network.online.value = false
        b.missions.rename(MissionId("mission_bio"), "Offline bearbeitet")
        assertTrue(a.missions.delete(MissionId("mission_bio")) is MissionWriteResult.Ok)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        b.network.online.value = true
        assertTrue(b.engine.synchronize() is SyncResult.Success)
        assertNull(b.missions.getMission(MissionId("mission_bio")))
        assertNull(missionDoc(alice, "mission_bio"))
        assertTrue(remote.collection(alice, RemoteCollection.STEPS, "mission_bio").isEmpty())
        assertEquals(1, b.store.binding()!!.conflictsResolved)
        assertEquals(0, b.count("sync_outbox"))
        // History keeps its Sessions.
        assertEquals(2, b.count("session_results"))
    }

    @Test fun concurrentPlanChangesKeepCompletedStepsAndRecordAConflict() = runBlocking {
        a.seed()
        a.linkAlice()
        val b = device()
        b.accounts.signInAs(FakeUsers.ALICE)
        b.engine.initialSync(alice)
        // B completes the current step offline; A replaces the open steps a little later.
        b.network.online.value = false
        assertTrue(b.missions.completeStep(MissionId("mission_bio"), MissionStepId("mission_bio_step_1")) is MissionWriteResult.Ok)
        clock.advanceMinutes(1)
        val current = a.missions.getMission(MissionId("mission_bio"))!!
        val adapted = listOf(AdaptedStep("Neuer Schritt A", "Aufgabe A", 30, "Plan angepasst"), AdaptedStep("Neuer Schritt B", "Aufgabe B", 30, "Plan angepasst"))
        assertTrue(a.missions.replaceRemainingSteps(current.id, adapted, current.updatedAt) is MissionWriteResult.Ok)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        b.network.online.value = true
        assertTrue(b.engine.synchronize() is SyncResult.Success)
        val merged = b.missions.getMission(MissionId("mission_bio"))!!
        // Completed steps are never lost: step 0 (completed before) and step 1 (completed on B).
        val completed = merged.steps.filter { it.status == MissionStepStatus.COMPLETED }.map { it.id.value }.toSet()
        assertEquals(setOf("mission_bio_step_0", "mission_bio_step_1"), completed)
        assertTrue(merged.steps.any { it.title == "Neuer Schritt A" })
        assertEquals(1, b.store.binding()!!.conflictsResolved)
        assertEquals(merged.steps.map { it.position }, merged.steps.indices.toList())
        // A converges to the same plan.
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(merged.steps.map { it.id to it.status }, a.missions.getMission(MissionId("mission_bio"))!!.steps.map { it.id to it.status })
    }

    @Test fun settingsFollowLastWriteWins() = runBlocking {
        a.linkAlice()
        val b = device()
        b.accounts.signInAs(FakeUsers.ALICE)
        b.engine.initialSync(alice)
        a.prefs.setDefaultQuickFocusMinutes(45)
        a.prefs.setNotificationDetail(NotificationDetailLevel.GENERIC)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertTrue(b.engine.synchronize() is SyncResult.Success)
        assertEquals(45, b.prefs.current().defaultQuickFocusMinutes)
        assertEquals(NotificationDetailLevel.GENERIC, b.prefs.current().notificationDetail)
        // Both change; the later write to the server is the one that stays.
        a.prefs.setDefaultQuickFocusMinutes(15)
        b.prefs.setDefaultQuickFocusMinutes(50)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertTrue(b.engine.synchronize() is SyncResult.Success)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        assertEquals(50, a.prefs.current().defaultQuickFocusMinutes)
        assertEquals(50L, remote.doc(alice, RemotePath.Doc(RemoteCollection.SETTINGS, RemoteSchema.SETTINGS_DOC))!!["defaultQuickFocusMinutes"])
    }

    @Test fun anAccountChangeDuringASyncWritesNothing() = runBlocking {
        a.seed()
        a.linkAlice()
        val b = device()
        b.accounts.signInAs(FakeUsers.ALICE)
        b.engine.initialSync(alice)
        a.missions.rename(MissionId("mission_bio"), "Neuer Titel")
        a.engine.synchronize()
        val before = b.missions.getMission(MissionId("mission_bio"))!!.title
        remote.beforeRead = { b.accounts.signInAs(FakeUsers.BOB) }
        assertEquals(SyncResult.Skipped(SyncSkipReason.ACCOUNT_CHANGED), b.engine.synchronize())
        remote.beforeRead = null
        assertEquals(before, b.missions.getMission(MissionId("mission_bio"))!!.title)
        assertTrue(remote.allFor(bob).isEmpty())
    }

    @Test fun importsIntoALinkedDatasetSyncAsOrdinaryUnverifiedHistory() = runBlocking {
        a.linkAlice()
        val r = SyncTestDevice.quick("s_imported", "2026-11-10").copy(origin = app.focusmodeplus.core.model.DataOrigin.IMPORTED_PWA)
        val plan = app.focusmodeplus.core.domain.backup.ImportPlan(emptyList(), listOf(r), app.focusmodeplus.core.domain.backup.ImportReport(null, null, 0, 0, 1, emptyList(), emptyList()))
        a.importer.apply(plan)
        assertTrue(a.engine.synchronize() is SyncResult.Success)
        val doc = remote.doc(alice, RemotePath.Doc(RemoteCollection.SESSIONS, "s_imported"))!!
        assertEquals("imported_pwa", doc["origin"])
        RemoteSchema.RESERVED_SERVER_FIELDS.forEach { assertFalse(it in doc) }
    }
}
