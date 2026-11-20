package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceChips
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceOption
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.HistoryFilter
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge

data class HistoryActions(val onOpenResult: (String) -> Unit, val onQuickFocus: () -> Unit)

@Composable
fun HistoryRoute(actions: HistoryActions, contentPadding: PaddingValues, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, actions, viewModel::setFilter, contentPadding)
}

@Composable
fun HistoryScreen(state: HistoryUiState, actions: HistoryActions, onFilter: (HistoryFilter) -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("history"),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal, end = Spacing.screenHorizontal,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { Text("Verlauf", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() }) }
            item { Summary(state) }
            item {
                ChoiceChips(HistoryFilter.entries.map { ChoiceOption(it, it.label) }, state.filter, onFilter, Modifier.fillMaxWidth().testTag("history_filter"))
            }
            if (!state.loading && state.sections.isEmpty()) {
                item {
                    EmptyState(
                        FocusIcons.History,
                        when (state.filter) { HistoryFilter.TODAY -> "Heute noch keine Sitzung"; HistoryFilter.WEEK -> "Diese Woche noch keine Sitzung"; HistoryFilter.ALL -> "Noch keine Sitzungen" },
                        if (state.totalCount > 0) "Wähle einen anderen Zeitraum, um frühere Sitzungen zu sehen." else "Abgeschlossene Fokus-Sitzungen erscheinen hier.",
                    ) { PrimaryButton("Quick Focus starten", actions.onQuickFocus, icon = FocusIcons.Play) }
                }
            }
            state.sections.forEach { section ->
                item(key = "h_" + section.label) {
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(section.label, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
                        Text(section.focusLabel, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    }
                }
                items(section.rows, key = { it.sessionId }) { row -> Row(row) { actions.onOpenResult(row.sessionId) } }
            }
            item {
                Text(
                    "Serie und Statistik werden lokal auf diesem Gerät berechnet und sind nicht verifiziert.",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun Summary(state: HistoryUiState) {
    val p = state.progress
    FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(Modifier.fillMaxWidth()) {
            Stat("${p.todayMinutes}", "Min. heute", Modifier.weight(1f))
            Stat("${p.weekSessions}", "Woche", Modifier.weight(1f))
            Stat("${p.streak.current}", "Serie", Modifier.weight(1f))
            Stat("${p.streak.best}", "Beste Serie", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Graphite)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Row(row: HistoryRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(Radii.md).background(FocusPalette.Surface)
            .clickable(role = Role.Button, onClickLabel = "Ergebnis öffnen", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (row.mission) Eyebrow("Mission", color = FocusPalette.Purple)
            Text(row.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(row.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        Spacer(Modifier.height(Spacing.xs))
        Column(horizontalAlignment = Alignment.End) {
            Text(row.minutes, style = MaterialTheme.typography.labelLarge, color = FocusPalette.Graphite)
            Text(
                row.badge.label, style = MaterialTheme.typography.labelMedium,
                color = when (row.badge) {
                    OutcomeBadge.COMPLETED -> Color(0xFF15803D)
                    OutcomeBadge.PARTIAL -> FocusPalette.PurpleInk
                    else -> FocusPalette.GraphiteSoft
                },
            )
        }
    }
}
