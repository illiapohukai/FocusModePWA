package app.focusmodeplus.core.domain.sync

import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The deterministic conflict rules of android/docs/sync-conflict-policy-v21.md. */
class SyncConflictPolicyTest {
    private val t0 = Instant.parse("2026-11-20T09:00:00Z")
    private val base = Fixtures.mission("mission_x", steps = 3, completed = 1).copy(updatedAt = t0)

    private fun snap(m: app.focusmodeplus.core.model.Mission, vararg progress: String) = MissionSnapshot(
        m, progress.map { MissionProgressEntry(SessionId(it), m.id, MissionStepId("mission_x_step_0"), SessionOutcome.COMPLETED, null, 25, 25, t0) },
    )

    @Test fun simpleFieldsAreLastWriteWinsByUpdatedAt() {
        val local = base.copy(title = "Lokal", updatedAt = t0.plusSeconds(10))
        val remote = base.copy(title = "Remote", updatedAt = t0.plusSeconds(20))
        val merged = SyncConflictPolicy.mergeMission(snap(local), "aaaa", snap(remote), "bbbb")
        assertEquals("Remote", merged.mission.title)
        assertTrue(merged.remoteWins)
        assertFalse("same step set: no conflict record", merged.conflict)
        val reverse = SyncConflictPolicy.mergeMission(snap(remote.copy(updatedAt = t0)), "aaaa", snap(local), "bbbb")
        assertEquals("Lokal", reverse.mission.title)
    }

    @Test fun tiesAreBrokenByTheMutationIdOnEveryDevice() {
        val local = base.copy(title = "A")
        val remote = base.copy(title = "B")
        val onDevice1 = SyncConflictPolicy.mergeMission(snap(local), "m_100", snap(remote), "m_200").mission.title
        val onDevice2 = SyncConflictPolicy.mergeMission(snap(remote), "m_200", snap(local), "m_100").mission.title
        assertEquals(onDevice1, onDevice2)
    }

    @Test fun completedStepsAreNeverUndoneOrLost() {
        val localDone = (app.focusmodeplus.core.domain.mission.MissionRules.completeStep(base, MissionStepId("mission_x_step_1"), t0.plusSeconds(5))
            as app.focusmodeplus.core.domain.mission.StepCompletion.Completed).mission
        // The newer remote plan replaced every open step.
        val remote = base.copy(
            updatedAt = t0.plusSeconds(60),
            steps = base.steps.filter { it.status == MissionStepStatus.COMPLETED } + base.steps[1].copy(id = MissionStepId("fresh_1"), position = 1, title = "Neu"),
            currentStepId = MissionStepId("fresh_1"),
        )
        val merged = SyncConflictPolicy.mergeMission(snap(localDone), "aaaa", snap(remote), "bbbb")
        assertTrue(merged.conflict)
        val ids = merged.mission.steps.map { it.id.value }
        assertTrue("mission_x_step_0" in ids && "mission_x_step_1" in ids && "fresh_1" in ids)
        assertEquals(MissionStepStatus.COMPLETED, merged.mission.steps.first { it.id.value == "mission_x_step_1" }.status)
        assertEquals(merged.mission.steps.indices.toList(), merged.mission.steps.map { it.position })
        assertEquals(MissionStepId("fresh_1"), merged.mission.currentStepId)
        assertEquals(MissionStepStatus.ACTIVE, merged.mission.steps.first { it.id.value == "fresh_1" }.status)
    }

    @Test fun aStepCompletedOnBothSidesKeepsTheServersCopy() {
        val a = base.copy(steps = base.steps.map { if (it.position == 1) it.copy(status = MissionStepStatus.COMPLETED, completedAt = t0.plusSeconds(30)) else it }, updatedAt = t0.plusSeconds(30))
        val b = base.copy(steps = base.steps.map { if (it.position == 1) it.copy(status = MissionStepStatus.COMPLETED, completedAt = t0.plusSeconds(10)) else it }, updatedAt = t0.plusSeconds(40))
        // Local completed later but its plan is newer: the server's completion record still stands.
        val merged = SyncConflictPolicy.mergeMission(snap(b), "bbbb", snap(a), "aaaa").mission
        assertEquals(t0.plusSeconds(30), merged.steps.first { it.position == 1 }.completedAt)
    }

    @Test fun allStepsDoneCompletesTheMission() {
        val done = base.copy(steps = base.steps.map { it.copy(status = MissionStepStatus.COMPLETED, completedAt = t0.plusSeconds(it.position * 60L)) })
        val normalized = SyncConflictPolicy.normalize(done)
        assertEquals(MissionStatus.COMPLETED, normalized.status)
        assertNull(normalized.currentStepId)
        assertEquals(t0.plusSeconds(120), normalized.completedAt)
    }

    @Test fun archivedMissionsKeepTheirStepStates() {
        val archived = app.focusmodeplus.core.domain.mission.MissionRules.archive(base, t0)
        assertEquals(archived.steps, SyncConflictPolicy.normalize(archived).steps)
        assertEquals(archived.archivedStepId, SyncConflictPolicy.normalize(archived).archivedStepId)
    }

    @Test fun progressEntriesAreAUnionBySessionId() {
        val merged = SyncConflictPolicy.mergeMission(snap(base, "s1", "s2"), "aaaa", snap(base.copy(updatedAt = t0.plusSeconds(1)), "s2", "s3"), "bbbb")
        assertEquals(listOf("s1", "s2", "s3"), merged.progress.map { it.sessionId.value })
    }

    @Test fun theTwoActiveRuleKeepsTheMostRecentlyUpdated() {
        val ms = (1..4).map { i -> Fixtures.mission("mission_$i").copy(updatedAt = t0.plusSeconds(i * 10L)) }
        assertEquals(listOf("mission_2", "mission_1"), SyncConflictPolicy.missionsToArchive(ms).map { it.id.value })
    }

    @Test fun onlyAWaitingLocalReviewAcceptsARemoteReview() {
        assertTrue(SyncConflictPolicy.SessionPolicy.acceptsRemoteReview(localAwaitingReview = true))
        assertFalse(SyncConflictPolicy.SessionPolicy.acceptsRemoteReview(localAwaitingReview = false))
    }
}
