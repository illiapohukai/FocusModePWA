package io.github.illiapohukai.focusmode.feature.result

import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceChips
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceOption
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing

data class ResultActions(val onDone: () -> Unit, val onHistory: () -> Unit, val onAgain: () -> Unit)

@Composable
fun ResultRoute(actions: ResultActions, viewModel: ResultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultScreen(state, actions, viewModel::submitReview)
}

@Composable
fun ResultScreen(state: ResultUiState, actions: ResultActions, onReview: (Boolean, Int?) -> Unit) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).testTag("result")) {
            FocusTopBar(onBack = actions.onDone)
            when {
                state.loading -> Unit
                state.notFound -> EmptyState(FocusIcons.Info, "Ergebnis nicht gefunden", "Diese Sitzung ist nicht (mehr) im Verlauf.") {
                    PrimaryButton("Zur Startseite", actions.onDone)
                }
                else -> Content(state, actions, onReview)
            }
        }
    }
}

@Composable
private fun Content(state: ResultUiState, actions: ResultActions, onReview: (Boolean, Int?) -> Unit) {
    AubergineSurface(Modifier.fillMaxWidth().testTag("result_summary"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Restrained green only for a completed Session.
            val dot = when (state.badge) {
                OutcomeBadge.COMPLETED -> FocusPalette.Green
                OutcomeBadge.PARTIAL -> FocusPalette.Lavender
                else -> FocusPalette.StarlightMuted
            }
            Box(Modifier.size(8.dp).clip(Radii.pill).background(dot))
            Spacer(Modifier.width(8.dp))
            Eyebrow(state.eyebrow, color = FocusPalette.HeroKicker)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(state.headline, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${state.minutes} von ${state.plannedMinutes} Minuten fokussiert" }) {
            Text("${state.minutes}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = Color.White)
            Spacer(Modifier.width(Spacing.xs))
            Text("von ${state.plannedMinutes} Min.", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Lavender, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(state.task, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(state.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroCopy)
        state.endNote?.let { Text("Grund: $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender) }
        if (!state.countsAsFocus) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Unter einer Minute oder abgebrochen – zählt nicht als Fokuszeit.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        }
    }
    Spacer(Modifier.height(Spacing.md))
    if (state.awaitingReview) {
        Review(state, onReview)
        Spacer(Modifier.height(Spacing.md))
    }
    FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Flame)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.streak.current == 1) "1 Tag in Folge" else "${state.streak.current} Tage in Folge",
                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite,
                )
                Text("Beste Serie: ${state.streak.best} · lokal berechnet, nicht verifiziert", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
        }
        if (state.imported) {
            Text("Aus einem Web-Backup übernommen.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    if (!state.awaitingReview) {
        PrimaryButton("Fertig", actions.onDone, Modifier.fillMaxWidth().testTag("result_done"))
        Spacer(Modifier.height(Spacing.xs))
        // Stacked so neither label wraps at 320 dp or large font scales.
        SecondaryButton("Noch eine Runde", actions.onAgain, Modifier.fillMaxWidth(), icon = FocusIcons.Play)
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Verlauf", actions.onHistory, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(Spacing.xl))
}

@Composable
private fun Review(state: ResultUiState, onReview: (Boolean, Int?) -> Unit) {
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    FocusCard(Modifier.fillMaxWidth().testTag("mission_review")) {
        Text("Ist dieser Missionsschritt erledigt?", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
        Text("Früher fertig zu sein ist völlig in Ordnung. Deine Fokuszeit bleibt erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        Spacer(Modifier.height(Spacing.sm))
        Text("Wie sicher fühlst du dich? (optional)", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
        Spacer(Modifier.height(Spacing.xxs))
        ChoiceChips((1..5).map { ChoiceOption<Int?>(it, "$it") }, rating, { rating = it })
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton("Schritt abgeschlossen", { onReview(true, rating) }, Modifier.fillMaxWidth().testTag("review_done"), enabled = !state.reviewSaving, icon = FocusIcons.Check)
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Schritt bleibt offen", { onReview(false, rating) }, Modifier.fillMaxWidth().testTag("review_open"), enabled = !state.reviewSaving)
    }
}
