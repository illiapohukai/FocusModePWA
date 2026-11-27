package app.focusmodeplus.core.designsystem.component

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.Inter
import app.focusmodeplus.core.designsystem.theme.Radii
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real launcher icon of an installed app, loaded lazily off the main thread at the drawn size
 * and never stored. Until it is loaded (or when the app is gone) the label's initial is shown.
 * Decorative: the app name is always next to it as text.
 */
@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName, px) {
        value = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap() } catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    Box(modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        val loaded = icon
        if (loaded != null) {
            Image(loaded, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Box(Modifier.size(size).clip(Radii.sm).background(FocusPalette.SurfaceMuted), contentAlignment = Alignment.Center) {
                Text(label.take(1).uppercase(), fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp, color = FocusPalette.GraphiteSoft)
            }
        }
    }
}

/** Overlapping real icons of up to [max] apps plus a "+N" chip (PWA selected-app stack). */
@Composable
fun AppIconStack(apps: List<Pair<String, String>>, total: Int, modifier: Modifier = Modifier, max: Int = 4, size: Dp = 34.dp) {
    val shown = apps.take(max)
    val extra = total - shown.size
    androidx.compose.foundation.layout.Row(modifier.clearAndSetSemantics { }, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(-(size / 3))) {
        shown.forEach { (pkg, label) ->
            Box(
                Modifier.size(size).clip(Radii.sm).background(FocusPalette.Surface)
                    .border(androidx.compose.foundation.BorderStroke(1.5.dp, FocusPalette.Surface), Radii.sm),
                contentAlignment = Alignment.Center,
            ) { AppIcon(pkg, label, size = size - 4.dp) }
        }
        if (extra > 0) {
            Box(Modifier.size(size).clip(Radii.sm).background(FocusPalette.SurfaceMuted), contentAlignment = Alignment.Center) {
                Text("+$extra", fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 12.sp, color = FocusPalette.Graphite)
            }
        }
        if (shown.isEmpty() && extra <= 0) {
            Box(Modifier.size(size).clip(Radii.sm).background(FocusPalette.SurfaceMuted))
        }
    }
}
