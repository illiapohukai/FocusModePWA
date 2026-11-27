package app.focusmodeplus.feature.missions

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusmodeplus.core.designsystem.component.AubergineSurface
import app.focusmodeplus.core.designsystem.component.EmptyState
import app.focusmodeplus.core.designsystem.component.Eyebrow
import app.focusmodeplus.core.designsystem.component.FocusCard
import app.focusmodeplus.core.designsystem.component.NavigationRow
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.SecondaryButton
import app.focusmodeplus.core.designsystem.component.SectionHeader
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.MissionTones
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.model.MissionStatus

data class MissionsActions(
    val onOpenMission: (String) -> Unit,
    val onQuickFocus: () -> Unit,
    val onImport: () -> Unit,
    val onCreate: () -> Unit = {},
)

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
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FOCUS MISSIONS", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
                        Text("Missionen", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
                    }
                    if (!state.loading) {
                        Text(
                            "${state.active.size}/2 aktiv", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft,
                            modifier = Modifier.semantics { contentDescription = "${state.active.size} von 2 aktiven Missionen" },
                        )
                    }
                }
            }
            if (!state.loading) {
                item {
                    if (state.limitReached) {
                        FocusCard(Modifier.fillMaxWidth().testTag("missions_limit"), color = FocusPalette.PurpleTint, contentPadding = PaddingValues(Spacing.md)) {
                            Text("Zwei aktive Missionen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                            Text("Schließe eine Mission ab oder archiviere sie, um eine neue zu planen. Archivierte Missionen kannst du später wieder aktivieren.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                        }
                    } else {
                        PrimaryButton(if (state.draftWaiting) "Mission weiter planen" else "Neue Mission", actions.onCreate, Modifier.fillMaxWidth().testTag("missions_create"), icon = FocusIcons.Sparkle)
                    }
                }
            }
            if (!state.loading && state.active.isEmpty() && state.finished.isEmpty()) {
                item {
                    EmptyState(FocusIcons.Target, "Noch keine Missionen", "Beschreibe ein Ziel und erhalte einen Plan aus konkreten Fokus-Schritten. Quick Focus funktioniert jederzeit ohne Mission.") {
                        SecondaryButton("Quick Focus starten", actions.onQuickFocus, icon = FocusIcons.Play)
                    }
                }
            }
            if (state.active.isNotEmpty()) item { SectionHeader("Aktiv") }
            items(state.active, key = { it.mission.id.value }) { MissionCard(it) { actions.onOpenMission(it.mission.id.value) } }
            if (state.finished.isNotEmpty()) item { SectionHeader("Abgeschlossen & archiviert") }
            items(state.finished, key = { it.mission.id.value }) { FinishedRow(it) { actions.onOpenMission(it.mission.id.value) } }
            if (state.importAvailable) {
                item { NavigationRow("Web-Backup übernehmen", actions.onImport, subtitle = "Missionen aus der Web-App importieren", icon = FocusIcons.Download) }
            }
        }
    }
}

@Composable
private fun MissionCard(item: MissionListItem, onClick: () -> Unit) {
    val m = item.mission
    val tone = MissionTones.of(item.theme)
    AubergineSurface(Modifier.fillMaxWidth().testTag("missions_card_${m.id.value}"), tone = tone, onClick = onClick, onClickLabel = "Mission öffnen") {
        Eyebrow("${typeKicker(m.type)} · ${m.completedStepCount}/${m.steps.size} Schritte", color = tone.accent)
        Text(m.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = listOfNotNull(m.subject.takeIf { it.isNotBlank() }, m.currentStep?.let { "Nächster Schritt: ${it.title}" }).joinToString(" · ")
        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = tone.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { m.progressPercent / 100f }, modifier = Modifier.weight(1f).height(6.dp).clip(Radii.pill),
                color = tone.accentStrong, trackColor = Color.White.copy(alpha = 0.12f), drawStopIndicator = {},
            )
            Spacer(Modifier.width(Spacing.sm))
            Text("${m.progressPercent} %", style = MaterialTheme.typography.labelMedium, color = tone.accent)
        }
    }
}

@Composable
private fun FinishedRow(item: MissionListItem, onClick: () -> Unit) {
    val tone = MissionTones.of(item.theme)
    FocusCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Mission öffnen", contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(Radii.pill).background(tone.accentStrong))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(item.mission.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (item.mission.status == MissionStatus.COMPLETED) "Abgeschlossen · ${item.mission.steps.size} Schritte"
                    else "Archiviert · ${item.mission.completedStepCount}/${item.mission.steps.size} Schritte",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                )
            }
        }
    }
}

