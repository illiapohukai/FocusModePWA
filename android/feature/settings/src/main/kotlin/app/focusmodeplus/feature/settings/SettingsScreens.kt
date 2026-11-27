package app.focusmodeplus.feature.settings

import app.focusmodeplus.core.designsystem.component.ScreenHeader
import app.focusmodeplus.core.designsystem.component.AubergineSurface
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusmodeplus.core.designsystem.component.AppIcon
import app.focusmodeplus.core.designsystem.component.AppIconStack
import app.focusmodeplus.core.designsystem.component.ChoiceChips
import app.focusmodeplus.core.designsystem.component.ChoiceOption
import app.focusmodeplus.core.designsystem.component.EmptyState
import app.focusmodeplus.core.designsystem.component.FocusCard
import app.focusmodeplus.core.designsystem.component.FocusTopBar
import app.focusmodeplus.core.designsystem.component.IconTile
import app.focusmodeplus.core.designsystem.component.NavigationRow
import app.focusmodeplus.core.designsystem.component.PillTone
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.SecondaryButton
import app.focusmodeplus.core.designsystem.component.SectionHeader
import app.focusmodeplus.core.designsystem.component.StatusPill
import app.focusmodeplus.core.designsystem.component.TextAction
import app.focusmodeplus.core.designsystem.component.ToggleRow
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.LabelType
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.designsystem.util.notificationRationale
import app.focusmodeplus.core.designsystem.util.rememberNotificationPermissionRequest
import app.focusmodeplus.core.domain.protection.PermissionRow
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.PermissionKind
import app.focusmodeplus.core.model.ProtectionCapability
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ProtectionPermissionState
import app.focusmodeplus.core.model.SettingsTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SettingsActions(
    val onPermissions: () -> Unit,
    val onAppSelection: () -> Unit,
    val onSchedules: () -> Unit,
    val onImport: () -> Unit,
    val appVersion: String,
    val debugBuild: Boolean,
    /** PWA "Daten & Backup": export, import and full deletion. */
    val onDataBackup: () -> Unit = {},
    /** v21: "Konto & Synchronisierung". */
    val onAccount: () -> Unit = {},
)

@Composable
fun SettingsRoute(actions: SettingsActions, contentPadding: PaddingValues, snackbar: SnackbarHostState, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
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
            item {
                ScreenHeader("Konfiguration & Profil", "Einstellungen", Modifier.padding(bottom = Spacing.xs)) {
                    Box(
                        Modifier.size(48.dp).shadow(4.dp, Radii.pill, ambientColor = FocusPalette.Shadow.copy(alpha = 0.07f), spotColor = FocusPalette.Shadow.copy(alpha = 0.08f))
                            .clip(Radii.pill).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.pill).clearAndSetSemantics { },
                        contentAlignment = Alignment.Center,
                    ) { Text("FM+", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black), color = FocusPalette.Purple) }
                }
            }
            if (state == null || prefs == null) return@LazyColumn
            val s = state!!
            item { SystemHero(summary, s.selectedApps.size, AccountCopy.storageChip(account.sync)) }
            item { SectionHeader("Konto & Synchronisierung") }
            item { AccountSettingsCard(account, actions.onAccount) }
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
            item { SectionHeader("Daten & Backup") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                    NavigationRow(
                        "Daten & Backup", actions.onDataBackup, Modifier.testTag("settings_data_backup"),
                        subtitle = "Lokale Daten sichern, importieren oder löschen", icon = FocusIcons.Download,
                    )
                    Text(
                        "Letztes Backup: " + (prefs.lastExportAt?.let { LAST_BACKUP.format(java.time.ZonedDateTime.ofInstant(it, java.time.ZoneId.systemDefault())) } ?: "Kein Backup"),
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(start = Spacing.xxs, bottom = Spacing.xs),
                    )
                }
            }
            item { SectionHeader("Datenschutz") }
            item {
                FocusCard(Modifier.fillMaxWidth().testTag("settings_privacy"), contentPadding = PaddingValues(Spacing.md)) {
                    Text("Nur auf diesem Gerät", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                    Text(
                        "Installierte und ausgewählte Apps, Nutzungsdaten, Berechtigungen, die laufende Sitzung, Erinnerungen, Geplanter Fokus, Entwürfe, Fotos und Anhänge. Keine Analyse, kein Tracking, keine Werbung.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Mit deinem Konto synchronisiert", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                    Text(
                        when (account.sync.phase) {
                            app.focusmodeplus.core.model.SyncPhase.UNAVAILABLE -> "Nichts. ${AccountCopy.UNCONFIGURED} Missionen, Verlauf, Serie und Einstellungen bleiben auf diesem Gerät."
                            app.focusmodeplus.core.model.SyncPhase.SIGNED_OUT, app.focusmodeplus.core.model.SyncPhase.DECISION_REQUIRED, app.focusmodeplus.core.model.SyncPhase.ACCOUNT_MISMATCH ->
                                "Nichts. Erst nach Anmeldung und deiner Bestätigung: Missionen mit Schritten, abgeschlossene Fokus-Sitzungen und ausgewählte Einstellungen."
                            else -> "Missionen mit Schritten, abgeschlossene Fokus-Sitzungen (Verlauf), Quick-Focus-Dauer, Bewegung, Sperrbildschirm-Inhalt und Synchronisierungsdaten. Serie und Statistiken werden daraus neu berechnet und nicht verifiziert."
                        },
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite, modifier = Modifier.testTag("settings_privacy_synced"),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Was an die AI geht", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                    Text(
                        "Nur wenn du „Mission planen“ tippst: dein Ziel, Typ, Sicherheit, Zeitplan und ein optionales, neu kodiertes Foto ohne Metadaten. " +
                            "Nur bei „Plan mit AI anpassen“: die Daten dieser einen Mission. Nie: andere Missionen, dein Verlauf, deine Serie, deine App-Auswahl oder Gerätedaten.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Benachrichtigungen auf dem Sperrbildschirm steuerst du oben unter „Inhalt auf dem Sperrbildschirm“. Löschen kannst du alles unter „Daten & Backup“.",
                        style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                    )
                    TextAction("Ablenkungsdaten löschen", viewModel::deleteDistractionData)
                    if (actions.debugBuild) ToggleRow("Web-Backup-Abkürzung in Missionen (Entwicklung)", prefs.featureFlags.pwaImportEnabled, viewModel::setPwaImport)
                }
            }
            item { Text("Focus Mode+ ${actions.appVersion} · Offline-fähig · Lokale Speicherung", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.sm)) }
        }
    }
}

@Composable
fun PermissionCenterRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
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

/** v21 Settings account section: one row into "Konto & Synchronisierung". */
@Composable
private fun AccountSettingsCard(summary: AccountSummary, onOpen: () -> Unit) {
    val user = summary.account.user
    val (label, tone) = AccountCopy.phase(summary.sync)
    FocusCard(Modifier.fillMaxWidth().testTag("settings_account"), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
        NavigationRow(
            title = user?.let { it.displayName ?: "Google-Konto" } ?: AccountCopy.GUEST_TITLE,
            onClick = onOpen,
            subtitle = when {
                summary.account == app.focusmodeplus.core.model.AccountState.FirebaseUnconfigured -> AccountCopy.UNCONFIGURED
                user != null -> user.email
                else -> "Optional: mit Google anmelden, synchronisieren und wiederherstellen"
            },
            icon = FocusIcons.User,
        )
        StatusPill(label, Modifier.padding(start = Spacing.xxs, bottom = Spacing.xs).semantics(mergeDescendants = true) {}.testTag("settings_account_pill"), tone = tone, maxLines = 3)
    }
}

/** PWA `.st-summary`: aubergine hero with three local counts. The chip says where the data lives. */
@Composable
private fun SystemHero(summary: SystemSummary, selectedApps: Int, storageChip: String = "Lokal gespeichert") {
    AubergineSurface(Modifier.fillMaxWidth().testTag("settings_summary"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Sparkle, null, tint = FocusPalette.Lavender, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Focus Mode+", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Lavender, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.07f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.pill).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(Radii.pill).background(FocusPalette.ProtectDot))
                Spacer(Modifier.width(6.dp))
                Text(storageChip, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("Dein Fokus-System", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.semantics { heading() })
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = Color.White.copy(alpha = 0.12f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroStat("${summary.activeMissions} / ${summary.maxActiveMissions}", "Aktive Missionen", Modifier.weight(1f))
            VerticalDivider(color = Color.White.copy(alpha = 0.12f))
            HeroStat("${summary.savedSessions}", "Sitzungen gespeichert", Modifier.weight(1f).padding(start = Spacing.sm))
            VerticalDivider(color = Color.White.copy(alpha = 0.12f))
            HeroStat("$selectedApps", "Apps ausgewählt", Modifier.weight(1f).padding(start = Spacing.sm))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroText)
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
            item {
                FocusTopBar(onBack, trailing = {
                    Text("FOKUS-SCHUTZ", style = LabelType.Kicker.copy(fontWeight = FontWeight.Black, letterSpacing = 0.17.em), color = FocusPalette.Purple, modifier = Modifier.padding(end = Spacing.xs))
                })
            }
            item {
                Text("Was lenkt dich ab?", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
            }
            item {
                Text(
                    "Wähle Apps, bei denen dich Focus Mode+ in einer geschützten Sitzung an deinen Fokus erinnern soll. Apps werden nicht blockiert. Deine Auswahl bleibt auf diesem Gerät.",
                    style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft,
                )
            }
            item { SelectionSummary(state.apps.filter { it.selected }, state.selectedCount) }
            item {
                OutlinedTextField(
                    state.query, viewModel::setQuery, Modifier.fillMaxWidth().testTag("app_search"), placeholder = { Text("App suchen") }, singleLine = true, shape = Radii.lg,
                    leadingIcon = { Icon(FocusIcons.Apps, null, tint = FocusPalette.GraphiteSoft) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FocusPalette.Surface, unfocusedContainerColor = FocusPalette.Surface,
                        focusedBorderColor = FocusPalette.Purple, unfocusedBorderColor = FocusPalette.Outline,
                    ),
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

/** PWA `.qs-selection` summary: a stack of the real selected icons, the count and an honest status. */
@Composable
private fun SelectionSummary(selected: List<AppRow>, count: Int) {
    FocusCard(Modifier.fillMaxWidth().testTag("app_selection_summary"), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconStack(selected.map { it.packageName to it.label }, count)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(if (count == 1) "1 App ausgewählt" else "$count Apps ausgewählt", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                Text(
                    if (count == 0) "Noch keine Auswahl" else "Hinweise in geschützten Sitzungen · nicht blockiert",
                    style = MaterialTheme.typography.bodySmall, color = if (count == 0) FocusPalette.GraphiteSoft else FocusPalette.GreenInk,
                )
            }
            IconTile(FocusIcons.ShieldCheck, background = SolidColor(if (count == 0) FocusPalette.SurfaceMuted else FocusPalette.GreenTint), tint = if (count == 0) FocusPalette.GraphiteSoft else FocusPalette.GreenInk)
        }
    }
}

@Composable
private fun AppRowItem(row: AppRow, onToggle: () -> Unit) {
    val shape = Radii.lg
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (row.selected) FocusPalette.PurpleTint.copy(alpha = 0.6f) else FocusPalette.Surface)
            .border(BorderStroke(1.dp, if (row.selected) FocusPalette.Lavender.copy(alpha = 0.8f) else FocusPalette.Outline), shape)
            .toggleable(row.selected, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .testTag("app_row_${row.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Real launcher icon, loaded lazily per visible row at small size and never stored.
        AppIcon(row.packageName, row.label, size = 40.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text(row.label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = FocusPalette.Graphite, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(Spacing.xs))
        SelectionMark(row.selected)
    }
}

/** PWA round check mark: filled purple when selected, an empty ring otherwise. */
@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        Modifier.size(28.dp).clip(Radii.pill)
            .background(if (selected) FocusPalette.Purple else FocusPalette.Surface)
            .border(BorderStroke(1.5.dp, if (selected) FocusPalette.Purple else FocusPalette.OutlineStrong), Radii.pill),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(FocusIcons.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
}

@Composable
fun DataImportRoute(onBack: () -> Unit, viewModel: DataImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.onFilePicked(resolver, uri) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding().testTag("data_import"), contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { FocusTopBar(onBack, title = "Backup importieren") }
            item {
                Text(
                    "Wähle eine Focus-Mode+-Backupdatei (.json) aus der Web-App oder von Android. Du siehst zuerst eine Vorschau. Bestehende Einträge werden nie überschrieben. " +
                        "Lokales Backup: Verlauf, Serie und Fortschritt stammen aus einer Datei und sind keine verifizierten Nachweise.",
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
                            Text("Herkunft: " + if (p.plan.report.platform == "android") "Focus Mode+ Android" else "Focus Mode+ Web-App", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                            Text("Prüfsumme: " + p.plan.report.integrity.label, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.testTag("import_integrity"))
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

private val LAST_BACKUP: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", java.util.Locale.GERMAN)
