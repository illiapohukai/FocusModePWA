package app.focusmodeplus.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.focusmodeplus.core.designsystem.R

/** Inter (variable, OFL) — the PWA's bundled typeface. Sizes are in sp, so system font scaling applies. */
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800), inter(900))

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Type scale from the v17.16 PWA computed styles (390 px reference): screen titles 32–36 px at
 * 900–950, hero titles 27 px/900, card titles 22 px/900, body copy at 500–600, buttons at 900,
 * eyebrows 9.5–12 px/850 with wide tracking. Android keeps 11 sp as the smallest label size.
 */
val FocusTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 1.0.em, letterSpacing = (-0.05).em),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 1.04.em, letterSpacing = (-0.045).em),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 1.05.em, letterSpacing = (-0.03).em),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 1.15.em, letterSpacing = (-0.025).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 1.25.em, letterSpacing = (-0.015).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 1.3.em),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 1.3.em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 1.45.em, lineHeightStyle = trim),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 1.4.em, lineHeightStyle = trim),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 1.38.em),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 1.2.em),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 1.25.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 1.2.em, letterSpacing = 0.12.em),
)

/**
 * Timer typography contract (PWA `--fm-timer-*`): Inter Black, tight optical tracking, tabular
 * figures so digits never jitter. Shared by the static Quick Focus readout and the live timer.
 */
object TimerType {
    /** PWA `--fm-timer-size: clamp(48px, 15vw, 68px)` ≈ 58.5 px at 390 px: one size for setup and Session. */
    val Live = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 58.sp, lineHeight = 1.0.em,
        letterSpacing = (-0.055).em, fontFeatureSettings = "tnum",
    )
    val Readout = Live
    val Compact = Live.copy(fontSize = 44.sp)
    /** Smallest step when the readout must shrink to fit (large font scales, compact widths). */
    val MinFitSize = 30.sp
}

/** PWA eyebrow/kicker variants (date, section labels, hero kickers). */
object LabelType {
    val Date = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 1.1.em, letterSpacing = 0.03.em)
    val Kicker = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 1.2.em, letterSpacing = 0.08.em)
    val Brand = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 1.0.em, letterSpacing = (-0.045).em)
    val Stat = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 1.05.em, letterSpacing = (-0.03).em)
    val Nav = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, lineHeight = 1.1.em, letterSpacing = (-0.01).em)
}
