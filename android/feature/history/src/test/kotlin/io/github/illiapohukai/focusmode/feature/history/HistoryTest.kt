package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.domain.history.HistoryFilter
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeSessionStore
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HistoryTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    @Test fun viewModelFiltersAndGroupsNewestFirst() = runTest {
        val clock = FakeClock(wall = Instant.parse("2026-11-19T12:00:00Z"))
        val store = FakeSessionStore(clock)
        store.finalize(Fixtures.result("a", LocalDate.parse("2026-11-19"), 25, finishedAt = Instant.parse("2026-11-19T08:00:00Z")))
        store.finalize(Fixtures.result("b", LocalDate.parse("2026-11-19"), 10, finishedAt = Instant.parse("2026-11-19T10:00:00Z")))
        store.finalize(Fixtures.result("c", LocalDate.parse("2026-11-12"), 30))
        val vm = HistoryViewModel(store, clock)
        vm.uiState.launchIn(backgroundScope)
        val today = vm.uiState.first { !it.loading }
        assertEquals(listOf("b", "a"), today.sections.single().rows.map { it.sessionId })
        vm.setFilter(HistoryFilter.ALL)
        val all = vm.uiState.first { it.filter == HistoryFilter.ALL }
        assertEquals(2, all.sections.size)
        assertEquals(65, all.progress.totalMinutes)
    }

    @Test fun emptyFilterExplainsAndFilterChipsWork() {
        var filter: HistoryFilter? = null
        compose.setContent { FocusModeTheme { HistoryScreen(HistoryUiState(loading = false, totalCount = 3), HistoryActions({}, {}), { filter = it }) } }
        compose.onNodeWithText("Heute noch keine Sitzung").assertIsDisplayed()
        compose.onNodeWithText("Alle").performClick()
        assertEquals(HistoryFilter.ALL, filter)
    }
}
