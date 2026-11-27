package app.focusmodeplus.feature.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusmodeplus.core.designsystem.component.ChoiceChips
import app.focusmodeplus.core.designsystem.component.ChoiceOption
import app.focusmodeplus.core.designsystem.component.EmptyState
import app.focusmodeplus.core.designsystem.component.FocusCard
import app.focusmodeplus.core.designsystem.component.FocusTopBar
import app.focusmodeplus.core.designsystem.component.PillTone
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.SectionHeader
import app.focusmodeplus.core.designsystem.component.StatusPill
import app.focusmodeplus.core.designsystem.component.TextAction
import app.focusmodeplus.core.designsystem.component.ToggleRow
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.Schedule
import java.time.DayOfWeek
import java.time.LocalTime

data class SchedulesActions(val onBack: () -> Unit, val onEdit: (String?) -> Unit, val onPermissions: () -> Unit)

@Composable
fun SchedulesRoute(actions: SchedulesActions, viewModel: SchedulesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("schedules"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { FocusTopBar(actions.onBack, title = "Geplanter Fokus") }
            item {
                Text(
                    "Focus Mode+ erinnert dich vor deinem Zeitfenster und bereitet die Sitzung vor. Der Fokus startet erst, wenn du ihn selbst startest.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft,
                )
            }
            item {
                FocusCard(Modifier.fillMaxWidth(), color = if (state.deliverable) FocusPalette.Surface else FocusPalette.DangerTint, contentPadding = PaddingValues(Spacing.md)) {
                    Text(state.delivery, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
                    if (!state.deliverable) TextAction("Berechtigungen öffnen", actions.onPermissions)
                }
            }
            if (!state.loading && state.rows.isEmpty()) {
                item { EmptyState(FocusIcons.Calendar, "Noch kein geplanter Fokus", "Lege feste Fokuszeiten an, z. B. Mo–Fr 16:00–16:45.") }
            }
            items(state.rows, key = { it.schedule.id.value }) { row ->
                FocusCard(Modifier.fillMaxWidth(), onClick = { actions.onEdit(row.schedule.id.value) }, onClickLabel = "Bearbeiten", contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.schedule.label, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                            Text("${row.days} · ${row.window}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                        }
                        StatusPill(row.schedule.protectionMode.label, tone = if (row.schedule.protectionMode == ProtectionMode.PROTECTED) PillTone.ACCENT else PillTone.NEUTRAL)
                    }
                    ToggleRow("Aktiv", row.schedule.enabled, { viewModel.setEnabled(row.schedule, it) },
                        subtitle = if (row.schedule.enabled) row.next?.let { "Nächste Erinnerung: $it" } ?: "Kein Termin in den nächsten Tagen" else "Pausiert")
                    Row {
                        TextAction(if (row.schedule.skipDate != null) "Überspringen aufheben" else "Nächsten Termin überspringen", { viewModel.toggleSkipNext(row.schedule) })
                        TextAction("Löschen", { deleting = row.schedule.id.value }, color = FocusPalette.Danger)
                    }
                }
            }
            item { PrimaryButton("Neuer geplanter Fokus", { actions.onEdit(null) }, Modifier.fillMaxWidth().testTag("schedule_add"), icon = FocusIcons.Plus) }
        }
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Geplanten Fokus löschen?") },
            confirmButton = { TextButton(onClick = { state.rows.firstOrNull { it.schedule.id.value == id }?.let { viewModel.delete(it.schedule) }; deleting = null }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorRoute(onDone: () -> Unit, viewModel: ScheduleEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel) { viewModel.saved.collect { onDone() } }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).testTag("schedule_editor")) {
            FocusTopBar(onDone, title = if (state.id == null) "Neuer geplanter Fokus" else "Geplanten Fokus bearbeiten")
            OutlinedTextField(
                state.label, { v -> viewModel.update { it.copy(label = v.take(60)) } }, Modifier.fillMaxWidth().testTag("schedule_label"),
                label = { Text("Name") }, placeholder = { Text("z. B. Lineare Funktionen") }, singleLine = true, shape = Radii.md,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = FocusPalette.Surface, unfocusedContainerColor = FocusPalette.Surface, focusedBorderColor = FocusPalette.Purple),
            )
            SectionHeader("Wochentage", Modifier.padding(top = Spacing.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    val on = day in state.weekdays
                    Box(
                        Modifier.size(48.dp).clip(Radii.pill).background(if (on) FocusPalette.Purple else FocusPalette.SurfaceMuted)
                            .toggleable(on, role = Role.Checkbox) { checked -> viewModel.update { s -> s.copy(weekdays = if (checked) s.weekdays + day else s.weekdays - day) } }
                            .semantics { contentDescription = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.GERMAN) },
                        contentAlignment = Alignment.Center,
                    ) { Text(DAY_SHORT.getValue(day), style = MaterialTheme.typography.labelLarge, color = if (on) FocusPalette.Surface else FocusPalette.Graphite) }
                }
            }
            SectionHeader("Zeitfenster", Modifier.padding(top = Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TimeField("Start", state.start, Modifier.weight(1f)) { picking = "start" }
                TimeField("Ende", state.end, Modifier.weight(1f)) { picking = "end" }
            }
            SectionHeader("Erinnerung vorher", Modifier.padding(top = Spacing.md))
            ChoiceChips(Schedule.LEAD_TIME_OPTIONS.map { ChoiceOption(it, if (it == 0) "Zu Beginn" else "$it Min.") }, state.leadTime, { v -> viewModel.update { it.copy(leadTime = v) } }, Modifier.fillMaxWidth())
            SectionHeader("Fokus-Schutz", Modifier.padding(top = Spacing.md))
            ChoiceChips(listOf(ChoiceOption(ProtectionMode.STANDARD, "Standard"), ChoiceOption(ProtectionMode.PROTECTED, "Geschützt")), state.mode, { v -> viewModel.update { it.copy(mode = v) } }, Modifier.fillMaxWidth())
            ToggleRow("Ausgewählte Apps verwenden", state.useSelectedApps, { v -> viewModel.update { it.copy(useSelectedApps = v) } },
                subtitle = "${state.selectedAppCount} Apps · bleiben auf diesem Gerät")
            ToggleRow("Aktiv", state.enabled, { v -> viewModel.update { it.copy(enabled = v) } })
            Text("Strikte Sperre auf diesem Gerät nicht verfügbar. Geplanter Fokus startet nie automatisch.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Danger, modifier = Modifier.padding(top = Spacing.sm)) }
            PrimaryButton("Speichern", viewModel::save, Modifier.fillMaxWidth().padding(vertical = Spacing.lg).testTag("schedule_save"))
        }
    }
    picking?.let { which ->
        val initial = if (which == "start") state.start else state.end
        val pickerState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (which == "start") "Start" else "Ende") },
            text = { TimePicker(pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(pickerState.hour, pickerState.minute)
                    viewModel.update { if (which == "start") it.copy(start = t) else it.copy(end = t) }
                    picking = null
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun TimeField(label: String, time: LocalTime, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.heightIn(min = 64.dp), onClick = onClick, onClickLabel = "$label ändern", contentPadding = PaddingValues(Spacing.md)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
        Text(TIME.format(time), style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
    }
}
