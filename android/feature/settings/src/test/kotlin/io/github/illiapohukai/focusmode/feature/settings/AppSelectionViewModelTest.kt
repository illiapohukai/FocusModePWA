package io.github.illiapohukai.focusmode.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.model.InstalledApp
import io.github.illiapohukai.focusmode.core.testing.FakeAppCatalog
import io.github.illiapohukai.focusmode.core.testing.FakeSelectedAppRepository
import io.github.illiapohukai.focusmode.core.testing.MainDispatcherRule
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

    @Test fun selectedAppsHaveNoPathToTheNetworkLayer() {
        // The settings/app-selection classpath does not contain the Worker client at all.
        try {
            Class.forName("io.github.illiapohukai.focusmode.core.network.WorkerApi")
            fail("network layer must not be reachable from app selection")
        } catch (_: ClassNotFoundException) { }
    }
}
