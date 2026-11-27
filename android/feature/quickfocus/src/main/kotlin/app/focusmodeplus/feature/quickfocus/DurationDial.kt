package app.focusmodeplus.feature.quickfocus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.TimerType
import app.focusmodeplus.core.domain.session.QuickFocusDuration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Circular duration dial (PWA v17.16): one visible duration input, 5–60 min in 5-minute steps on a
 * 60-minute clock face. Only the ring band owns the drag; the 12 o'clock seam is never crossed in
 * one move. The centre shows the static `MM:00` readout in the timer typography. Accessible as a
 * slider (TalkBack adjust, custom actions, arrow keys) — no separate preset controls.
 */
private const val DISC = 0.9f

@Composable
fun DurationDial(minutes: Int, onMinutes: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val current by rememberUpdatedState(minutes)
    fun apply(raw: Float) {
        var m = raw
        if (current >= 45 && m < 15) m = QuickFocusDuration.MAX.toFloat()
        else if (current <= 15 && m > 45) m = QuickFocusDuration.MIN.toFloat()
        onMinutes(QuickFocusDuration.normalize(Math.round(m)))
    }
    fun minutesAt(p: Offset, size: Size): Pair<Float, Float> {
        val c = Offset(size.width / 2f, size.height / 2f)
        val dx = p.x - c.x
        val dy = p.y - c.y
        val angle = (Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())) + 360.0) % 360.0
        return (hypot(dx, dy) / (size.width / 2f)) to (angle / 360.0 * QuickFocusDuration.MAX).toFloat()
    }
    Box(
        modifier
            .widthIn(max = 312.dp)
            .aspectRatio(1f)
            .testTag("duration_dial")
            .semantics {
                contentDescription = "Dauer"
                stateDescription = "$minutes Minuten"
                progressBarRangeInfo = ProgressBarRangeInfo(minutes.toFloat(), QuickFocusDuration.MIN.toFloat()..QuickFocusDuration.MAX.toFloat(), steps = 10)
                if (enabled) {
                    setProgress { v -> onMinutes(QuickFocusDuration.normalize(Math.round(v))); true }
                    customActions = listOf(
                        CustomAccessibilityAction("5 Minuten mehr") { onMinutes(QuickFocusDuration.normalize(current + 5)); true },
                        CustomAccessibilityAction("5 Minuten weniger") { onMinutes(QuickFocusDuration.normalize(current - 5)); true },
                    )
                }
            }
            .focusable(enabled)
            .onKeyEvent { e ->
                if (!enabled || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.DirectionRight -> { onMinutes(QuickFocusDuration.normalize(current + 5)); true }
                    Key.DirectionDown, Key.DirectionLeft -> { onMinutes(QuickFocusDuration.normalize(current - 5)); true }
                    else -> false
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { p -> val (d, m) = minutesAt(p, Size(size.width.toFloat(), size.height.toFloat())); if (d in 0.52f..1.12f) apply(m) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var active = false
                detectDragGestures(
                    onDragStart = { p -> val (d, m) = minutesAt(p, Size(size.width.toFloat(), size.height.toFloat())); active = d in 0.52f..1.12f; if (active) apply(m) },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false },
                ) { change, _ ->
                    if (active) { change.consume(); apply(minutesAt(change.position, Size(size.width.toFloat(), size.height.toFloat())).second) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Raised cream disc (PWA `.qb-dial-face`): a soft aubergine-tinted shadow under a warm radial face.
        Box(
            Modifier.fillMaxSize(DISC).shadow(18.dp, CircleShape, ambientColor = FocusPalette.Shadow.copy(alpha = 0.10f), spotColor = FocusPalette.Purple.copy(alpha = 0.16f))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF6F1EA), Color(0xFFEAE2D5)), center = Offset.Unspecified))
                .clearAndSetSemantics { },
        )
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val s = size.minDimension
            val c = center
            val discR = s / 2f * DISC
            val stroke = s * 0.075f
            val radius = discR - stroke * 1.25f
            // Faint outer orbit with three purple motes (decorative, static).
            val orbitR = s / 2f - 1.dp.toPx()
            drawCircle(FocusPalette.Lavender.copy(alpha = 0.45f), orbitR, c, style = Stroke(1.dp.toPx()))
            listOf(-62.0, 38.0, 128.0).forEachIndexed { i, deg ->
                val a = Math.toRadians(deg)
                drawCircle(FocusPalette.Violet.copy(alpha = if (i == 0) 0.85f else 0.55f), (if (i == 0) 3.5f else 2.6f).dp.toPx(), Offset(c.x + (orbitR * cos(a)).toFloat(), c.y + (orbitR * sin(a)).toFloat()))
            }
            // Ticks every 5 minutes on the disc rim, majors at the quarters.
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90.0)
                val tr = discR - stroke * 0.42f
                drawCircle(if (i % 3 == 0) Color(0xFFB3A797) else FocusPalette.OutlineStrong, if (i % 3 == 0) stroke * 0.12f else stroke * 0.08f, Offset(c.x + (tr * cos(a)).toFloat(), c.y + (tr * sin(a)).toFloat()))
            }
            drawCircle(Color(0xFFE3DACB), radius, c, style = Stroke(stroke))
            drawCircle(Color(0x12000000), radius - stroke * 0.62f, c, style = Stroke(1.dp.toPx()))
            val sweep = minutes / QuickFocusDuration.MAX.toFloat() * 360f
            val arcTopLeft = Offset(c.x - radius, c.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(FocusPalette.Purple.copy(alpha = 0.09f), -90f, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke * 1.35f, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(FocusPalette.PurpleBright, FocusPalette.Purple, FocusPalette.PurpleInk, FocusPalette.PurpleBright), center = c),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = arcTopLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val ta = Math.toRadians(sweep - 90.0)
            val knob = Offset(c.x + (radius * cos(ta)).toFloat(), c.y + (radius * sin(ta)).toFloat())
            drawCircle(FocusPalette.Purple.copy(alpha = 0.16f), stroke * 1.35f, knob)
            drawCircle(FocusPalette.Purple, stroke * 0.92f, knob)
            drawCircle(Color.White, stroke * 0.62f, knob)
            drawCircle(FocusPalette.Purple, stroke * 0.24f, knob)
        }
        // The static MM:00 readout shares the live timer typography; it shrinks only to fit the face.
        BasicText(
            QuickFocusDuration.staticReadout(minutes),
            style = TimerType.Readout.copy(color = FocusPalette.Ink, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = TimerType.MinFitSize, maxFontSize = TimerType.Readout.fontSize),
            modifier = Modifier.fillMaxWidth(0.52f).clearAndSetSemantics { },
        )
    }
}
