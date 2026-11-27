package app.focusmodeplus.feature.home

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.domain.mission.CalendarEventKind
import app.focusmodeplus.core.domain.mission.HomeMissionMode
import app.focusmodeplus.core.domain.mission.MissionCalendar
import app.focusmodeplus.core.model.MissionTheme
import app.focusmodeplus.core.testing.Fixtures
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class HomeMissionStatesTest {
    @get:Rule val rule = createComposeRule()
    private val opened = mutableListOf<String>()
    private val actions = HomeActions(
        { opened += "quick" }, { opened += "session" }, { opened += "mission:$it" }, { opened += "missions" }, { opened += "history" },
        { opened += "result:$it" }, { opened += "schedules" }, onCreateMission = { opened += "create" },
    )
    private val today = LocalDate.parse("2026-11-25")

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val one = MissionCard(
        "mission_a", "Biologie-Klausur: Zellatmung und Photosynthese im Vergleich", "Biologie · bis Do., 10. Dez.", "SCHRITT 02 / 04",
        "Glykolyse Schritt für Schritt", "Die zehn Reaktionen mit Energiebilanz", 25, "step_a", 25, MissionTheme.FOREST, completedSteps = 1, totalSteps = 4,
    )
    private val two = MissionCard("mission_b", "Referat Weimarer Republik", "Geschichte", "SCHRITT 01 / 03", "Quellen sichten", null, 30, "step_b", 0, MissionTheme.EMBER, 0, 3, primary = false)

    private fun calendar(): HomeCalendar {
        val m = Fixtures.mission("mission_a", title = "Biologie-Klausur", deadline = LocalDate.parse("2026-12-10")).copy(preparationStartDate = LocalDate.parse("2026-11-25"))
        val events = MissionCalendar.events(listOf(m), emptyList(), mapOf(m.id to MissionTheme.FOREST), ZoneId.of("Europe/Berlin"))
        val byDate = MissionCalendar.byDate(events)
        return HomeCalendar(
            empty = false, month = YearMonth.of(2026, 11), today = today, selected = today, days = MissionCalendar.monthDays(YearMonth.of(2026, 11), byDate, today),
            nextLine = "Prüfung · Biologie-Klausur · in 15 Tagen", selectedTitle = "Heute · 25. November",
            selectedEvents = byDate[today].orEmpty().map { CalendarEventItem(it.missionId.value, it.title, MissionCalendar.chip(it), MissionCalendar.detail(it, today), it.theme, it.kind) },
        )
    }

    @Test fun returningUserGetsTheCompactCardNotTheOnboarding() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, mode = HomeMissionMode.RETURNING_EMPTY), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home_mission_return").assertIsDisplayed()
        rule.onNodeWithTag("home_mission_empty").assertDoesNotExist()
        rule.onNodeWithText("Bereit für ein neues Ziel?").assertIsDisplayed()
        rule.onNodeWithTag("home_create_mission").performClick()
        assertEquals(listOf("create"), opened)
        shot("h01_home_returning_empty")
    }

    @Test fun oneMissionPutsProgressLeftAndTheStartActionRight() {
        var started: Pair<String, String>? = null
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, missions = listOf(one), activeMissionCount = 1, calendar = calendar()), actions, { m, s -> started = m to s }) } }
        val progress = rule.onNodeWithTag("home_mission_progress").fetchSemanticsNode()
        val start = rule.onNodeWithTag("home_start_step").fetchSemanticsNode()
        assertTrue(progress.boundsInRoot.right <= start.boundsInRoot.left) // progress left, action right
        assertTrue(start.boundsInRoot.height >= 47f * rule.density.density) // ≥ 48 dp touch target
        assertTrue(progress.config[SemanticsProperties.ContentDescription].single().contains("1 von 4 Schritten"))
        rule.onNodeWithTag("home_start_step").performClick()
        assertEquals("mission_a" to "step_a", started)
        shot("h02_home_one_mission")
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_calendar"))
        shot("h03_home_calendar")
    }

    @Test fun twoMissionsAreASwipeablePagerWithStableTones() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, missions = listOf(one, two), activeMissionCount = 2), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home_mission_carousel").assertIsDisplayed()
        rule.onNodeWithText("HAUPTMISSION").assertIsDisplayed()
        shot("h04_home_two_missions")
        rule.onNodeWithTag("home_mission_carousel").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("Referat Weimarer Republik").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("home_start_step").fetchSemanticsNodes().size <= 2) // never a third card
    }

    @Test fun emptyCalendarIsACompactCentredNote() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, mode = HomeMissionMode.FIRST_TIME), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_calendar_empty"))
        rule.onNodeWithText("Noch keine Missionstermine").assertIsDisplayed()
        rule.onNodeWithTag("month_calendar").assertDoesNotExist()
    }

    @Test fun calendarEventsOpenTheirMission() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, missions = listOf(one), activeMissionCount = 1, calendar = calendar()), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_calendar_event"))
        assertEquals(CalendarEventKind.START, calendar().selectedEvents.single().kind)
        rule.onNodeWithTag("home_calendar_event").performClick()
        assertEquals(listOf("mission:mission_a"), opened)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun longGermanTitlesFitAtCompactWidthAndLargeFont() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, missions = listOf(one, two), activeMissionCount = 2), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home_mission_carousel").assertIsDisplayed()
        val page = androidx.compose.ui.test.hasAnyAncestor(hasTestTag("home_mission_page_0"))
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_start_step") and page) // the tall hero continues below the fold
        shot("h05_home_two_missions_320dp_font160")
        val start = rule.onNode(hasTestTag("home_start_step") and page).assertIsDisplayed().fetchSemanticsNode()
        val progress = rule.onNode(hasTestTag("home_mission_progress") and page).assertIsDisplayed().fetchSemanticsNode()
        assertTrue(start.boundsInRoot.height >= 47f * rule.density.density) // touch target kept
        assertTrue(progress.boundsInRoot.width > 40f * rule.density.density) // the progress readout is never squeezed away
        shot("h05_home_two_missions_320dp_font160")
    }
}
