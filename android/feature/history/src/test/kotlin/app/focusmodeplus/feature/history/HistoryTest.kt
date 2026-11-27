package app.focusmodeplus.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import androidx.lifecycle.SavedStateHandle
import app.focusmodeplus.core.domain.history.HistoryFilter
import app.focusmodeplus.core.domain.history.PeriodSummary
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeSessionStore
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.MainDispatcherRule
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
        val vm = HistoryViewModel(store, FakeMissionRepository(clock), clock, SavedStateHandle())
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
        compose.onNodeWithTag("history").performScrollToNode(hasText("Heute noch keine Sitzung"))
        compose.onNodeWithText("Heute noch keine Sitzung").assertIsDisplayed()
        compose.onNodeWithTag("history").performScrollToNode(hasTestTag("history_filter"))
        compose.onNodeWithText("Alle").performClick()
        assertEquals(HistoryFilter.ALL, filter)
    }

    @Test fun periodSummaryCountsOutcomesForTheSelectedFilterOnly() = runTest {
        val clock = FakeClock(wall = Instant.parse("2026-11-19T12:00:00Z"))
        val store = FakeSessionStore(clock)
        store.finalize(Fixtures.result("a", LocalDate.parse("2026-11-19"), 25, finishedAt = Instant.parse("2026-11-19T08:00:00Z")))
        store.finalize(Fixtures.result("b", LocalDate.parse("2026-11-19"), 10, finishedAt = Instant.parse("2026-11-19T10:00:00Z")))
        store.finalize(Fixtures.result("c", LocalDate.parse("2026-11-12"), 30))
        val vm = HistoryViewModel(store, FakeMissionRepository(clock), clock, SavedStateHandle())
        vm.uiState.launchIn(backgroundScope)
        val today = vm.uiState.first { !it.loading }
        assertEquals(2, today.period.sessions)
        assertEquals(35, today.period.countedMinutes)
        vm.setFilter(HistoryFilter.ALL)
        val all = vm.uiState.first { it.filter == HistoryFilter.ALL }
        assertEquals(3, all.period.sessions)
        assertEquals(65, all.period.countedMinutes)
        assertEquals(all.period.sessions, all.period.completed + all.period.partial + all.period.cancelled)
    }

    @Test fun summaryAndRowsRenderThePwaStructure() {
        val row = HistoryRow("s1", "Kapitel 3 zusammenfassen", "16:20–16:45", "25 Min.", app.focusmodeplus.core.domain.history.OutcomeBadge.COMPLETED, mission = false)
        compose.setContent {
            FocusModeTheme {
                HistoryScreen(
                    HistoryUiState(loading = false, totalCount = 1, period = PeriodSummary(25, 1, 1, 0, 0), sections = listOf(HistorySection("Heute", "25 Min. Fokus", listOf(row)))),
                    HistoryActions({}, {}), {},
                )
            }
        }
        compose.onNodeWithText("Deine Fokuszeit").assertIsDisplayed()
        compose.onNodeWithTag("history_summary").assertIsDisplayed()
        compose.onNodeWithText("gezählte Fokuszeit").assertIsDisplayed()
        compose.onNodeWithTag("history").performScrollToNode(hasText("Kapitel 3 zusammenfassen"))
        compose.onNodeWithText("QUICK FOCUS").assertIsDisplayed()
    }
}
