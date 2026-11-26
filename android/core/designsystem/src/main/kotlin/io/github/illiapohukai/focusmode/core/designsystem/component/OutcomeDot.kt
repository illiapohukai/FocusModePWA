package io.github.illiapohukai.focusmode.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette

/**
 * How much of an outcome node is filled (PWA Activity): 1 = abgeschlossen (filled), 0.5 =
 * teilweise (half), 0 = abgebrochen / unterbrochen (open ring). The shape carries the meaning,
 * so the outcome is never communicated by colour alone; the node itself is decorative and the
 * text label next to it is what assistive technology reads.
 */
@Composable
fun OutcomeDot(fill: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp, background: Color = FocusPalette.Surface) {
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val radius = this.size.minDimension / 2f
        val inner = radius - stroke / 2f
        when {
            fill >= 1f -> drawCircle(color, radius)
            fill <= 0f -> {
                drawCircle(background, radius)
                drawCircle(FocusPalette.NodeOpen, inner, style = Stroke(stroke))
            }
            else -> {
                drawCircle(background, radius)
                drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset.Zero, size = Size(radius * 2, radius * 2))
                drawCircle(color, inner, style = Stroke(stroke))
            }
        }
    }
}

/** Outcome colours: restrained green for completed, warm amber for partial, graphite for open. */
object OutcomeColors {
    fun node(fill: Float): Color = when {
        fill >= 1f -> FocusPalette.Green
        fill <= 0f -> FocusPalette.NodeOpen
        else -> FocusPalette.AmberInk
    }

    fun ink(fill: Float): Color = when {
        fill >= 1f -> FocusPalette.GreenInk
        fill <= 0f -> FocusPalette.GraphiteSoft
        else -> FocusPalette.AmberInk
    }
}

/** Node plus concise German status text ("Abgeschlossen", "Teilweise", "Abgebrochen"). */
@Composable
fun OutcomeLabel(fill: Float, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutcomeDot(fill, OutcomeColors.node(fill), size = 10.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = OutcomeColors.ink(fill), maxLines = 1)
    }
}
