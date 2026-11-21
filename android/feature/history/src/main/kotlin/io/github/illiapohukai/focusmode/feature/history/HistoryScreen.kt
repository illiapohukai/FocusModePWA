package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceChips
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceOption
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.ScreenHeader
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.HistoryFilter
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
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
            item { ScreenHeader("Sitzungsverlauf", "Deine Fokuszeit", Modifier.padding(bottom = Spacing.xs)) }
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
                    ) { PrimaryButton("Quick Focus starten", actions.onQuickFocus, icon = FocusIcons.PlayFilled) }
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

/** PWA "Fokus-Bilanz": aubergine summary of the selected period, outcomes in their state colours. */
@Composable
private fun Summary(state: HistoryUiState) {
    val p = state.period
    val local = state.progress
    AubergineSurface(Modifier.fillMaxWidth().testTag("history_summary"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Activity, null, tint = FocusPalette.Lavender, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Fokus-Bilanz", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Lavender, modifier = Modifier.weight(1f))
            Text(
                state.filter.label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE9E2F5),
                modifier = Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.08f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(HistoryQueries.formatMinutes(p.countedMinutes), style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp), color = Color.White, maxLines = 1)
                Text("gezählte Fokuszeit", style = MaterialTheme.typography.titleSmall, color = Color(0xFFE9E2F5))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text("${p.sessions}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(if (p.sessions == 1) "Sitzung" else "Sitzungen", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9ADC9))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text("Diese Woche: ${local.weekMinutes} Min. · Serie ${local.streak.current} (beste ${local.streak.best})", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE9E2F5))
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = Color.White.copy(alpha = 0.12f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Outcome("${p.completed}", "Abgeschlossen", Color(0xFF86EFAC), Modifier.weight(1f))
            VerticalDivider(color = Color.White.copy(alpha = 0.12f))
            Outcome("${p.partial}", "Teilweise", FocusPalette.Lavender, Modifier.weight(1f).padding(start = Spacing.sm))
            VerticalDivider(color = Color.White.copy(alpha = 0.12f))
            Outcome("${p.cancelled}", "Abgebrochen", Color(0xFFFCD34D), Modifier.weight(1f).padding(start = Spacing.sm))
        }
    }
}

@Composable
private fun Outcome(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        // One word per label ("Abgeschlossen"): shrink to fit a 320–360 dp column instead of truncating.
        BasicText(
            label, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB9ADC9)), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.bodySmall.fontSize),
        )
    }
}

@Composable
private fun Row(row: HistoryRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(Radii.lg).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.lg)
            .clickable(role = Role.Button, onClickLabel = "Ergebnis öffnen", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (row.mission) "MISSION" else "QUICK FOCUS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (row.mission) FocusPalette.PurpleInk else FocusPalette.Graphite, maxLines = 1,
                    modifier = Modifier.clip(Radii.pill).background(if (row.mission) FocusPalette.PurpleTint else FocusPalette.SurfaceMuted).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(horizontalAlignment = Alignment.End) {
            Text(row.minutes, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
            Spacer(Modifier.height(4.dp))
            OutcomePill(row.badge)
        }
    }
}

/** Outcome pill: restrained green only for completed Sessions. */
@Composable
private fun OutcomePill(badge: OutcomeBadge) {
    val (bg, fg) = when (badge) {
        OutcomeBadge.COMPLETED -> FocusPalette.GreenTint to Color(0xFF15803D)
        OutcomeBadge.PARTIAL -> FocusPalette.PurpleTint to FocusPalette.PurpleInk
        else -> FocusPalette.SurfaceMuted to FocusPalette.GraphiteSoft
    }
    Row(Modifier.clip(Radii.pill).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (badge == OutcomeBadge.COMPLETED) {
            Icon(FocusIcons.Check, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(badge.label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}
