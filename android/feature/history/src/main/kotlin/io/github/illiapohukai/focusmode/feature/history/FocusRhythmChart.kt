package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.keepDurationsTogether
import io.github.illiapohukai.focusmode.core.designsystem.theme.Easings
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.FocusInsights
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.RhythmRange
import io.github.illiapohukai.focusmode.core.domain.history.RhythmSeries
import kotlin.math.max
import kotlin.math.min

private val PlotHeight = 118.dp
private const val DIM_ALPHA = 0.38f

/**
 * PWA "Fokus-Rhythmus" (`FocusInsights.renderChart`): counted focus per day (7 Tage) or per
 * calendar week (4 Wochen), stacked Mission (aubergine) under Quick Focus (violet), the dashed
 * average with at least two active buckets, the strongest bucket labelled, a legend and the trend.
 * Each bar is one accessible, selectable element; the drawing itself is excluded from semantics.
 * Bars grow once per range (520 ms); reduced motion draws the final state immediately.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusRhythmCard(series: RhythmSeries, onRange: (RhythmRange) -> Unit, modifier: Modifier = Modifier) {
    var selected by rememberSaveable(series.range) { mutableIntStateOf(-1) }
    val motion = LocalMotion.current
    val reveal = remember(series.range) { Animatable(if (motion.reduced || series.total == 0) 1f else 0f) }
    LaunchedEffect(series.range, motion.reduced) {
        if (motion.reduced) reveal.snapTo(1f) else reveal.animateTo(1f, tween(520, easing = Easings.Standard))
    }
    FocusCard(modifier.fillMaxWidth().testTag("rhythm_chart"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = Spacing.xs)) {
                Text("FOKUS-RHYTHMUS", style = LabelType.Kicker, color = FocusPalette.Purple, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(2.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Black, color = FocusPalette.Graphite)) { append(HistoryQueries.formatMinutes(series.total).keepDurationsTogether()) }
                        append("  " + series.range.periodLabel + " · " + FocusInsights.sessionsLabel(series.count))
                    },
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                )
            }
            RangeToggle(series.range, onRange)
        }
        Spacer(Modifier.height(Spacing.sm))
        Plot(series, selected, reveal.value) { selected = if (selected == it) -1 else it }
        Spacer(Modifier.height(Spacing.sm))
        Footer(series, selected)
    }
}

@Composable
private fun RangeToggle(range: RhythmRange, onRange: (RhythmRange) -> Unit) {
    Row(
        Modifier.clip(Radii.pill).background(FocusPalette.SurfaceMuted).padding(3.dp).semantics { contentDescription = "Zeitraum" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RhythmRange.entries.forEach { option ->
            val on = option == range
            Box(
                Modifier.heightIn(min = 40.dp).clip(Radii.pill).background(if (on) FocusPalette.Surface else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { if (!on) onRange(option) })
                    .padding(horizontal = 12.dp).testTag("rhythm_range_${option.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (on) FocusPalette.Graphite else FocusPalette.GraphiteSoft, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Plot(series: RhythmSeries, selected: Int, progress: Float, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val bestStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = FocusPalette.Aubergine)
    val bestText = series.bestIndex.takeIf { it >= 0 }?.let { HistoryQueries.formatMinutes(series.buckets[it].total) }
    val week = series.range == RhythmRange.WEEKS_4
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(PlotHeight)) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                val count = series.buckets.size
                val slot = size.width / count
                val barW = max(10.dp.toPx(), min((if (week) 46 else 30).dp.toPx(), slot * 0.56f))
                val radius = min(9.dp.toPx(), barW / 2f)
                val top = 18.dp.toPx()
                val base = size.height - 1.dp.toPx()
                val plotH = base - top
                val maxTotal = max(1, series.buckets.maxOf { it.total }).toFloat()
                drawLine(FocusPalette.ChartBaseline, Offset(0f, base + 0.5f), Offset(size.width, base + 0.5f), strokeWidth = 1.dp.toPx())
                if (series.showsAverage) {
                    val y = base - (series.average.toFloat() / maxTotal) * plotH
                    drawLine(FocusPalette.ChartAverage, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                }
                series.buckets.forEachIndexed { i, b ->
                    val x = slot * i + (slot - barW) / 2f
                    val alpha = if (selected >= 0 && selected != i) DIM_ALPHA else 1f
                    if (b.total <= 0) {
                        drawRoundRect(FocusPalette.ChartPlaceholder.copy(alpha = alpha), Offset(x, base - 4.dp.toPx()), Size(barW, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    } else {
                        val totalH = max(6.dp.toPx(), (b.total / maxTotal) * plotH) * progress
                        val missionH = totalH * (b.mission.toFloat() / b.total)
                        val quickH = totalH - missionH
                        val barTop = base - totalH
                        val path = Path().apply {
                            addRoundRect(RoundRect(x, barTop, x + barW, base, CornerRadius(radius, radius), CornerRadius(radius, radius), CornerRadius.Zero, CornerRadius.Zero))
                        }
                        if (quickH > 0.5f && missionH > 0.5f) {
                            drawPath(path, FocusPalette.ChartMission.copy(alpha = alpha))
                            // Quick Focus sits on top: clip the rounded bar to its upper part.
                            val quickPath = Path().apply {
                                addRoundRect(RoundRect(x, barTop, x + barW, barTop + quickH, CornerRadius(radius, radius), CornerRadius(radius, radius), CornerRadius.Zero, CornerRadius.Zero))
                            }
                            drawPath(quickPath, FocusPalette.ChartQuick.copy(alpha = alpha))
                        } else {
                            drawPath(path, (if (missionH > quickH) FocusPalette.ChartMission else FocusPalette.ChartQuick).copy(alpha = alpha))
                        }
                        if (i == series.bestIndex && bestText != null && progress >= 0.99f) {
                            val layout = measurer.measure(bestText, bestStyle)
                            val tx = (slot * i + slot / 2f - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                            val ty = max(0f, barTop - layout.size.height - 3.dp.toPx())
                            drawText(layout, topLeft = Offset(tx, ty))
                        }
                    }
                }
            }
            if (series.total == 0) {
                Text(
                    FocusInsights.emptyLabel(series.range), style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.md),
                )
            }
            Row(Modifier.fillMaxSize()) {
                series.buckets.forEachIndexed { i, b ->
                    val description = FocusInsights.describeBucket(series, b)
                    Box(
                        Modifier.weight(1f).fillMaxSize()
                            .clickable(role = Role.Button, onClickLabel = "Details anzeigen") { onSelect(i) }
                            .semantics {
                                contentDescription = description
                                this.selected = selected == i
                                if (i == series.bestIndex) stateDescription = if (week) "Stärkste Woche" else "Stärkster Tag"
                            }
                            .testTag("rhythm_bucket_$i"),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            series.buckets.indices.forEach { i ->
                val current = i == series.buckets.lastIndex
                // Shrinks (never overlaps its neighbour) at large font scales on narrow phones.
                androidx.compose.foundation.text.BasicText(
                    FocusInsights.bucketLabel(series, i), modifier = Modifier.weight(1f), maxLines = 1,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, letterSpacing = 0.sp, textAlign = TextAlign.Center,
                        fontWeight = if (current) FontWeight.Black else FontWeight.Bold,
                        color = if (current) FocusPalette.Graphite else FocusPalette.ChartAxis,
                    ),
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun Footer(series: RhythmSeries, selected: Int) {
    val bucket = series.buckets.getOrNull(selected)
    if (bucket != null) {
        // PWA tooltip, shown inline below the chart so it never covers a bar and reflows at large fonts.
        Column(
            Modifier.fillMaxWidth().clip(Radii.sm).background(FocusPalette.Aubergine).padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {}.testTag("rhythm_detail"),
        ) {
            Text(FocusInsights.bucketTitle(series, bucket), style = MaterialTheme.typography.labelMedium, color = FocusPalette.HeroMuted)
            Text(if (bucket.total > 0) HistoryQueries.formatMinutes(bucket.total) else "Keine Fokuszeit", style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (bucket.count > 0) {
                val split = listOfNotNull(
                    FocusInsights.sessionsLabel(bucket.count),
                    bucket.mission.takeIf { it > 0 }?.let { "Mission " + HistoryQueries.formatMinutes(it) },
                    bucket.quick.takeIf { it > 0 }?.let { "Quick " + HistoryQueries.formatMinutes(it) },
                ).joinToString(" · ")
                Text(split, style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroText)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
    FlowRow(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Legend(FocusPalette.ChartMission, "Mission " + HistoryQueries.formatMinutes(series.mission))
        Legend(FocusPalette.ChartQuick, "Quick " + HistoryQueries.formatMinutes(series.quick))
    }
    if (series.bestIndex >= 0) {
        val best = series.buckets[series.bestIndex]
        InsightRow(
            if (series.range == RhythmRange.WEEKS_4) "Stärkste Woche" else "Stärkster Tag",
            FocusInsights.bucketTitle(series, best) + " · " + HistoryQueries.formatMinutes(best.total),
            FocusPalette.Graphite,
        )
    }
    FocusInsights.trendLabel(series)?.let { label ->
        val pct = series.trendPercent ?: 0
        InsightRow("Trend", label, when { pct > 0 -> FocusPalette.GreenInk; else -> FocusPalette.Graphite })
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text.keepDurationsTogether(), style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
    }
}

/** Label left, value right; when both do not fit (large font), the value moves under the label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightRow(label: String, value: String, valueColor: Color) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 6.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(end = Spacing.sm))
        Text(value.keepDurationsTogether(), style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}
