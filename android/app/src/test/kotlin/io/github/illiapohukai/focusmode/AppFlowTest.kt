package io.github.illiapohukai.focusmode

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

/**
 * End-to-end vertical slice on the JVM (Robolectric + the real Hilt graph, Room and DataStore).
 * This is not device testing; it exercises the real app wiring and UI without an emulator.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    /** Robolectric has no boot counter; the Session clock uses it to detect reboots. */
    @get:Rule(order = 0) val system = object : ExternalResource() {
        override fun before() {
            val cr = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
            Settings.Global.putInt(cr, Settings.Global.BOOT_COUNT, 3)
        }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun openQuickFocus() {
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_quick_focus"))
        rule.onNodeWithTag("home_quick_focus").performClick()
        waitFor("quick_focus")
    }

    private fun present(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /**
     * The Cosmic Core runs an endless frame loop, so Compose never becomes idle on the Focus
     * screen. Like any infinite animation under test, the clock is then advanced manually.
     */
    private fun waitFor(tag: String, timeoutMs: Long = 10_000) = pumpUntil(timeoutMs) { present(tag) }

    private fun pumpUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        if (rule.mainClock.autoAdvance) { rule.waitUntil(timeoutMs, condition); return }
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within $timeoutMs ms" }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    /** Lets enter/exit animations finish (about one second of frames) under the manual clock. */
    private fun settle(frames: Int = 60) {
        if (rule.mainClock.autoAdvance) rule.waitForIdle() else repeat(frames) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun startSession() {
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("quick_start").performClick()
        waitFor("focus_timer")
    }

    private fun shot(name: String) {
        settle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun quickFocusSessionIsPersistedEndToEndAndSurvivesRecreation() {
        waitFor("home")
        rule.onNodeWithText("Focus Mode+").assertIsDisplayed()
        shot("01_home_empty")

        openQuickFocus()
        rule.onNodeWithTag("duration_dial").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "25 Minuten"))
        rule.onNodeWithTag("duration_dial").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        rule.onNodeWithTag("duration_dial").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30 Minuten"))
        rule.onNodeWithTag("quick_task").performTextInput("Lineare Funktionen")
        shot("02_quick_focus")

        startSession()
        rule.onNodeWithTag("focus_timer").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Noch 30 Minuten")))
        shot("03_focus_running")

        // Rotation / process-like recreation keeps the one authoritative Session.
        rule.activityRule.scenario.recreate()
        waitFor("focus_timer")
        rule.onNodeWithTag("focus_timer").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Noch 30 Minuten")))

        ShadowSystemClock.advanceBy(Duration.ofMinutes(12))
        rule.mainClock.advanceTimeBy(2_000)
        pumpUntil(5_000) {
            rule.onAllNodes(hasTestTag("focus_timer")).fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.firstOrNull() == "Noch 18 Minuten"
        }

        rule.onNodeWithTag("focus_end").performClick()
        waitFor("end_sheet")
        settle()
        shot("04_end_sheet")
        rule.onNodeWithTag("end_confirm").assertIsEnabled().performClick()
        settle()

        waitFor("result_summary")
        rule.onNodeWithText("Fokuszeit gespeichert").assertIsDisplayed()
        rule.onNodeWithText("12").assertIsDisplayed()
        shot("05_result")
        rule.onNodeWithTag("result_done").performClick()
        settle()

        waitFor("home")
        rule.onNodeWithTag("nav_history").performClick()
        settle()
        waitFor("history")
        val row = androidx.compose.ui.test.hasText("Lineare Funktionen") and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("history"))
        pumpUntil { rule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        settle()
        rule.onNode(row).assertIsDisplayed()
        shot("06_history")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun protectedFocusHasBarrierButEmergencyExitIsImmediate() {
        waitFor("home")
        openQuickFocus()
        rule.onNodeWithTag("protection_row").performClick()
        rule.onNodeWithText("Geschützter Fokus").performClick()
        rule.onNodeWithText("Strikter Modus").assertIsNotEnabled()
        rule.onNodeWithTag("protection_done").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("protection_done")).fetchSemanticsNodes().isEmpty() }
        startSession()
        rule.onNodeWithTag("focus_end").performClick()
        waitFor("end_sheet")
        settle()
        rule.onNodeWithTag("barrier_countdown").assertIsDisplayed()
        rule.onNodeWithTag("end_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("end_emergency").assertIsEnabled()
        shot("07_protected_barrier")
        rule.onNodeWithTag("end_emergency").performClick()
        settle()
        waitFor("result")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun compactWidthWithLargeFontKeepsStartReachable() {
        waitFor("home")
        shot("08_home_320dp_font160")
        openQuickFocus()
        rule.onNodeWithTag("quick_start").assertIsDisplayed()
        shot("09_quick_focus_320dp_font160")
    }

    @Test
    @Config(qualifiers = "w840dp-h1100dp-xhdpi")
    fun tabletUsesRailAndReadableWidth() {
        waitFor("home")
        rule.onNodeWithTag("nav_plus").assertIsDisplayed()
        assertTrue(rule.onAllNodes(hasTestTag("bottom_nav")).fetchSemanticsNodes().isEmpty())
        shot("10_home_tablet")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeFocusKeepsTimerReadable() {
        waitFor("home")
        openQuickFocus()
        startSession()
        rule.onNodeWithTag("focus_timer").assertIsDisplayed()
        shot("11_focus_landscape")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun permissionCenterExplainsEveryPermissionHonestly() {
        waitFor("home")
        rule.onNodeWithTag("nav_settings").performClick()
        waitFor("settings")
        // Scrolling a lazy list only brings the row just into view, which can leave it under the
        // floating bar; the semantics click targets the row itself, as TalkBack does.
        rule.onNodeWithTag("settings").performScrollToNode(androidx.compose.ui.test.hasText("Berechtigungen"))
        rule.onNodeWithText("Berechtigungen").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitFor("permission_center")
        rule.onNodeWithTag("permission_center").performScrollToNode(hasTestTag("permission_accessibility"))
        rule.onNodeWithText("Nicht verwendet", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithTag("permission_center").performScrollToNode(hasTestTag("permission_managed_device"))
        rule.onNodeWithText("Nicht verwaltet", ignoreCase = true).assertIsDisplayed()
        shot("12_permission_center")
    }
}
