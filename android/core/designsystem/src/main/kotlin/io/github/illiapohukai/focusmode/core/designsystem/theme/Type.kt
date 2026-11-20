package io.github.illiapohukai.focusmode.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.illiapohukai.focusmode.core.designsystem.R

/** Inter (variable, OFL) — the PWA's bundled typeface. Sizes are in sp, so system font scaling applies. */
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800), inter(900))

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

val FocusTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 1.0.em, letterSpacing = (-0.05).em),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 1.12.em, letterSpacing = (-0.03).em),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 1.15.em, letterSpacing = (-0.025).em),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 1.2.em, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 1.25.em, letterSpacing = (-0.015).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 1.3.em),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 1.3.em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 1.45.em, lineHeightStyle = trim),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 1.45.em, lineHeightStyle = trim),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 1.4.em),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 1.2.em),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 1.25.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 1.2.em, letterSpacing = 0.12.em),
)

/**
 * Timer typography contract (PWA `--fm-timer-*`): Inter Black, tight optical tracking, tabular
 * figures so digits never jitter. Shared by the static Quick Focus readout and the live timer.
 */
object TimerType {
    val Live = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 64.sp, lineHeight = 1.0.em,
        letterSpacing = (-0.055).em, fontFeatureSettings = "tnum",
    )
    val Readout = Live.copy(fontSize = 52.sp)
    val Compact = Live.copy(fontSize = 40.sp)
}
