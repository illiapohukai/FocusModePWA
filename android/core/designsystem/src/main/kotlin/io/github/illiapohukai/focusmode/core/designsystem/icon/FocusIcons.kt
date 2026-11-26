package io.github.illiapohukai.focusmode.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Stroke icons on a 24-unit grid with round caps, matching the PWA's line-icon character.
 * Drawn here (no icon library); tinted by `Icon`. Decorative unless given a contentDescription.
 */
object FocusIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    /** Solid glyphs (PWA uses filled play/stop marks on its primary Session actions). */
    private fun solid(name: String, d: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }.build()

    private const val CIRCLE9 = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0 -18z"

    val Home = icon("home", "M3.5 10.5L12 3.5l8.5 7", "M5.5 9v11h13V9", "M10 20v-5.5h4V20")
    val Target = icon("target", CIRCLE9, "M12 7.5a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0 -9z", "M12 11.5a0.5 0.5 0 1 0 0 1a0.5 0.5 0 1 0 0 -1z")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val History = icon("history", "M3.5 12a8.5 8.5 0 1 0 2.6-6.1", "M3.5 4v4.5H8", "M12 7.5V12l3 2")
    val Settings = icon("settings", "M4 7h9", "M17 7h3", "M15 5v4", "M4 17h3", "M11 17h9", "M9 15v4")
    val Bell = icon("bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Shield = icon("shield", "M12 3l7.5 3v5.5c0 4.7-3.2 8-7.5 9.5c-4.3-1.5-7.5-4.8-7.5-9.5V6z")
    val ShieldCheck = icon("shield_check", "M12 3l7.5 3v5.5c0 4.7-3.2 8-7.5 9.5c-4.3-1.5-7.5-4.8-7.5-9.5V6z", "M9 12l2 2l4-4")
    val Play = icon("play", "M8 5.5v13l10.5-6.5z")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val ChevronRight = icon("chevron_right", "M9.5 5.5l6.5 6.5l-6.5 6.5")
    val ArrowLeft = icon("arrow_left", "M19 12H5.5", "M11 6l-6 6l6 6")
    val Pencil = icon("pencil", "M4 20h4l10.5-10.5l-4-4L4 16z", "M13 7l4 4")
    val Calendar = icon("calendar", "M4.5 6h15v14h-15z", "M4.5 10h15", "M8.5 3.5v4", "M15.5 3.5v4")
    val Clock = icon("clock", CIRCLE9, "M12 7.5V12l3 2")
    val Moon = icon("moon", "M19.5 14.5A7.5 7.5 0 0 1 9.5 4.5a8 8 0 1 0 10 10z")
    val Apps = icon("apps", "M7.5 3.5h9a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z", "M11 17.5h2")
    val Info = icon("info", CIRCLE9, "M12 11v5", "M12 8h0.01")
    val Alert = icon("alert", "M12 4l9 16H3z", "M12 10v4", "M12 17h0.01")
    val Lock = icon("lock", "M6 11h12v9.5H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Activity = icon("activity", "M3 12h4l2.5-6l5 12l2.5-6h4")
    val Download = icon("download", "M12 4v11", "M7 10.5l5 5l5-5", "M5 20h14")
    val Trash = icon("trash", "M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l1 13h9l1-13")
    val Sparkle = icon("sparkle", "M12 3.5l1.8 5.2L19 10.5l-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z")
    val Zap = icon("zap", "M13 2.5L4 13.5h7l-1 8l9-11h-7z")
    val ArrowRight = icon("arrow_right", "M5 12h13.5", "M13 6l6 6l-6 6")
    val BookOpen = icon("book_open", "M3 5h5.5A3.5 3.5 0 0 1 12 8.5V20a2.5 2.5 0 0 0-2.5-2.5H3z", "M21 5h-5.5A3.5 3.5 0 0 0 12 8.5V20a2.5 2.5 0 0 1 2.5-2.5H21z")
    val User = icon("user", "M12 12.5a4 4 0 1 0 0-8a4 4 0 1 0 0 8z", "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val BarChart = icon("bar_chart", "M6 20v-6", "M12 20V8", "M18 20V4")
    val PlayFilled = solid("play_filled", "M7.5 4.8c0-.8.9-1.3 1.6-.9l10.6 6.9c.6.4.6 1.4 0 1.8L9.1 19.5c-.7.4-1.6-.1-1.6-.9z")
    val StopFilled = solid("stop_filled", "M7 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z")
    // v19 Missions
    val Flag = icon("flag", "M5 21V4.5", "M5 4.5h11.5l-2 4l2 4H5")
    val Image = icon("image", "M4.5 5h15v14h-15z", "M9 10.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 1 0 0 3z", "M19.5 15l-4.5-4.5L6 19")
    val Paperclip = icon("paperclip", "M20 11.5l-8 8a5 5 0 0 1-7-7l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7L9.7 17.2a1.7 1.7 0 0 1-2.4-2.4L15 7")
    val Layers = icon("layers", "M12 3.5l8.5 4.5L12 12.5L3.5 8z", "M3.5 12l8.5 4.5l8.5-4.5", "M3.5 16l8.5 4.5l8.5-4.5")
    val ChevronLeft = icon("chevron_left", "M14.5 5.5L8 12l6.5 6.5")
    val ChevronDown = icon("chevron_down", "M5.5 9.5L12 16l6.5-6.5")
    val Wand = icon("wand", "M4 20L15 9", "M14 4.5v3", "M17.5 6h3", "M12.5 6l1.5 1.5", "M18 9.5l1.5 1.5", "M15 9l1 1")
    val Archive = icon("archive", "M3.5 5h17v4h-17z", "M5 9v10.5h14V9", "M10 13h4")
    val Rotate = icon("rotate", "M4 12a8 8 0 1 0 2.4-5.7", "M4 4v4.5h4.5")
    val Gauge = icon("gauge", "M4 17a8 8 0 1 1 16 0", "M12 17l4-5")
    val Flame = icon("flame", "M12 21c-3.6 0-6-2.4-6-5.6c0-3.4 2.6-5 3.6-8.4c1.5 1.2 2 2.7 2 4c1-0.8 1.6-2 1.7-3.5c2.4 2 4.7 4.6 4.7 7.9C18 18.6 15.6 21 12 21z")
    val Camera = icon("camera", "M4 8h3.2l1.8-2.5h6l1.8 2.5H20v11H4z", "M12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0 -7z")
    val Mic = icon("mic", "M12 3.5a2.5 2.5 0 0 0-2.5 2.5v6a2.5 2.5 0 0 0 5 0V6A2.5 2.5 0 0 0 12 3.5z", "M6.5 11.5a5.5 5.5 0 0 0 11 0", "M12 17v3.5")
    val Upload = icon("upload", "M12 20V9", "M7 13.5l5-5l5 5", "M5 4h14")
    val Medal = icon("medal", "M8.5 3.5l2.3 6", "M15.5 3.5l-2.3 6", "M12 10a5 5 0 1 0 0 10a5 5 0 1 0 0 -10z")
}
