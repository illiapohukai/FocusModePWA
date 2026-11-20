package io.github.illiapohukai.focusmode.feature.focus

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.domain.session.EndBarrier
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import org.junit.Assert.assertEquals
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
