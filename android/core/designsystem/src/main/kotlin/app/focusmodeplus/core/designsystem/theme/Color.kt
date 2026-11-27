package app.focusmodeplus.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.focusmodeplus.core.model.MissionTheme

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
    val GreenInk = Color(0xFF15803D)       // completed text (.history-status.status-completed)
    /** Restrained warm amber for partial outcomes and gentle warnings (`.home-activity-row.outcome-partial`). */
    val AmberInk = Color(0xFF8A4B0F)
    /** Open outcome node border (`.outcome-aborted .home-activity-node`). */
    val NodeOpen = Color(0xFFB3A897)
    /** Light text on aubergine summary surfaces (Fokus-Bilanz, Settings hero). */
    val HeroText = Color(0xFFE9E2F5)
    val HeroMuted = Color(0xFFB9ADC9)
    val Danger = Color(0xFFB42318)
    val DangerTint = Color(0xFFFBE9E7)
    val Shadow = Color(0xFF25162B)         // shadows are aubergine-tinted, never black
    val Ink = Color(0xFF14161B)            // brand and screen titles (.home-brand-title, .st-title)
    val DateInk = Color(0xFF7B8496)        // .home-current-date
    val HeroCopy = Color(0xFFC0C6D5)       // .mh-empty-copy on aubergine
    val HeroKicker = Color(0xE0FFFFFF)     // .mh-kicker rgba(255,255,255,.88)
    val SurfaceGlass = Color(0xF7FCF9F4)   // floating nav rgba(252,249,244,.97)

    // Fokus-Rhythmus chart and consistency grid (PWA styles/insights.css).
    val ChartMission = Color(0xFF2E1A3B)   // .fm-chart-seg.is-mission
    val ChartQuick = Color(0xFF8B5CF6)     // .fm-chart-seg.is-quick
    val ChartPlaceholder = Color(0xFFE6DED1)
    val ChartAxis = Color(0xFF8F877D)
    val ChartBaseline = Color(0x1F2D231E)
    val ChartAverage = Color(0x526D28D9)
    val ConsistencyEmpty = Color(0xFFECE5D8)
    val ConsistencyLow = Color(0xFFDCCDF6)
    val ConsistencyMid = Color(0xFFA78BFA)

    // Cosmic Focus environment (dark).
    val Space = Color(0xFF05050A)
    val SpaceRaised = Color(0xFF0E0B16)
    val Starlight = Color(0xFFF7F7FB)
    val StarlightMuted = Color(0xFF9797AA)
    val SpaceLine = Color(0x17FFFFFF)
    /** Active Focus shell (PWA `.uc-shell`): 170° graphite-indigo gradient under a purple bloom. */
    val SpaceTop = Color(0xFF040613)
    val SpaceMid = Color(0xFF07091B)
    val SpaceBottom = Color(0xFF090A1D)
    val SpaceBloom = Color(0x386D28D9)     // rgba(109,40,217,.22)
    val SpaceBloomHigh = Color(0x474C1D95) // rgba(76,29,149,.28)
    /** Protected-focus badge (`.uc-protect.is-active`): restrained green for the active state. */
    val ProtectInk = Color(0xFF5EEAD4)
    val ProtectDot = Color(0xFF34D399)
    val ProtectSurface = Color(0x85082F36)
    val ProtectLine = Color(0x5C34D399)
    /** Neutral badge (`.uc-protect.is-inactive`). */
    val NeutralBadgeInk = Color(0xFFCBD5E1)
    val NeutralBadgeSurface = Color(0x8F0F172A)
    val NeutralBadgeLine = Color(0x3D94A3B8)
    /** Mode chip in the Session header (`.uc-meta`). */
    val ModeChipInk = Color(0xFFBBA6FF)
    val ModeChipSurface = Color(0x335B21B6)
    val ModeChipLine = Color(0x24A78BFA)
    /** Task card (`#ucFocusCard`). */
    val TaskCardTop = Color(0x1BFFFFFF)
    val TaskCardBottom = Color(0x57272349)
    val TaskCardLine = Color(0x858B5CF6)
    val TaskLabel = Color(0xFF9BA3B8)
    /** End action (`.uc-actions` last button): rose surface, rose ink. */
    val EndInk = Color(0xFFE11D48)
    val EndSurfaceStart = Color(0xFFFFF1F2)
    val EndSurfaceEnd = Color(0xFFFCE7F3)
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
