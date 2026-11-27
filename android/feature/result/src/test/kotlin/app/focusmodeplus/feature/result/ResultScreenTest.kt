package app.focusmodeplus.feature.result

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.model.LocalStreak
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
        rule.setContent { FocusModeTheme { ResultScreen(ResultUiState(loading = false, notFound = true), actions, ResultCallbacks()) } }
        rule.onNodeWithText("Ergebnis nicht gefunden").assertIsDisplayed()
    }

    @Test fun completedResultShowsLocalStreakBoundary() {
        rule.setContent {
            FocusModeTheme {
                ResultScreen(ResultUiState(loading = false, headline = "Fokus abgeschlossen", eyebrow = "Abgeschlossen", minutes = 25, plannedMinutes = 25, task = "Lesen", streak = LocalStreak(3, 5, true)), actions, ResultCallbacks())
            }
        }
        rule.onNodeWithText("3 Tage in Folge").assertIsDisplayed()
        rule.onNodeWithText("Beste Serie: 5 · lokal berechnet, nicht verifiziert").assertIsDisplayed()
    }

    @Test fun missionReviewSubmitsStepOutcome() {
        val calls = mutableListOf<String>()
        rule.setContent {
            FocusModeTheme {
                ResultScreen(
                    ResultUiState(loading = false, awaitingReview = true, minutes = 25, plannedMinutes = 25, task = "Kapitel 1"), actions,
                    ResultCallbacks(onOutcome = { calls += "outcome:${it.wire}" }, onSubmit = { calls += "submit" }),
                )
            }
        }
        rule.onNodeWithTag("review_partial").performClick()
        rule.onNodeWithTag("review_submit").performScrollTo().performClick()
        assertEquals(listOf("outcome:partial", "submit"), calls)
    }
}
