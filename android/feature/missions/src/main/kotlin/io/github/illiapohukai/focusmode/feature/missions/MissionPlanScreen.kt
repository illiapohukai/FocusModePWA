package io.github.illiapohukai.focusmode.feature.missions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTone
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.mission.MissionCalendar
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.StepState
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MissionDetailActions(
    val onBack: () -> Unit,
    val onOpenSession: () -> Unit,
    val onOpenResult: (String) -> Unit = {},
)

@Composable
fun MissionPlanRoute(actions: MissionDetailActions, snackbar: SnackbarHostState, viewModel: MissionDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                MissionDetailEvent.OpenSession -> actions.onOpenSession()
                is MissionDetailEvent.OpenResult -> actions.onOpenResult(e.sessionId)
                MissionDetailEvent.Closed -> actions.onBack()
                is MissionDetailEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    MissionPlanScreen(
        state, actions.onBack,
        PlanCallbacks(viewModel::startCurrentStep, viewModel::openSession, viewModel::openReview, viewModel::archive, viewModel::restore, viewModel::delete, viewModel::rename),
    )
}

data class PlanCallbacks(
    val onStart: () -> Unit = {},
    val onOpenSession: () -> Unit = {},
    val onReview: () -> Unit = {},
    val onArchive: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDelete: () -> Unit = {},
    /** PWA "Umbenennen": the title only; id, colour, steps, History and reminders stay. */
    val onRename: (String) -> Unit = {},
)

private val PLAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** PWA hero status line (`aiPlanStatus`). */
internal fun planStatus(m: Mission): String = when {
    m.status == MissionStatus.COMPLETED -> "Mission abgeschlossen"
    m.status == MissionStatus.ARCHIVED -> "Archiviert"
    m.completedStepCount == 0 -> "Bereit für den ersten Schritt"
    else -> m.currentStep?.let { "Schritt ${m.steps.indexOf(it) + 1} von ${m.steps.size} als Nächstes" } ?: "Alle Schritte bearbeitet"
}

@Composable
fun MissionPlanScreen(state: MissionDetailUiState, onBack: () -> Unit, callbacks: PlanCallbacks) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding().testTag("mission_detail"),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                FocusTopBar(onBack = onBack, title = "Focus Mission") {
                    if (state.mission != null) {
                        androidx.compose.material3.IconButton(onClick = { renaming = true }, enabled = !state.busy, modifier = Modifier.size(48.dp).testTag("plan_rename")) {
                            Icon(FocusIcons.Pencil, contentDescription = "Mission umbenennen", tint = FocusPalette.Graphite)
                        }
                    }
                }
            }
            val item = state.item
            if (state.notFound || item == null) {
                if (!state.loading) item { EmptyState(FocusIcons.Info, "Mission nicht gefunden", "Diese Mission existiert auf diesem Gerät nicht (mehr).") }
                return@LazyColumn
            }
            val m = item.mission
            val tone = MissionTones.of(item.theme)
            item { PlanHero(m, tone, state) }
            item {
                Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("Deine Missionsschritte", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
                    Text("${m.completedStepCount} von ${m.steps.size} erledigt", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                }
            }
            item {
                Column(Modifier.semantics { collectionInfo = CollectionInfo(m.steps.size, 1) }) {
                    m.steps.forEachIndexed { i, step ->
                        RouteStep(
                            index = i, total = m.steps.size, step = step, state = state.stepStates[step.id] ?: StepState.UPCOMING, tone = tone,
                            canStart = state.canStartCurrent && !state.busy, callbacks = callbacks,
                        )
                    }
                }
            }
            if (m.summary.isNotBlank()) item { WhyCard(m.summary) }
            if (state.otherSessionActive) {
                item { Text("Eine andere Fokus-Sitzung läuft gerade. Beende sie, um diesen Schritt zu starten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft) }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        when (m.status) {
                            MissionStatus.ACTIVE -> SecondaryButton("Archivieren", { confirmArchive = true }, Modifier.weight(1f).testTag("plan_archive"), enabled = !state.busy, icon = FocusIcons.Archive)
                            MissionStatus.ARCHIVED -> SecondaryButton(
                                "Wieder aktivieren", callbacks.onRestore, Modifier.weight(1f).testTag("plan_restore"),
                                enabled = !state.busy && state.activeCount < 2, icon = FocusIcons.Rotate,
                            )
                            MissionStatus.COMPLETED -> Unit
                        }
                        SecondaryButton("Löschen", { confirmDelete = true }, Modifier.weight(1f).testTag("plan_delete"), enabled = !state.busy, icon = FocusIcons.Trash)
                    }
                    Text("Abgeschlossene Sitzungen dieser Mission bleiben im Verlauf erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Mission archivieren?") },
            text = { Text("Sie verschwindet von Home und zählt nicht mehr als aktiv. Fortschritt und Verlauf bleiben erhalten; du kannst sie später wieder aktivieren.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; callbacks.onArchive() }, Modifier.testTag("plan_archive_confirm")) { Text("Archivieren") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Abbrechen") } },
        )
    }
    if (renaming) {
        state.mission?.let { m -> RenameDialog(m.title, onDismiss = { renaming = false }, onSave = { renaming = false; callbacks.onRename(it) }) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Mission löschen?") },
            text = { Text("Die Mission, ihre Schritte, ihr Fortschritt und ihre Erinnerungen werden von diesem Gerät entfernt. Deine Fokus-Sitzungen bleiben im Verlauf erhalten. Das kann nicht rückgängig gemacht werden.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; callbacks.onDelete() }, Modifier.testTag("plan_delete_confirm")) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PlanHero(m: Mission, tone: MissionTone, state: MissionDetailUiState) {
    AubergineSurface(Modifier.fillMaxWidth().testTag("plan_hero"), tone = tone, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(Radii.sm).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(FocusIcons.Target, null, tint = tone.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.xs))
            Text(typeKicker(m.type).uppercase() + (m.subject.takeIf { it.isNotBlank() && it != "Allgemein" }?.let { " · ${it.uppercase()}" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = FocusPalette.HeroKicker, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            MissionCalendar.deadlineOf(m)?.let { d ->
                Row(Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp).semantics(mergeDescendants = true) { contentDescription = "Frist: ${PLAN_DATE.format(d)}" }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(FocusIcons.Calendar, null, tint = tone.accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(PLAN_DATE.format(d), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = tone.accent)
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(m.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Fortschritt ${m.progressPercent} Prozent, ${m.completedStepCount} von ${m.steps.size} Schritten erledigt. ${planStatus(m)}"
                progressBarRangeInfo = ProgressBarRangeInfo(m.progressPercent / 100f, 0f..1f)
            },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${m.progressPercent}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp), color = Color.White)
                Text("%", style = MaterialTheme.typography.titleLarge, color = tone.accent, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(planStatus(m), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text("${m.completedStepCount} von ${m.steps.size} Schritten erledigt", style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroCopy)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // PWA `.mp-route`: one segment per step; done = tone, current = white glow. Decorative.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            m.steps.forEach { s ->
                val st = state.stepStates[s.id]
                val color = when (st) {
                    StepState.COMPLETED -> tone.accent
                    StepState.CURRENT, StepState.IN_PROGRESS, StepState.NEEDS_REVIEW -> Color.White
                    else -> Color.White.copy(alpha = 0.14f)
                }
                Box(Modifier.weight(1f).height(6.dp).then(if (color == Color.White) Modifier.shadow(6.dp, Radii.pill, ambientColor = tone.glow, spotColor = tone.glow) else Modifier).clip(Radii.pill).background(color))
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            Meta("Offen", "${m.openStepCount}", Modifier.weight(1f))
            Meta("Restzeit", if (m.remainingMinutes > 0) formatTotal(m.remainingMinutes) else "–", Modifier.weight(1f))
            Meta("Nächster Fokus", m.currentStep?.let { "${it.durationMinutes} Min." } ?: "–", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Meta(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = FocusPalette.HeroCopy, maxLines = 2)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

/**
 * One stop on the plotted route: a node on a continuous line, then the step. The current step is
 * the dominant card with its action; completed and future steps stay compact.
 */
@Composable
private fun RouteStep(index: Int, total: Int, step: MissionStep, state: StepState, tone: MissionTone, canStart: Boolean, callbacks: PlanCallbacks) {
    val current = state == StepState.CURRENT || state == StepState.IN_PROGRESS || state == StepState.NEEDS_REVIEW
    val spoken = when (state) {
        StepState.COMPLETED -> "erledigt"
        StepState.CURRENT -> "aktueller Schritt"
        StepState.IN_PROGRESS -> "läuft gerade"
        StepState.NEEDS_REVIEW -> "Ergebnis offen"
        StepState.UPCOMING -> "noch offen"
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }
            .testTag("plan_step_$index"),
    ) {
        // Route column: line above, node, line below.
        Box(Modifier.width(36.dp).fillMaxHeight().clearAndSetSemantics { }, contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2f
                val nodeY = 30.dp.toPx()
                val doneColor = tone.accentStrong
                val upcoming = FocusPalette.OutlineStrong
                if (index > 0) drawLine(if (state == StepState.COMPLETED || current) doneColor else upcoming, Offset(x, 0f), Offset(x, nodeY), strokeWidth = 3.dp.toPx())
                if (index < total - 1) {
                    drawLine(
                        if (state == StepState.COMPLETED) doneColor else upcoming, Offset(x, nodeY), Offset(x, size.height), strokeWidth = 3.dp.toPx(),
                        pathEffect = if (state == StepState.COMPLETED) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
            }
            Box(
                Modifier.padding(top = if (current) 16.dp else 18.dp).size(if (current) 28.dp else 24.dp).clip(CircleShape)
                    .background(
                        when (state) {
                            StepState.COMPLETED -> tone.accentStrong
                            StepState.UPCOMING -> FocusPalette.Surface
                            else -> tone.base
                        },
                    )
                    .border(BorderStroke(if (current) 3.dp else 1.5.dp, if (state == StepState.UPCOMING) FocusPalette.OutlineStrong else tone.accentStrong), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (state == StepState.COMPLETED) Icon(FocusIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                else Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (state == StepState.UPCOMING) FocusPalette.GraphiteSoft else Color.White)
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (current) {
                AubergineSurface(Modifier.fillMaxWidth().testTag("plan_current_step"), tone = tone, contentPadding = PaddingValues(18.dp)) {
                    Column(Modifier.semantics(mergeDescendants = true) { stateDescription = spoken; contentDescription = "Schritt ${index + 1} von $total: ${step.title}, ${step.durationMinutes} Minuten, $spoken" }) {
                        Text(
                            when (state) {
                                StepState.IN_PROGRESS -> "LÄUFT GERADE"
                                StepState.NEEDS_REVIEW -> "ERGEBNIS OFFEN"
                                else -> "NÄCHSTER SCHRITT · ${index + 1} VON $total"
                            },
                            style = MaterialTheme.typography.labelSmall, color = tone.accent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(step.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        if (step.task.isNotBlank()) Text(step.task, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
                        Text("ca. ${step.durationMinutes} Min.", style = MaterialTheme.typography.labelMedium, color = tone.accent, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (step.requiredTools.isNotEmpty()) {
                        // PWA `mp-tools` ("Du brauchst"): the Worker's tools for this step, text only.
                        Spacer(Modifier.height(Spacing.sm))
                        Column(Modifier.semantics(mergeDescendants = true) {}.testTag("plan_step_tools")) {
                            Text("DU BRAUCHST", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = tone.accent)
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                step.requiredTools.forEach {
                                    Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    when (state) {
                        StepState.IN_PROGRESS -> PrimaryButton("Sitzung öffnen", callbacks.onOpenSession, Modifier.fillMaxWidth().testTag("plan_open_session"), icon = FocusIcons.Clock)
                        StepState.NEEDS_REVIEW -> PrimaryButton("Ergebnis bewerten", callbacks.onReview, Modifier.fillMaxWidth().testTag("plan_review"), icon = FocusIcons.Check)
                        else -> PrimaryButton("Schritt ${index + 1} starten", callbacks.onStart, Modifier.fillMaxWidth().testTag("mission_start_step"), enabled = canStart, icon = FocusIcons.PlayFilled)
                    }
                }
            } else {
                FocusCard(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = spoken; contentDescription = "Schritt ${index + 1} von $total: ${step.title}, ${step.durationMinutes} Minuten, $spoken" },
                    color = if (state == StepState.COMPLETED) FocusPalette.Surface else FocusPalette.Surface.copy(alpha = 0.7f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                step.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (state == StepState.COMPLETED) FontWeight.Bold else FontWeight.ExtraBold),
                                color = if (state == StepState.COMPLETED) FocusPalette.GraphiteSoft else FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (step.task.isNotBlank() && state != StepState.COMPLETED) Text(step.task, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Text(if (state == StepState.COMPLETED) "Erledigt" else "${step.durationMinutes} Min.", style = MaterialTheme.typography.labelMedium, color = if (state == StepState.COMPLETED) FocusPalette.GreenInk else FocusPalette.GraphiteSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun WhyCard(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    FocusCard(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentPadding = PaddingValues(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Radii.sm).clickable(role = Role.Button, onClickLabel = "So ist deine Mission aufgebaut") { open = !open }
                .semantics { stateDescription = if (open) "geöffnet" else "geschlossen" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Warum so?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text("So ist deine Mission aufgebaut", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
            Icon(if (open) FocusIcons.ChevronDown else FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
        }
        if (open) Text(text, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** PWA `openRenameMissionV116`: one field (80 characters), "Speichern" only with a usable title. */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    val clean = MissionRules.cleanTitle(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mission umbenennen") },
        text = {
            Column {
                Text("Der Plan und die bisherige Historie bleiben unverändert.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft)
                Spacer(Modifier.height(Spacing.sm))
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it.take(MissionRules.TITLE_MAX) }, singleLine = true,
                    label = { Text("Titel") }, supportingText = { Text("${text.length} / ${MissionRules.TITLE_MAX}") },
                    modifier = Modifier.fillMaxWidth().testTag("plan_rename_field"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { clean?.let(onSave) }, enabled = clean != null && clean != current, modifier = Modifier.testTag("plan_rename_save")) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
