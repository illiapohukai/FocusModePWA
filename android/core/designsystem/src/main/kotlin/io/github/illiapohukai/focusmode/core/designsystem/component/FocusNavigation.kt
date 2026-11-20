package io.github.illiapohukai.focusmode.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.Elevation
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing

data class NavDestinationItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Floating rounded bottom navigation (PWA `fm-bottom-nav`): four roots around a raised Plus
 * action. Plus is an action (opens the start sheet), never a root page.
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
    val shape = Radii.xl
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .shadow(Elevation.nav, shape, ambientColor = FocusPalette.Shadow.copy(alpha = 0.14f), spotColor = FocusPalette.Shadow.copy(alpha = 0.2f))
                .clip(shape)
                .background(FocusPalette.Surface)
                .border(BorderStroke(1.dp, FocusPalette.Outline), shape)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .selectableGroup()
                .testTag("bottom_nav"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.take(2).forEach { NavItem(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { PlusButton(onPlus) }
            items.drop(2).forEach { NavItem(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun NavItem(item: NavDestinationItem, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val motion = LocalMotion.current
    val bg by animateColorAsState(if (selected) FocusPalette.Glider else Color.Transparent, tween(motion.fast), label = "navBg")
    val fg by animateColorAsState(if (selected) FocusPalette.Graphite else FocusPalette.GraphiteSoft, tween(motion.fast), label = "navFg")
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(Radii.pill)
            .background(bg)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.key) })
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag("nav_${item.key}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.width(14.dp).height(3.5.dp).clip(Radii.pill).background(if (selected) FocusPalette.Purple else Color.Transparent))
        Spacer(Modifier.height(3.dp))
        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        // Shrinks instead of clipping at large font scales; the full label stays in semantics.
        BasicText(
            item.label,
            style = MaterialTheme.typography.labelMedium.copy(color = fg, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            modifier = Modifier.padding(horizontal = 2.dp).clearAndSetSemantics { contentDescription = item.label },
        )
    }
}

@Composable
private fun PlusButton(onPlus: () -> Unit) {
    Box(
        Modifier
            .offset(y = (-14).dp)
            .size(56.dp)
            .shadow(12.dp, CircleShape, ambientColor = FocusPalette.Purple.copy(alpha = 0.3f), spotColor = FocusPalette.Purple.copy(alpha = 0.36f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(FocusPalette.PurpleBright, FocusPalette.Purple, FocusPalette.PurpleDeep)))
            .border(BorderStroke(3.dp, FocusPalette.Cream), CircleShape)
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
        items.forEach { NavItem(it, it.key == selectedKey, onSelect, Modifier.fillMaxWidth()) }
    }
}
