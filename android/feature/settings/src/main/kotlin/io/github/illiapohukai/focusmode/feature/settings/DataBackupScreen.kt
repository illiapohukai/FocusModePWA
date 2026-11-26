package io.github.illiapohukai.focusmode.feature.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.NavigationRow
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.backup.AndroidBackupExporter
import io.github.illiapohukai.focusmode.core.domain.backup.BackupExportOwner
import io.github.illiapohukai.focusmode.core.domain.backup.BackupScope
import io.github.illiapohukai.focusmode.core.domain.backup.EraseOutcome
import io.github.illiapohukai.focusmode.core.domain.backup.ExportResult
import io.github.illiapohukai.focusmode.core.domain.backup.PersonalDataEraser
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Inline result of the last action (PWA `dataFeedbackBanner`). */
data class DataFeedback(val success: Boolean, val title: String, val body: String)

data class DataBackupUiState(
    val loading: Boolean = true,
    val activeMissions: Int = 0,
    val missions: Int = 0,
    val sessions: Int = 0,
    val lastBackup: String = "Kein Backup",
    val busy: Boolean = false,
    val feedback: DataFeedback? = null,
    /** v21: a Google account is signed in or the data is linked to one. */
    val accountLinked: Boolean = false,
)

/**
 * PWA `app-screen-data` ("Daten & Backup"): export (full or Missions & settings), import, and
 * the deletion of all personal data. The file is written through the Storage Access Framework
 * (the user picks the place; no storage permission). Nothing here talks to the network.
 */
@HiltViewModel
class DataBackupViewModel @Inject constructor(
    missions: MissionRepository,
    history: SessionHistoryRepository,
    preferences: PreferencesRepository,
    private val exporter: BackupExportOwner,
    private val eraser: PersonalDataEraser,
    private val clock: FocusClock,
    sync: io.github.illiapohukai.focusmode.core.domain.sync.SyncRepository? = null,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<DataFeedback?>(null)
    private val linked: kotlinx.coroutines.flow.Flow<Boolean> = sync?.syncState?.map { st ->
        st.phase !in setOf(io.github.illiapohukai.focusmode.core.model.SyncPhase.UNAVAILABLE, io.github.illiapohukai.focusmode.core.model.SyncPhase.SIGNED_OUT)
    } ?: kotlinx.coroutines.flow.flowOf(false)

    val uiState: StateFlow<DataBackupUiState> = combine(
        combine(missions.observeMissions(), history.observeResults(), preferences.preferences, ::Triple), busy, feedback, linked,
    ) { (m, r, p), b, f, isLinked ->
        DataBackupUiState(
            loading = false,
            activeMissions = m.count { it.status == MissionStatus.ACTIVE },
            missions = m.size,
            sessions = r.size,
            lastBackup = p.lastExportAt?.let { LAST.format(ZonedDateTime.ofInstant(it, clock.zone())) } ?: "Kein Backup",
            busy = b,
            feedback = f,
            accountLinked = isLinked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataBackupUiState())

    /** File name offered to the system "save as" dialog: a fixed prefix and today's date only. */
    fun fileName(scope: BackupScope): String = AndroidBackupExporter.fileName(clock.today(), scope)

    fun export(scope: BackupScope, resolver: ContentResolver, target: Uri) {
        if (busy.value) return
        busy.value = true
        feedback.value = null
        viewModelScope.launch {
            feedback.value = when (val r = exporter.export(scope)) {
                is ExportResult.Failed -> {
                    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, target) } }
                    DataFeedback(false, "Export fehlgeschlagen", r.message)
                }
                is ExportResult.Ready -> {
                    val written = withContext(Dispatchers.IO) {
                        runCatching { resolver.openOutputStream(target, "wt")?.use { it.write(r.file.json.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
                    }
                    if (written) {
                        exporter.markWritten()
                        val scopeNote = if (r.file.scope == BackupScope.MISSIONS) "ohne Sitzungsverlauf" else "${r.file.sessionCount} Sitzungen"
                        DataFeedback(true, "Lokales Backup gespeichert", "${r.file.fileName} · ${r.file.missionCount} Missionen, $scopeNote. Mit Prüfsumme (SHA-256) gegen Beschädigung.")
                    } else {
                        withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, target) } }
                        DataFeedback(false, "Export fehlgeschlagen", "Die Datei konnte nicht gespeichert werden. Deine Daten wurden nicht verändert.")
                    }
                }
            }
            busy.value = false
        }
    }

    fun eraseAll() {
        if (busy.value) return
        busy.value = true
        feedback.value = null
        viewModelScope.launch {
            feedback.value = runCatching { eraser.eraseAll() }.fold(
                onSuccess = {
                    if (it == EraseOutcome.COMPLETE) DataFeedback(true, "Daten gelöscht", "Alle persönlichen und nutzergenerierten Daten wurden von diesem Gerät entfernt.")
                    else DataFeedback(false, "Nicht vollständig gelöscht", "Einige Einträge sind noch vorhanden. Bitte versuche es erneut.")
                },
                onFailure = { DataFeedback(false, "Nicht vollständig gelöscht", "Das Löschen wurde unterbrochen. Bitte versuche es erneut; bereits Gelöschtes bleibt gelöscht.") },
            )
            busy.value = false
        }
    }

    private companion object {
        val LAST: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMAN)
    }
}

@Composable
fun DataBackupRoute(onBack: () -> Unit, onImport: () -> Unit, viewModel: DataBackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    // The chosen scope survives the system dialog and process recreation.
    var pendingScope by rememberSaveable { mutableStateOf<String?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val scope = pendingScope?.let(BackupScope::valueOf)
        pendingScope = null
        if (uri != null && scope != null) viewModel.export(scope, resolver, uri)
    }
    DataBackupScreen(
        state, onBack, onImport,
        onExport = { scope -> pendingScope = scope.name; create.launch(viewModel.fileName(scope)) },
        onErase = viewModel::eraseAll,
    )
}

@Composable
fun DataBackupScreen(state: DataBackupUiState, onBack: () -> Unit, onImport: () -> Unit, onExport: (BackupScope) -> Unit, onErase: () -> Unit) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding().testTag("data_backup"),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { FocusTopBar(onBack, title = "Daten & Backup") }
            item {
                FocusCard(Modifier.fillMaxWidth().testTag("data_summary"), contentPadding = PaddingValues(Spacing.md)) {
                    Text("Übersicht deiner lokal gespeicherten Fokus-Daten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    Spacer(Modifier.height(Spacing.xs))
                    SummaryLine("Aktive Missionen", "${state.activeMissions} / ${Mission.MAX_ACTIVE} (${state.missions})")
                    SummaryLine("Gespeicherte Sitzungen", "${state.sessions}")
                    SummaryLine("Letztes Backup", state.lastBackup)
                }
            }
            state.feedback?.let { f ->
                item {
                    FocusCard(
                        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("data_feedback"),
                        color = if (f.success) FocusPalette.GreenTint else FocusPalette.DangerTint, contentPadding = PaddingValues(Spacing.md),
                    ) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall, color = if (f.success) FocusPalette.GreenInk else FocusPalette.Danger)
                        Text(f.body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
                    }
                }
            }
            item {
                Text(
                    "Backups enthalten deine Missionen, Aufgaben und Notizen als lesbaren Text. Teile Backup-Dateien nur mit Personen, denen du vertraust. " +
                        "Ein Backup ist ein lokaler Datenexport – kein Nachweis für Fortschritt oder Serien. Nicht enthalten: eine laufende Sitzung, App-Auswahl, Geplanter Fokus und Erinnerungen.",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                )
            }
            item { SectionHeader("Aktionen") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                    NavigationRow("Daten exportieren", { if (!state.busy) onExport(BackupScope.FULL) }, Modifier.testTag("data_export_full"), subtitle = "Vollständiges JSON-Backup speichern", icon = FocusIcons.Download)
                    NavigationRow("Nur Missionen & Einstellungen", { if (!state.busy) onExport(BackupScope.MISSIONS) }, Modifier.testTag("data_export_missions"), subtitle = "Backup ohne Sitzungsverlauf", icon = FocusIcons.Download)
                    NavigationRow("Daten importieren", onImport, Modifier.testTag("data_import_open"), subtitle = "Aus einer Backup-Datei der Web-App oder von Android", icon = FocusIcons.Upload)
                }
            }
            item { SectionHeader("Löschen") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                    NavigationRow(
                        "Alle persönlichen und nutzergenerierten Daten löschen", { if (!state.busy) confirmErase = true }, Modifier.testTag("data_erase"),
                        subtitle = "Missionen, Verlauf, Erinnerungen, App-Auswahl und Einstellungen", icon = FocusIcons.Trash, iconTint = FocusPalette.Danger,
                    )
                }
            }
            if (state.busy) item { Text("Wird ausgeführt …", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Alle persönlichen und nutzergenerierten Daten löschen?", modifier = Modifier.semantics { heading() }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "Missionen mit Schritten, Entwürfe und Fotos, Sitzungsverlauf, Serie und Aktivität, Erinnerungen, Geplanter Fokus, App-Auswahl, " +
                            "Ablenkungsdaten und deine Einstellungen werden von diesem Gerät entfernt. Eine laufende Sitzung endet ohne Speichern.",
                    )
                    Text("Das kann nicht rückgängig gemacht werden. Exportiere vorher ein Backup, wenn du die Daten behalten möchtest.")
                    if (state.accountLinked) {
                        Text(
                            "Dein Konto: Du wirst auf diesem Gerät abgemeldet. Die Daten in deinem Konto bleiben erhalten; löschen kannst du sie unter „Konto & Synchronisierung“ → „Konto löschen“.",
                            modifier = Modifier.testTag("data_erase_account_note"),
                        )
                    }
                    Text("Systemberechtigungen wie Benachrichtigungen oder Nutzungszugriff änderst du in den Android-Einstellungen.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onErase() }, Modifier.testTag("data_erase_confirm")) { Text("Endgültig löschen", color = FocusPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
    }
}
