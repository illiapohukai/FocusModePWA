package io.github.illiapohukai.focusmode.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.illiapohukai.focusmode.core.model.MissionTheme

/**
 * Focus Mode+ color tokens, translated 1:1 from the PWA (`styles/navigation.css`, `missions.css`,
 * `cosmic-core.js`). Warm cream planning surfaces, graphite type, deep aubergine summary surfaces,
 * purple as the interaction accent, restrained green only for ready/active/completed states.
 */
object FocusPalette {
    val Cream = Color(0xFFF6F1E7)          // --app-bg
    val Surface = Color(0xFFFCF9F4)        // --surface
    val SurfaceMuted = Color(0xFFEFE9DD)   // --surface-muted
    val Glider = Color(0xC7EFE9DD)         // nav glider rgba(239,233,221,.78)
    val Graphite = Color(0xFF1D1A17)       // --text-primary
    val GraphiteSoft = Color(0xFF766E65)   // --text-secondary
    val Outline = Color(0x1A2D231E)        // --border rgba(45,35,30,.10)
    val OutlineStrong = Color(0xFFDCD2C2)
    val Purple = Color(0xFF6D28D9)         // --primary
    val PurpleBright = Color(0xFF7C3AED)
    val PurpleDeep = Color(0xFF4C1D95)
    val PurpleInk = Color(0xFF5B21B6)
    val PurpleTint = Color(0xFFF1EAFB)
    val Lavender = Color(0xFFC4B5FD)
    val Violet = Color(0xFF8B5CF6)
    val Aubergine = Color(0xFF25162B)      // --primary-dark / Mission base
    val AubergineMid = Color(0xFF1B1020)
    val AubergineDeep = Color(0xFF160D1B)
    val Green = Color(0xFF16A34A)          // --success
    val GreenTint = Color(0xFFE7F4EC)
    val Danger = Color(0xFFB42318)
    val DangerTint = Color(0xFFFBE9E7)
    val Shadow = Color(0xFF25162B)         // shadows are aubergine-tinted, never black

    // Cosmic Focus environment (dark).
    val Space = Color(0xFF05050A)
    val SpaceRaised = Color(0xFF0E0B16)
    val Starlight = Color(0xFFF7F7FB)
    val StarlightMuted = Color(0xFF9797AA)
    val SpaceLine = Color(0x17FFFFFF)
}

/** Stable Mission identity tones (PWA v17.7 `--mt-*`). */
@Immutable
data class MissionTone(
    val base: Color,
    val mid: Color,
    val deep: Color,
    val glow: Color,
    val accent: Color,
    val accentStrong: Color,
    val tint: Color,
    val ink: Color,
)

object MissionTones {
    val Aubergine = MissionTone(Color(0xFF25162B), Color(0xFF1B1020), Color(0xFF160D1B), Color(0x477C3AED), Color(0xFFC4B5FD), Color(0xFF8B5CF6), Color(0xFFF1EAFB), Color(0xFF5B21B6))
    val Forest = MissionTone(Color(0xFF14271F), Color(0xFF0F1E18), Color(0xFF0B1712), Color(0x3D2DA076), Color(0xFFA7E3C8), Color(0xFF3FA37C), Color(0xFFE7F2EC), Color(0xFF1F5E45))
    val Ember = MissionTone(Color(0xFF35190F), Color(0xFF28130B), Color(0xFF1F0F09), Color(0x42E2743A), Color(0xFFF6C4A3), Color(0xFFD9713F), Color(0xFFF8EBE2), Color(0xFF8A3A16))
    val Burgundy = MissionTone(Color(0xFF2E121C), Color(0xFF230D15), Color(0xFF1B0A10), Color(0x3DDB527C), Color(0xFFF3B9CB), Color(0xFFC84B74), Color(0xFFF7E7EC), Color(0xFF8C2347))

    fun of(theme: MissionTheme): MissionTone = when (theme) {
        MissionTheme.AUBERGINE -> Aubergine
        MissionTheme.FOREST -> Forest
        MissionTheme.EMBER -> Ember
        MissionTheme.BURGUNDY -> Burgundy
    }
}
