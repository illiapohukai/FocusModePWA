package app.focusmodeplus.feature.focus

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.domain.session.EndBarrier
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.ProtectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class FocusScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: FocusUiState, onEmergency: () -> Unit = {}, onConfirm: () -> Unit = {}) = rule.setContent {
        FocusModeTheme(MotionPreference.REDUCED) {
            FocusScreen(state, {}, {}, onConfirm, {}, onEmergency, {}, {}, {}, {})
        }
    }

    @Test fun runningScreenShowsHonestProtectionBadgeTaskAndOpensTheSafeEndFlow() {
        var requested = 0
        rule.setContent {
            FocusModeTheme(MotionPreference.REDUCED) {
                FocusScreen(
                    FocusUiState(phase = FocusPhase.RUNNING, timerText = "24:57", timerDescription = "Noch 25 Minuten", headline = "Kapitel 3", task = "Kapitel 3", modeLabel = "Kurzer Fokus", plannedMinutes = 25, protectionMode = ProtectionMode.PROTECTED),
                    { requested++ }, {}, {}, {}, {}, {}, {}, {}, {},
                )
            }
        }
        rule.onNodeWithText("FOKUS-SITZUNG").assertIsDisplayed()
        rule.onNodeWithText("25 Min.").assertIsDisplayed()
        rule.onNodeWithText("KURZER FOKUS").assertIsDisplayed()
        // The badge names the real mode; nothing claims that apps are blocked.
        rule.onNodeWithTag("focus_protection_badge").assertIsDisplayed()
        rule.onNodeWithText("Geschützter Fokus").assertIsDisplayed()
        rule.onAllNodesWithText("blockiert", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onNodeWithTag("focus_task_card").assertIsDisplayed()
        rule.onNodeWithTag("focus_end").assertIsDisplayed().performClick()
        assertEquals("the end action only requests the end flow", 1, requested)
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.6f)
    fun timerAndEndActionStayVisibleAtCompactWidthAndLargeFont() {
        show(FocusUiState(phase = FocusPhase.RUNNING, timerText = "59:59", timerDescription = "Noch 60 Minuten", headline = "Sehr lange Aufgabe für den Fokus", task = "Sehr lange Aufgabe für den Fokus", modeLabel = "Kurzer Fokus", plannedMinutes = 60))
        rule.onNodeWithTag("focus_timer").assertIsDisplayed()
        rule.onNodeWithTag("focus_end").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val timer = rule.onNodeWithTag("focus_timer").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("timer stays inside the screen", timer.left >= root.left && timer.right <= root.right)
    }

    @Test fun timerAnnouncesMinutesNotSeconds() {
        show(FocusUiState(phase = FocusPhase.RUNNING, timerText = "17:42", timerDescription = "Noch 18 Minuten", task = "Lesen", modeLabel = "Kurzer Fokus"))
        rule.onNodeWithTag("focus_timer").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Noch 18 Minuten")))
    }

    @Test fun protectedBarrierBlocksEndUntilDelayAndReasonButNeverBlocksEmergencyExit() {
        var emergency = 0
        var confirmed = 0
        val barrier = EndBarrier.forMode(ProtectionMode.PROTECTED)
        show(FocusUiState(phase = FocusPhase.RUNNING, timerText = "10:00", endSheet = EndSheetState(barrier, 3, null, 15)), { emergency++ }, { confirmed++ })
        rule.onNodeWithTag("end_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("end_emergency").assertIsEnabled().performClick()
        assertEquals(1, emergency)
        assertEquals(0, confirmed)
    }

    @Test fun barrierOpensAfterDelayWithReason() {
        val barrier = EndBarrier.forMode(ProtectionMode.PROTECTED)
        show(FocusUiState(phase = FocusPhase.RUNNING, timerText = "10:00", endSheet = EndSheetState(barrier, 10, EndNote.NEED_BREAK, 15)))
        rule.onNodeWithTag("end_confirm").assertIsEnabled()
        rule.onNodeWithTag("end_discard").assertIsEnabled()
    }

    @Test fun recoveryCannotSaveBelowOneMinute() {
        show(FocusUiState(phase = FocusPhase.FROZEN, title = "Lesen", frozenElapsedMinutes = 0, plannedMinutes = 25, frozenByRestart = true))
        rule.onNodeWithTag("recovery_save").assertIsNotEnabled()
        rule.onNodeWithTag("recovery_resume").assertIsEnabled()
    }
}
