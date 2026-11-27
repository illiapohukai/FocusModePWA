package app.focusmodeplus.feature.missions

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.designsystem.theme.LocalMotion
import app.focusmodeplus.core.designsystem.theme.MotionTokens
import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.mission.MissionCalendar
import app.focusmodeplus.core.domain.mission.MissionRules
import app.focusmodeplus.core.domain.mission.MissionSchedulePlanner
import app.focusmodeplus.core.domain.mission.ScheduleDraft
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionTheme
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.PlanFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Mission screens on Robolectric (not a device). Screenshots go to build/screenshots/ for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class MissionScreensTest {
    @get:Rule val rule = createComposeRule()
    private val today = LocalDate.parse("2026-11-25")

    /** The operator runs a frame loop, so those tests drive the clock by hand (like the Cosmic Core). */
    private fun pump(frames: Int) = repeat(frames) { rule.mainClock.advanceTimeByFrame(); Thread.sleep(4) }

    private fun pumpUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within $timeoutMs ms" }
            pump(1)
        }
    }

    private fun shot(name: String) {
        if (rule.mainClock.autoAdvance) rule.waitForIdle() else pump(30)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun createState(schedule: ScheduleDraft = ScheduleDraft(), activeCount: Int = 0, expanded: Boolean = false): CreateUiState {
        val existing = Fixtures.mission("mission_a", title = "Mathe-Klausur", deadline = LocalDate.parse("2026-12-03"))
        val byDate = MissionCalendar.plannerEvents(MissionCalendar.events(listOf(existing), emptyList(), mapOf(existing.id to MissionTheme.FOREST), ZoneId.of("Europe/Berlin")))
        return CreateUiState(
            loading = false,
            goalText = "Biologie-Klausur über Zellatmung am 10. Dezember, Kapitel 4–6",
            type = MissionType.EXAM,
            confidence = GenerationConfidence.PARTIAL,
            schedule = schedule,
            validation = MissionSchedulePlanner.validate(schedule, today),
            today = today,
            planner = PlannerUi(
                expanded = expanded, month = YearMonth.of(2026, 12),
                days = MissionCalendar.monthDays(YearMonth.of(2026, 12), byDate, today) { d ->
                    if (d == schedule.deadline) listOf("Frist der neuen Mission") else emptyList()
                },
                focused = schedule.deadline, previousEnabled = true,
                detailTitle = schedule.deadline?.let { MissionCalendar.dayTitle(it, today) },
                rows = listOf(PlannerRow("Neue Mission", "Frist", "in 15 Tagen", null, null)),
            ),
            deadlineSummary = schedule.deadline?.let { "In 15 Tagen · 1 weitere Frist im Zeitraum" },
            recommendedMode = ScheduleStartMode.ONE_WEEK_BEFORE,
            startPreviews = ScheduleStartMode.entries.associateWith { MissionSchedulePlanner.previewStart(schedule, it, today) },
            activeCount = activeCount,
        )
    }

    private val schedule = ScheduleDraft(LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-03"), ScheduleStartMode.ONE_WEEK_BEFORE)

    @Test fun creationPlansOnceAndShowsTheZeitplan() {
        var plans = 0
        rule.setContent { FocusModeTheme { MissionCreateScreen(createState(schedule), {}, {}, CreateCallbacks(onPlan = { plans++ })) } }
        rule.onNodeWithText("Was ist dein Ziel heute?").assertIsDisplayed()
        shot("m01_create")
        rule.onNodeWithTag("create_plan").assertIsEnabled().performClick()
        assertEquals(1, plans)
        rule.onNodeWithTag("mission_create").performScrollToNode(hasTestTag("start_mode_one_week_before"))
        rule.onNodeWithTag("start_mode_one_week_before").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithText("1 Woche vorher").assertIsDisplayed()
        shot("m02_create_schedule")
    }

    @Test fun inlineCalendarMarksExistingDeadlinesWithoutSelectingThem() {
        val tapped = mutableListOf<LocalDate>()
        rule.setContent { FocusModeTheme { MissionCreateScreen(createState(schedule, expanded = true), {}, {}, CreateCallbacks(onDay = { tapped += it })) } }
        rule.onNodeWithTag("mission_create").performScrollToNode(hasTestTag("calendar_day_2026-12-03"))
        val existing = rule.onNodeWithTag("calendar_day_2026-12-03")
        val label = existing.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(label, label.contains("Prüfung: Mathe-Klausur"))
        existing.performClick()
        assertEquals(listOf(LocalDate.parse("2026-12-03")), tapped) // the screen decides; the VM opens details first
        val newDeadline = rule.onNodeWithTag("calendar_day_2026-12-10").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(newDeadline.contains("Frist der neuen Mission"))
        shot("m03_create_calendar")
    }

    @Test fun limitReachedExplainsAndBlocksPlanning() {
        rule.setContent { FocusModeTheme { MissionCreateScreen(createState(activeCount = 2), {}, {}, CreateCallbacks()) } }
        rule.onNodeWithTag("create_limit").assertIsDisplayed()
        rule.onNodeWithTag("create_plan").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun creationAtCompactWidthAndLargeFontKeepsThePlanActionReachable() {
        rule.setContent { FocusModeTheme { MissionCreateScreen(createState(schedule), {}, {}, CreateCallbacks()) } }
        rule.onNodeWithTag("create_plan").assertIsDisplayed()
        shot("m04_create_320dp_font160")
    }

    @Test fun generationWorksThenReportsSuccessOnce() {
        var cancelled = 0
        var shown = 0
        val state = androidx.compose.runtime.mutableStateOf(GenerateUiState(GenerationPhase.WORKING))
        rule.mainClock.autoAdvance = false
        rule.setContent { FocusModeTheme { MissionGenerateScreen(state.value, { cancelled++ }, { shown++ }) } }
        pump(120) // the sprite sheets decode off the main thread meanwhile
        rule.onNodeWithText("Deine Mission entsteht").assertIsDisplayed()
        rule.onNodeWithTag("generate_status").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite))
        rule.onNodeWithTag("mission_operator").assertExists() // decorative art, no semantics of its own
        shot("m05_generation")
        rule.onNodeWithTag("generate_cancel").performClick()
        assertEquals(1, cancelled)
        assertEquals(0, shown) // no success while the request is pending
        state.value = GenerateUiState(GenerationPhase.SUCCESS)
        pumpUntil { shown > 0 }
        rule.onNodeWithText("Deine Mission ist bereit").assertIsDisplayed()
        pump(200)
        assertEquals(1, shown) // the success sequence never restarts
    }

    @Test fun reducedMotionShowsAStillOperatorAndStillCompletes() {
        var shown = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FocusModeTheme { CompositionLocalProvider(LocalMotion provides MotionTokens.Reduced) { MissionGenerateScreen(GenerateUiState(GenerationPhase.SUCCESS), {}, { shown++ }) } }
        }
        pumpUntil { shown > 0 }
        pump(100)
        assertEquals(1, shown)
        shot("m06_generation_reduced_motion")
    }

    private fun briefingState(activeCount: Int = 1) = BriefingUiState(
        loading = false, plan = PlanFixtures.plan(4, title = "Biologie-Klausur Zellatmung"), type = MissionType.EXAM, confidence = GenerationConfidence.PARTIAL,
        schedule = schedule, theme = MissionTheme.EMBER, today = today, activeCount = activeCount,
    )

    @Test fun briefingAcceptsOnceAndRespectsTheLimit() {
        var accepted = 0
        val limit = androidx.compose.runtime.mutableStateOf(1)
        rule.setContent { FocusModeTheme { MissionBriefingScreen(briefingState(limit.value), {}, { accepted++ }, {}, {}, {}) } }
        rule.onNodeWithText("Dein Plan ist bereit.").assertIsDisplayed()
        rule.onNodeWithText("ERSTER SCHRITT").performScrollTo().assertIsDisplayed()
        shot("m07_briefing")
        rule.onNodeWithTag("briefing_accept").performClick()
        assertEquals(1, accepted)
        limit.value = 2
        rule.onNodeWithTag("briefing_accept").assertIsNotEnabled()
    }

    private fun planState(running: Boolean = false): MissionDetailUiState {
        val m = Fixtures.mission("mission_a", title = "Biologie-Klausur: Zellatmung, Glykolyse und Citratzyklus", steps = 4, completed = 1).copy(summary = "Erst verstehen, dann üben.")
        val link = if (running) MissionLink(m.id, m.steps[1].id) else null
        return MissionDetailUiState(
            loading = false, item = MissionListItem(m, MissionTheme.FOREST), stepStates = MissionRules.stepStates(m, link, null),
            canStartCurrent = !running, activeCount = 1,
        )
    }

    @Test fun planMakesTheCurrentStepDominantAndStartsIt() {
        var started = 0
        rule.setContent { FocusModeTheme { MissionPlanScreen(planState(), {}, PlanCallbacks(onStart = { started++ })) } }
        rule.onNodeWithTag("plan_hero").assertIsDisplayed()
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("mission_start_step"))
        rule.onNodeWithText("Schritt 2 starten").assertIsDisplayed().performClick()
        assertEquals(1, started)
        val step = rule.onNodeWithTag("plan_step_0").fetchSemanticsNode().config
        assertEquals(0, step[SemanticsProperties.CollectionItemInfo].rowIndex) // ordered semantics
        shot("m08_plan")
    }

    @Test fun aRunningStepOffersTheSessionInsteadOfASecondStart() {
        rule.setContent { FocusModeTheme { MissionPlanScreen(planState(running = true), {}, PlanCallbacks()) } }
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("plan_open_session"))
        rule.onNodeWithTag("plan_open_session").assertIsDisplayed()
        rule.onNodeWithTag("mission_start_step").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun planInLandscape() {
        rule.setContent { FocusModeTheme { MissionPlanScreen(planState(), {}, PlanCallbacks()) } }
        rule.onNodeWithTag("plan_hero").assertIsDisplayed()
        shot("m09_plan_landscape")
    }

    @Test
    @Config(qualifiers = "w840dp-h1100dp-xhdpi")
    fun planOnTablet() {
        rule.setContent { FocusModeTheme { MissionPlanScreen(planState(), {}, PlanCallbacks()) } }
        rule.onNodeWithTag("plan_hero").assertIsDisplayed()
        shot("m10_plan_tablet")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun planAtCompactWidthAndLargeFont() {
        rule.setContent { FocusModeTheme { MissionPlanScreen(planState(), {}, PlanCallbacks()) } }
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("mission_start_step"))
        rule.onNodeWithTag("mission_start_step").assertIsDisplayed()
        shot("m11_plan_320dp_font160")
    }

    @Test fun completedMissionShowsNoStartAction() {
        val m = Fixtures.mission("mission_done", steps = 2, completed = 2, status = MissionStatus.COMPLETED)
        rule.setContent {
            FocusModeTheme { MissionPlanScreen(MissionDetailUiState(loading = false, item = MissionListItem(m, MissionTheme.AUBERGINE), stepStates = MissionRules.stepStates(m, null, null)), {}, PlanCallbacks()) }
        }
        rule.onNodeWithText("Mission abgeschlossen").assertIsDisplayed()
        rule.onNodeWithTag("mission_start_step").assertDoesNotExist()
        shot("m12_plan_completed")
    }

    @Test fun missionsListAtTheLimitExplainsInsteadOfCreating() {
        val items = listOf(Fixtures.mission("mission_a"), Fixtures.mission("mission_b")).map { MissionListItem(it, MissionTheme.AUBERGINE) }
        rule.setContent { FocusModeTheme { MissionsScreen(MissionsUiState(active = items, loading = false), MissionsActions({}, {}, {})) } }
        rule.onNodeWithTag("missions_limit").assertIsDisplayed()
        rule.onNodeWithTag("missions_create").assertDoesNotExist()
    }
}
