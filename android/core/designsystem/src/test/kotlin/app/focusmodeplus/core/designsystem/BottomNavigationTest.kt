package app.focusmodeplus.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.component.FloatingBottomBar
import app.focusmodeplus.core.designsystem.component.NavDestinationItem
import app.focusmodeplus.core.designsystem.component.navItemWidth
import app.focusmodeplus.core.designsystem.component.rememberFittedNavLabelStyle
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.designsystem.theme.LabelType
import app.focusmodeplus.core.designsystem.theme.NavGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v18.1 floating navigation: PWA geometry, no clipped labels at 320 dp / large fonts, semantics. */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        NavDestinationItem("home", "Home", FocusIcons.Home), NavDestinationItem("missions", "Missionen", FocusIcons.Target),
        NavDestinationItem("history", "Verlauf", FocusIcons.History), NavDestinationItem("settings", "Profil", FocusIcons.User),
    )

    /** Every label, measured with the fitted style at the current density and font scale, fits its item. */
    private fun assertLabelsFit(screenWidthDp: Int) {
        val barWidth = (minOf(screenWidthDp - 2 * NavGeometry.sideMargin.value.toInt(), 480)).dp
        val available = navItemWidth(barWidth) - 6.dp
        var style: TextStyle? = null
        var widths: List<Int> = emptyList()
        var availablePx = 0f
        rule.setContent {
            FocusModeTheme {
                val fitted = rememberFittedNavLabelStyle(items.map { it.label }, available)
                val measurer = rememberTextMeasurer()
                style = fitted
                widths = items.map { measurer.measure(it.label, fitted, maxLines = 1).size.width }
                availablePx = with(LocalDensity.current) { available.toPx() }
            }
        }
        rule.waitForIdle()
        widths.forEachIndexed { i, w -> assertTrue("${items[i].label} needs $w px, has $availablePx px", w <= availablePx + 1f) }
        assertTrue(style!!.fontSize.value <= LabelType.Nav.fontSize.value)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun labelsFitAt360dp() = assertLabelsFit(360)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.6f)
    fun labelsShrinkUniformlyAt320dpAndFontScale160() = assertLabelsFit(320)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun labelsFitAtFontScale130() = assertLabelsFit(320)

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.6f)
    fun barKeepsTouchTargetsSelectionAndAccessibleLabels() {
        var selected = "home"
        var plus = 0
        rule.setContent {
            FocusModeTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    FloatingBottomBar(items, selected, { selected = it }, { plus++ })
                }
            }
        }
        items.forEach { rule.onNodeWithTag("nav_${it.key}").assertHeightIsAtLeast(48.dp).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(it.label))) }
        rule.onNodeWithTag("nav_home").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithTag("nav_missions").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)).performClick()
        assertEquals("missions", selected)
        rule.onNodeWithTag("nav_plus").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, plus)
        rule.onNodeWithTag("bottom_nav").assertHeightIsAtLeast(NavGeometry.barHeight)
    }
}
