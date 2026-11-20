package io.github.illiapohukai.focusmode.feature.result

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.model.LocalStreak
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ResultScreenTest {
    @get:Rule val rule = createComposeRule()
    private val actions = ResultActions({}, {}, {})

    @Test fun unknownSessionIsSafe() {
        rule.setContent { FocusModeTheme { ResultScreen(ResultUiState(loading = false, notFound = true), actions) { _, _ -> } } }
        rule.onNodeWithText("Ergebnis nicht gefunden").assertIsDisplayed()
    }

    @Test fun completedResultShowsLocalStreakBoundary() {
        rule.setContent {
            FocusModeTheme {
                ResultScreen(ResultUiState(loading = false, headline = "Fokus abgeschlossen", eyebrow = "Abgeschlossen", minutes = 25, plannedMinutes = 25, task = "Lesen", streak = LocalStreak(3, 5, true)), actions) { _, _ -> }
            }
        }
        rule.onNodeWithText("3 Tage in Folge").assertIsDisplayed()
        rule.onNodeWithText("Beste Serie: 5 · lokal berechnet, nicht verifiziert").assertIsDisplayed()
    }

    @Test fun missionReviewSubmitsStepOutcome() {
        var review: Pair<Boolean, Int?>? = null
        rule.setContent { FocusModeTheme { ResultScreen(ResultUiState(loading = false, awaitingReview = true, minutes = 25, plannedMinutes = 25, task = "Kapitel 1"), actions) { d, r -> review = d to r } } }
        rule.onNodeWithTag("review_open").performClick()
        assertEquals(false to null, review)
    }
}
