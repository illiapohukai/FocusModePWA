package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationResult
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMaterialStore
import io.github.illiapohukai.focusmode.core.testing.FakeMissionGenerator
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakeNetworkStatus
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.PlanFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissionGenerationOwnerTest {
    private val clock = FakeClock()
    private val missions = FakeMissionRepository(clock)
    private val generator = FakeMissionGenerator()
    private val material = FakeMaterialStore()
    private val prefs = FakePreferencesRepository()
    private val network = FakeNetworkStatus()

    private fun TestScope.owner() = MissionGenerationOwner(missions, generator, material, prefs, network, clock, backgroundScope)

    private suspend fun MissionGenerationOwner.ready(goal: String = "Mathe-Klausur vorbereiten") {
        openDraft()
        edit { it.copy(goalText = goal, type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL) }
    }

    @Test fun successBecomesABriefingOnlyAfterTheValidatedAnswer() = runTest {
        val owner = owner()
        owner.ready()
        assertTrue(owner.start() is GenerationStart.Started)
        runCurrent()
        assertEquals(MissionDraftPhase.GENERATING, missions.getDraft()!!.phase)
        assertNull(missions.getDraft()!!.plan)
        generator.respond(GenerationResult.Success(PlanFixtures.plan(3)))
        runCurrent()
        val draft = missions.getDraft()!!
        assertEquals(MissionDraftPhase.BRIEFING, draft.phase)
        assertEquals(3, draft.plan!!.steps.size)
        assertTrue(missions.missions.value.isEmpty()) // no Mission before "Mission übernehmen"
        assertTrue(owner.run.value is GenerationRun.Succeeded)
    }

    @Test fun cancelKeepsInputAndDropsALateResponse() = runTest {
        val owner = owner()
        owner.ready("Referat Geschichte")
        generator.ignoreCancellation = true // the answer is already on the wire
        owner.start()
        runCurrent()
        runCurrent()
        owner.cancel()
        val afterCancel = missions.getDraft()!!
        assertEquals(MissionDraftPhase.EDITING, afterCancel.phase)
        assertEquals("Referat Geschichte", afterCancel.goalText)
        generator.respond(GenerationResult.Success(PlanFixtures.plan(2)))
        runCurrent()
        assertEquals(MissionDraftPhase.EDITING, missions.getDraft()!!.phase)
        assertNull(missions.getDraft()!!.plan)
        assertTrue(missions.missions.value.isEmpty())
        assertTrue(owner.run.value is GenerationRun.Cancelled)
    }

    @Test fun aSecondTapNeverSendsASecondRequest() = runTest {
        val owner = owner()
        owner.ready()
        owner.start()
        runCurrent()
        assertEquals(GenerationStart.Rejected(GenerationError.AlreadyInFlight), owner.start())
        runCurrent()
        assertEquals(1, generator.requests.size)
    }

    @Test fun localProblemsNeverReachTheWorker() = runTest {
        val owner = owner()
        owner.openDraft()
        assertEquals(GenerationStart.Rejected(GenerationError.EmptyInput), owner.start())
        owner.edit { it.copy(goalText = "Ziel") }
        assertEquals(GenerationStart.Rejected(GenerationError.InvalidRequest), owner.start()) // type and confidence missing
        owner.edit { it.copy(type = MissionType.PROJECT, confidence = GenerationConfidence.CONFIDENT) }
        owner.edit { it.copy(goalText = "x".repeat(6001)) }
        assertEquals(GenerationStart.Rejected(GenerationError.GoalTooLong), owner.start())
        owner.edit { it.copy(goalText = "Ziel") }
        network.online.value = false
        assertEquals(GenerationStart.Rejected(GenerationError.Offline), owner.start())
        assertEquals("Ziel", missions.getDraft()!!.goalText) // input preserved offline
        network.online.value = true
        missions.create(Fixtures.mission("mission_a"))
        missions.create(Fixtures.mission("mission_b"))
        assertEquals(GenerationStart.Rejected(GenerationError.MissionLimitReached), owner.start())
        assertEquals(0, generator.requests.size)
    }

    @Test fun failureKeepsTheDraftAndRetryIsExplicit() = runTest {
        val owner = owner()
        owner.ready()
        owner.start()
        runCurrent()
        generator.respond(GenerationResult.Failure(GenerationError.RateLimited, "req_12345"))
        runCurrent()
        val failed = missions.getDraft()!!
        assertEquals(MissionDraftPhase.FAILED, failed.phase)
        assertEquals(GenerationError.RateLimited, failed.error)
        assertEquals("req_12345", failed.errorRequestId)
        assertEquals("Mathe-Klausur vorbereiten", failed.goalText)
        assertEquals(1, generator.requests.size) // nothing retried automatically
        generator.next = GenerationResult.Success(PlanFixtures.plan(2))
        owner.start()
        runCurrent()
        runCurrent()
        assertEquals(2, generator.requests.size)
        assertEquals(MissionDraftPhase.BRIEFING, missions.getDraft()!!.phase)
    }

    @Test fun aRequestCutByProcessDeathIsMarkedInterruptedNotResent() = runTest {
        val owner = owner()
        owner.ready()
        owner.start()
        runCurrent()
        // New process: a fresh owner over the same stored draft.
        val restarted = MissionGenerationOwner(missions, generator, material, prefs, network, clock, backgroundScope)
        restarted.reconcile()
        assertEquals(MissionDraftPhase.FAILED, missions.getDraft()!!.phase)
        assertEquals(GenerationError.Interrupted, missions.getDraft()!!.error)
        assertEquals(1, generator.requests.size)
    }

    @Test fun acceptingCreatesExactlyOneMissionAndClearsTheDraftAndPhoto() = runTest {
        val owner = owner()
        owner.ready()
        material.stored = io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterial(ByteArray(10), "image/jpeg")
        generator.next = GenerationResult.Success(PlanFixtures.plan(3))
        owner.start()
        runCurrent()
        runCurrent()
        val first = owner.accept()
        val second = owner.accept()
        assertTrue(first is MissionWriteResult.Ok)
        assertTrue(second is MissionWriteResult.Rejected) // the draft is gone; no second Mission
        assertEquals(1, missions.missions.value.size)
        assertEquals(MissionStatus.ACTIVE, missions.missions.value.single().status)
        assertNull(missions.getDraft())
        assertNull(material.stored)
        assertTrue(prefs.state.value.homeOnboardingDone)
        assertEquals((first as MissionWriteResult.Ok).mission.id.value, prefs.state.value.lastSelectedMissionId)
    }

    @Test fun acceptAtTheLimitKeepsTheBriefing() = runTest {
        val owner = owner()
        owner.ready()
        generator.next = GenerationResult.Success(PlanFixtures.plan(2))
        owner.start()
        runCurrent()
        runCurrent()
        missions.create(Fixtures.mission("mission_a"))
        missions.create(Fixtures.mission("mission_b"))
        assertEquals(MissionWriteResult.Rejected("active_mission_limit_reached"), owner.accept())
        assertEquals(MissionDraftPhase.BRIEFING, missions.getDraft()!!.phase)
        assertEquals(2, missions.missions.value.size)
    }

    @Test fun editingAnInputDropsAStalePlanAndIsBlockedWhileGenerating() = runTest {
        val owner = owner()
        owner.ready()
        owner.start()
        runCurrent()
        assertNull(owner.edit { it.copy(goalText = "neu") }) // read-only while the request runs
        generator.respond(GenerationResult.Success(PlanFixtures.plan(2)))
        runCurrent()
        owner.edit { it.copy(goalText = "anderes Ziel") }
        val draft = missions.getDraft()!!
        assertEquals(MissionDraftPhase.EDITING, draft.phase)
        assertNull(draft.plan)
    }

    @Test fun discardRemovesDraftAndPhoto() = runTest {
        val owner = owner()
        owner.ready()
        owner.discard()
        assertNull(missions.getDraft())
        assertEquals(1, material.cleared)
    }
}
