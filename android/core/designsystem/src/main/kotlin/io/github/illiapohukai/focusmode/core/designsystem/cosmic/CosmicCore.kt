package io.github.illiapohukai.focusmode.core.designsystem.cosmic

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Quality tiers (PWA `TIERS`): particle density and extras scale down on weaker devices. */
enum class CosmicQuality(val density: Float, val stars: Int) { HIGH(0.55f, 70), MEDIUM(0.34f, 46), LOW(0.18f, 26) }

private data class Orbit(
    val radius: Float, val incline: Float, val rollDeg: Float, val flip: Int, val periodS: Float, val dir: Int,
    val lineWidth: Float, val lineAlpha: Float, val core: Color, val halo: Color, val dust: Int,
)

/** Orbit system translated from the PWA `ORBITS` (radii in core radii). */
private val ORBITS = listOf(
    Orbit(1.56f, 0.30f, 7f, 1, 30f, 1, 2.2f, 0.95f, Color(0xFFF0E8FF), Color(0xFF966CFF), 210),
    Orbit(1.36f, 0.60f, -56f, 1, 19f, -1, 1.0f, 0.50f, Color(0xFFD6C6FF), Color(0xFF8B5CF6), 84),
    Orbit(1.50f, 0.50f, 63f, 1, 15f, 1, 0.8f, 0.34f, Color(0xFFC6B8FD), Color(0xFF7C5CF6), 58),
    Orbit(1.50f, 0.42f, -22f, -1, 36f, -1, 1.15f, 0.50f, Color(0xFFE2D8FF), Color(0xFF9678FA), 96),
    Orbit(1.12f, 0.50f, -5f, -1, 11f, 1, 0f, 0f, Color(0xFFE2D8FF), Color(0xFF9678FA), 36),
)

/** Warm white, cool white, pale violet, lavender, restrained purple, accent (PWA `PALETTE`). */
private val PALETTE = listOf(
    Color(0xFFFFF4E8), Color(0xFFEEF0FF), Color(0xFFE0D6FF), Color(0xFFC4B5FD), Color(0xFFA78BFA), Color(0xFF9EC6F6),
)

private const val TAU = (PI * 2).toFloat()
private const val CAMERA = 5.6f
private const val STATIC_CLOCK = 7.4f

/** Particle state precomputed once per quality tier; positions are a closed-form function of time. */
private class Dust(quality: CosmicQuality) {
    val orbit: IntArray
    val phase: FloatArray
    val radial: FloatArray
    val lift: FloatArray
    val size: FloatArray
    val color: IntArray
    val alpha: FloatArray

    init {
        val rnd = Random(0x17c09e)
        val counts = ORBITS.map { (it.dust * quality.density).toInt().coerceAtLeast(4) }
        val n = counts.sum()
        orbit = IntArray(n); phase = FloatArray(n); radial = FloatArray(n); lift = FloatArray(n)
        size = FloatArray(n); color = IntArray(n); alpha = FloatArray(n)
        var i = 0
        counts.forEachIndexed { o, c ->
            repeat(c) {
                orbit[i] = o
                phase[i] = rnd.nextFloat() * TAU
                radial[i] = (rnd.nextFloat() - 0.5f) * 0.09f
                lift[i] = (rnd.nextFloat() - 0.5f) * 0.05f
                size[i] = 0.6f + rnd.nextFloat() * rnd.nextFloat() * 1.8f
                color[i] = if (rnd.nextFloat() < 0.12f) 5 else rnd.nextInt(5)
                alpha[i] = 0.35f + rnd.nextFloat() * 0.65f
                i++
            }
        }
    }
}

/** Initial tier from the device class; [rememberCosmicQuality] may step down at runtime. */
fun deviceCosmicQuality(context: Context): CosmicQuality {
    val am = context.getSystemService(ActivityManager::class.java)
    return when {
        am?.isLowRamDevice == true -> CosmicQuality.LOW
        (am?.memoryClass ?: 256) < 192 -> CosmicQuality.MEDIUM
        else -> CosmicQuality.HIGH
    }
}

/**
 * Native Cosmic Core. Decorative only (excluded from semantics); the timer is separate real text
 * placed over the core. One frame loop, which runs only while the screen is visible (lifecycle
 * STARTED), animation is allowed and reduced motion is off. Otherwise a static frame is drawn.
 * Adaptive quality steps down when frames are consistently slow; Session timing never depends on it.
 *
 * @param coreFraction core diameter relative to the smaller side of the canvas.
 * @param readabilityZone fraction of the core radius kept clear of front-layer orbits (timer area).
 */
@Composable
fun CosmicCore(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    reducedMotion: Boolean = false,
    coreFraction: Float = 0.5f,
    readabilityZone: Float = 0.82f,
) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val tier = remember { mutableIntStateOf(deviceCosmicQuality(context).ordinal) }
    val quality = CosmicQuality.entries[tier.intValue]
    val dust = remember(quality) { Dust(quality) }
    val clock = remember { mutableFloatStateOf(STATIC_CLOCK) }
    val birth = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val running = animate && visible && !reducedMotion

    LaunchedEffect(reducedMotion) { if (!reducedMotion) birth.animateTo(1f, tween(2150)) else birth.snapTo(1f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        val base = clock.floatValue
        var last = start
        var slowFrames = 0
        var frames = 0
        while (true) {
            withFrameNanos { now ->
                clock.floatValue = base + (now - start) / 1_000_000_000f
                val dt = now - last
                last = now
                frames++
                if (dt > 24_000_000L) slowFrames++
                if (frames >= 120) {
                    // Sustained jank: step down one tier (never up within a Session).
                    if (slowFrames > 40 && tier.intValue < CosmicQuality.LOW.ordinal) tier.intValue++
                    frames = 0; slowFrames = 0
                }
            }
        }
    }

    Box(
        modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val c = Offset(w / 2f, h / 2f)
                val r = min(w, h) * coreFraction / 2f
                val starRnd = Random(42)
                val stars = List(quality.stars) {
                    Triple(Offset(starRnd.nextFloat() * w, starRnd.nextFloat() * h), 0.6f + starRnd.nextFloat() * 1.3f, 0.15f + starRnd.nextFloat() * 0.55f)
                }
                val haze = Brush.radialGradient(listOf(Color(0x335239BE), Color(0x147457FF), Color.Transparent), center = c, radius = r * 3.4f)
                val halo = Brush.radialGradient(listOf(Color(0x806D28D9), Color(0x2E7C3AED), Color.Transparent), center = c, radius = r * 1.75f)
                val body = Brush.radialGradient(
                    listOf(Color(0xFF3B1F6E), Color(0xFF221238), Color(0xFF120A1E), Color(0xFF0A0612)),
                    center = c + Offset(-r * 0.28f, -r * 0.32f), radius = r * 1.35f,
                )
                val rim = Brush.sweepGradient(listOf(Color(0x66C4B5FD), Color(0x10C4B5FD), Color(0x40A78BFA), Color(0x10C4B5FD), Color(0x66C4B5FD)), center = c)
                val zone = Path().apply {
                    addRoundRect(RoundRect(Rect(c.x - r * readabilityZone, c.y - r * 0.36f, c.x + r * readabilityZone, c.y + r * 0.36f), CornerRadius(r * 0.3f)))
                }
                // Orbit polylines split into rear (behind the core) and front halves, cached per size.
                val orbitPaths = ORBITS.map { o -> buildOrbitPaths(o, c, r) }
                onDrawBehind {
                    drawRect(Color(0xFF05050A))
                    drawRect(haze)
                    stars.forEach { (p, s, a) -> drawCircle(Color.White.copy(alpha = a), s, p) }
                    val k = birth.value
                    val t = clock.floatValue
                    withTransform({ scale(0.9f + 0.1f * k, 0.9f + 0.1f * k, c) }) {
                        drawCircle(halo, r * 1.75f, c, alpha = k)
                        // Rear layer
                        ORBITS.forEachIndexed { i, o ->
                            if (o.lineWidth > 0f) drawPath(orbitPaths[i].first, o.halo.copy(alpha = o.lineAlpha * 0.45f * k), style = Stroke(o.lineWidth * density, cap = StrokeCap.Round))
                        }
                        drawDust(dust, c, r, t, k, front = false)
                        // Core
                        drawCircle(body, r, c, alpha = 0.25f + 0.75f * k)
                        drawCircle(rim, r, c, style = Stroke(1.2f * density), alpha = k)
                        // Front layer, kept clear of the timer readability zone.
                        clipPath(zone, ClipOp.Difference) {
                            ORBITS.forEachIndexed { i, o ->
                                if (o.lineWidth > 0f) drawPath(orbitPaths[i].second, o.core.copy(alpha = o.lineAlpha * k), style = Stroke(o.lineWidth * density, cap = StrokeCap.Round))
                            }
                            drawDust(dust, c, r, t, k, front = true)
                        }
                    }
                }
            },
    )
}

/** Weak-perspective projection of a point on an inclined, rolled orbit. Returns x, y, depth. */
private inline fun project(o: Orbit, theta: Float, radius: Float, lift: Float, out: (Float, Float, Float) -> Unit) {
    val x0 = radius * cos(theta)
    val ring = radius * sin(theta)
    val y0 = ring * o.incline + lift
    val z = ring * sqrt(1f - o.incline * o.incline) * o.flip
    val roll = o.rollDeg / 180f * PI.toFloat()
    val x = x0 * cos(roll) - y0 * sin(roll)
    val y = x0 * sin(roll) + y0 * cos(roll)
    val s = CAMERA / (CAMERA - z)
    out(x * s, y * s, z)
}

private fun buildOrbitPaths(o: Orbit, c: Offset, r: Float): Pair<Path, Path> {
    val rear = Path()
    val front = Path()
    var prevFront: Boolean? = null
    val steps = 96
    for (i in 0..steps) {
        val theta = i / steps.toFloat() * TAU
        project(o, theta, o.radius, 0f) { x, y, z ->
            val p = Offset(c.x + x * r, c.y + y * r)
            val isFront = z >= 0f
            val path = if (isFront) front else rear
            if (prevFront != isFront) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            prevFront = isFront
        }
    }
    return rear to front
}

private fun DrawScope.drawDust(d: Dust, c: Offset, r: Float, t: Float, k: Float, front: Boolean) {
    for (i in d.orbit.indices) {
        val o = ORBITS[d.orbit[i]]
        val theta = d.phase[i] + o.dir * TAU * t / o.periodS
        project(o, theta, o.radius + d.radial[i], d.lift[i]) { x, y, z ->
            if ((z >= 0f) == front) {
                val depthFade = if (front) 1f else 0.55f
                drawCircle(
                    PALETTE[d.color[i]].copy(alpha = (d.alpha[i] * depthFade * k).coerceIn(0f, 1f)),
                    radius = d.size[i] * density * (CAMERA / (CAMERA - z)) * 0.9f,
                    center = Offset(c.x + x * r, c.y + y * r),
                )
            }
        }
    }
}
