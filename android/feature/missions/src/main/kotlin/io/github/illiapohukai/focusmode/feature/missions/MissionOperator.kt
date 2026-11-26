package io.github.illiapohukai.focusmode.feature.missions

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Frame metadata of the approved pixel-art operator (`assets/operator/mission-operator.json`,
 * asset v17.14-1). The two sprite sheets are byte-identical copies of the PWA assets.
 */
internal object OperatorSheet {
    const val FRAME_W = 266
    const val FRAME_H = 358
    const val WORK_COLUMNS = 4
    const val WORK_FRAMES = 16
    const val SUCCESS_COLUMNS = 6
    const val SUCCESS_FRAMES = 24
    const val FPS = 12
    const val FINAL_HOLD_MS = 450L
    const val MIN_WORK_MS = 500L
    const val MAX_EXIT_WAIT_MS = 450L
    const val REDUCED_FADE_MS = 200
    /** Work frame → matching success frame, so the success segment joins without a jump. */
    val EXIT_TO_SUCCESS: List<Int?> = listOf(0, 2, 1, 0, 4, 5, 5, null, null, 5, 4, 1, 1, 0, 3, 3)
}

/** The visual phase of one run (PWA `MissionOperator` states). */
internal enum class OperatorSegment { WORK, SUCCESS, HOLD, DONE }

/**
 * Pure frame stepper of the PWA operator (`advance`): the work loop runs while the request is
 * pending; once success is requested and the minimum working moment passed, the loop exits at
 * the next frame that has a matching success frame (or after a short wait), plays the success
 * segment once and holds the last frame.
 */
internal class OperatorStepper {
    var segment = OperatorSegment.WORK; private set
    var index = 0; private set
    private var workShownAt = -1L
    private var exitWaitSince = -1L

    fun start(now: Long) { workShownAt = now }

    /** Advances one frame; returns true when something changed. */
    fun advance(now: Long, successRequested: Boolean): Boolean {
        when (segment) {
            OperatorSegment.WORK -> {
                val next = if (index >= OperatorSheet.WORK_FRAMES - 1) 0 else index + 1
                if (successRequested && workShownAt >= 0 && now - workShownAt >= OperatorSheet.MIN_WORK_MS) {
                    if (exitWaitSince < 0) exitWaitSince = now
                    val entry = OperatorSheet.EXIT_TO_SUCCESS[next]
                    if (entry != null) { segment = OperatorSegment.SUCCESS; index = entry; return true }
                    if (now - exitWaitSince >= OperatorSheet.MAX_EXIT_WAIT_MS) { segment = OperatorSegment.SUCCESS; index = 0; return true }
                }
                index = next
                return true
            }
            OperatorSegment.SUCCESS -> {
                if (index >= OperatorSheet.SUCCESS_FRAMES - 1) { segment = OperatorSegment.HOLD; return true }
                index += 1
                return true
            }
            OperatorSegment.HOLD, OperatorSegment.DONE -> return false
        }
    }

    fun finish() { segment = OperatorSegment.DONE }
}

private class OperatorArt(val work: ImageBitmap, val success: ImageBitmap)

private fun decode(res: Resources, id: Int): ImageBitmap? = runCatching {
    BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false })?.asImageBitmap()
}.getOrNull()

/**
 * The pixel operator at its terminal. Purely decorative (excluded from accessibility); the
 * screen's live status text carries the meaning. [succeeded] becomes true only after the plan
 * was fully validated and stored; [onSuccessShown] fires once the success segment and its hold
 * have played. Leaving the screen stops it immediately (the composition ends).
 */
@Composable
internal fun MissionOperatorArt(succeeded: Boolean, reducedMotion: Boolean, onSuccessShown: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art by produceState<OperatorArt?>(null, context) {
        value = withContext(Dispatchers.Default) {
            val w = decode(context.resources, R.drawable.mission_operator_work)
            val s = decode(context.resources, R.drawable.mission_operator_success)
            if (w != null && s != null) OperatorArt(w, s) else null
        }
    }
    val latestSucceeded by rememberUpdatedState(succeeded)
    val onDone by rememberUpdatedState(onSuccessShown)
    // Reported once per run, even if the sprites finish decoding after the success started.
    var reported by remember { mutableStateOf(false) }
    val latestDone = { if (!reported) { reported = true; onDone() } }
    val stepper = remember { OperatorStepper() }
    var frame by remember { mutableIntStateOf(0) }
    var segment by remember { mutableStateOf(OperatorSegment.WORK) }
    val finalAlpha = remember { Animatable(0f) }

    LaunchedEffect(art, reducedMotion) {
        if (reported) return@LaunchedEffect
        val loaded = art
        if (loaded == null) {
            // Without art the success is still reported truthfully, briefly (PWA completeWithoutArt).
            while (!latestSucceeded) delay(50)
            delay(OperatorSheet.FINAL_HOLD_MS)
            latestDone()
            return@LaunchedEffect
        }
        val startedAt = withFrameMillis { it }
        stepper.start(startedAt)
        if (reducedMotion) {
            // One still work frame, then a short crossfade to the final success frame.
            while (!latestSucceeded) delay(50)
            val waited = withFrameMillis { it } - startedAt
            if (waited < OperatorSheet.MIN_WORK_MS) delay(OperatorSheet.MIN_WORK_MS - waited)
            finalAlpha.animateTo(1f, tween(OperatorSheet.REDUCED_FADE_MS))
            delay(OperatorSheet.FINAL_HOLD_MS)
            latestDone()
            return@LaunchedEffect
        }
        val frameMs = 1000L / OperatorSheet.FPS
        var nextAt = startedAt + frameMs
        while (stepper.segment != OperatorSegment.HOLD) {
            val now = withFrameMillis { it }
            if (now >= nextAt) {
                stepper.advance(now, latestSucceeded)
                frame = stepper.index
                segment = stepper.segment
                nextAt += frameMs
                // After a stall continue from now instead of racing to catch up.
                if (now - nextAt > frameMs * 3) nextAt = now + frameMs
            }
        }
        delay(OperatorSheet.FINAL_HOLD_MS)
        stepper.finish()
        latestDone()
    }

    Box(modifier.testTag("mission_operator").clearAndSetSemantics { }) {
        val loaded = art ?: return@Box
        Canvas(Modifier.matchParentSize()) {
            val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
            fun draw(sheet: ImageBitmap, columns: Int, index: Int, alpha: Float = 1f) {
                val col = index % columns
                val row = index / columns
                drawImage(
                    sheet,
                    srcOffset = IntOffset(col * OperatorSheet.FRAME_W, row * OperatorSheet.FRAME_H),
                    srcSize = IntSize(OperatorSheet.FRAME_W, OperatorSheet.FRAME_H),
                    dstSize = dst,
                    alpha = alpha,
                    filterQuality = FilterQuality.None,
                )
            }
            if (reducedMotion) {
                draw(loaded.work, OperatorSheet.WORK_COLUMNS, 0, 1f - finalAlpha.value)
                if (finalAlpha.value > 0f) draw(loaded.success, OperatorSheet.SUCCESS_COLUMNS, OperatorSheet.SUCCESS_FRAMES - 1, finalAlpha.value)
            } else if (segment == OperatorSegment.WORK) {
                draw(loaded.work, OperatorSheet.WORK_COLUMNS, frame)
            } else {
                draw(loaded.success, OperatorSheet.SUCCESS_COLUMNS, frame.coerceIn(0, OperatorSheet.SUCCESS_FRAMES - 1))
            }
        }
    }
}
