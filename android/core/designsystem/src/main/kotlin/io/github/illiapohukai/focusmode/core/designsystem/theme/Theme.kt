package io.github.illiapohukai.focusmode.core.designsystem.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.illiapohukai.focusmode.core.model.MotionPreference

private val PlanningColors = lightColorScheme(
    primary = FocusPalette.Purple,
    onPrimary = Color.White,
    primaryContainer = FocusPalette.PurpleTint,
    onPrimaryContainer = FocusPalette.PurpleInk,
    secondary = FocusPalette.Aubergine,
    onSecondary = Color.White,
    secondaryContainer = FocusPalette.SurfaceMuted,
    onSecondaryContainer = FocusPalette.Graphite,
    tertiary = FocusPalette.Green,
    onTertiary = Color.White,
    tertiaryContainer = FocusPalette.GreenTint,
    background = FocusPalette.Cream,
    onBackground = FocusPalette.Graphite,
    surface = FocusPalette.Surface,
    onSurface = FocusPalette.Graphite,
    surfaceVariant = FocusPalette.SurfaceMuted,
    onSurfaceVariant = FocusPalette.GraphiteSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FocusPalette.Surface,
    surfaceContainer = FocusPalette.Surface,
    surfaceContainerHigh = FocusPalette.SurfaceMuted,
    surfaceContainerHighest = FocusPalette.SurfaceMuted,
    outline = FocusPalette.OutlineStrong,
    outlineVariant = FocusPalette.Outline,
    error = FocusPalette.Danger,
    errorContainer = FocusPalette.DangerTint,
    inverseSurface = FocusPalette.Aubergine,
    inverseOnSurface = Color.White,
    scrim = Color(0x99160D1B),
)

private val CosmicColors = darkColorScheme(
    primary = FocusPalette.Lavender,
    onPrimary = FocusPalette.AubergineDeep,
    primaryContainer = FocusPalette.PurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = FocusPalette.Violet,
    background = FocusPalette.Space,
    onBackground = FocusPalette.Starlight,
    surface = FocusPalette.SpaceRaised,
    onSurface = FocusPalette.Starlight,
    surfaceVariant = Color(0xFF1A1526),
    onSurfaceVariant = FocusPalette.StarlightMuted,
    surfaceContainer = Color(0xFF14101E),
    surfaceContainerHigh = Color(0xFF1A1526),
    surfaceContainerHighest = Color(0xFF221B31),
    outline = Color(0x33FFFFFF),
    outlineVariant = FocusPalette.SpaceLine,
    error = Color(0xFFFFB4AB),
)

private val FocusShapes = Shapes(
    extraSmall = Radii.sm, small = Radii.sm, medium = Radii.md, large = Radii.lg, extraLarge = Radii.xl,
)

val LocalMotion = staticCompositionLocalOf { MotionTokens.Standard }

/** True when the system "Remove animations" setting is on (animator scale 0). */
@Composable
fun systemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** Light planning theme (Home, planning, History, Settings). */
@Composable
fun FocusModeTheme(motion: MotionPreference = MotionPreference.SYSTEM, content: @Composable () -> Unit) {
    val reduced = motion == MotionPreference.REDUCED || systemReducedMotion()
    CompositionLocalProvider(LocalMotion provides if (reduced) MotionTokens.Reduced else MotionTokens.Standard) {
        MaterialTheme(colorScheme = PlanningColors, typography = FocusTypography, shapes = FocusShapes, content = content)
    }
}

/** Dark Cosmic Focus environment for the active Session. */
@Composable
fun CosmicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CosmicColors, typography = FocusTypography, shapes = FocusShapes, content = content)
}

internal val CardShadowSpread = 0.dp
