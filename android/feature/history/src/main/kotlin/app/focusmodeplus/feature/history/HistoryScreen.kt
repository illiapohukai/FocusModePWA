package app.focusmodeplus.feature.history

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
import app.focusmodeplus.core.designsystem.component.ChoiceChips
import app.focusmodeplus.core.designsystem.component.ChoiceOption
import app.focusmodeplus.core.designsystem.component.EmptyState
import app.focusmodeplus.core.designsystem.component.AubergineSurface
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.ScreenHeader
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.domain.history.HistoryFilter
import app.focusmodeplus.core.domain.history.HistoryQueries
import app.focusmodeplus.core.domain.history.OutcomeNode
import app.focusmodeplus.core.domain.history.RhythmRange
import app.focusmodeplus.core.designsystem.component.OutcomeLabel
import app.focusmodeplus.core.designsystem.component.isTextCompact
import app.focusmodeplus.core.designsystem.theme.MissionTones
import androidx.compose.ui.semantics.contentDescription

data class HistoryActions(val onOpenResult: (String) -> Unit, val onQuickFocus: () -> Unit, val onOpenInsights: () -> Unit = {})

@Composable
fun HistoryRoute(actions: HistoryActions, contentPadding: PaddingValues, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, actions, viewModel::setFilter, contentPadding, viewModel::setRange)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    actions: HistoryActions,
    onFilter: (HistoryFilter) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    onRange: (RhythmRange) -> Unit = {},
) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("history"),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal, end = Spacing.screenHorizontal,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                ScreenHeader("Sitzungsverlauf", "Deine Fokuszeit", Modifier.padding(bottom = Spacing.xs)) {
                    HeaderIconButton(FocusIcons.BarChart, "Statistik", actions.onOpenInsights, Modifier.testTag("history_open_insights"))
                }
            }
            item { Summary(state) }
            state.rhythm?.let { series -> item { FocusRhythmCard(series, onRange) } }
            item {
                ChoiceChips(HistoryFilter.entries.map { ChoiceOption(it, it.label) }, state.filter, onFilter, Modifier.fillMaxWidth().testTag("history_filter"))
            }
            if (!state.loading && state.sections.isEmpty()) {
                item {
                    val (title, body) = when {
                        state.totalCount == 0 -> "Dein Sitzungsverlauf ist noch leer." to "Deine Fokus-Sitzungen erscheinen hier."
                        state.filter == HistoryFilter.TODAY -> "Heute noch keine Sitzung" to "Starte einen Quick Focus oder setze deine Mission fort."
                        state.filter == HistoryFilter.WEEK -> "Diese Woche noch keine Sitzung" to "Deine nächste abgeschlossene Sitzung erscheint hier."
                        else -> "Keine Sitzung gefunden" to "Für diesen Filter gibt es noch keine Einträge."
                    }
                    EmptyState(FocusIcons.Clock, title, body) {
                        PrimaryButton(if (state.totalCount == 0) "Erste Sitzung starten" else "Fokus starten", actions.onQuickFocus, icon = FocusIcons.PlayFilled)
                    }
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

/** PWA `.fm-root-header-btn`: a 48 dp round surface button in a root header. */
@Composable
internal fun HeaderIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.size(48.dp).clip(Radii.pill).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.pill)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = FocusPalette.Graphite, modifier = Modifier.size(20.dp)) }
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
                state.filter.label, style = MaterialTheme.typography.labelMedium, color = FocusPalette.HeroText,
                modifier = Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.08f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(HistoryQueries.formatMinutes(p.countedMinutes), style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp), color = Color.White, maxLines = 1)
                Text("gezählte Fokuszeit", style = MaterialTheme.typography.titleSmall, color = FocusPalette.HeroText)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text("${p.sessions}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(if (p.sessions == 1) "Sitzung" else "Sitzungen", style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroMuted)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text("Diese Woche: ${HistoryQueries.formatMinutes(local.weekMinutes)} · Serie ${local.streak.current} (beste ${local.streak.best})", style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroText)
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = Color.White.copy(alpha = 0.12f))
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (isTextCompact(maxWidth.value, fontScale, 240f)) {
                // Large font on a narrow phone: one outcome per line instead of clipped words.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutcomeLine("${p.completed}", "Abgeschlossen", Color(0xFF86EFAC))
                    OutcomeLine("${p.partial}", "Teilweise", FocusPalette.Lavender)
                    OutcomeLine("${p.cancelled}", "Abgebrochen", Color(0xFFFCD34D))
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Outcome("${p.completed}", "Abgeschlossen", Color(0xFF86EFAC), Modifier.weight(1f))
                    VerticalDivider(color = Color.White.copy(alpha = 0.12f))
                    Outcome("${p.partial}", "Teilweise", FocusPalette.Lavender, Modifier.weight(1f).padding(start = Spacing.sm))
                    VerticalDivider(color = Color.White.copy(alpha = 0.12f))
                    Outcome("${p.cancelled}", "Abgebrochen", Color(0xFFFCD34D), Modifier.weight(1f).padding(start = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun OutcomeLine(value: String, label: String, color: Color) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, modifier = Modifier.padding(end = Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroMuted)
    }
}

@Composable
private fun Outcome(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        // One word per label ("Abgeschlossen"): shrink to fit a 320–360 dp column instead of truncating.
        BasicText(
            label, style = MaterialTheme.typography.bodySmall.copy(color = FocusPalette.HeroMuted), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.bodySmall.fontSize),
        )
    }
}

@Composable
private fun Row(row: HistoryRow, onClick: () -> Unit) {
    val tone = row.theme?.let(MissionTones::of)
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = isTextCompact(maxWidth.value, fontScale, 290f)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(Radii.lg).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.lg)
                .clickable(role = Role.Button, onClickLabel = "Ergebnis öffnen", onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .semantics(mergeDescendants = true) {}
                .testTag("history_row_${row.sessionId}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    Text(
                        if (row.mission) "MISSION" else "QUICK FOCUS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = if (row.mission) (tone?.ink ?: FocusPalette.PurpleInk) else FocusPalette.Graphite, maxLines = 1,
                        modifier = Modifier.clip(Radii.pill).background(if (row.mission) (tone?.tint ?: FocusPalette.PurpleTint) else FocusPalette.SurfaceMuted).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(row.title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                row.missionContext?.let { context ->
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(Modifier.size(7.dp).clip(Radii.pill).background(tone?.accentStrong ?: FocusPalette.Purple))
                        Spacer(Modifier.width(6.dp))
                        Text(context, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (compact) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.minutes, style = MaterialTheme.typography.titleSmall, color = if (row.node == OutcomeNode.OPEN) FocusPalette.GraphiteSoft else FocusPalette.Graphite)
                        Spacer(Modifier.width(Spacing.xs))
                        OutcomeLabel(row.node.fill, row.badge.label)
                    }
                }
            }
            if (!compact) {
                Spacer(Modifier.width(Spacing.xs))
                Column(horizontalAlignment = Alignment.End) {
                    Text(row.minutes, style = MaterialTheme.typography.titleMedium, color = if (row.node == OutcomeNode.OPEN) FocusPalette.GraphiteSoft else FocusPalette.Graphite)
                    Spacer(Modifier.height(4.dp))
                    OutcomeLabel(row.node.fill, row.badge.label)
                }
            }
        }
    }
}
