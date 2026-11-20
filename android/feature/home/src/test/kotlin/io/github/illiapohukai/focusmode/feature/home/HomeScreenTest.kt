package io.github.illiapohukai.focusmode.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.model.MissionTheme
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
        rule.onNodeWithText("Schritt starten · 25 Min.").performClick()
        assertEquals("mission_a" to "step_a", started)
        rule.onNodeWithTag("home_session_banner").performClick()
        assertEquals(listOf("session"), opened)
    }

    @Test fun emptyHomeOffersMissionsAndQuickFocus() {
        rule.setContent { FocusModeTheme { HomeScreen(HomeUiState(loading = false), actions, { _, _ -> }) } }
        rule.onNodeWithText("Bereit für ein neues Ziel?").assertIsDisplayed()
        rule.onNodeWithTag("home_quick_focus").performClick()
        assertEquals(listOf("quick"), opened)
    }
}
