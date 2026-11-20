package io.github.illiapohukai.focusmode.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screenHorizontal = 20.dp
    /** Content never stretches across a tablet: phone-like reading width. */
    val maxContentWidth = 560.dp
}

object Radii {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(18.dp)
    val lg = RoundedCornerShape(24.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Dimensional cards use soft aubergine-tinted shadows (PWA `--shadow: 0 14px 34px rgba(37,22,43,.11)`). */
object Elevation {
    val card: Dp = 10.dp
    val raised: Dp = 16.dp
    val nav: Dp = 18.dp
}

object TouchTarget {
    val min: Dp = 48.dp
    val primaryButtonHeight: Dp = 56.dp
}

/** PWA `styles/motion.css` tokens. Reduced motion shortens (never removes) state feedback. */
@Immutable
data class MotionTokens(
    val instant: Int,
    val fast: Int,
    val normal: Int,
    val emphasized: Int,
    val focusEnter: Int,
    val reduced: Boolean,
) {
    companion object {
        val Standard = MotionTokens(instant = 90, fast = 160, normal = 350, emphasized = 390, focusEnter = 720, reduced = false)
        val Reduced = MotionTokens(instant = 1, fast = 80, normal = 110, emphasized = 120, focusEnter = 120, reduced = true)
    }
}

object Easings {
    val Standard: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val SoftSpring: Easing = CubicBezierEasing(0.34f, 1.16f, 0.64f, 1f)
}
