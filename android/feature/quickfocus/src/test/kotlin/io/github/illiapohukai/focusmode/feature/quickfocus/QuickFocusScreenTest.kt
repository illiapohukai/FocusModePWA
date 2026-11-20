package io.github.illiapohukai.focusmode.feature.quickfocus

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class QuickFocusScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dialAccessibilityActionsStepByFiveAndStartIsOneTap() {
        var minutes = 25
        var starts = 0
        var asked = false
        rule.setContent {
            FocusModeTheme {
                QuickFocusScreen(
                    QuickFocusUiState(minutes = minutes, notificationPrompt = NotificationPrompt.ASK, protectionSummary = "Standard-Fokus · Fokus-Hinweis noch nicht erlaubt"),
                    QuickFocusActions({}, {}, {}, {}),
                    onMinutes = { minutes = it }, onTask = {}, onMode = {}, onStart = { starts++ }, onAllowNotifications = { asked = true },
                )
            }
        }
        rule.onNodeWithTag("duration_dial").performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        assertEquals(40, minutes) // snapped to the 5-minute contract
        rule.onNodeWithText("Erlauben").performClick()
        assertTrue(asked)
        rule.onNodeWithTag("quick_start").performClick()
        assertEquals(1, starts)
    }

    @Test fun startIsDisabledWhileLaunching() {
        rule.setContent {
            FocusModeTheme { QuickFocusScreen(QuickFocusUiState(starting = true), QuickFocusActions({}, {}, {}, {}), {}, {}, {}, {}, {}) }
        }
        rule.onNodeWithText("Richte deine Sitzung ein.").assertIsDisplayed()
        rule.onNodeWithTag("quick_start").assertIsNotEnabled()
    }
}
