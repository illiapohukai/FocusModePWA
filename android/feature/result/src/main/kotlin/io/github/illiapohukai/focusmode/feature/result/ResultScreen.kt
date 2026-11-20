package io.github.illiapohukai.focusmode.feature.result

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
    AubergineSurface(Modifier.fillMaxWidth().testTag("result_summary")) {
        Eyebrow(state.eyebrow, color = FocusPalette.Lavender)
        Spacer(Modifier.height(Spacing.xs))
        Text(state.headline, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${state.minutes} von ${state.plannedMinutes} Minuten fokussiert" }) {
            Text("${state.minutes}", style = MaterialTheme.typography.displayLarge, color = Color.White)
            Spacer(Modifier.width(Spacing.xs))
            Text("von ${state.plannedMinutes} Min.", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Lavender, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(state.task, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(state.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender)
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
    FocusCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Flame, null, tint = FocusPalette.Purple, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.xs))
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SecondaryButton("Verlauf", actions.onHistory, Modifier.weight(1f))
            SecondaryButton("Noch eine Runde", actions.onAgain, Modifier.weight(1f), icon = FocusIcons.Play)
        }
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
