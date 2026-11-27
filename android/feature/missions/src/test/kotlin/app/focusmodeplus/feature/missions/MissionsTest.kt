package app.focusmodeplus.feature.missions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MissionsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    @Test fun viewModelSplitsActiveAndFinishedWithStableThemes() = runTest {
        val repo = FakeMissionRepository(FakeClock())
        repo.create(Fixtures.mission("mission_a"))
        repo.create(Fixtures.mission("mission_b", status = MissionStatus.COMPLETED))
        val vm = MissionsViewModel(repo, FakePreferencesRepository())
        vm.uiState.launchIn(backgroundScope)
        val s = vm.uiState.first { !it.loading }
        assertEquals(listOf("mission_a"), s.active.map { it.mission.id.value })
        assertEquals(listOf("mission_b"), s.finished.map { it.mission.id.value })
    }

    @Test fun emptyStateIsHonestAboutMissionPlanning() {
        compose.setContent { FocusModeTheme { MissionsScreen(MissionsUiState(loading = false), MissionsActions({}, {}, {})) } }
        compose.onNodeWithText("Noch keine Missionen").assertIsDisplayed()
        compose.onNodeWithText("Quick Focus starten").assertIsDisplayed()
    }

    @Test fun unknownMissionIsSafe() {
        compose.setContent { FocusModeTheme { MissionPlanScreen(MissionDetailUiState(loading = false, notFound = true), {}, PlanCallbacks()) } }
        compose.onNodeWithText("Mission nicht gefunden").assertIsDisplayed()
    }
}
