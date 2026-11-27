package app.focusmodeplus.feature.missions

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.ai.GenerationResult
import app.focusmodeplus.core.domain.mission.MissionDraftPhase
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMaterialStore
import app.focusmodeplus.core.testing.FakeMissionGenerator
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.MainDispatcherRule
import app.focusmodeplus.core.testing.PlanFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MissionFlowViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = FakeClock()
    private val missions = FakeMissionRepository(clock)
    private val generator = FakeMissionGenerator()
    private val material = FakeMaterialStore()
    private val prefs = FakePreferencesRepository()
    private val network = FakeNetworkStatus()

    private fun TestScope.owner() = MissionGenerationOwner(missions, generator, material, prefs, network, clock, backgroundScope)

    private fun TestScope.createVm(owner: MissionGenerationOwner, saved: SavedStateHandle = SavedStateHandle()) =
        MissionCreateViewModel(saved, owner, missions, material, network, clock).also { it.uiState.launchIn(backgroundScope) }

    @Test fun formSurvivesProcessRecreationThroughTheRoomDraft() = runTest {
        val owner = owner()
        val vm = createVm(owner)
        runCurrent()
        vm.onGoalChange("Referat über die Weimarer Republik")
        vm.selectType(MissionType.PROJECT)
        vm.selectConfidence(GenerationConfidence.UNCERTAIN)
        advanceTimeBy(1_000)
        runCurrent()
        vm.pick(LocalDate.parse("2026-12-15"))
        runCurrent()
        // New process: empty SavedStateHandle, a fresh owner over the same stored draft.
        val restored = createVm(owner())
        runCurrent()
        val s = restored.uiState.first { !it.loading }
        assertEquals("Referat über die Weimarer Republik", s.goalText)
        assertEquals(MissionType.PROJECT, s.type)
        assertEquals(GenerationConfidence.UNCERTAIN, s.confidence)
        assertEquals(LocalDate.parse("2026-12-15"), s.schedule.deadline)
    }

    @Test fun typingNotYetWrittenSurvivesInSavedState() = runTest {
        val owner = owner()
        val saved = SavedStateHandle(mapOf("goal" to "noch nicht gespeichert"))
        val vm = createVm(owner, saved)
        runCurrent()
        assertEquals("noch nicht gespeichert", vm.uiState.first { !it.loading }.goalText)
        assertEquals("noch nicht gespeichert", missions.getDraft()!!.goalText)
    }

    @Test fun planningSendsOneRequestPerTapAndKeepsInputOnFailure() = runTest {
        val owner = owner()
        val vm = createVm(owner)
        runCurrent()
        vm.onGoalChange("Mathe")
        vm.selectType(MissionType.EXAM)
        vm.selectConfidence(GenerationConfidence.CONFIDENT)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(vm.uiState.value.canPlan)
        vm.plan()
        vm.plan() // a double tap
        runCurrent()
        assertEquals(1, generator.requests.size)
        generator.respond(GenerationResult.Failure(app.focusmodeplus.core.domain.ai.GenerationError.Unavailable, "req_ABCD12"))
        runCurrent()
        val s = vm.uiState.first { it.error != null }
        assertEquals("Mathe", s.goalText)
        assertEquals("req_ABCD12", s.errorRequestId)
    }

    @Test fun briefingAcceptCreatesOneMissionEvenOnADoubleTap() = runTest {
        val owner = owner()
        owner.openDraft()
        owner.edit { it.copy(goalText = "Mathe", type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL) }
        generator.next = GenerationResult.Success(PlanFixtures.plan(3))
        owner.start()
        runCurrent()
        assertEquals(MissionDraftPhase.BRIEFING, missions.getDraft()!!.phase)
        val vm = MissionBriefingViewModel(missions, owner, clock)
        vm.uiState.launchIn(backgroundScope)
        runCurrent()
        assertEquals(3, vm.uiState.first { it.plan != null }.plan!!.steps.size)
        vm.accept()
        vm.accept()
        runCurrent()
        assertEquals(1, missions.missions.value.size)
        assertNull(missions.getDraft())
    }

    @Test fun generationScreenFollowsTheDraftPhase() = runTest {
        val owner = owner()
        owner.openDraft()
        owner.edit { it.copy(goalText = "Mathe", type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL) }
        owner.start()
        runCurrent()
        val vm = MissionGenerateViewModel(missions, owner)
        vm.uiState.launchIn(backgroundScope)
        runCurrent()
        assertEquals(GenerationPhase.WORKING, vm.uiState.value.phase)
        generator.respond(GenerationResult.Success(PlanFixtures.plan(2)))
        runCurrent()
        assertEquals(GenerationPhase.SUCCESS, vm.uiState.value.phase)
        advanceTimeBy(10_000)
        assertTrue(vm.uiState.value.slow)
    }

    @Test fun cancellingFromTheGenerationScreenReturnsAndCreatesNothing() = runTest {
        val owner = owner()
        owner.openDraft()
        owner.edit { it.copy(goalText = "Mathe", type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL) }
        generator.ignoreCancellation = true
        owner.start()
        runCurrent()
        val vm = MissionGenerateViewModel(missions, owner)
        vm.uiState.launchIn(backgroundScope)
        var left = false
        vm.cancel { left = true }
        runCurrent()
        assertTrue(left)
        assertEquals(GenerationPhase.ENDED, vm.uiState.value.phase)
        generator.respond(GenerationResult.Success(PlanFixtures.plan(2))) // too late
        runCurrent()
        assertEquals(MissionDraftPhase.EDITING, missions.getDraft()!!.phase)
        assertTrue(missions.missions.value.isEmpty())
    }
}
