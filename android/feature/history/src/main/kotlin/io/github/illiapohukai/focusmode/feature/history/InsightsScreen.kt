package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.BackButton
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import io.github.illiapohukai.focusmode.core.designsystem.component.keepDurationsTogether
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.FocusInsights
import io.github.illiapohukai.focusmode.core.domain.history.FocusInsightsSummary
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.RhythmRange
import io.github.illiapohukai.focusmode.core.model.SessionType

@Composable
fun InsightsRoute(onBack: () -> Unit, onStartFocus: () -> Unit, viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsScreen(state, onBack, onStartFocus, viewModel::setRange)
}

/**
 * "Dein Fokusverlauf" (PWA `app-screen-stats`): total focus, average, completion rate, today, the
 * Fokus-Rhythmus chart (4 Wochen first), Konstanz with the 28-day grid, Bestwerte and Sitzungsarten.
 * Every number is derived from local History by the one insights owner and labelled as local.
 * The League link of the PWA is not shown: League is a future server-authoritative feature.
 */
@Composable
fun InsightsScreen(state: InsightsUiState, onBack: () -> Unit, onStartFocus: () -> Unit, onRange: (RhythmRange) -> Unit = {}) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("insights"),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.xs, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onBack)
                    Column(Modifier.weight(1f).padding(start = Spacing.xxs)) {
                        Text("FORTSCHRITT", style = LabelType.Kicker, color = FocusPalette.GraphiteSoft)
                        Text("Dein Fokusverlauf", style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
                    }
                }
            }
            val summary = state.summary
            if (summary != null) {
                item { Hero(summary) }
                state.rhythm?.let { item { FocusRhythmCard(it, onRange) } }
                if (!summary.hasData) {
                    item {
                        FocusCard(Modifier.fillMaxWidth().testTag("insights_empty")) {
                            EmptyState(
                                FocusIcons.Sparkle, "Dein Fortschritt beginnt mit der ersten Sitzung",
                                "Sobald du fokussierst, erscheinen hier Konstanz, Bestwerte und deine Sitzungsarten.",
                            ) { PrimaryButton("Fokus starten", onStartFocus, icon = FocusIcons.PlayFilled) }
                        }
                    }
                } else {
                    item { Consistency(summary) }
                    item { Best(summary) }
                    item { Types(summary) }
                }
            }
            item {
                Text(
                    "Alle Werte werden lokal aus deinem Verlauf berechnet und sind nicht verifiziert.",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun Hero(s: FocusInsightsSummary) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    AubergineSurface(Modifier.fillMaxWidth().testTag("insights_hero"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = io.github.illiapohukai.focusmode.core.designsystem.component.isTextCompact(maxWidth.value, fontScale, 250f)
            Column {
                Text("GESAMTE FOKUSZEIT", style = LabelType.Kicker, color = FocusPalette.Lavender, modifier = Modifier.semantics { heading() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // PWA hero figure: one line, shrinking instead of wrapping "3 Std. 27 Min.".
                    androidx.compose.foundation.text.BasicText(
                        HistoryQueries.formatMinutes(s.totalMinutes), maxLines = 1, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineLarge.copy(color = Color.White),
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
                    )
                    if (!compact) {
                        Spacer(Modifier.width(Spacing.sm))
                        SessionsBadge(s.countedCount)
                    }
                }
                Text(
                    if (s.hasData) "${s.activeDays28} ${if (s.activeDays28 == 1) "aktiver Tag" else "aktive Tage"} in den letzten 4 Wochen" else "Noch keine gezählte Fokuszeit",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroText,
                )
                if (compact) {
                    Spacer(Modifier.height(Spacing.xs))
                    SessionsBadge(s.countedCount)
                }
                Spacer(Modifier.height(Spacing.sm))
                val stats = listOf(
                    (if (s.hasData) HistoryQueries.formatMinutes(s.averageMinutes) else "–") to "Ø pro Sitzung",
                    (s.completionRate?.let { "$it %" } ?: "–") to "Abschlussquote",
                    HistoryQueries.formatMinutes(s.todayMinutes) to "Heute",
                )
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { stats.forEach { (v, l) -> HeroStat(v, l, Modifier.fillMaxWidth()) } }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        stats.forEachIndexed { i, (v, l) ->
                            if (i > 0) VerticalDivider(color = Color.White.copy(alpha = 0.12f))
                            HeroStat(v, l, Modifier.weight(1f).padding(start = if (i > 0) Spacing.sm else 0.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsBadge(count: Int) {
    Column(
        Modifier.clip(Radii.sm).background(Color.White.copy(alpha = 0.08f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.sm)
            .padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(if (count == 1) "SITZUNG" else "SITZUNGEN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = FocusPalette.HeroMuted)
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value.keepDurationsTogether(), style = MaterialTheme.typography.titleMedium, color = Color.White)
        // One word ("Abschlussquote"): shrinks to fit a third of a 320–360 dp card instead of breaking.
        androidx.compose.foundation.text.BasicText(
            label, maxLines = 1, style = MaterialTheme.typography.bodySmall.copy(color = FocusPalette.HeroMuted),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.bodySmall.fontSize),
        )
    }
}

@Composable
private fun CardHead(icon: ImageVector, title: String, subtitle: String, iconTint: Color = FocusPalette.Purple, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint = iconTint, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Consistency(s: FocusInsightsSummary) {
    val streak = s.streak
    FocusCard(Modifier.fillMaxWidth().testTag("insights_consistency"), contentPadding = PaddingValues(16.dp)) {
        CardHead(
            FocusIcons.Flame, "Konstanz",
            when { streak.current == 0 -> "Starte heute eine neue Serie"; streak.todayActive -> "Heute bereits fokussiert"; else -> "Fokussiere heute, um die Serie zu halten" },
        ) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text("${streak.current}", style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Graphite)
                Text(if (streak.current == 1) "Tag in Folge" else "Tage in Folge", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // 28 days, two rows of 14 (PWA `fm-consistency-grid`); one spoken summary for the whole grid.
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${s.activeDays28} aktive Tage in den letzten 28 Tagen"; testTag = "insights_grid" },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            s.recentDays.chunked(14).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { day ->
                        val color = when (s.levelOf(day)) {
                            0 -> FocusPalette.ConsistencyEmpty
                            1 -> FocusPalette.ConsistencyLow
                            2 -> FocusPalette.ConsistencyMid
                            else -> FocusPalette.Purple
                        }
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(color)
                                .then(if (day.isToday) Modifier.border(BorderStroke(1.5.dp, FocusPalette.Graphite), RoundedCornerShape(5.dp)) else Modifier),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth()) {
            Text("${s.activeDays28} von 28 Tagen aktiv", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f))
            Text("Beste Serie: ${streak.best} ${if (streak.best == 1) "Tag" else "Tage"}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Best(s: FocusInsightsSummary) {
    FocusCard(Modifier.fillMaxWidth().testTag("insights_best"), contentPadding = PaddingValues(16.dp)) {
        CardHead(FocusIcons.Medal, "Bestwerte", "Deine stärksten Zeiträume", iconTint = FocusPalette.AmberInk)
        Spacer(Modifier.height(Spacing.xs))
        ValueRow("Stärkster Tag", s.bestDay?.let { FocusInsights.dayShort(it.start) + " · " + HistoryQueries.formatMinutes(it.minutes) } ?: "–")
        ValueRow("Stärkste Woche", s.bestWeek?.let { "KW ${FocusInsights.isoWeek(it.start)} · " + HistoryQueries.formatMinutes(it.minutes) } ?: "–")
        ValueRow("Längste Sitzung", s.longestSession?.let { HistoryQueries.formatMinutes(it.minutes) + " · " + if (it.type == SessionType.MISSION) "Mission" else "Quick" } ?: "–")
    }
}

@Composable
private fun Types(s: FocusInsightsSummary) {
    FocusCard(Modifier.fillMaxWidth().testTag("insights_types"), contentPadding = PaddingValues(16.dp)) {
        CardHead(FocusIcons.Layers, "Sitzungsarten", "Gezählte Fokuszeit nach Art")
        Spacer(Modifier.height(Spacing.sm))
        val total = (s.mission.minutes + s.quick.minutes).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(10.dp).clip(Radii.pill).background(FocusPalette.SurfaceMuted).clearAndSetSemantics { }) {
            if (s.mission.minutes > 0) Box(Modifier.weight(s.mission.minutes.toFloat() / total).fillMaxHeight().background(FocusPalette.ChartMission))
            if (s.quick.minutes > 0) Box(Modifier.weight(s.quick.minutes.toFloat() / total).fillMaxHeight().background(FocusPalette.ChartQuick))
        }
        Spacer(Modifier.height(Spacing.xs))
        ValueRow("Focus Mission", "${s.mission.count} ${if (s.mission.count == 1) "Schritt" else "Schritte"} · ${HistoryQueries.formatMinutes(s.mission.minutes)}", FocusPalette.ChartMission)
        ValueRow("Quick Focus", "${FocusInsights.sessionsLabel(s.quick.count)} · ${HistoryQueries.formatMinutes(s.quick.minutes)}", FocusPalette.ChartQuick)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ValueRow(label: String, value: String, dot: Color? = null) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = Spacing.sm)) {
            if (dot != null) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(dot))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft)
        }
        Text(value.keepDurationsTogether(), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
    }
}
