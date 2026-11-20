package io.github.illiapohukai.focusmode.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.PillTone
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.component.StatusPill
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
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
                start = Spacing.screenHorizontal, end = Spacing.screenHorizontal,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { Header(state) }
            state.banner?.let { banner -> item { Banner(banner, actions) } }
            item { MissionSection(state.mission, actions, onStartStep) }
            item { QuickFocusRow(state.quickMinutes, actions.onQuickFocus) }
            item { TodayCard(state) }
            item { RemindersCard(state, actions.onOpenSchedules) }
            item { SectionHeader("Aktivität") { TextAction("Alle anzeigen", actions.onOpenHistory) } }
            if (state.activity.isEmpty()) {
                item { Text("Noch keine Sitzungen. Deine erste Fokuszeit erscheint hier.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft) }
            } else {
                items(state.activity, key = { it.sessionId }) { ActivityRow(it) { actions.onOpenResult(it.sessionId) } }
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Eyebrow(state.dateLabel)
            Text("Focus Mode+", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        }
        StatusPill(
            if (state.online) "Bereit" else "Offline",
            tone = if (state.online) PillTone.READY else PillTone.NEUTRAL,
            modifier = Modifier.semantics { contentDescription = if (state.online) "Bereit. AI-Planung erreichbar." else "Offline. Fokus funktioniert ohne Internet." },
        )
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

@Composable
private fun MissionSection(mission: MissionCard?, actions: HomeActions, onStartStep: (String, String) -> Unit) {
    if (mission == null) {
        AubergineSurface(Modifier.fillMaxWidth().testTag("home_mission_empty")) {
            Eyebrow("Focus Mission", color = FocusPalette.Lavender)
            Spacer(Modifier.height(Spacing.xs))
            Text("Bereit für ein neues Ziel?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(Spacing.xxs))
            Text("Plane es als Mission oder starte direkt einen Quick Focus.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Lavender)
            Spacer(Modifier.height(Spacing.md))
            SecondaryButton("Missionen ansehen", actions.onOpenMissions, onDark = true)
        }
        return
    }
    val tone = MissionTones.of(mission.theme)
    AubergineSurface(Modifier.fillMaxWidth().testTag("home_mission"), tone = tone, onClick = { actions.onOpenMission(mission.missionId) }, onClickLabel = "Mission öffnen") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Aktive Mission", color = tone.accent, modifier = Modifier.weight(1f))
            Eyebrow(mission.stepLabel, color = tone.accent)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(mission.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (mission.subtitle.isNotBlank()) Text(mission.subtitle, style = MaterialTheme.typography.bodySmall, color = tone.accent)
        if (mission.nextStepTitle != null) {
            Spacer(Modifier.height(Spacing.md))
            Eyebrow("Nächster Schritt", color = tone.accent)
            Text(mission.nextStepTitle, style = MaterialTheme.typography.titleMedium, color = Color.White)
            mission.nextStepTask?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(
            progress = { mission.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radii.pill),
            color = tone.accentStrong, trackColor = Color.White.copy(alpha = 0.12f), drawStopIndicator = {},
        )
        Text("${mission.percent} %", style = MaterialTheme.typography.labelMedium, color = tone.accent, modifier = Modifier.padding(top = Spacing.xxs))
        if (mission.stepId != null) {
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton("Schritt starten · ${mission.stepMinutes} Min.", { onStartStep(mission.missionId, mission.stepId) }, Modifier.fillMaxWidth(), icon = FocusIcons.Play)
        }
    }
}

@Composable
private fun QuickFocusRow(minutes: Int, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_quick_focus"), onClick = onClick, onClickLabel = "Quick Focus einrichten", contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(Radii.sm).background(FocusPalette.PurpleTint), contentAlignment = Alignment.Center) {
                Icon(FocusIcons.Play, null, tint = FocusPalette.Purple)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Eyebrow("Quick Focus")
                Text("Was möchtest du jetzt erledigen?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
            }
            StatusPill("$minutes Min", tone = PillTone.ACCENT)
        }
    }
}

@Composable
private fun TodayCard(state: HomeUiState) {
    val p = state.progress
    FocusCard(Modifier.fillMaxWidth().testTag("home_today")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Heute", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
            Text("Woche: ${p.weekSessions} Sitzungen · ${p.weekMinutes} Min.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            Stat("${p.todayMinutes}", "Min. Fokus", Modifier.weight(1f))
            Stat("${p.todaySessions}", "Sitzungen", Modifier.weight(1f))
            Stat("${p.streak.current}", if (p.streak.current == 1) "Tag Serie" else "Tage Serie", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = FocusPalette.Graphite)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
    }
}

@Composable
private fun RemindersCard(state: HomeUiState, onOpen: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), onClick = onOpen, onClickLabel = "Geplanten Fokus öffnen", contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Bell, null, tint = FocusPalette.Purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Erinnerungen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f))
            Icon(FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
        }
        if (!state.remindersDeliverable && state.reminders.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            StatusPill("Benachrichtigungen deaktiviert", tone = PillTone.WARNING, icon = FocusIcons.Alert)
        }
        Spacer(Modifier.height(Spacing.xs))
        if (state.reminders.isEmpty()) {
            Text("Keine anstehenden Erinnerungen.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        } else {
            state.reminders.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(r.whenLabel, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Purple, modifier = Modifier.width(132.dp))
                    Text(r.title, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radii.md).background(FocusPalette.Surface)
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
