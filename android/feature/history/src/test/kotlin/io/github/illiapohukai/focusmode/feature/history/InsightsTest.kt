package io.github.illiapohukai.focusmode.feature.history

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import io.github.illiapohukai.focusmode.core.domain.history.FocusInsights
import io.github.illiapohukai.focusmode.core.domain.history.RhythmRange
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSessionStore
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Fokus-Rhythmus and "Dein Fokusverlauf" (PWA `focus-insights.js`, `app-screen-stats`) on Robolectric. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class InsightsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()
    private val today = LocalDate.parse("2026-11-26")

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val results = listOf(
        Fixtures.result("a", today, 25), Fixtures.result("b", today.minusDays(1), 40), Fixtures.result("c", today.minusDays(3), 15),
        Fixtures.result("d", today.minusDays(9), 30),
    )

    @Test fun rhythmBarsAreSelectableAndTheRangeToggles() {
        var range: RhythmRange? = null
        val series = FocusInsights.series(results, RhythmRange.DAYS_7, today)
        compose.setContent { FocusModeTheme { FocusRhythmCard(series, { range = it }) } }
        compose.onNodeWithTag("rhythm_bucket_5").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Mi., 25. Nov.: 40 Min., 1 Sitzung")))
        compose.onNodeWithTag("rhythm_bucket_5").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stärkster Tag"))
        compose.onNodeWithTag("rhythm_bucket_6").performClick()
        compose.onNodeWithTag("rhythm_detail").assertIsDisplayed()
        compose.onNodeWithTag("rhythm_bucket_6").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Stärkster Tag").assertIsDisplayed()
        compose.onNodeWithTag("rhythm_range_WEEKS_4").performClick()
        assertEquals(RhythmRange.WEEKS_4, range)
        shot("h01_rhythm_selected")
    }

    @Test fun emptyRhythmSaysSoInsteadOfDrawingFakeBars() {
        compose.setContent { FocusModeTheme { FocusRhythmCard(FocusInsights.series(emptyList(), RhythmRange.WEEKS_4, today), {}) } }
        compose.onNodeWithText("In den letzten 4 Wochen noch keine Fokuszeit.").assertIsDisplayed()
    }

    @Test fun insightsScreenShowsRealLocalValues() {
        val state = InsightsUiState(loading = false, summary = FocusInsights.summary(results, today), rhythm = FocusInsights.series(results, RhythmRange.WEEKS_4, today))
        compose.setContent { FocusModeTheme { InsightsScreen(state, {}, {}) } }
        compose.onNodeWithText("Dein Fokusverlauf").assertIsDisplayed()
        compose.onNodeWithText("1 Std. 50 Min.").assertIsDisplayed()
        compose.onNodeWithText("100 %").assertIsDisplayed()
        shot("h02_insights")
        compose.onNodeWithTag("insights").performScrollToNode(hasTestTag("insights_consistency"))
        compose.onNodeWithTag("insights_grid").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("4 aktive Tage in den letzten 28 Tagen")))
        compose.onNodeWithTag("insights").performScrollToNode(hasTestTag("insights_types"))
        compose.onNodeWithText("Mi., 25. Nov. · 40\u00A0Min.").assertIsDisplayed() // durations never break across lines
        shot("h03_insights_cards")
    }

    @Test fun emptyInsightsInviteTheFirstSession() {
        var started = 0
        val state = InsightsUiState(loading = false, summary = FocusInsights.summary(emptyList(), today), rhythm = FocusInsights.series(emptyList(), RhythmRange.WEEKS_4, today))
        compose.setContent { FocusModeTheme { InsightsScreen(state, {}, { started++ }) } }
        compose.onNodeWithTag("insights").performScrollToNode(hasTestTag("insights_empty"))
        compose.onNodeWithText("Dein Fortschritt beginnt mit der ersten Sitzung").assertIsDisplayed()
        compose.onNodeWithText("Fokus starten").performClick()
        assertEquals(1, started)
        assertTrue(compose.onAllNodes(hasTestTag("insights_consistency")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun viewModelsShareTheHistoryOwnerAndKeepTheRangeAcrossRecreation() = runTest {
        val clock = FakeClock(wall = Instant.parse("2026-11-26T12:00:00Z"))
        val store = FakeSessionStore(clock)
        results.forEach { store.finalize(it) }
        val saved = SavedStateHandle()
        val vm = HistoryViewModel(store, FakeMissionRepository(clock), clock, saved)
        vm.uiState.launchIn(backgroundScope)
        assertEquals(RhythmRange.DAYS_7, vm.uiState.first { !it.loading }.rhythm!!.range)
        vm.setRange(RhythmRange.WEEKS_4)
        val recreated = HistoryViewModel(store, FakeMissionRepository(clock), clock, saved)
        recreated.uiState.launchIn(backgroundScope)
        assertEquals(RhythmRange.WEEKS_4, recreated.uiState.first { !it.loading }.rhythm!!.range)

        val insights = InsightsViewModel(store, clock, SavedStateHandle())
        insights.uiState.launchIn(backgroundScope)
        val s = insights.uiState.first { !it.loading }
        assertEquals(RhythmRange.WEEKS_4, s.rhythm!!.range) // the PWA stats chart opens on 4 Wochen
        assertEquals(110, s.summary!!.totalMinutes)
        assertEquals(recreated.uiState.first { !it.loading }.progress.totalMinutes, s.summary!!.totalMinutes)
    }
}
