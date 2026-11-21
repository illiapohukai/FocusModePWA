package io.github.illiapohukai.focusmode.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.Elevation
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.NavGeometry
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing

data class NavDestinationItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Floating rounded bottom navigation (PWA `fm-bottom-nav`): a 65 dp cream capsule 10 dp above the
 * system navigation inset, four roots around a raised Plus action. Plus is an action (opens the
 * start sheet), never a root page.
 *
 * Labels share one fitted size: the longest label is measured once and every label uses the same
 * size, so nothing clips at 320 dp or large font scales and the row stays visually even. The full
 * label always stays in semantics.
 */
@Composable
fun FloatingBottomBar(
    items: List<NavDestinationItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.size == 4)
    val shape = Radii.pill
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(start = NavGeometry.sideMargin, end = NavGeometry.sideMargin, bottom = NavGeometry.bottomMargin),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val barWidth = minOf(maxWidth, 480.dp)
        val itemWidth = navItemWidth(barWidth)
        val labelStyle = rememberFittedNavLabelStyle(items.map { it.label }, itemWidth - 6.dp)
        Row(
            Modifier
                .testTag("bottom_nav")
                .width(barWidth)
                .heightIn(min = NavGeometry.barHeight)
                .shadow(Elevation.nav, shape, ambientColor = FocusPalette.Shadow.copy(alpha = 0.14f), spotColor = FocusPalette.Shadow.copy(alpha = 0.2f))
                .clip(shape)
                .background(FocusPalette.SurfaceGlass)
                .border(BorderStroke(1.dp, Color(0x1C2D231E)), shape)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.take(2).forEach { NavItem(it, it.key == selectedKey, onSelect, labelStyle, Modifier.weight(1f)) }
            Box(Modifier.width(PLUS_SLOT), contentAlignment = Alignment.Center) { PlusButton(onPlus, Modifier.offset(y = (-12).dp)) }
            items.drop(2).forEach { NavItem(it, it.key == selectedKey, onSelect, labelStyle, Modifier.weight(1f)) }
        }
    }
}

internal val PLUS_SLOT = 64.dp

/** Width each of the four items gets inside a bar of [barWidth] (8 dp padding, 1 dp border, Plus slot). */
internal fun navItemWidth(barWidth: Dp): Dp = (barWidth - 2.dp - 16.dp - PLUS_SLOT) / 4

/** One label size for every item: the base nav style, shrunk only as far as the longest label needs. */
@Composable
internal fun rememberFittedNavLabelStyle(labels: List<String>, available: Dp): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LabelType.Nav
    return remember(labels, available, density, measurer) {
        val availablePx = with(density) { available.toPx() }
        val widest = labels.maxOf { measurer.measure(it, base, maxLines = 1).size.width }.toFloat()
        if (widest <= availablePx || widest <= 0f) base
        else base.copy(fontSize = base.fontSize * (availablePx / widest).coerceAtLeast(0.55f))
    }
}

@Composable
private fun NavItem(item: NavDestinationItem, selected: Boolean, onSelect: (String) -> Unit, labelStyle: TextStyle, modifier: Modifier) {
    val motion = LocalMotion.current
    val bg by animateColorAsState(if (selected) FocusPalette.Glider else Color.Transparent, tween(motion.fast), label = "navBg")
    val fg by animateColorAsState(if (selected) FocusPalette.Graphite else FocusPalette.GraphiteSoft, tween(motion.fast), label = "navFg")
    val iconTint by animateColorAsState(if (selected) FocusPalette.Purple else FocusPalette.GraphiteSoft, tween(motion.fast), label = "navIcon")
    // The glider is a shaped background, not a clip: clipping the item to a pill cut the first and
    // last letters of labels sitting near its rounded bottom corners (v18 "Missionen" at 360 dp).
    Column(
        modifier
            .heightIn(min = 51.dp)
            .background(bg, Radii.pill)
            .selectable(
                selected = selected, role = Role.Tab,
                interactionSource = null, indication = ripple(bounded = false, radius = 30.dp),
                onClick = { onSelect(item.key) },
            )
            .semantics { contentDescription = item.label }
            .padding(top = 4.dp, bottom = 4.dp)
            .testTag("nav_${item.key}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(14.dp).height(3.5.dp).clip(Radii.pill).background(if (selected) FocusPalette.Purple else Color.Transparent))
        Spacer(Modifier.height(3.dp))
        Icon(item.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            style = labelStyle.copy(fontWeight = if (selected) FontWeight.Black else labelStyle.fontWeight),
            color = fg,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp).clearAndSetSemantics { testTag = "nav_label_${item.key}" },
        )
    }
}

@Composable
private fun PlusButton(onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(NavGeometry.plusSize)
            .shadow(12.dp, CircleShape, ambientColor = FocusPalette.Purple.copy(alpha = 0.3f), spotColor = FocusPalette.Purple.copy(alpha = 0.36f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(FocusPalette.PurpleBright, FocusPalette.Purple, FocusPalette.PurpleDeep)))
            .border(BorderStroke(4.dp, FocusPalette.Surface), CircleShape)
            .selectable(selected = false, role = Role.Button, onClick = onPlus)
            .semantics { contentDescription = "Neu starten" }
            .testTag("nav_plus"),
        contentAlignment = Alignment.Center,
    ) { Icon(FocusIcons.Plus, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
}

/** Wide windows (tablet, landscape, unfolded): the same destinations as a floating rail. */
@Composable
fun FloatingNavRail(
    items: List<NavDestinationItem>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = Radii.xl
    Column(
        modifier
            .fillMaxHeight()
            .padding(Spacing.sm)
            .shadow(Elevation.nav, shape, ambientColor = FocusPalette.Shadow.copy(alpha = 0.14f), spotColor = FocusPalette.Shadow.copy(alpha = 0.2f))
            .clip(shape)
            .background(FocusPalette.Surface)
            .border(BorderStroke(1.dp, FocusPalette.Outline), shape)
            .padding(vertical = Spacing.md, horizontal = 6.dp)
            .width(76.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.padding(top = Spacing.md, bottom = Spacing.sm)) { PlusButton(onPlus) }
        val labelStyle = rememberFittedNavLabelStyle(items.map { it.label }, 64.dp)
        items.forEach { NavItem(it, it.key == selectedKey, onSelect, labelStyle, Modifier.fillMaxWidth()) }
    }
}
