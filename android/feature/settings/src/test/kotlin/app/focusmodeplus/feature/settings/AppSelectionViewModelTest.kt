package app.focusmodeplus.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.model.InstalledApp
import app.focusmodeplus.core.testing.FakeAppCatalog
import app.focusmodeplus.core.testing.FakeSelectedAppRepository
import app.focusmodeplus.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSelectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val compose = createComposeRule()

    @Test fun selectionPersistsLocallyAndUninstalledAppsAreReported() = runTest {
        val catalog = FakeAppCatalog(listOf(InstalledApp("com.example.video", "Video", true), InstalledApp("com.example.chat", "Chat", true)))
        val selected = FakeSelectedAppRepository().apply { setSelected("com.example.gone", "Alt", true) }
        val vm = AppSelectionViewModel(catalog, selected)
        vm.uiState.launchIn(backgroundScope)
        val s = vm.uiState.first { !it.loading }
        assertEquals(listOf("Alt"), s.unavailable.map { it.label })
        vm.toggle(s.apps.first { it.packageName == "com.example.video" })
        assertTrue(selected.getSelected().any { it.packageName == "com.example.video" })
        vm.setQuery("cha")
        assertEquals(listOf("com.example.chat"), vm.uiState.first { it.query == "cha" }.apps.map { it.packageName })
        vm.remove("com.example.gone")
        assertEquals(setOf("com.example.video"), selected.getSelected().map { it.packageName }.toSet())
    }

    @Test fun pickerShowsRealCatalogSearchSelectedCountAndNoBlockingClaim() {
        val catalog = FakeAppCatalog(listOf(InstalledApp("com.example.video", "Video", true), InstalledApp("com.example.chat", "Chat", true)))
        val selected = FakeSelectedAppRepository().apply { kotlinx.coroutines.runBlocking { setSelected("com.example.video", "Video", true) } }
        val vm = AppSelectionViewModel(catalog, selected)
        compose.setContent { FocusModeTheme { AppSelectionRoute(onBack = {}, viewModel = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app_row_com.example.chat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Was lenkt dich ab?").assertIsDisplayed()
        compose.onNodeWithText("1 App ausgewählt").assertIsDisplayed()
        compose.onNodeWithTag("app_selection").performScrollToNode(hasText("1 AUSGEWÄHLT"))
        compose.onNodeWithText("Apps werden nicht blockiert", substring = true).assertIsDisplayed()
        // Real launchable apps from the catalog, as rows with checkbox semantics.
        compose.onNodeWithTag("app_selection").performScrollToNode(hasTestTag("app_row_com.example.chat"))
        compose.onNodeWithTag("app_row_com.example.chat").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("2 Apps ausgewählt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("app_search").performTextInput("vid")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("app_row_com.example.chat").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("app_row_com.example.video").assertExists()
    }

    @Test fun selectedAppsHaveNoPathToTheNetworkLayer() {
        // The settings/app-selection classpath does not contain the Worker client at all.
        try {
            Class.forName("app.focusmodeplus.core.network.WorkerApi")
            fail("network layer must not be reachable from app selection")
        } catch (_: ClassNotFoundException) { }
    }
}
