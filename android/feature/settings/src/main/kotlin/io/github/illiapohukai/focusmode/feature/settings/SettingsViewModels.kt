package io.github.illiapohukai.focusmode.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.domain.backup.BackupImportGateway
import io.github.illiapohukai.focusmode.core.domain.backup.ImportApplyResult
import io.github.illiapohukai.focusmode.core.domain.backup.ImportLimits
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPreview
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPreviewResult
import io.github.illiapohukai.focusmode.core.domain.platform.InstalledAppCatalog
import io.github.illiapohukai.focusmode.core.domain.platform.SystemSettingsNavigator
import io.github.illiapohukai.focusmode.core.domain.protection.AppSelectionReconciler
import io.github.illiapohukai.focusmode.core.domain.repository.DistractionLog
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SelectedAppRepository
import io.github.illiapohukai.focusmode.core.model.AppAvailability
import io.github.illiapohukai.focusmode.core.model.InstalledApp
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SettingsTarget
import io.github.illiapohukai.focusmode.core.protection.ProtectionCapabilityOwner
import io.github.illiapohukai.focusmode.core.protection.ProtectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import javax.inject.Inject

/** v21: what the Settings account section shows (never tokens or ids). */
data class AccountSummary(
    val account: io.github.illiapohukai.focusmode.core.model.AccountState = io.github.illiapohukai.focusmode.core.model.AccountState.FirebaseUnconfigured,
    val sync: io.github.illiapohukai.focusmode.core.model.SyncState = io.github.illiapohukai.focusmode.core.model.SyncState(phase = io.github.illiapohukai.focusmode.core.model.SyncPhase.UNAVAILABLE),
)

/** Local summary for the Settings hero (PWA "Dein Fokus-System"); counts only, nothing leaves the device. */
data class SystemSummary(val activeMissions: Int = 0, val maxActiveMissions: Int = io.github.illiapohukai.focusmode.core.model.Mission.MAX_ACTIVE, val savedSessions: Int = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val protection: ProtectionCapabilityOwner,
    private val navigator: SystemSettingsNavigator,
    private val distractions: DistractionLog,
    missions: io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository,
    history: io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository,
    accounts: io.github.illiapohukai.focusmode.core.domain.account.AccountRepository,
    sync: io.github.illiapohukai.focusmode.core.domain.sync.SyncRepository,
) : ViewModel() {
    val state: StateFlow<ProtectionState?> = protection.state
    val account: StateFlow<AccountSummary> = combine(accounts.accountState, sync.syncState, ::AccountSummary)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountSummary(accounts.accountState.value, sync.syncState.value))
    val summary: StateFlow<SystemSummary> = kotlinx.coroutines.flow.combine(missions.observeMissions(), history.observeResults()) { m, r ->
        SystemSummary(activeMissions = m.count { it.status == io.github.illiapohukai.focusmode.core.model.MissionStatus.ACTIVE }, savedSessions = r.size)
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), SystemSummary())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages

    fun refresh(rationale: Boolean?) = protection.refresh(rationale)
    fun openSettings(target: SettingsTarget) { navigator.open(target) }
    fun onNotificationPermissionResult(rationale: Boolean) = viewModelScope.launch {
        preferences.incrementNotificationPermissionRequests(); protection.refresh(rationale)
    }

    fun setDefaultMode(mode: ProtectionMode) = viewModelScope.launch { preferences.setDefaultProtectionMode(mode) }
    fun setUsageMonitoring(enabled: Boolean) = viewModelScope.launch { preferences.setUsageMonitoringEnabled(enabled) }
    fun setDnd(enabled: Boolean) = viewModelScope.launch { preferences.setDoNotDisturbDuringFocus(enabled) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    fun setDetail(level: NotificationDetailLevel) = viewModelScope.launch { preferences.setNotificationDetail(level) }
    fun setMissionReminders(enabled: Boolean) = viewModelScope.launch { preferences.setMissionRemindersEnabled(enabled) }
    fun setDailyPlanning(enabled: Boolean, time: LocalTime) = viewModelScope.launch { preferences.setDailyPlanning(enabled, time) }
    fun setMotion(motion: MotionPreference) = viewModelScope.launch { preferences.setMotion(motion) }
    fun setPwaImport(enabled: Boolean) = viewModelScope.launch { preferences.setPwaImportEnabled(enabled) }
    fun deleteDistractionData() = viewModelScope.launch {
        val n = distractions.purgeAll()
        _messages.emit(if (n == 0) "Keine Ablenkungsdaten gespeichert." else "Ablenkungsdaten gelöscht.")
    }
}

data class AppRow(val packageName: String, val label: String, val selected: Boolean)
data class UnavailableRow(val packageName: String, val label: String, val reason: String)

data class AppSelectionUiState(
    val loading: Boolean = true,
    val query: String = "",
    val apps: List<AppRow> = emptyList(),
    val unavailable: List<UnavailableRow> = emptyList(),
    val selectedCount: Int = 0,
)

/**
 * App selection. The launchable-app list exists only in memory while this screen is open; only
 * the selected package names (with a label snapshot) are stored, locally.
 */
@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    private val catalog: InstalledAppCatalog,
    private val selected: SelectedAppRepository,
) : ViewModel() {
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { installed.value = catalog.launchableApps() }
    }

    val uiState: StateFlow<AppSelectionUiState> = combine(installed, selected.observeSelected(), query) { apps, sel, q ->
        val selectedSet = sel.map { it.packageName }.toSet()
        val resolved = AppSelectionReconciler.reconcile(sel, catalog::resolve)
        AppSelectionUiState(
            loading = apps == null,
            query = q,
            apps = apps.orEmpty().filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
                .map { AppRow(it.packageName, it.label, it.packageName in selectedSet) },
            unavailable = resolved.filter { it.availability != AppAvailability.INSTALLED }.map {
                UnavailableRow(it.selection.packageName, it.displayLabel, if (it.availability == AppAvailability.DISABLED) "Deaktiviert" else "Nicht mehr installiert")
            },
            selectedCount = selectedSet.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSelectionUiState())

    fun setQuery(q: String) { query.value = q.take(40) }
    fun toggle(row: AppRow) = viewModelScope.launch { selected.setSelected(row.packageName, row.label, !row.selected) }
    fun remove(packageName: String) = viewModelScope.launch { selected.setSelected(packageName, "", false) }
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Reading : ImportUiState
    data class Preview(val preview: ImportPreview) : ImportUiState
    data class Rejected(val message: String) : ImportUiState
    data object Importing : ImportUiState
    data class Done(val result: ImportApplyResult) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

@HiltViewModel
class DataImportViewModel @Inject constructor(private val gateway: BackupImportGateway) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    /** Reads at most the import size limit through the Storage Access Framework. */
    fun onFilePicked(resolver: ContentResolver, uri: Uri) {
        _state.value = ImportUiState.Reading
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { readCapped(resolver, uri, ImportLimits().maxBytes) }
            _state.value = when {
                bytes == null -> ImportUiState.Rejected("Die Datei konnte nicht gelesen werden oder ist größer als 5 MB.")
                else -> when (val r = gateway.preview(bytes)) {
                    is ImportPreviewResult.Ready -> ImportUiState.Preview(r.preview)
                    is ImportPreviewResult.Rejected -> ImportUiState.Rejected(r.message)
                }
            }
        }
    }

    fun confirm() {
        val preview = (_state.value as? ImportUiState.Preview)?.preview ?: return
        _state.value = ImportUiState.Importing
        viewModelScope.launch {
            _state.value = try {
                ImportUiState.Done(gateway.apply(preview.plan))
            } catch (e: Exception) {
                ImportUiState.Failed("Der Import wurde vollständig zurückgerollt. Es wurden keine Daten geändert.")
            }
        }
    }

    fun reset() { _state.update { ImportUiState.Idle } }

    private fun readCapped(resolver: ContentResolver, uri: Uri, max: Int): ByteArray? = try {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    } catch (_: Exception) { null }
}
