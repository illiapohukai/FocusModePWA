package io.github.illiapohukai.focusmode.feature.focus

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
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
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicCore
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.CosmicTheme
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.LocalMotion
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.designsystem.theme.TimerType
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import kotlin.math.max

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
        Box(Modifier.fillMaxSize().then(SessionBackground).testTag("focus_screen")) {
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
                containerColor = FocusPalette.AubergineMid,
                contentColor = FocusPalette.Starlight,
            ) { EndSheet(sheet, onSelectNote, onConfirmEnd, onDiscard, onEmergency, onDismissEnd) }
        }
    }
}

/** PWA `.uc-shell`: a 170° graphite-indigo gradient under two purple blooms. Drawn once (static). */
private val SessionBackground = Modifier.drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.linearGradient(
        0f to FocusPalette.SpaceTop, 0.52f to FocusPalette.SpaceMid, 1f to FocusPalette.SpaceBottom,
        start = Offset(w * 0.41f, 0f), end = Offset(w * 0.59f, h),
    )
    val bloom = Brush.radialGradient(listOf(FocusPalette.SpaceBloom, Color.Transparent), center = Offset(w * 0.5f, h * 0.34f), radius = max(w, h) * 0.46f)
    val high = Brush.radialGradient(listOf(FocusPalette.SpaceBloomHigh, Color.Transparent), center = Offset(w * 0.82f, h * 0.08f), radius = max(w, h) * 0.30f)
    onDrawBehind { drawRect(base); drawRect(bloom); drawRect(high) }
}

@Composable
private fun RunningContent(state: FocusUiState, onRequestEnd: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val reduced = LocalMotion.current.reduced
    val coreFraction = if (landscape) 0.62f else 0.6f
    if (landscape) {
        Row(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.md)) {
            Scene(state.timerText, state.timerDescription, state.protectionMode, reduced, coreFraction, landscape = true, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = Spacing.md), verticalArrangement = Arrangement.Center) {
                Header(state.headline, state.modeLabel, state.stepLabel, state.plannedMinutes)
                Spacer(Modifier.height(Spacing.md))
                TaskCard(state.task, state.monitoringLabel, state.stepLabel)
                Spacer(Modifier.height(Spacing.md))
                EndButton(onRequestEnd, Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(start = 18.dp, end = 18.dp, top = Spacing.md, bottom = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(state.headline, state.modeLabel, state.stepLabel, state.plannedMinutes)
            Scene(state.timerText, state.timerDescription, state.protectionMode, reduced, coreFraction, landscape = false, Modifier.weight(1f).fillMaxWidth())
            TaskCard(state.task, state.monitoringLabel, state.stepLabel)
            Spacer(Modifier.height(11.dp))
            EndButton(onRequestEnd, Modifier.widthIn(max = 560.dp).fillMaxWidth())
        }
    }
}

/**
 * The Cosmic Core with the live timer as real text centred on the sphere, and the protection
 * badge under it. The Core draws in its own layers, so a timer change re-records only the text.
 */
@Composable
private fun Scene(
    timerText: String,
    timerDescription: String,
    mode: ProtectionMode,
    reduced: Boolean,
    coreFraction: Float,
    landscape: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight) * coreFraction
        CosmicCore(Modifier.fillMaxSize(), animate = true, reducedMotion = reduced, coreFraction = coreFraction)
        BasicText(
            timerText,
            style = (if (landscape) TimerType.Compact else TimerType.Live).copy(color = FocusPalette.Starlight, textAlign = TextAlign.Center, shadow = TimerShadow),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = TimerType.MinFitSize, maxFontSize = if (landscape) TimerType.Compact.fontSize else TimerType.Live.fontSize),
            modifier = Modifier.width(diameter * 0.86f).testTag("focus_timer").clearAndSetSemantics { contentDescription = timerDescription },
        )
        ProtectionBadge(mode, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs))
    }
}

private val TimerShadow = Shadow(color = Color(0xEB030512), offset = Offset(0f, 4f), blurRadius = 24f)

@Composable
private fun Header(headline: String, modeLabel: String, stepLabel: String?, plannedMinutes: Int) {
    Column(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
        Text("FOKUS-SITZUNG", style = LabelType.Kicker.copy(fontWeight = FontWeight.Black, letterSpacing = 0.17.em), color = Color(0xFFA78BFA))
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            headline, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp), color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs), itemVerticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Icon(FocusIcons.BookOpen, null, tint = FocusPalette.StarlightMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$plannedMinutes Min.", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Starlight.copy(alpha = 0.86f))
            }
            Text(
                modeLabel.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, fontSize = 11.sp),
                color = FocusPalette.ModeChipInk,
                maxLines = 1,
                modifier = Modifier.clip(Radii.pill).background(FocusPalette.ModeChipSurface).border(BorderStroke(1.dp, FocusPalette.ModeChipLine), Radii.pill)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
            stepLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.StarlightMuted) }
        }
    }
}

/** PWA `.uc-protect`: restrained green only when a protected Session is active; neutral otherwise. */
@Composable
private fun ProtectionBadge(mode: ProtectionMode, modifier: Modifier = Modifier) {
    val active = mode == ProtectionMode.PROTECTED
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(Radii.pill)
            .background(if (active) FocusPalette.ProtectSurface else FocusPalette.NeutralBadgeSurface)
            .border(BorderStroke(1.dp, if (active) FocusPalette.ProtectLine else FocusPalette.NeutralBadgeLine), Radii.pill)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("focus_protection_badge"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Box(Modifier.size(9.dp).clip(Radii.pill).background(FocusPalette.ProtectDot))
        } else {
            Icon(FocusIcons.Shield, null, tint = FocusPalette.NeutralBadgeInk, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(mode.label, style = MaterialTheme.typography.titleSmall, color = if (active) FocusPalette.ProtectInk else FocusPalette.NeutralBadgeInk, maxLines = 1)
    }
}

@Composable
private fun TaskCard(task: String, monitoringLabel: String?, stepLabel: String?) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth()
            .shadow(14.dp, Radii.session, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = FocusPalette.PurpleBright.copy(alpha = 0.18f))
            .clip(Radii.session)
            .background(FocusPalette.SpaceMid.copy(alpha = 0.55f))
            .background(Brush.linearGradient(listOf(FocusPalette.TaskCardTop, FocusPalette.TaskCardBottom)))
            .border(BorderStroke(1.dp, FocusPalette.TaskCardLine), Radii.session)
            .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 18.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("focus_task_card"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Target, null, tint = FocusPalette.TaskLabel, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("AUFGABE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.1.em), color = FocusPalette.TaskLabel, modifier = Modifier.weight(1f))
            stepLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FocusPalette.ModeChipInk) }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            task.ifBlank { "Konzentriert weiterarbeiten." },
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        monitoringLabel?.let {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(FocusIcons.Activity, null, tint = FocusPalette.Lavender, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender)
            }
        }
    }
}

/** PWA end action: rose surface and ink with a stop mark. It opens the safe end flow, never ends directly. */
@Composable
private fun EndButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 55.dp)
            .clip(Radii.md)
            .background(Brush.linearGradient(listOf(FocusPalette.EndSurfaceStart, FocusPalette.EndSurfaceEnd)))
            .border(BorderStroke(1.dp, Color(0x29FB7185)), Radii.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg)
            .testTag("focus_end"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FocusIcons.StopFilled, null, tint = FocusPalette.EndInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text("Sitzung beenden", style = MaterialTheme.typography.labelLarge, color = FocusPalette.EndInk)
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

