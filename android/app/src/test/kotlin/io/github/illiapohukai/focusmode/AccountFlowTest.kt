package io.github.illiapohukai.focusmode

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v21 on the real Hilt graph of a build without google-services.json (the CI build): guest mode
 * only, no Firebase initialized, honest account copy, and the full app still works.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountFlowTest {
    @get:Rule(order = 0) val system = object : ExternalResource() {
        override fun before() {
            val cr = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver
            Settings.Global.putInt(cr, Settings.Global.BOOT_COUNT, 3)
        }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun present(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = rule.waitUntil(10_000) { present(tag) }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun theUnconfiguredBuildNeverInitializesFirebase() {
        waitFor("home")
        assertFalse(BuildConfig.FIREBASE_CONFIGURED)
        assertEquals("", BuildConfig.GOOGLE_SERVER_CLIENT_ID)
        // The automatic init provider is removed and the account module binds guest-only owners.
        assertEquals(emptyList<Any>(), com.google.firebase.FirebaseApp.getApps(ApplicationProvider.getApplicationContext()))
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun settingsShowsTheAccountSectionAndTheUnconfiguredState() {
        waitFor("home")
        rule.onNodeWithTag("nav_settings").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("settings")
        rule.onNodeWithTag("settings").performScrollToNode(hasTestTag("settings_account"))
        rule.onNodeWithText("Lokaler Gastmodus").assertIsDisplayed()
        rule.onNodeWithTag("settings_account_pill").assert(hasText("Nicht eingerichtet", ignoreCase = true))
        shot("40_settings_account_unconfigured")
        rule.onNodeWithTag("settings").performScrollToNode(hasTestTag("settings_privacy_synced"))
        rule.onNodeWithTag("settings_privacy_synced").assert(hasText("noch nicht eingerichtet", substring = true))
        rule.onNodeWithTag("settings").performScrollToNode(hasTestTag("settings_account"))
        rule.onNodeWithText("Lokaler Gastmodus").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("account")
        rule.onNodeWithTag("account_unconfigured").assertIsDisplayed()
        shot("41_account_unconfigured")
        // Local backup stays one tap away in guest mode.
        rule.onNodeWithTag("account").performScrollToNode(hasTestTag("account_backup"))
        rule.onNodeWithTag("account_backup").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("data_backup")
    }
}
