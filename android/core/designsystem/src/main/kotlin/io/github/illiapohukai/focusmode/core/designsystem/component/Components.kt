package io.github.illiapohukai.focusmode.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.Elevation
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTone
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.designsystem.theme.TouchTarget

/** Dimensional rounded card on cream: soft aubergine-tinted shadow, hairline border. */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radii.lg,
    color: Color = FocusPalette.Surface,
    elevation: Dp = Elevation.card,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(elevation, shape, ambientColor = FocusPalette.Shadow.copy(alpha = 0.10f), spotColor = FocusPalette.Shadow.copy(alpha = 0.16f))
            .clip(shape)
            .background(color)
            .border(BorderStroke(1.dp, FocusPalette.Outline), shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Deep aubergine Mission/summary surface with the PWA hero light and vignette. */
@Composable
fun AubergineSurface(
    modifier: Modifier = Modifier,
    tone: MissionTone = MissionTones.Aubergine,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Radii.xl
    Column(
        modifier
            .shadow(Elevation.raised, shape, ambientColor = tone.base.copy(alpha = 0.25f), spotColor = tone.base.copy(alpha = 0.35f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(tone.base, tone.mid, tone.deep)))
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.075f), Color.Transparent), radius = 900f, center = androidx.compose.ui.geometry.Offset.Zero))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = Radii.pill
    val brush = if (enabled) Brush.linearGradient(listOf(FocusPalette.PurpleBright, FocusPalette.Purple, FocusPalette.PurpleDeep))
    else Brush.linearGradient(listOf(FocusPalette.OutlineStrong, FocusPalette.OutlineStrong))
    Row(
        modifier
            .heightIn(min = TouchTarget.primaryButtonHeight)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = FocusPalette.Purple.copy(alpha = 0.3f), spotColor = FocusPalette.Purple.copy(alpha = 0.36f))
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else FocusPalette.GraphiteSoft)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDark: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = Radii.pill
    val bg = if (onDark) Color.White.copy(alpha = 0.10f) else FocusPalette.SurfaceMuted
    val fg = when {
        !enabled -> if (onDark) FocusPalette.StarlightMuted else FocusPalette.GraphiteSoft
        onDark -> FocusPalette.Starlight
        else -> FocusPalette.Graphite
    }
    Row(
        modifier
            .heightIn(min = TouchTarget.min)
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, if (onDark) FocusPalette.SpaceLine else FocusPalette.Outline), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = FocusPalette.Purple) {
    Box(
        modifier.sizeIn(minHeight = TouchTarget.min).clip(Radii.pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = color) }
}

enum class PillTone { NEUTRAL, READY, ACCENT, WARNING, ON_DARK }

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.NEUTRAL, icon: ImageVector? = null) {
    val (bg, fg) = when (tone) {
        PillTone.NEUTRAL -> FocusPalette.SurfaceMuted to FocusPalette.GraphiteSoft
        PillTone.READY -> FocusPalette.GreenTint to Color(0xFF15803D)
        PillTone.ACCENT -> FocusPalette.PurpleTint to FocusPalette.PurpleInk
        PillTone.WARNING -> Color(0xFFFFF4E5) to Color(0xFF92400E)
        PillTone.ON_DARK -> Color.White.copy(alpha = 0.10f) to FocusPalette.Starlight
    }
    Row(
        modifier.clip(Radii.pill).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = FocusPalette.GraphiteSoft) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Semantic heading for screen readers. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = FocusPalette.GraphiteSoft,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke(this)
    }
}

/** Flow-screen top bar: one back target (48dp) and an optional semantic title. */
@Composable
fun FocusTopBar(onBack: (() -> Unit)?, modifier: Modifier = Modifier, title: String? = null, onDark: Boolean = false, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget.min)) {
                Icon(FocusIcons.ArrowLeft, contentDescription = "Zurück", tint = if (onDark) FocusPalette.Starlight else FocusPalette.Graphite)
            }
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = if (onDark) FocusPalette.Starlight else FocusPalette.Graphite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) Spacing.xs else 0.dp).semantics { heading() },
            )
        } else Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Whole row toggles (large touch target, Switch semantics). */
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) FocusPalette.Graphite else FocusPalette.GraphiteSoft)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = FocusPalette.Purple, uncheckedTrackColor = FocusPalette.SurfaceMuted, uncheckedBorderColor = FocusPalette.OutlineStrong),
        )
    }
}

@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = FocusPalette.Purple,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clip(Radii.md).clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.xs, horizontal = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(Radii.sm).background(FocusPalette.PurpleTint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        trailing?.invoke()
        Icon(FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(Radii.md).background(FocusPalette.PurpleTint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = FocusPalette.Purple, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.size(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(Spacing.xxs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.size(Spacing.md))
            action()
        }
    }
}

@Immutable
data class ChoiceOption<T>(val value: T, val label: String)

/** Single-choice segmented chips with radio semantics. */
@Composable
fun <T> ChoiceChips(options: List<ChoiceOption<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(Radii.pill).background(FocusPalette.SurfaceMuted).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(Radii.pill)
                    .background(if (isSelected) FocusPalette.Surface else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option.value) })
                    .padding(horizontal = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) FocusPalette.Graphite else FocusPalette.GraphiteSoft, maxLines = 1)
            }
        }
    }
}

/** Centers phone-width content on large screens instead of stretching cards across a tablet. */
@Composable
fun ReadableWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Spacing.maxContentWidth).fillMaxWidth()) { content() }
    }
}
