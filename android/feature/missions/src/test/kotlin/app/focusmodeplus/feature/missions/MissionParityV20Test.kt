package app.focusmodeplus.feature.missions

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.ai.SourceAnalysis
import app.focusmodeplus.core.domain.mission.DraftAttachment
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.domain.mission.MissionRules
import app.focusmodeplus.core.domain.mission.ScheduleDraft
import app.focusmodeplus.core.model.MissionTheme
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMaterialStore
import app.focusmodeplus.core.testing.FakeMissionGenerator
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.MainDispatcherRule
import app.focusmodeplus.core.testing.PlanFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** v20 Mission gaps: material facts, step tools, rename, camera and dictation (Robolectric, not a device). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class MissionParityV20Test {
    @get:Rule val rule = createComposeRule()
    @get:Rule val main = MainDispatcherRule()
    private val today = LocalDate.parse("2026-11-25")

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun briefing(photo: Boolean, source: SourceAnalysis): BriefingUiState {
        val base = PlanFixtures.plan(3, title = "Mathe-Klausur Ableitungen")
        val plan = base.copy(
            source = source,
            steps = base.steps.mapIndexed { i, s -> if (i == 0) s.copy(requiredTools = listOf("Taschenrechner", "Formelsammlung"), recommendedApps = listOf("GeoGebra")) else s },
        )
        return BriefingUiState(
            loading = false, plan = plan, type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL,
            schedule = ScheduleDraft(LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-03")), theme = MissionTheme.FOREST, today = today, activeCount = 0, hadPhoto = photo,
        )
    }

    @Test fun briefingShowsWhatWasRecognizedInTheMaterialAndTheStepTools() {
        val source = SourceAnalysis(detectedTasks = "Aufgaben 1–6", detectedPages = "S. 40–42", confidencePercent = 82, missingInformation = listOf("Welche Kapitel kommen dran?"), facts = listOf("Thema: Kettenregel"), estimates = listOf("6 Aufgaben à 10 Min."))
        rule.setContent { FocusModeTheme { MissionBriefingScreen(briefing(photo = true, source), {}, {}, {}, {}, {}) } }
        rule.onNodeWithTag("briefing_unclear").performScrollTo()
        rule.onNodeWithText("• Welche Kapitel kommen dran?").assertIsDisplayed()
        rule.onAllNodesWithText("Warum so?")[0].performScrollTo().performClick()
        rule.onNodeWithText("Taschenrechner").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("GeoGebra").assertExists()
        rule.onNodeWithText("AI-Erkennung: hoch").performScrollTo().performClick()
        rule.onNodeWithText("Aus dem Material erkannt").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("• Aufgaben: Aufgaben 1–6").assertExists()
        rule.onNodeWithText("• Thema: Kettenregel").performScrollTo().assertIsDisplayed()
        shot("m20_briefing_material")
    }

    @Test fun anUnreadablePhotoIsSaidPlainlyNeverInvented() {
        rule.setContent { FocusModeTheme { MissionBriefingScreen(briefing(photo = true, SourceAnalysis()), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("Angaben, Erkanntes und Schätzungen").performScrollTo().performClick()
        rule.onNodeWithText("• Im Foto wurde nichts eindeutig erkannt. Der Plan stützt sich auf deine Beschreibung.").performScrollTo().assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasText("Aus dem Material erkannt")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun planShowsToolsOfTheCurrentStepAndRenamesWithoutChangingIdentity() {
        val m = Fixtures.mission("mission_a", title = "Biologie-Klausur", steps = 3, completed = 1).let { it.copy(steps = it.steps.map { s -> if (s.position == 1) s.copy(requiredTools = listOf("Lehrbuch S. 40", "Karteikarten")) else s }) }
        val renamed = mutableListOf<String>()
        val state = MissionDetailUiState(loading = false, item = MissionListItem(m, MissionTheme.BURGUNDY), stepStates = MissionRules.stepStates(m, null, null), canStartCurrent = true, activeCount = 1)
        rule.setContent { FocusModeTheme { MissionPlanScreen(state, {}, PlanCallbacks(onRename = { renamed += it })) } }
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("plan_step_tools"))
        rule.onNodeWithTag("plan_step_tools").assert(hasText("Karteikarten", substring = true))
        shot("m21_plan_tools")
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("plan_rename"))
        rule.onNodeWithTag("plan_rename").performClick()
        rule.onNodeWithText("Der Plan und die bisherige Historie bleiben unverändert.").assertIsDisplayed()
        rule.onNodeWithTag("plan_rename_save").assertIsNotEnabled() // unchanged title
        rule.onNodeWithTag("plan_rename_field").performTextClearance()
        rule.onNodeWithTag("plan_rename_save").assertIsNotEnabled() // empty title
        rule.onNodeWithTag("plan_rename_field").performTextInput("Bio: Zellatmung")
        shot("m22_plan_rename")
        rule.onNodeWithTag("plan_rename_save").assertIsEnabled().performClick()
        assertEquals(listOf("Bio: Zellatmung"), renamed)
    }

    @Test fun cameraAndDictationButtonsAppearOnlyWhenTheSystemOffersThem() {
        var captures = 0
        var dictations = 0
        val state = CreateUiState(loading = false, goalText = "", today = today)
        rule.setContent { FocusModeTheme { MissionCreateScreen(state, {}, {}, CreateCallbacks(onCapturePhoto = { captures++ }, onDictate = { dictations++ })) } }
        rule.onNodeWithTag("create_capture_photo").performClick()
        rule.onNodeWithTag("create_dictate").performClick()
        assertEquals(1, captures)
        assertEquals(1, dictations)
        shot("m23_create_inputs")
    }

    @Test fun noSystemInputsMeansNoButtons() {
        rule.setContent { FocusModeTheme { MissionCreateScreen(CreateUiState(loading = false, today = today), {}, {}, CreateCallbacks()) } }
        assertTrue(rule.onAllNodes(hasTestTag("create_capture_photo")).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodes(hasTestTag("create_dictate")).fetchSemanticsNodes().isEmpty())
    }

    // ---------------------------------------------------------------- ViewModel

    private val clock = FakeClock()
    private val missions = FakeMissionRepository(clock)
    private val material = FakeMaterialStore()

    private fun TestScope.vm(saved: SavedStateHandle = SavedStateHandle()): MissionCreateViewModel {
        val owner = MissionGenerationOwner(missions, FakeMissionGenerator(), material, FakePreferencesRepository(), FakeNetworkStatus(), clock, backgroundScope)
        return MissionCreateViewModel(saved, owner, missions, material, FakeNetworkStatus(), clock).also { it.uiState.launchIn(backgroundScope) }
    }

    @Test fun dictatedTextLandsInTheEditableGoalOnly() = runTest {
        val vm = vm()
        runCurrent()
        vm.onGoalChange("Mathe-Klausur")
        vm.onDictated("über Ableitungen\u0007 am Freitag")
        runCurrent()
        assertEquals("Mathe-Klausur über Ableitungen  am Freitag", vm.uiState.first { !it.loading }.goalText)
        vm.onDictated(null)
        vm.onDictated("   ")
        runCurrent()
        assertEquals("Mathe-Klausur über Ableitungen  am Freitag", vm.uiState.first { !it.loading }.goalText)
        assertNull("nothing is sent before Mission planen", missions.getDraft()?.plan)
    }

    @Test fun cameraTargetSurvivesProcessDeathAndFeedsThePhotoPipeline() = runTest {
        val saved = SavedStateHandle()
        val first = vm(saved)
        runCurrent()
        var launched: String? = null
        first.requestCapture { launched = it }
        runCurrent()
        assertEquals(material.captureTargetValue, launched)
        // The camera app is open; the process is killed and recreated with the saved state.
        material.prepareResult = app.focusmodeplus.core.domain.ai.MaterialPreparation.Ready(DraftAttachment(200_000, "image/jpeg", 1800, 1350))
        val second = vm(saved)
        runCurrent()
        second.onCaptured(success = true)
        runCurrent()
        assertTrue(second.uiState.first { !it.loading }.attachment != null)
        assertEquals(1, material.captureRequests)
    }

    @Test fun cancelledCameraKeepsTheDraftUnchanged() = runTest {
        val vm = vm()
        runCurrent()
        vm.requestCapture { }
        runCurrent()
        vm.onCaptured(success = false)
        runCurrent()
        assertNull(vm.uiState.first { !it.loading }.attachment)
    }
}
