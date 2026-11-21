package io.github.illiapohukai.focusmode.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.component.FloatingBottomBar
import io.github.illiapohukai.focusmode.core.designsystem.component.NavDestinationItem
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicCore
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.MotionTokens
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemTest {
    @get:Rule val rule = createComposeRule()

    @Test fun reducedMotionDrawsAStaticCoreThatIsExcludedFromSemantics() {
        rule.setContent {
            FocusModeTheme(MotionPreference.REDUCED) {
                Box(Modifier.testTag("scene")) {
                    CosmicCore(Modifier.size(300.dp).testTag("core"), animate = true, reducedMotion = LocalMotion.current.reduced)
                    Text("25:00")
                }
            }
        }
        rule.waitForIdle() // would never idle if the frame loop ran under reduced motion
        val config = rule.onNodeWithTag("core", useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(config.none { it.key == SemanticsProperties.ContentDescription || it.key == SemanticsProperties.Text })
        rule.onNodeWithText("25:00").assertExists()
    }

    @Test fun reducedMotionTokensAreShortButNonZeroForFeedback() {
        var tokens: MotionTokens? = null
        rule.setContent { FocusModeTheme(MotionPreference.REDUCED) { tokens = LocalMotion.current } }
        rule.waitForIdle()
        assertEquals(MotionTokens.Reduced, tokens)
        assertTrue(tokens!!.normal in 1..150)
    }

    @Test fun touchTargetsMeetMinimumSize() {
        rule.setContent {
            FocusModeTheme {
                Box {
                    PrimaryButton("Fokus starten", {}, Modifier.testTag("primary"))
                    FloatingBottomBar(
                        listOf(
                            NavDestinationItem("home", "Home", FocusIcons.Home), NavDestinationItem("missions", "Missionen", FocusIcons.Target),
                            NavDestinationItem("history", "Verlauf", FocusIcons.History), NavDestinationItem("settings", "Profil", FocusIcons.User),
                        ),
                        "home", {}, {},
                    )
                }
            }
        }
        rule.onNodeWithTag("primary").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("nav_home").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("nav_plus").assertHeightIsAtLeast(48.dp)
    }
}
