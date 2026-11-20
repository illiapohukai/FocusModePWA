package io.github.illiapohukai.focusmode.feature.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus

data class MissionsActions(val onOpenMission: (String) -> Unit, val onQuickFocus: () -> Unit, val onImport: () -> Unit)

@Composable
fun MissionsRoute(actions: MissionsActions, contentPadding: PaddingValues, viewModel: MissionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MissionsScreen(state, actions, contentPadding)
}

@Composable
fun MissionsScreen(state: MissionsUiState, actions: MissionsActions, contentPadding: PaddingValues = PaddingValues()) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("missions"),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal, end = Spacing.screenHorizontal,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { Text("Missionen", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() }) }
            if (!state.loading && state.active.isEmpty() && state.finished.isEmpty()) {
                item {
                    EmptyState(
                        FocusIcons.Target, "Noch keine Missionen",
                        "In dieser Android-Version planst du neue Missionen noch in der Web-App. " +
                            (if (state.importAvailable) "Du kannst sie per Backup übernehmen." else "Quick Focus funktioniert sofort."),
                    ) {
                        if (state.importAvailable) PrimaryButton("Backup übernehmen", actions.onImport, icon = FocusIcons.Download)
                        else PrimaryButton("Quick Focus starten", actions.onQuickFocus, icon = FocusIcons.Play)
                    }
                }
            }
            if (state.active.isNotEmpty()) item { SectionHeader("Aktiv") }
            items(state.active, key = { it.mission.id.value }) { MissionCard(it) { actions.onOpenMission(it.mission.id.value) } }
            if (state.finished.isNotEmpty()) item { SectionHeader("Abgeschlossen & archiviert") }
            items(state.finished, key = { it.mission.id.value }) { FinishedRow(it) { actions.onOpenMission(it.mission.id.value) } }
        }
    }
}

@Composable
private fun MissionCard(item: MissionListItem, onClick: () -> Unit) {
    val m = item.mission
    val tone = MissionTones.of(item.theme)
    AubergineSurface(Modifier.fillMaxWidth(), tone = tone, onClick = onClick, onClickLabel = "Mission öffnen") {
        Eyebrow("${m.type.label} · ${m.completedStepCount}/${m.steps.size} Schritte", color = tone.accent)
        Text(m.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(m.subject, m.deadlineText.takeIf { it != "Nicht angegeben" }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = tone.accent)
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { m.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radii.pill),
            color = tone.accentStrong, trackColor = Color.White.copy(alpha = 0.12f), drawStopIndicator = {},
        )
    }
}

@Composable
private fun FinishedRow(item: MissionListItem, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Text(item.mission.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
        Text(if (item.mission.status.name == "COMPLETED") "Abgeschlossen" else "Archiviert", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
    }
}

data class MissionDetailActions(val onBack: () -> Unit, val onOpenSession: () -> Unit)

@Composable
fun MissionDetailRoute(actions: MissionDetailActions, snackbar: SnackbarHostState, viewModel: MissionDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                MissionDetailEvent.OpenSession -> actions.onOpenSession()
                MissionDetailEvent.Closed -> actions.onBack()
                is MissionDetailEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    MissionDetailScreen(state, actions, viewModel::startCurrentStep, viewModel::archive, viewModel::delete)
}

@Composable
fun MissionDetailScreen(state: MissionDetailUiState, actions: MissionDetailActions, onStart: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("mission_detail"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { FocusTopBar(onBack = actions.onBack) }
            val item = state.item
            if (state.notFound || item == null) {
                if (!state.loading) item { EmptyState(FocusIcons.Info, "Mission nicht gefunden", "Diese Mission existiert auf diesem Gerät nicht (mehr).") }
                return@LazyColumn
            }
            val m = item.mission
            val tone = MissionTones.of(item.theme)
            item {
                AubergineSurface(Modifier.fillMaxWidth(), tone = tone) {
                    Eyebrow("${m.type.label} · ${m.progressPercent} %", color = tone.accent)
                    Text(m.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
                    Text(listOf(m.subject, "Termin: ${m.deadlineText}").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = tone.accent)
                    if (state.canStartCurrent) {
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton("Aktuellen Schritt starten", onStart, Modifier.fillMaxWidth().testTag("mission_start_step"), icon = FocusIcons.Play)
                    }
                }
            }
            item { SectionHeader("Schritte") }
            items(m.steps, key = { it.id.value }) { step ->
                val current = step.id == m.currentStepId
                FocusCard(Modifier.fillMaxWidth(), color = if (current) tone.tint else FocusPalette.Surface, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(Radii.pill).background(if (step.status == MissionStepStatus.COMPLETED) FocusPalette.Green else if (current) tone.accentStrong else FocusPalette.SurfaceMuted),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (step.status == MissionStepStatus.COMPLETED) Icon(FocusIcons.Check, "Erledigt", tint = Color.White, modifier = Modifier.size(16.dp))
                            else Text("${step.position + 1}", style = MaterialTheme.typography.labelMedium, color = if (current) Color.White else FocusPalette.GraphiteSoft)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(step.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                            if (step.task.isNotBlank()) Text(step.task, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${step.durationMinutes} Min.", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (m.status.name == "ACTIVE") SecondaryButton("Archivieren", onArchive, Modifier.weight(1f))
                    SecondaryButton("Löschen", { confirmDelete = true }, Modifier.weight(1f), icon = FocusIcons.Trash)
                }
                Text("Abgeschlossene Sitzungen dieser Mission bleiben im Verlauf erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Mission löschen?") },
            text = { Text("Die Mission und ihre Schritte werden von diesem Gerät entfernt. Dein Verlauf bleibt erhalten.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}
