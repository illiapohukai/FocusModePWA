package io.github.illiapohukai.focusmode.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceChips
import io.github.illiapohukai.focusmode.core.designsystem.component.ChoiceOption
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.NavigationRow
import io.github.illiapohukai.focusmode.core.designsystem.component.PillTone
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.component.StatusPill
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.component.ToggleRow
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.designsystem.util.notificationRationale
import io.github.illiapohukai.focusmode.core.designsystem.util.rememberNotificationPermissionRequest
import io.github.illiapohukai.focusmode.core.domain.protection.PermissionRow
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.PermissionKind
import io.github.illiapohukai.focusmode.core.model.ProtectionCapability
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.SettingsTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsActions(
    val onPermissions: () -> Unit,
    val onAppSelection: () -> Unit,
    val onSchedules: () -> Unit,
    val onImport: () -> Unit,
    val appVersion: String,
    val debugBuild: Boolean,
)

@Composable
fun SettingsRoute(actions: SettingsActions, contentPadding: PaddingValues, snackbar: SnackbarHostState, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) { viewModel.refresh(context.notificationRationale()); onPauseOrDispose { } }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val prefs = state?.preferences
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("settings"),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { Text("Einstellungen", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() }) }
            if (state == null || prefs == null) return@LazyColumn
            val s = state!!
            item { SectionHeader("Fokus-Schutz") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    Text("Standard für neue Sitzungen", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                    Spacer(Modifier.height(Spacing.xxs))
                    ChoiceChips(listOf(ChoiceOption(ProtectionMode.STANDARD, "Standard"), ChoiceOption(ProtectionMode.PROTECTED, "Geschützt")), prefs.defaultProtectionMode, viewModel::setDefaultMode, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.xs))
                    NavigationRow("Ablenkende Apps", actions.onAppSelection, subtitle = s.overview.summaryFor(ProtectionMode.PROTECTED, s.selectedApps.size).substringAfter(" · ", ""), icon = FocusIcons.Apps)
                    val usageGranted = s.snapshot.usageAccess == ProtectionPermissionState.GRANTED
                    ToggleRow(
                        "Ablenkungen überwachen", prefs.usageMonitoringEnabled && usageGranted,
                        { on -> if (on && !usageGranted) actions.onPermissions() else viewModel.setUsageMonitoring(on) },
                        subtitle = if (usageGranted) "Nur in geschützten Sitzungen. Apps werden nicht blockiert." else "Nutzungszugriff erforderlich",
                        enabled = s.snapshot.usageAccess != ProtectionPermissionState.UNSUPPORTED,
                    )
                    val dndGranted = s.snapshot.doNotDisturb == ProtectionPermissionState.GRANTED
                    ToggleRow(
                        "Nicht stören während des Fokus", prefs.doNotDisturbDuringFocus && dndGranted,
                        { on -> if (on && !dndGranted) actions.onPermissions() else viewModel.setDnd(on) },
                        subtitle = if (dndGranted) "Wird danach wiederhergestellt. Deine Ausnahmen bleiben." else "Systemzugriff erforderlich",
                        enabled = s.snapshot.doNotDisturb != ProtectionPermissionState.UNSUPPORTED,
                    )
                    Text("Strikte Sperre auf diesem Gerät nicht verfügbar · Verwaltetes Gerät erforderlich.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            }
            item { NavigationRow("Geplanter Fokus", actions.onSchedules, subtitle = "Feste Fokuszeiten mit Erinnerung", icon = FocusIcons.Calendar) }
            item { NavigationRow("Berechtigungen", actions.onPermissions, subtitle = "Was Focus Mode+ nutzt – und was nicht", icon = FocusIcons.Lock) }
            item { SectionHeader("Benachrichtigungen") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    ToggleRow("Erinnerungen und Hinweise", prefs.notificationsEnabled, viewModel::setNotifications, subtitle = "Die laufende Sitzung bleibt immer sichtbar.")
                    ToggleRow("Missions-Erinnerungen", prefs.missionRemindersEnabled, viewModel::setMissionReminders, subtitle = "Vorbereitungsstart und Deadline in 2 Tagen")
                    ToggleRow("Tagesplanung", prefs.dailyPlanningEnabled, { viewModel.setDailyPlanning(it, prefs.dailyPlanningTime) }, subtitle = "Täglich um ${"%02d:%02d".format(prefs.dailyPlanningTime.hour, prefs.dailyPlanningTime.minute)}")
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Inhalt auf dem Sperrbildschirm", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                    Spacer(Modifier.height(Spacing.xxs))
                    ChoiceChips(
                        listOf(ChoiceOption(NotificationDetailLevel.FULL_TASK, "Aufgabe"), ChoiceOption(NotificationDetailLevel.GENERIC, "Nur Status"), ChoiceOption(NotificationDetailLevel.HIDDEN, "Ausblenden")),
                        prefs.notificationDetail, viewModel::setDetail, Modifier.fillMaxWidth().testTag("notification_detail"),
                    )
                    Text(
                        when (prefs.notificationDetail) {
                            NotificationDetailLevel.FULL_TASK -> "Die Aufgabe erscheint nur, wenn dein Sperrbildschirm vertrauliche Inhalte zeigen darf."
                            NotificationDetailLevel.GENERIC -> "Benachrichtigungen zeigen nie deine Aufgabe."
                            NotificationDetailLevel.HIDDEN -> "Auf dem Sperrbildschirm erscheint nichts."
                        },
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                    )
                }
            }
            item { SectionHeader("Bewegung") }
            item {
                ChoiceChips(listOf(ChoiceOption(MotionPreference.SYSTEM, "Wie System"), ChoiceOption(MotionPreference.REDUCED, "Reduziert")), prefs.motion, viewModel::setMotion, Modifier.fillMaxWidth())
            }
            item { SectionHeader("Daten & Datenschutz") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    Text(
                        "Missionen, Verlauf, App-Auswahl und Einstellungen bleiben auf diesem Gerät. Keine Analyse, kein Tracking, keine Werbung. " +
                            "AI-Planung läuft nur über den bestehenden Focus-Mode+-Dienst; dein Ziel wird dafür gesendet, deine Apps und Nutzungsdaten nie.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite,
                    )
                    TextAction("Ablenkungsdaten löschen", viewModel::deleteDistractionData)
                    if (prefs.featureFlags.pwaImportEnabled) NavigationRow("Web-Backup übernehmen", actions.onImport, subtitle = "Focus-Mode+-Backup (JSON) prüfen und importieren", icon = FocusIcons.Download)
                    if (actions.debugBuild) ToggleRow("Backup-Import (Entwicklung)", prefs.featureFlags.pwaImportEnabled, viewModel::setPwaImport)
                }
            }
            item { Text("Focus Mode+ ${actions.appVersion}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.sm)) }
        }
    }
}

@Composable
fun PermissionCenterRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) { viewModel.refresh(context.notificationRationale()); onPauseOrDispose { } }
    val request = rememberNotificationPermissionRequest { _, rationale -> viewModel.onNotificationPermissionResult(rationale) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("permission_center"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { FocusTopBar(onBack, title = "Berechtigungen") }
            item {
                Text(
                    "Alles ist optional. Quick Focus funktioniert ohne jede Berechtigung. Focus Mode+ fragt nur, wenn du eine Funktion nutzen möchtest.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft,
                )
            }
            val rows = state?.overview?.permissionRows.orEmpty()
            items(rows, key = { it.kind.name }) { row ->
                PermissionCard(row, onAction = {
                    if (row.requestInApp) request() else row.settingsTarget?.let(viewModel::openSettings)
                })
            }
        }
    }
}

@Composable
private fun PermissionCard(row: PermissionRow, onAction: () -> Unit) {
    val tone = when (row.state) {
        ProtectionPermissionState.GRANTED, ProtectionPermissionState.NOT_REQUIRED -> PillTone.READY
        ProtectionPermissionState.PERMANENTLY_DENIED, ProtectionPermissionState.DENIED -> PillTone.WARNING
        else -> PillTone.NEUTRAL
    }
    FocusCard(Modifier.fillMaxWidth().testTag("permission_${row.kind.name.lowercase()}"), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
            StatusPill(row.stateLabel, tone = tone)
        }
        Spacer(Modifier.height(Spacing.xs))
        Labeled("Wofür", row.purpose)
        Labeled("Welche Daten", row.dataAccessed)
        Labeled("Verlässt das Gerät", row.leavesDevice)
        Labeled("Grenzen", row.limitation)
        if (row.actionLabel != null && row.kind != PermissionKind.ACCESSIBILITY) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextAction(row.actionLabel!!, onAction) }
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column(Modifier.padding(vertical = 3.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
        Text(text, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
    }
}

@Composable
fun AppSelectionRoute(onBack: () -> Unit, viewModel: AppSelectionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("app_selection"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            item { FocusTopBar(onBack, title = "Ablenkende Apps") }
            item {
                Text(
                    "Wähle Apps, bei denen dich Focus Mode+ in einer geschützten Sitzung an deinen Fokus erinnern soll. Apps werden nicht blockiert. Deine Auswahl bleibt auf diesem Gerät.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft,
                )
            }
            item {
                OutlinedTextField(
                    state.query, viewModel::setQuery, Modifier.fillMaxWidth().testTag("app_search"), placeholder = { Text("App suchen") }, singleLine = true, shape = Radii.md,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = FocusPalette.Surface, unfocusedContainerColor = FocusPalette.Surface, focusedBorderColor = FocusPalette.Purple),
                )
            }
            item { SectionHeader("${state.selectedCount} ausgewählt") }
            if (state.unavailable.isNotEmpty()) {
                items(state.unavailable, key = { "u_" + it.packageName }) { u ->
                    FocusCard(Modifier.fillMaxWidth(), color = FocusPalette.SurfaceMuted, contentPadding = PaddingValues(Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.label, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                                Text(u.reason, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                            }
                            TextAction("Entfernen", { viewModel.remove(u.packageName) })
                        }
                    }
                }
            }
            if (!state.loading && state.apps.isEmpty()) item { EmptyState(FocusIcons.Apps, "Keine Apps gefunden", "Es wurden keine startbaren Apps gefunden.") }
            items(state.apps, key = { it.packageName }) { row -> AppRowItem(row) { viewModel.toggle(row) } }
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, onToggle: () -> Unit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { 36.dp.roundToPx() }
    // Icons are loaded lazily per visible row at small size and never stored.
    val icon by produceState<ImageBitmap?>(null, row.packageName) {
        value = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(row.packageName).toBitmap(px, px).asImageBitmap() } catch (_: PackageManager.NameNotFoundException) { null }
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radii.md).background(FocusPalette.Surface)
            .toggleable(row.selected, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon?.let { Image(it, contentDescription = null) } }
        Spacer(Modifier.width(Spacing.sm))
        Text(row.label, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f))
        Checkbox(checked = row.selected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = FocusPalette.Purple))
    }
}

@Composable
fun DataImportRoute(onBack: () -> Unit, viewModel: DataImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.onFilePicked(resolver, uri) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("data_import"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { FocusTopBar(onBack, title = "Web-Backup übernehmen") }
            item {
                Text(
                    "Wähle eine Focus-Mode+-Backupdatei (.json) aus der Web-App. Du siehst zuerst eine Vorschau. Bestehende Einträge werden nie überschrieben. " +
                        "Übernommene Serien gelten nicht als verifiziert.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft,
                )
            }
            when (val s = state) {
                ImportUiState.Idle, is ImportUiState.Rejected, is ImportUiState.Failed -> {
                    if (s is ImportUiState.Rejected) item { Text(s.message, color = FocusPalette.Danger, style = MaterialTheme.typography.bodyMedium) }
                    if (s is ImportUiState.Failed) item { Text(s.message, color = FocusPalette.Danger, style = MaterialTheme.typography.bodyMedium) }
                    item { PrimaryButton("Datei auswählen", { picker.launch(arrayOf("application/json", "text/plain")) }, Modifier.fillMaxWidth(), icon = FocusIcons.Download) }
                }
                ImportUiState.Reading, ImportUiState.Importing -> item { Text("Wird geprüft …", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft) }
                is ImportUiState.Preview -> {
                    val p = s.preview
                    item {
                        FocusCard(Modifier.fillMaxWidth()) {
                            Text("Vorschau", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                            Text("${p.newMissions} neue Missionen (${p.plan.report.stepsAccepted} Schritte), ${p.duplicateMissions} bereits vorhanden", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
                            Text("${p.newSessions} neue Sitzungen, ${p.duplicateSessions} bereits vorhanden", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
                            if (p.missionsArchivedByLimit > 0) Text("${p.missionsArchivedByLimit} aktive Missionen werden archiviert (höchstens zwei aktiv).", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                        }
                    }
                    if (p.plan.report.notImported.isNotEmpty()) item {
                        FocusCard(Modifier.fillMaxWidth(), color = FocusPalette.SurfaceMuted) {
                            Text("Wird nicht übernommen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                            p.plan.report.notImported.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft) }
                            p.plan.report.skipped.forEach { Text("• Übersprungen: $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft) }
                        }
                    }
                    item { PrimaryButton("Importieren", viewModel::confirm, Modifier.fillMaxWidth().testTag("import_confirm")) }
                    item { SecondaryButton("Abbrechen", viewModel::reset, Modifier.fillMaxWidth()) }
                }
                is ImportUiState.Done -> item {
                    FocusCard(Modifier.fillMaxWidth()) {
                        Text("Import abgeschlossen", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                        Text("${s.result.missionsImported} Missionen und ${s.result.sessionsImported} Sitzungen übernommen.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
                        TextAction("Fertig", onBack)
                    }
                }
            }
        }
    }
}
