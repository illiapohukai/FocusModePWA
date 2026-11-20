package io.github.illiapohukai.focusmode.feature.focus

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.PillTone
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.StatusPill
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicCore
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.CosmicTheme
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.designsystem.theme.TimerType
import io.github.illiapohukai.focusmode.core.model.ProtectionMode

data class FocusActions(val onShowResult: (String) -> Unit, val onLeave: () -> Unit)

@Composable
fun FocusRoute(actions: FocusActions, snackbar: SnackbarHostState, viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is FocusEvent.ShowResult -> actions.onShowResult(e.sessionId)
                FocusEvent.Leave -> actions.onLeave()
                is FocusEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    FocusScreen(
        state = state,
        onRequestEnd = viewModel::requestEnd,
        onSelectNote = viewModel::selectNote,
        onConfirmEnd = viewModel::confirmEnd,
        onDiscard = viewModel::discard,
        onEmergency = viewModel::emergencyExit,
        onDismissEnd = viewModel::dismissEnd,
        onResume = { viewModel.resume() },
        onSaveRecovered = viewModel::saveRecovered,
        onDiscardRecovered = { viewModel.discardRecovered() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    state: FocusUiState,
    onRequestEnd: () -> Unit,
    onSelectNote: (io.github.illiapohukai.focusmode.core.model.EndNote) -> Unit,
    onConfirmEnd: () -> Unit,
    onDiscard: () -> Unit,
    onEmergency: () -> Unit,
    onDismissEnd: () -> Unit,
    onResume: () -> Unit,
    onSaveRecovered: () -> Unit,
    onDiscardRecovered: () -> Unit,
) {
    CosmicTheme {
        Box(Modifier.fillMaxSize().background(FocusPalette.Space).testTag("focus_screen")) {
            when (state.phase) {
                FocusPhase.RUNNING -> RunningContent(state, onRequestEnd)
                FocusPhase.FROZEN -> RecoveryContent(state, onResume, onSaveRecovered, onDiscardRecovered)
                FocusPhase.LOADING, FocusPhase.ENDED -> Unit
            }
        }
        state.endSheet?.let { sheet ->
            ModalBottomSheet(
                onDismissRequest = onDismissEnd,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = FocusPalette.SpaceRaised,
                contentColor = FocusPalette.Starlight,
            ) { EndSheet(sheet, onSelectNote, onConfirmEnd, onDiscard, onEmergency, onDismissEnd) }
        }
    }
}

@Composable
private fun RunningContent(state: FocusUiState, onRequestEnd: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reduced = LocalMotion.current.reduced
    val core: @Composable (Modifier) -> Unit = { m ->
        Box(m, contentAlignment = Alignment.Center) {
            CosmicCore(Modifier.fillMaxSize(), animate = true, reducedMotion = reduced, coreFraction = if (landscape) 0.62f else 0.56f)
            Text(
                state.timerText,
                style = if (landscape) TimerType.Compact else TimerType.Live,
                color = FocusPalette.Starlight,
                modifier = Modifier.testTag("focus_timer").clearAndSetSemantics { contentDescription = state.timerDescription },
            )
        }
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.md)) {
            core(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = Spacing.md), verticalArrangement = Arrangement.Center) {
                Header(state)
                Spacer(Modifier.height(Spacing.md))
                TaskCard(state)
                Spacer(Modifier.height(Spacing.md))
                SecondaryButton("Beenden", onRequestEnd, Modifier.fillMaxWidth().testTag("focus_end"), onDark = true)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Header(state)
            core(Modifier.weight(1f).fillMaxWidth())
            TaskCard(state)
            Spacer(Modifier.height(Spacing.md))
            SecondaryButton("Beenden", onRequestEnd, Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("focus_end"), onDark = true)
        }
    }
}

@Composable
private fun Header(state: FocusUiState) {
    Row(Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow(state.modeLabel, color = FocusPalette.Lavender)
            state.stepLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.StarlightMuted) }
        }
        StatusPill(state.protectionMode.label, tone = PillTone.ON_DARK,
            icon = if (state.protectionMode == ProtectionMode.PROTECTED) FocusIcons.ShieldCheck else FocusIcons.Shield,
        )
    }
}

@Composable
private fun TaskCard(state: FocusUiState) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(Radii.lg).background(Color.White.copy(alpha = 0.06f)).padding(Spacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Eyebrow("Aktuelle Aufgabe", color = FocusPalette.StarlightMuted)
        Text(state.task, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Starlight, maxLines = 3, overflow = TextOverflow.Ellipsis)
        state.monitoringLabel?.let {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FocusIcons.Activity, null, tint = FocusPalette.Lavender, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender)
            }
        }
    }
}

@Composable
private fun EndSheet(
    sheet: EndSheetState,
    onSelectNote: (io.github.illiapohukai.focusmode.core.model.EndNote) -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onEmergency: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal).padding(bottom = Spacing.xl).testTag("end_sheet")) {
        Text("Sitzung beenden?", style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Starlight, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        val counted = sheet.countedMinutes
        Text(
            if (counted >= 1) "Du hast $counted ${if (counted == 1) "Minute" else "Minuten"} fokussiert. Wenn du jetzt beendest, wird diese Zeit gespeichert."
            else "Unter einer Minute wird die Sitzung nicht gezählt.",
            style = MaterialTheme.typography.bodyMedium, color = FocusPalette.StarlightMuted,
        )
        if (sheet.barrier.reasonRequired) {
            Spacer(Modifier.height(Spacing.md))
            if (sheet.secondsLeft > 0) {
                Text(
                    "Atme kurz durch … ${sheet.secondsLeft}",
                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.Lavender,
                    modifier = Modifier.testTag("barrier_countdown").semantics { contentDescription = "Beenden ist in wenigen Sekunden möglich" },
                )
            }
            Text("Warum möchtest du beenden?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Starlight, modifier = Modifier.padding(top = Spacing.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xs)) {
                sheet.barrier.reasons.forEach { note ->
                    val selected = sheet.note == note
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(Radii.pill)
                            .background(if (selected) FocusPalette.Lavender else Color.White.copy(alpha = 0.08f))
                            .selectable(selected, role = Role.RadioButton) { onSelectNote(note) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) { Text(note.label, style = MaterialTheme.typography.labelMedium, color = if (selected) FocusPalette.AubergineDeep else FocusPalette.Starlight) }
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton("Weiter fokussieren", onKeepGoing, Modifier.fillMaxWidth().testTag("end_keep_going"))
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton(
            if (counted >= 1) "Beenden und Fokuszeit speichern" else "Beenden",
            onConfirm, Modifier.fillMaxWidth().testTag("end_confirm"), enabled = sheet.canConfirm, onDark = true,
        )
        if (counted >= 1) {
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Abbrechen ohne zu speichern", onDiscard, Modifier.fillMaxWidth().testTag("end_discard"), enabled = sheet.canConfirm, onDark = true)
        }
        if (sheet.barrier.reasonRequired) {
            TextButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("end_emergency")) {
                Text("Notfall – sofort beenden", color = FocusPalette.Lavender, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun RecoveryContent(state: FocusUiState, onResume: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.screenHorizontal).testTag("recovery"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Eyebrow(state.modeLabel, color = FocusPalette.Lavender)
            Spacer(Modifier.height(Spacing.xs))
            Text("Fokus-Sitzung fortsetzen?", style = MaterialTheme.typography.headlineMedium, color = FocusPalette.Starlight, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Du hast bereits ${state.frozenElapsedMinutes} von ${state.plannedMinutes} Minuten fokussiert. " +
                    if (state.frozenByRestart) "Der Timer wurde beim Neustart des Geräts angehalten." else "Der Timer wurde angehalten.",
                style = MaterialTheme.typography.bodyMedium, color = FocusPalette.StarlightMuted,
            )
            Spacer(Modifier.height(Spacing.md))
            Column(Modifier.fillMaxWidth().clip(Radii.lg).background(Color.White.copy(alpha = 0.06f)).padding(Spacing.md)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Starlight)
                Row(Modifier.padding(top = Spacing.sm)) {
                    Column(Modifier.weight(1f)) { Eyebrow("Fokussiert", color = FocusPalette.StarlightMuted); Text("${state.frozenElapsedMinutes} Min.", style = MaterialTheme.typography.titleLarge, color = FocusPalette.Starlight) }
                    Column(Modifier.weight(1f)) { Eyebrow("Übrig", color = FocusPalette.StarlightMuted); Text("${(state.plannedMinutes - state.frozenElapsedMinutes).coerceAtLeast(0)} Min.", style = MaterialTheme.typography.titleLarge, color = FocusPalette.Starlight) }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton("Fortsetzen", onResume, Modifier.fillMaxWidth().testTag("recovery_resume"), icon = FocusIcons.Play)
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Fokuszeit speichern", onSave, Modifier.fillMaxWidth().testTag("recovery_save"), enabled = state.frozenElapsedMinutes >= 1, onDark = true)
            TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Verwerfen", color = FocusPalette.StarlightMuted, textAlign = TextAlign.Center)
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Sitzung verwerfen?") },
            text = { Text("Sie wird nicht im Verlauf gespeichert.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Zurück") } },
        )
    }
}

