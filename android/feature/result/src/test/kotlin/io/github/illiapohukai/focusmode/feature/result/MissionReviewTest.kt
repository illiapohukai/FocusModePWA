package io.github.illiapohukai.focusmode.feature.result

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class MissionReviewTest {
    @get:Rule val rule = createComposeRule()
    private val actions = ResultActions({}, {}, {})

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val mission = MissionContext(
        missionId = "mission_a", title = "Biologie-Klausur", theme = MissionTheme.FOREST, stepTitle = "Glykolyse", stepNumber = 2, total = 4, completed = 1,
        percent = 25, stepDone = false, nextTitle = "Glykolyse", nextMinutes = 25, missionCompleted = false, missionActive = true,
    )

    private fun review(outcome: SessionOutcome, m: MissionContext = mission) = ResultUiState(
        loading = false, headline = "Wie weit bist du gekommen?", eyebrow = "Ergebnis offen", minutes = 22, plannedMinutes = 25, task = "Glykolyse",
        awaitingReview = true, isMission = true, mission = m, selectedOutcome = outcome,
    )

    @Test fun threeOutcomesRatingAndNoteAreOffered() {
        val calls = mutableListOf<String>()
        rule.setContent {
            FocusModeTheme {
                ResultScreen(review(SessionOutcome.COMPLETED), actions, ResultCallbacks(onOutcome = { calls += it.wire }, onRating = { calls += "rating:$it" }, onSubmit = { calls += "submit" }, onAdapt = { calls += "adapt" }))
            }
        }
        rule.onNodeWithTag("review_completed").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithText("Mission: 1 → 2 von 4 Schritten").assertIsDisplayed()
        shot("r01_mission_review")
        rule.onNodeWithTag("review_aborted").performClick()
        rule.onNodeWithTag("review_rating").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.onNodeWithTag("review_adapt").performScrollTo().performClick()
        rule.onNodeWithTag("review_submit").performScrollTo().performClick()
        assertEquals(listOf("aborted", "rating:5", "adapt", "submit"), calls)
    }

    @Test fun abortedAndPartialSayTheStepStaysOpen() {
        rule.setContent { FocusModeTheme { ResultScreen(review(SessionOutcome.CANCELED), actions, ResultCallbacks()) } }
        rule.onNodeWithText("Abgebrochen · Fortschritt bleibt. Der Schritt bleibt offen.").assertIsDisplayed()
    }

    @Test fun completingTheFinalStepOffersNoAdaptation() {
        val last = review(SessionOutcome.COMPLETED, mission.copy(stepNumber = 4, completed = 3))
        assertFalse(last.canOfferAdaptation)
        assertTrue(review(SessionOutcome.PARTIAL, mission.copy(stepNumber = 4, completed = 3)).canOfferAdaptation)
        rule.setContent { FocusModeTheme { ResultScreen(last, actions, ResultCallbacks()) } }
        rule.onNodeWithTag("review_adapt").assertDoesNotExist()
    }

    @Test fun savingDisablesTheActionsSoAResultIsAppliedOnce() {
        rule.setContent { FocusModeTheme { ResultScreen(review(SessionOutcome.PARTIAL).copy(reviewSaving = true), actions, ResultCallbacks()) } }
        rule.onNodeWithTag("review_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test fun adaptationFailureKeepsProgressAndOffersRetryOrContinue() {
        val calls = mutableListOf<String>()
        val state = review(SessionOutcome.PARTIAL).copy(awaitingReview = false, adaptation = AdaptationUi.Failed(offline = true))
        rule.setContent { FocusModeTheme { ResultScreen(state, actions, ResultCallbacks(onRetryAdapt = { calls += "retry" }, onSkipAdapt = { calls += "skip" })) } }
        rule.onNodeWithText("Keine Internetverbindung", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Dein Fortschritt ist trotzdem sicher.").assertIsDisplayed()
        shot("r02_adaptation_offline")
        rule.onNodeWithTag("adapt_retry").performClick()
        rule.onNodeWithTag("adapt_skip").performClick()
        assertEquals(listOf("retry", "skip"), calls)
    }

    @Test fun afterTheReviewTheMissionProgressIsShown() {
        val opened = mutableListOf<String>()
        val done = review(SessionOutcome.COMPLETED).copy(awaitingReview = false, headline = "Schritt erledigt", mission = mission.copy(completed = 2, percent = 50, stepDone = true, nextTitle = "Citratzyklus"))
        rule.setContent { FocusModeTheme { ResultScreen(done, ResultActions({}, {}, {}, onOpenMission = { opened += it }), ResultCallbacks()) } }
        rule.onNodeWithTag("result_mission_progress").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2 von 4 Schritten erledigt").assertIsDisplayed()
        shot("r03_mission_result")
        rule.onNodeWithTag("result_open_mission").performScrollTo().performClick()
        assertEquals(listOf("mission_a"), opened)
    }
}
