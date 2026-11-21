package io.github.illiapohukai.focusmode.core.designsystem.cosmic

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Adaptive quality tiers. Every tier keeps the same timer, core sphere and orbit identity; only
 * decorative detail and the animation cadence scale down.
 *
 * - [HIGH]: full approved orbit and particle detail at the display frame rate.
 * - [BALANCED]: about 60 % of the particles, same orbits and core.
 * - [LOW]: the primary orbit only, a sparse particle ring, about 30 fps, no orbit glow.
 */
enum class CosmicQuality(
    val density: Float,
    val stars: Int,
    /** How many of the orbit lines are drawn (from the primary ring outwards). */
    val orbitLines: Int,
    /** 0 = every display frame; otherwise the minimum time between animated frames. */
    val frameIntervalMs: Long,
    val glow: Boolean,
) {
    HIGH(0.55f, 70, 4, 0L, true),
    BALANCED(0.32f, 46, 4, 0L, true),
    LOW(0.15f, 26, 1, 33L, false),
    ;

    fun particleCount(): Int = ORBITS.sumOf { (it.dust * density).toInt().coerceAtLeast(MIN_DUST_PER_ORBIT) }

    fun lower(): CosmicQuality = entries[min(ordinal + 1, entries.lastIndex)]
}

/** Initial tier from the device class: low-RAM devices and battery saver start lower. */
object CosmicQualityPolicy {
    fun initial(lowRamDevice: Boolean, memoryClassMb: Int, powerSave: Boolean): CosmicQuality = when {
        lowRamDevice || powerSave -> CosmicQuality.LOW
        memoryClassMb < 192 -> CosmicQuality.BALANCED
        else -> CosmicQuality.HIGH
    }
}

/** Initial tier for this device; the governor may step down at runtime. */
fun deviceCosmicQuality(context: Context): CosmicQuality {
    val am = context.getSystemService(ActivityManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    val initial = CosmicQualityPolicy.initial(am?.isLowRamDevice == true, am?.memoryClass ?: 256, pm?.isPowerSaveMode == true)
    return maxOf(initial, CosmicQualityGovernor.processFloor)
}

/**
 * Steps quality down (never up) after sustained slow frames. Pure, so it is unit-tested. Long
 * pauses (screen off, backgrounding, a debugger) are ignored rather than counted as jank.
 */
class CosmicQualityGovernor(
    initial: CosmicQuality,
    private val window: Int = 120,
    private val slowFrameNanos: Long = 24_000_000L,
    private val maxSlowFrames: Int = 40,
    private val pauseNanos: Long = 250_000_000L,
) {
    var quality: CosmicQuality = initial
        private set
    private var frames = 0
    private var slow = 0

    /** Feed the time since the previous frame; returns the new tier when it stepped down. */
    fun onFrame(frameNanos: Long): CosmicQuality? {
        if (frameNanos <= 0L || frameNanos >= pauseNanos) return null
        val expected = quality.frameIntervalMs * 1_000_000L
        frames++
        if (frameNanos > expected + slowFrameNanos) slow++
        if (frames < window) return null
        val stepDown = slow > maxSlowFrames && quality != CosmicQuality.LOW
        frames = 0
        slow = 0
        if (!stepDown) return null
        quality = quality.lower()
        processFloor = maxOf(processFloor, quality)
        return quality
    }

    companion object {
        /** A device that needed a lower tier once keeps it for the process (no jank on every visit). */
        @Volatile var processFloor: CosmicQuality = CosmicQuality.HIGH
            internal set
    }
}

/** The decorative frame loop runs only while visible, allowed and not reduced. */
fun cosmicFrameLoopRuns(animate: Boolean, visible: Boolean, reducedMotion: Boolean): Boolean = animate && visible && !reducedMotion

private class Orbit(
    val radius: Float, val incline: Float, rollDeg: Float, flip: Int, val periodS: Float, val dir: Int,
    val lineWidth: Float, val lineAlpha: Float, val core: Color, val halo: Color, val dust: Int,
) {
    private val roll = rollDeg / 180f * PI.toFloat()
    val cosRoll = cos(roll)
    val sinRoll = sin(roll)
    /** Depth factor of the ring plane (sign = which half faces the viewer). */
    val depth = sqrt(1f - incline * incline) * flip
    val angularSpeed = dir * TAU / periodS
}

/** Orbit system translated from the PWA `ORBITS` (radii in core radii). The primary ring is first. */
private val ORBITS = listOf(
    Orbit(1.56f, 0.30f, 7f, 1, 30f, 1, 2.2f, 0.95f, Color(0xFFF0E8FF), Color(0xFF966CFF), 210),
    Orbit(1.50f, 0.42f, -22f, -1, 36f, -1, 1.15f, 0.50f, Color(0xFFE2D8FF), Color(0xFF9678FA), 96),
    Orbit(1.36f, 0.60f, -56f, 1, 19f, -1, 1.0f, 0.50f, Color(0xFFD6C6FF), Color(0xFF8B5CF6), 84),
    Orbit(1.50f, 0.50f, 63f, 1, 15f, 1, 0.8f, 0.34f, Color(0xFFC6B8FD), Color(0xFF7C5CF6), 58),
    Orbit(1.12f, 0.50f, -5f, -1, 11f, 1, 0f, 0f, Color(0xFFE2D8FF), Color(0xFF9678FA), 36),
)

/** Warm white, cool white, pale violet, lavender, restrained purple, accent (PWA `PALETTE`). */
private val PALETTE = listOf(
    Color(0xFFFFF4E8), Color(0xFFEEF0FF), Color(0xFFE0D6FF), Color(0xFFC4B5FD), Color(0xFFA78BFA), Color(0xFF9EC6F6),
)

private const val TAU = (PI * 2).toFloat()
private const val CAMERA = 5.6f
private const val STATIC_CLOCK = 7.4f
private const val MIN_DUST_PER_ORBIT = 4

/**
 * Particle state allocated once per tier. Positions are a closed-form function of one clock; the
 * per-frame projection is written into preallocated arrays (no allocation per particle or frame).
 */
private class Dust(quality: CosmicQuality) {
    val n: Int
    val orbit: IntArray
    val phase: FloatArray
    val radial: FloatArray
    val lift: FloatArray
    val size: FloatArray
    val color: IntArray
    val alpha: FloatArray
    val px: FloatArray
    val py: FloatArray
    val pz: FloatArray

    init {
        val rnd = Random(0x17c09e)
        val counts = ORBITS.map { (it.dust * quality.density).toInt().coerceAtLeast(MIN_DUST_PER_ORBIT) }
        n = counts.sum()
        orbit = IntArray(n); phase = FloatArray(n); radial = FloatArray(n); lift = FloatArray(n)
        size = FloatArray(n); color = IntArray(n); alpha = FloatArray(n)
        px = FloatArray(n); py = FloatArray(n); pz = FloatArray(n)
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

    /** Projects every particle once per frame (shared by the rear and the front pass). */
    fun project(t: Float, cx: Float, cy: Float, r: Float) {
        for (i in 0 until n) {
            val o = ORBITS[orbit[i]]
            val theta = phase[i] + o.angularSpeed * t
            val radius = o.radius + radial[i]
            val x0 = radius * cos(theta)
            val ring = radius * sin(theta)
            val y0 = ring * o.incline + lift[i]
            val z = ring * o.depth
            val s = CAMERA / (CAMERA - z)
            px[i] = cx + (x0 * o.cosRoll - y0 * o.sinRoll) * s * r
            py[i] = cy + (x0 * o.sinRoll + y0 * o.cosRoll) * s * r
            pz[i] = z
        }
    }
}

/**
 * Native Cosmic Core. Decorative only (excluded from semantics); the timer is separate real text
 * placed over it, outside these layers.
 *
 * Rendering is split into two layers: a static deep field (haze and stars, recorded once) and the
 * animated system in its own graphics layer, so a frame re-records only the orbits and particles,
 * never the rest of the screen, and a timer text change never re-records the Core. The shaded
 * sphere is baked once per size off the main thread (as the PWA bakes its core sprite).
 *
 * The frame loop runs only while the screen is at least STARTED, animation is allowed and reduced
 * motion is off; otherwise one static frame is drawn. Quality adapts (see [CosmicQualityGovernor])
 * without touching anything outside this composable: Session timing never depends on it.
 *
 * @param coreFraction core diameter relative to the smaller side of the canvas.
 * @param readabilityZone fraction of the core radius kept clear of front-layer orbits (timer area).
 * @param quality fixed tier (tests, previews); null = adaptive.
 */
@Composable
fun CosmicCore(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    reducedMotion: Boolean = false,
    coreFraction: Float = 0.5f,
    readabilityZone: Float = 0.82f,
    quality: CosmicQuality? = null,
    onQualityChange: ((CosmicQuality) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val tier = remember(quality) { mutableStateOf(quality ?: deviceCosmicQuality(context)) }
    val current = tier.value
    val dust = remember(current) { Dust(current) }
    val clock = remember { mutableFloatStateOf(STATIC_CLOCK) }
    val birth = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val running = cosmicFrameLoopRuns(animate, visible, reducedMotion)
    val qualityListener by rememberUpdatedState(onQualityChange)

    LaunchedEffect(reducedMotion) { if (!reducedMotion) birth.animateTo(1f, tween(2150)) else birth.snapTo(1f) }
    LaunchedEffect(running, quality) {
        if (!running) return@LaunchedEffect
        val governor = if (quality == null) CosmicQualityGovernor(tier.value) else null
        val start = withFrameNanos { it }
        val base = clock.floatValue
        var last = start
        while (true) {
            val interval = tier.value.frameIntervalMs
            if (interval > 0L) delay(interval)
            withFrameNanos { now ->
                clock.floatValue = base + (now - start) / 1_000_000_000f
                governor?.onFrame(now - last)?.let { stepped ->
                    tier.value = stepped
                    qualityListener?.invoke(stepped)
                }
                last = now
            }
        }
    }

    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        val localDensity = LocalDensity.current
        val coreRadiusPx = with(localDensity) { min(maxWidth.toPx(), maxHeight.toPx()) } * coreFraction / 2f
        val spriteSize = CosmicSphere.spriteSize(coreRadiusPx)
        val sprite by produceState<ImageBitmap?>(null, spriteSize) {
            value = if (spriteSize <= 0) null else withContext(Dispatchers.Default) { CosmicSphere.bakeBitmap(spriteSize, readabilityZone) }
        }

        // Static deep field: recorded once per size (reads no animated state).
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) * coreFraction / 2f
                val starRnd = Random(42)
                val count = current.stars
                val sx = FloatArray(count) { starRnd.nextFloat() * size.width }
                val sy = FloatArray(count) { starRnd.nextFloat() * size.height }
                val ss = FloatArray(count) { 0.6f + starRnd.nextFloat() * 1.3f }
                val sa = FloatArray(count) { 0.15f + starRnd.nextFloat() * 0.55f }
                val haze = Brush.radialGradient(listOf(Color(0x266D28D9), Color(0x124C1D95), Color.Transparent), center = c, radius = r * 3.4f)
                onDrawBehind {
                    drawRect(haze)
                    for (i in 0 until count) drawCircle(Color.White.copy(alpha = sa[i]), ss[i], Offset(sx[i], sy[i]))
                }
            },
        )

        // Animated system in its own layer: birth scale and alpha are layer properties (no re-record).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = birth.value
                    val s = 0.9f + 0.1f * k
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin.Center
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val c = Offset(w / 2f, h / 2f)
                    val r = min(w, h) * coreFraction / 2f
                    val zone = Rect(c.x - r * readabilityZone, c.y - r * 0.36f, c.x + r * readabilityZone, c.y + r * 0.36f)
                    val lines = ORBITS.take(current.orbitLines).filter { it.lineWidth > 0f }
                    val paths = lines.map { buildOrbitPaths(it, c, r, zone) }
                    val strokes = lines.map { Stroke(it.lineWidth * density, cap = StrokeCap.Round) }
                    val glowStroke = Stroke(lines.firstOrNull()?.let { it.lineWidth * 4.5f * density } ?: 0f, cap = StrokeCap.Round)
                    val rimStroke = Stroke(1.15f * density)
                    val halo = Brush.radialGradient(listOf(Color(0x806D28D9), Color(0x2E7C3AED), Color.Transparent), center = c, radius = r * 1.75f)
                    val atmosphere = Brush.radialGradient(
                        0f to Color.Transparent, 0.6f to Color.Transparent, 0.645f to Color(0x8C9872FF),
                        0.72f to Color(0x2E9872FF), 0.86f to Color(0x0A9872FF), 1f to Color.Transparent,
                        center = c + Offset(-r * 0.05f, -r * 0.08f), radius = r * 1.55f,
                    )
                    val rim = Brush.linearGradient(
                        0f to Color(0xC7EEE4FF), 0.45f to Color(0x63EEE4FF), 1f to Color.Transparent,
                        start = c + Offset(-0.52f * r, -0.85f * r), end = c + Offset(0.16f * r, 0.26f * r),
                    )
                    val fallbackBody = Brush.radialGradient(
                        listOf(Color(0xFF3B1F6E), Color(0xFF221238), Color(0xFF120A1E), Color(0xFF0A0612)),
                        center = c + Offset(-r * 0.28f, -r * 0.32f), radius = r * 1.35f,
                    )
                    val image = sprite
                    val dst = IntSize((r * 2f).roundToInt(), (r * 2f).roundToInt())
                    val dstOffset = IntOffset((c.x - r).roundToInt(), (c.y - r).roundToInt())
                    onDrawBehind {
                        val k = birth.value
                        val t = clock.floatValue
                        dust.project(t, c.x, c.y, r)
                        drawCircle(halo, r * 1.75f, c, alpha = k)
                        // Rear layer
                        lines.forEachIndexed { i, o -> drawPath(paths[i].first, o.halo, alpha = o.lineAlpha * 0.45f * k, style = strokes[i]) }
                        drawDust(dust, k, front = false, zone = zone)
                        // Core: baked shaded sphere, its atmosphere and the crisp rim light.
                        drawCircle(atmosphere, r * 1.55f, c, alpha = k)
                        if (image != null) {
                            drawImage(image, dstOffset = dstOffset, dstSize = dst, alpha = 0.25f + 0.75f * k, filterQuality = FilterQuality.Medium)
                        } else {
                            drawCircle(fallbackBody, r, c, alpha = 0.25f + 0.75f * k)
                        }
                        drawCircle(rim, r - 0.6f * density, c, style = rimStroke, alpha = k)
                        // Front layer: precomputed paths that already skip the timer readability zone.
                        if (current.glow && lines.isNotEmpty()) {
                            drawPath(paths[0].second, lines[0].halo, alpha = 0.16f * k, style = glowStroke)
                        }
                        lines.forEachIndexed { i, o -> drawPath(paths[i].second, o.core, alpha = o.lineAlpha * k, style = strokes[i]) }
                        drawDust(dust, k, front = true, zone = zone)
                    }
                },
        )
    }
}

/** Orbit polyline split into rear and front halves; the front half leaves the timer zone open. */
private fun buildOrbitPaths(o: Orbit, c: Offset, r: Float, zone: Rect): Pair<Path, Path> {
    val rear = Path()
    val front = Path()
    var penDownRear = false
    var penDownFront = false
    val steps = 144
    for (i in 0..steps) {
        val theta = i / steps.toFloat() * TAU
        val x0 = o.radius * cos(theta)
        val ring = o.radius * sin(theta)
        val y0 = ring * o.incline
        val z = ring * o.depth
        val s = CAMERA / (CAMERA - z)
        val x = c.x + (x0 * o.cosRoll - y0 * o.sinRoll) * s * r
        val y = c.y + (x0 * o.sinRoll + y0 * o.cosRoll) * s * r
        if (z >= 0f) {
            penDownRear = false
            if (zone.contains(Offset(x, y))) { penDownFront = false; continue }
            if (penDownFront) front.lineTo(x, y) else front.moveTo(x, y)
            penDownFront = true
        } else {
            penDownFront = false
            if (penDownRear) rear.lineTo(x, y) else rear.moveTo(x, y)
            penDownRear = true
        }
    }
    return rear to front
}

private fun DrawScope.drawDust(d: Dust, k: Float, front: Boolean, zone: Rect) {
    val depthFade = if (front) 1f else 0.55f
    for (i in 0 until d.n) {
        val z = d.pz[i]
        if ((z >= 0f) != front) continue
        val x = d.px[i]
        val y = d.py[i]
        if (front && x > zone.left && x < zone.right && y > zone.top && y < zone.bottom) continue
        drawCircle(
            PALETTE[d.color[i]],
            radius = d.size[i] * density * (CAMERA / (CAMERA - z)) * 0.9f,
            center = Offset(x, y),
            alpha = (d.alpha[i] * depthFade * k).coerceIn(0f, 1f),
        )
    }
}

/**
 * The PWA core sphere (`bakeCore`): per-pixel rim light from the upper left, a nebula mapped onto
 * the sphere, a darker centre where the timer sits, a specular glint and the light of the primary
 * ring. Baked once into a bitmap per size; drawing it is then one image per frame.
 */
object CosmicSphere {
    /** Bitmap edge in pixels for a core radius (PWA: 72 % of the diameter, 128–288 px). */
    fun spriteSize(coreRadiusPx: Float): Int = if (coreRadiusPx <= 0f) 0 else (coreRadiusPx * 2f * 0.72f).roundToInt().coerceIn(128, 288)

    fun bakeBitmap(q: Int, readabilityZone: Float): ImageBitmap =
        Bitmap.createBitmap(bake(q, readabilityZone), q, q, Bitmap.Config.ARGB_8888).asImageBitmap()

    /** ARGB pixels of the shaded sphere (transparent outside the limb). Pure; unit-tested. */
    fun bake(q: Int, readabilityZone: Float = 0.82f, accent: Int = 0xFF9EC6F6.toInt()): IntArray {
        val noise = ValueNoise(Random(0x5eed))
        val ar = (accent shr 16 and 0xFF).toFloat()
        val ag = (accent shr 8 and 0xFF).toFloat()
        val ab = (accent and 0xFF).toFloat()
        val zrx = max(0.5f, readabilityZone)
        val zry = max(0.34f, 0.36f * 1.1f)
        val primary = ORBITS[0]
        val hx = -0.3f
        val hy = -0.66f
        val hz = sqrt(1f - hx * hx - hy * hy)
        val out = IntArray(q * q)
        for (j in 0 until q) {
            val v0 = (j + 0.5f) / q * 2f - 1f
            for (i in 0 until q) {
                val u0 = (i + 0.5f) / q * 2f - 1f
                val rr = sqrt(u0 * u0 + v0 * v0)
                if (rr > 1.02f) continue
                val u = if (rr > 1f) u0 / rr else u0
                val v = if (rr > 1f) v0 / rr else v0
                val rc = min(rr, 1f)
                val nz = sqrt(max(0f, 1f - rc * rc))
                val ndl = -0.52f * u - 0.85f * v - 0.06f * nz
                val lit = smooth(-0.35f, 0.85f, ndl)
                val rimLit = smooth(-0.2f, 0.7f, ndl)
                val fres = (1f - nz).pow(2.4f)
                val kk = if (rc > 0.0001f) asin(rc) / rc else 1f
                val tu = u * kk
                val tv = v * kk
                val n1 = noise.fbm(tu * 1.5f + 3.1f, tv * 1.5f - 1.4f, 4)
                val n2 = noise.fbm(tu * 3.4f - 7.7f + n1 * 0.9f, tv * 3.4f + 2.2f - n1 * 0.6f, 3)
                val cloud = smooth(0.36f, 0.8f, n1 * 0.62f + n2 * 0.38f)
                val ex = u / zrx
                val ey = v / zry
                val hollow = 1f - smooth(0.62f, 1.4f, sqrt(ex * ex + ey * ey))
                val depth = 0.5f + 0.5f * nz.pow(0.6f)
                val nb = cloud * (0.26f + 0.74f * lit) * (1f - 0.88f * hollow) * depth
                val hot = nb * cloud * lit
                val rim = fres * (0.12f + 0.88f * rimLit)
                val edge = fres * fres * rimLit
                val counter = fres * smooth(0.15f, 0.9f, -ndl) * 0.3f
                val lens = exp(-((rc - 0.87f) / 0.014f).pow(2)) * (0.05f + 0.2f * lit)
                val ringY = primary.incline * primary.radius * 1.28f * sqrt(max(0f, 1f - (u / 1.7f).pow(2))) + 0.12f * u
                val sheen = exp(-((v - ringY + 0.05f) / 0.13f).pow(2)) * 0.2f * nz.pow(0.4f)
                val nh = max(0f, u * hx + v * hy + nz * hz)
                val glint = nh.pow(620f) + nh.pow(40f) * 0.17f
                val baseR = 5f + 24f * lit * depth
                val baseG = 4f + 13f * lit * depth
                val baseB = 15f + 64f * lit * depth
                val red = baseR + 108f * nb + 96f * hot + 150f * rim + 96f * edge + 62f * counter + 170f * lens + ar * sheen * 0.5f + 62f * sheen + 255f * glint
                val green = baseG + 64f * nb + 84f * hot + 116f * rim + 110f * edge + 24f * counter + 150f * lens + ag * sheen * 0.5f + 38f * sheen + 250f * glint
                val blue = baseB + 226f * nb + 30f * hot + 250f * rim + 20f * edge + 112f * counter + 255f * lens + ab * sheen * 0.5f + 118f * sheen + 255f * glint
                // Anti-aliased limb: alpha falls off over the outermost ~1 % of the radius.
                val alpha = (1f - smooth(0.99f, 1.02f, rr)) * 255f
                out[j * q + i] = (alpha.toInt() shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
            }
        }
        return out
    }

    private fun channel(v: Float): Int = v.toInt().coerceIn(0, 255)
}

private fun smooth(e0: Float, e1: Float, v: Float): Float {
    val k = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return k * k * (3f - 2f * k)
}

/** The PWA value noise (`createNoise`) with fractal sum. */
private class ValueNoise(rnd: Random) {
    private val perm = IntArray(512)
    private val values = FloatArray(256)

    init {
        for (i in 0 until 256) { perm[i] = i; values[i] = rnd.nextFloat() }
        for (i in 255 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = perm[i]; perm[i] = perm[j]; perm[j] = t
        }
        for (i in 0 until 256) perm[256 + i] = perm[i]
    }

    private fun at(x: Float, y: Float): Float {
        val xi = floor(x).toInt()
        val yi = floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val u = xf * xf * (3 - 2 * xf)
        val v = yf * yf * (3 - 2 * yf)
        val gx = xi and 255
        val gy = yi and 255
        val a = values[perm[gx + perm[gy]]]
        val b = values[perm[gx + 1 + perm[gy]]]
        val c = values[perm[gx + perm[gy + 1]]]
        val d = values[perm[gx + 1 + perm[gy + 1]]]
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    fun fbm(x0: Float, y0: Float, octaves: Int): Float {
        var x = x0
        var y = y0
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        repeat(octaves) {
            sum += amp * at(x, y)
            norm += amp
            x = x * 2.03f + 17.1f
            y = y * 2.03f - 9.7f
            amp *= 0.5f
        }
        return sum / norm
    }
}
