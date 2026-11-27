package app.focusmodeplus.feature.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.model.LocalProgress
import app.focusmodeplus.core.model.MissionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val opened = mutableListOf<String>()
    private val actions = HomeActions({ opened += "quick" }, { opened += "session" }, { opened += "mission:$it" }, { opened += "missions" }, { opened += "history" }, { opened += "result:$it" }, { opened += "schedules" })

    @Test fun showsMissionBannerAndStartsStep() {
        var started: Pair<String, String>? = null
        rule.setContent {
            FocusModeTheme {
                HomeScreen(
                    HomeUiState(
                        dateLabel = "DONNERSTAG, 19. NOV.", online = false,
                        banner = SessionBanner.Running(18, "Lineare Funktionen"),
                        mission = MissionCard("mission_a", "Mathe-Klausur", "Mathematik", "SCHRITT 01 / 03", "Kapitel 1", "Lesen", 25, "step_a", 33, MissionTheme.FOREST),
                        loading = false,
                    ),
                    actions, { m, s -> started = m to s },
                )
            }
        }
        rule.onNodeWithText("Mathe-Klausur").assertIsDisplayed()
        rule.onNodeWithContentDescription("Offline. Fokus funktioniert ohne Internet.").assertIsDisplayed()
        rule.onNodeWithText("Noch 18 Min. · Lineare Funktionen").assertIsDisplayed()
        rule.onNodeWithTag("home_start_step").performClick()
        assertEquals("mission_a" to "step_a", started)
        rule.onNodeWithTag("home_session_banner").performClick()
        assertEquals(listOf("session"), opened)
    }

    @Test fun emptyHomeOffersMissionsAndQuickFocus() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home_mission_empty").assertIsDisplayed()
        rule.onNodeWithText("Was möchtest du heute fokussiert schaffen?").assertIsDisplayed()
        rule.onNodeWithTag("home_create_mission").performClick() // HomeActions default: creation opens via onOpenMissions here
        assertEquals(listOf("missions"), opened)
        rule.onNodeWithTag("home_quick_focus").performClick()
        assertEquals(listOf("missions", "quick"), opened)
        // No fake Mission data in the empty state, and no "several Missions" affordance.
        rule.onNodeWithTag("home_mission").assertDoesNotExist()
        rule.onNodeWithTag("home_mission_more").assertDoesNotExist()
    }

    @Test fun homeStatesAreDistinctForOneAndSeveralMissions() {
        val mission = MissionCard("mission_a", "Mathe-Klausur", "Mathematik", "SCHRITT 01 / 03", "Kapitel 1", "Lesen", 25, "step_a", 33, MissionTheme.AUBERGINE)
        val second = MissionCard("mission_b", "Referat", "Geschichte", "SCHRITT 02 / 04", "Quellen", "Lesen", 30, "step_b", 25, MissionTheme.FOREST, primary = false)
        val missions = mutableStateOf(listOf(mission))
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false, mission = mission, activeMissionCount = missions.value.size, missions = missions.value), actions, { _, _ -> }) } }
        rule.onNodeWithTag("home_mission").assertIsDisplayed()
        rule.onNodeWithTag("home_mission_more").assertDoesNotExist()
        missions.value = listOf(mission, second)
        rule.onNodeWithTag("home_mission_carousel").assertIsDisplayed()
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_mission_more"))
        rule.onNodeWithTag("home_mission_more").assertIsDisplayed().performClick()
        assertEquals(listOf("missions"), opened)
    }

    @Test fun todayStatsAndEmptyRemindersFollowThePwaStructure() {
        rule.setContent {
            FocusModeTheme {
                HomeScreen(HomeUiState(loading = false, progress = LocalProgress.EMPTY.copy(todayMinutes = 42, todaySessions = 2)), actions, { _, _ -> })
            }
        }
        rule.onNodeWithText("QUICK FOCUS").assertExists()
        rule.onNodeWithText("Woche: 0 Sitzungen · 0 Min.").assertExists()
        rule.onNodeWithContentDescription("42 Minuten Fokus heute").assertExists()
        rule.onNodeWithText("SITZUNGEN").assertExists()
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_reminders"))
        rule.onNodeWithText("Keine anstehenden Erinnerungen").assertIsDisplayed()
        rule.onNodeWithTag("home_reminders").performClick()
        assertEquals(listOf("schedules"), opened)
    }
}
