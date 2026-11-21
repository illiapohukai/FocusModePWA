package io.github.illiapohukai.focusmode.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryGradient
import io.github.illiapohukai.focusmode.core.designsystem.component.PillTone
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.component.StatusPill
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge

data class HomeActions(
    val onQuickFocus: () -> Unit,
    val onOpenSession: () -> Unit,
    val onOpenMission: (String) -> Unit,
    val onOpenMissions: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenResult: (String) -> Unit,
    val onOpenSchedules: () -> Unit,
)

@Composable
fun HomeRoute(actions: HomeActions, contentPadding: PaddingValues, snackbar: SnackbarHostState, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                HomeEvent.OpenSession -> actions.onOpenSession()
                is HomeEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    HomeScreen(state, actions, viewModel::startMissionStep, contentPadding)
}

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, onStartStep: (String, String) -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("home"),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { Header(state) }
            state.banner?.let { banner -> item { Banner(banner, actions) } }
            item { MissionSection(state.mission, state.activeMissionCount, actions, onStartStep) }
            item { SectionHeader("Quick Focus", Modifier.padding(top = Spacing.xs)) }
            item { QuickFocusRow(state.quickMinutes, actions.onQuickFocus) }
            item {
                SectionHeader("Heute", Modifier.padding(top = Spacing.xs)) {
                    Text("Woche: ${state.progress.weekSessions} ${if (state.progress.weekSessions == 1) "Sitzung" else "Sitzungen"} · ${state.progress.weekMinutes} Min.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            }
            item { TodayCard(state) }
            item { SectionHeader("Erinnerungen", Modifier.padding(top = Spacing.xs)) }
            item { RemindersCard(state, actions.onOpenSchedules) }
            item { SectionHeader("Aktivität", Modifier.padding(top = Spacing.xs)) { TextAction("Alle anzeigen", actions.onOpenHistory) } }
            if (state.activity.isEmpty()) {
                item { Text("Noch keine Sitzungen. Deine erste Fokuszeit erscheint hier.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(horizontal = 2.dp)) }
            } else {
                items(state.activity, key = { it.sessionId }) { ActivityRow(it) { actions.onOpenResult(it.sessionId) } }
            }
        }
    }
}

/**
 * PWA `.home-header`: slate date over the heavy brand title, the readiness pill on the right. On
 * phones the pill shares the date line so the brand title keeps one line at 360 dp.
 */
@Composable
private fun Header(state: HomeUiState) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.dateLabel, style = LabelType.Date, color = FocusPalette.DateInk, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.xs))
            StatusPill(
                if (state.online) "Bereit" else "Offline",
                tone = PillTone.SURFACE,
                dot = if (state.online) FocusPalette.Green else FocusPalette.GraphiteSoft,
                modifier = Modifier.semantics { contentDescription = if (state.online) "Bereit. AI-Planung erreichbar." else "Offline. Fokus funktioniert ohne Internet." },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("Focus Mode+", style = LabelType.Brand, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Banner(banner: SessionBanner, actions: HomeActions) {
    val (title, body, action) = when (banner) {
        is SessionBanner.Running -> Triple("Fokus läuft", "Noch ${banner.remainingMinutes} Min. · ${banner.task}", actions.onOpenSession)
        SessionBanner.Frozen -> Triple("Sitzung angehalten", "Fortsetzen, speichern oder verwerfen.", actions.onOpenSession)
        is SessionBanner.Review -> Triple("Ergebnis offen", "Bewerte deinen Missionsschritt.", { actions.onOpenResult(banner.sessionId) })
    }
    AubergineSurface(Modifier.fillMaxWidth().testTag("home_session_banner"), onClick = action, onClickLabel = "Sitzung öffnen", contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(Radii.pill).background(FocusPalette.Green))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(FocusIcons.ChevronRight, null, tint = Color.White)
        }
    }
}

/** PWA hero kicker: live dot, uppercase label, optional outlined chip on the right. */
@Composable
private fun HeroKicker(label: String, chip: String?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(Radii.pill).background(FocusPalette.Green))
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14.em), color = FocusPalette.HeroKicker, modifier = Modifier.weight(1f))
        chip?.let {
            Text(
                it.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1,
                modifier = Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.06f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** Inner glass card of the hero (PWA `.mh-next`): tile, small label, title, one line of detail. */
@Composable
private fun HeroNextCard(label: String, title: String, detail: String?, accent: Color, onClick: (() -> Unit)?, tile: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Radii.lg).background(Color.White.copy(alpha = 0.06f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), Radii.lg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accent)
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onClick != null) Icon(FocusIcons.ChevronRight, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MissionSection(mission: MissionCard?, activeCount: Int, actions: HomeActions, onStartStep: (String, String) -> Unit) {
    if (mission == null) {
        AubergineSurface(Modifier.fillMaxWidth().testTag("home_mission_empty"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
            HeroKicker("Focus Mission", "Bereit", FocusPalette.Lavender)
            Spacer(Modifier.height(Spacing.sm))
            Text("Was möchtest du heute fokussiert schaffen?", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            Text("Plane eine Mission oder starte direkt einen Quick Focus.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = FocusPalette.HeroCopy)
            Spacer(Modifier.height(Spacing.md))
            HeroNextCard("Missionen", "Missionen ansehen", "Aktive, abgeschlossene und archivierte", FocusPalette.Lavender, actions.onOpenMissions) {
                IconTile(FocusIcons.Target, background = SolidColor(FocusPalette.Surface), tint = FocusPalette.Purple, size = 52.dp, iconSize = 24.dp)
            }
        }
        return
    }
    val tone = MissionTones.of(mission.theme)
    AubergineSurface(
        Modifier.fillMaxWidth().testTag("home_mission"), tone = tone, onClick = { actions.onOpenMission(mission.missionId) }, onClickLabel = "Mission öffnen",
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp),
    ) {
        HeroKicker("Aktive Mission", mission.stepLabel, tone.accent)
        Spacer(Modifier.height(Spacing.sm))
        Text(mission.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        if (mission.subtitle.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(mission.subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = FocusPalette.HeroCopy)
        }
        if (mission.nextStepTitle != null) {
            Spacer(Modifier.height(Spacing.md))
            HeroNextCard("Nächster Schritt", mission.nextStepTitle, mission.nextStepTask, tone.accent, onClick = null) {
                Column(
                    Modifier.size(52.dp).clip(Radii.tile).background(FocusPalette.Surface),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text("${mission.stepMinutes ?: 0}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = FocusPalette.Ink)
                    Text("MIN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = FocusPalette.GraphiteSoft)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { mission.percent / 100f },
                modifier = Modifier.weight(1f).height(6.dp).clip(Radii.pill),
                color = tone.accentStrong, trackColor = Color.White.copy(alpha = 0.12f), drawStopIndicator = {},
            )
            Spacer(Modifier.width(Spacing.sm))
            Text("${mission.percent} %", style = MaterialTheme.typography.labelMedium, color = tone.accent)
        }
        if (mission.stepId != null) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton("Schritt starten · ${mission.stepMinutes} Min.", { onStartStep(mission.missionId, mission.stepId) }, Modifier.fillMaxWidth(), icon = FocusIcons.PlayFilled)
        }
        if (activeCount > 1) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radii.md).clickable(role = Role.Button, onClickLabel = "Alle Missionen ansehen", onClick = actions.onOpenMissions)
                    .testTag("home_mission_more"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text("1 von $activeCount aktiven Missionen · Alle ansehen", style = MaterialTheme.typography.labelMedium, color = tone.accent)
            }
        }
    }
}

/** PWA `.home-quick-row`: gradient lightning tile, the prompt, a muted duration chip with an arrow. */
@Composable
private fun QuickFocusRow(minutes: Int, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_quick_focus"), onClick = onClick, onClickLabel = "Quick Focus einrichten", contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Zap, Modifier.shadow(8.dp, Radii.tile, ambientColor = FocusPalette.Purple.copy(alpha = 0.25f), spotColor = FocusPalette.Purple.copy(alpha = 0.3f)), background = PrimaryGradient, tint = Color.White)
            Spacer(Modifier.width(Spacing.sm))
            Text("Was möchtest du jetzt erledigen?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.xs))
            Row(
                Modifier.clip(Radii.sm).background(FocusPalette.SurfaceMuted).border(BorderStroke(1.dp, Color(0x0F2D231E)), Radii.sm).heightIn(min = 32.dp).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$minutes MIN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.04.em), color = FocusPalette.Graphite)
                Spacer(Modifier.width(6.dp))
                Icon(FocusIcons.ArrowRight, null, tint = FocusPalette.Graphite, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** PWA `.home-today`: three centred cells divided by hairlines. */
@Composable
private fun TodayCard(state: HomeUiState) {
    val p = state.progress
    FocusCard(Modifier.fillMaxWidth().testTag("home_today"), contentPadding = PaddingValues(vertical = 16.dp, horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Stat("${p.todayMinutes}m", "Fokus", "${p.todayMinutes} Minuten Fokus heute", Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight(0.7f), color = FocusPalette.Outline)
            Stat("${p.todaySessions}", if (p.todaySessions == 1) "Sitzung" else "Sitzungen", null, Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight(0.7f), color = FocusPalette.Outline)
            Stat("${p.streak.current}", if (p.streak.current == 1) "Tag Serie" else "Tage Serie", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, description: String?, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 6.dp).then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier.semantics(mergeDescendants = true) {}),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = LabelType.Stat, color = FocusPalette.Graphite, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
    }
}

/** Upcoming reminders; the empty state mirrors the PWA calendar card (tile, title, one line). */
@Composable
private fun RemindersCard(state: HomeUiState, onOpen: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_reminders"), onClick = onOpen, onClickLabel = "Geplanten Fokus öffnen", contentPadding = PaddingValues(Spacing.md)) {
        if (!state.remindersDeliverable && state.reminders.isNotEmpty()) {
            StatusPill("Benachrichtigungen deaktiviert", tone = PillTone.WARNING, icon = FocusIcons.Alert)
            Spacer(Modifier.height(Spacing.xs))
        }
        if (state.reminders.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(FocusIcons.Calendar, background = SolidColor(FocusPalette.SurfaceMuted), tint = FocusPalette.GraphiteSoft)
                Spacer(Modifier.height(Spacing.sm))
                Text("Keine anstehenden Erinnerungen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, textAlign = TextAlign.Center)
                Text("Geplanter Fokus und Mission-Deadlines erscheinen hier.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
            }
        } else {
            state.reminders.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(FocusIcons.Bell, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.whenLabel, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Purple)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(Radii.lg).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.lg)
            .clickable(role = Role.Button, onClickLabel = "Ergebnis öffnen", onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.time, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(item.minutes, style = MaterialTheme.typography.labelLarge, color = FocusPalette.Graphite)
            BadgeText(item.badge)
        }
    }
}

@Composable
private fun BadgeText(badge: OutcomeBadge) {
    val color = when (badge) {
        OutcomeBadge.COMPLETED -> Color(0xFF15803D)
        OutcomeBadge.PARTIAL -> FocusPalette.PurpleInk
        OutcomeBadge.CANCELLED, OutcomeBadge.INTERRUPTED -> FocusPalette.GraphiteSoft
    }
    Text(badge.label, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.clearAndSetSemantics { contentDescription = badge.label })
}
