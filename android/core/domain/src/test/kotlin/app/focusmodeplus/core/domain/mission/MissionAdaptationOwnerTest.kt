package app.focusmodeplus.core.domain.mission

import app.focusmodeplus.core.domain.ai.AdaptationResult
import app.focusmodeplus.core.domain.ai.AdaptedStep
import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.MissionAdaptation
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionReview
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMissionAdapter
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakeSessionStore
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionAdaptationOwnerTest {
    private val clock = FakeClock()
    private val missions = FakeMissionRepository(clock)
    private val history = FakeSessionStore(clock).also { it.missions = missions }
    private val adapter = FakeMissionAdapter()
    private val network = FakeNetworkStatus()
    private val mission = Fixtures.mission("mission_a", steps = 3)
    private val other = Fixtures.mission("mission_other", title = "Andere Mission")

    private fun TestScope.owner() = MissionAdaptationOwner(missions, history, adapter, network, backgroundScope)

    /** A finished Mission Session awaiting review, then reviewed as [outcome]. */
    private suspend fun reviewed(outcome: SessionOutcome, note: String? = "Fälle unklar"): app.focusmodeplus.core.model.SessionId {
        missions.create(mission)
        missions.create(other)
        val r = Fixtures.result("session_m1").copy(type = SessionType.MISSION, missionLink = MissionLink(mission.id, mission.steps[0].id), endReason = SessionEndReason.AWAITING_USER_REVIEW, outcome = SessionOutcome.PARTIAL)
        history.finalize(r)
        history.applyMissionReview(r.sessionId, MissionReview(outcome, 2, note))
        return r.sessionId
    }

    private val adapted = MissionAdaptation("Kürzere Blöcke", "Fälle zuerst üben", listOf(AdaptedStep("Fälle üben", "3 Fälle lösen", 20, "Unsicherheit"), AdaptedStep("Wiederholen", "Karteikarten", 15, "")))

    @Test fun validAnswerReplacesOnlyOpenStepsOfThisMission() = runTest {
        val id = reviewed(SessionOutcome.PARTIAL)
        val owner = owner()
        assertTrue(owner.canAdapt(id))
        assertTrue(owner.adapt(id))
        runCurrent()
        val request = adapter.requests.single()
        assertEquals(mission.id.value, request.missionId)
        assertEquals("partial", request.outcome)
        assertEquals("Fälle unklar", request.feedback)
        assertEquals(3, request.remainingSteps.size)
        assertEquals(listOf("session_m1"), request.history.map { it.sessionId }) // this Mission's progress only
        assertTrue(request.remainingSteps.none { it.title == other.steps[0].title && it.id.startsWith("mission_other") })
        adapter.respond(AdaptationResult.Success(adapted))
        runCurrent()
        val m = missions.getMission(mission.id)!!
        assertEquals(listOf("Fälle üben", "Wiederholen"), m.steps.map { it.title })
        assertEquals(m.steps[0].id, m.currentStepId)
        assertEquals(other, missions.getMission(other.id)) // other Missions untouched
        assertTrue(owner.run.value is AdaptationRun.Adapted)
        assertFalse(owner.adapt(id)) // idempotent per Session
    }

    @Test fun completedStepsAndHistoryNeverChange() = runTest {
        val id = reviewed(SessionOutcome.COMPLETED)
        val before = missions.getMission(mission.id)!!
        val owner = owner()
        owner.adapt(id)
        runCurrent()
        adapter.respond(AdaptationResult.Success(adapted))
        runCurrent()
        val after = missions.getMission(mission.id)!!
        assertEquals(before.steps.first(), after.steps.first())
        assertEquals(MissionStepStatus.COMPLETED, after.steps.first().status)
        assertEquals(SessionOutcome.COMPLETED, history.getResult(id)!!.outcome)
    }

    @Test fun failureOrOfflineKeepsTheExistingPlan() = runTest {
        val id = reviewed(SessionOutcome.PARTIAL)
        val before = missions.getMission(mission.id)!!
        val owner = owner()
        network.online.value = false
        assertFalse(owner.adapt(id))
        assertEquals(AdaptationRun.Failed(id, GenerationError.Offline), owner.run.value)
        assertTrue(adapter.requests.isEmpty())
        network.online.value = true
        owner.adapt(id)
        runCurrent()
        adapter.respond(AdaptationResult.Failure(GenerationError.Unavailable))
        runCurrent()
        assertEquals(before, missions.getMission(mission.id))
        assertEquals(AdaptationRun.Failed(id, GenerationError.Unavailable), owner.run.value)
    }

    @Test fun aMissionChangedMeanwhileIsNotOverwritten() = runTest {
        val id = reviewed(SessionOutcome.PARTIAL)
        val owner = owner()
        owner.adapt(id)
        runCurrent()
        clock.advanceMinutes(1)
        missions.completeStep(mission.id, mission.steps[0].id) // the Mission moved on in the meantime
        val changed = missions.getMission(mission.id)!!
        adapter.respond(AdaptationResult.Success(adapted))
        runCurrent()
        assertEquals(changed, missions.getMission(mission.id))
        assertEquals(AdaptationRun.Failed(id, GenerationError.StorageFailed), owner.run.value)
    }

    @Test fun anUnreviewedSessionIsNeverAdapted() = runTest {
        missions.create(mission)
        val r = Fixtures.result("session_open").copy(type = SessionType.MISSION, missionLink = MissionLink(mission.id, mission.steps[0].id), endReason = SessionEndReason.AWAITING_USER_REVIEW)
        history.finalize(r)
        assertFalse(owner().adapt(r.sessionId))
        assertTrue(adapter.requests.isEmpty())
    }
}
