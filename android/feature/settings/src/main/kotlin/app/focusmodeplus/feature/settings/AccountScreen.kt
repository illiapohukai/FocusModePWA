package app.focusmodeplus.feature.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusmodeplus.core.designsystem.component.FocusCard
import app.focusmodeplus.core.designsystem.component.FocusTopBar
import app.focusmodeplus.core.designsystem.component.NavigationRow
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.SecondaryButton
import app.focusmodeplus.core.designsystem.component.SectionHeader
import app.focusmodeplus.core.designsystem.component.StatusPill
import app.focusmodeplus.core.designsystem.component.TextAction
import app.focusmodeplus.core.designsystem.component.ToggleRow
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.domain.account.AccountChooserHost
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountSwitchChoice
import app.focusmodeplus.core.model.MigrationPreview
import app.focusmodeplus.core.model.MigrationStatus
import app.focusmodeplus.core.model.SyncPhase
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Account screen can ask for. The Activity handle is created per tap and never stored. */
data class AccountScreenActions(
    val onBack: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onContinueAsGuest: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onRetryParked: () -> Unit = {},
    val onLinkLocalData: () -> Unit = {},
    val onUseCloudData: () -> Unit = {},
    val onPostpone: () -> Unit = {},
    val onExpandDecision: () -> Unit = {},
    val onSignOut: (removeLocalData: Boolean) -> Unit = {},
    val onDeleteAccount: (alsoDeleteLocal: Boolean) -> Unit = {},
    val onResolveSwitch: (AccountSwitchChoice) -> Unit = {},
    val onUnlink: () -> Unit = {},
    val onExportBackup: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AccountRoute(onBack: () -> Unit, onDataBackup: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val host = AccountChooserHost { context.findActivity() ?: context }
    AccountScreen(
        state,
        AccountScreenActions(
            onBack = onBack,
            onSignIn = { viewModel.signIn(host) },
            onContinueAsGuest = viewModel::continueAsGuest,
            onSyncNow = viewModel::syncNow,
            onRetryParked = viewModel::retryParked,
            onLinkLocalData = viewModel::linkLocalData,
            onUseCloudData = viewModel::useCloudData,
            onPostpone = viewModel::postpone,
            onExpandDecision = viewModel::expandDecision,
            onSignOut = viewModel::signOut,
            onDeleteAccount = { local -> viewModel.deleteAccount(host, local) },
            onResolveSwitch = viewModel::resolveSwitch,
            onUnlink = viewModel::unlink,
            onExportBackup = onDataBackup,
            onDismissFeedback = viewModel::dismissFeedback,
        ),
    )
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

@Composable
fun AccountScreen(state: AccountUiState, actions: AccountScreenActions) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var removeLocalOnSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteLocalToo by rememberSaveable { mutableStateOf(false) }
    var confirmCloud by rememberSaveable { mutableStateOf(false) }
    var confirmSwitch by rememberSaveable { mutableStateOf<String?>(null) }
    val phase = state.sync.phase
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding().testTag("account"),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { FocusTopBar(actions.onBack, title = "Konto") }
            if (state.loading) return@LazyColumn
            item { StatusCard(state) }
            state.feedback?.let { f -> item { FeedbackCard(f, actions.onDismissFeedback) } }
            AccountCopy.accountMessage(state.account)?.let { (title, body) -> item { NoticeCard(title, body, "account_notice") } }

            // ---- decisions that only the user can make
            if (phase == SyncPhase.DECISION_REQUIRED) {
                item {
                    if (state.decisionPostponed) {
                        FocusCard(Modifier.fillMaxWidth().testTag("account_link_later"), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                            NavigationRow("Lokale Daten noch nicht verbunden", actions.onExpandDecision, subtitle = "Jetzt ansehen und entscheiden", icon = FocusIcons.Layers)
                        }
                    } else {
                        LinkDecisionCard(state, state.preview, actions, onUseCloud = { confirmCloud = true })
                    }
                }
            }
            if (phase == SyncPhase.ACCOUNT_MISMATCH) item { SwitchCard(state, actions, onConfirm = { confirmSwitch = it.name }) }
            if (phase == SyncPhase.MIGRATING) item { MigratingCard(state) }
            if (phase == SyncPhase.PAUSED) item { PausedCard(state, actions) }
            if (phase == SyncPhase.DELETING) {
                item { NoticeCard("Kontolöschung nicht abgeschlossen", "Einige Cloud-Daten sind noch vorhanden. Bis zum Abschluss wird nichts synchronisiert.", "account_deleting") }
                item { SecondaryButton("Löschung fortsetzen", { confirmDelete = true }, Modifier.fillMaxWidth().testTag("account_delete_retry"), enabled = !state.busy) }
            }

            // ---- signed in and linked: status and manual sync
            if (state.signedIn && phase in setOf(SyncPhase.IDLE, SyncPhase.SYNCING, SyncPhase.OFFLINE, SyncPhase.FAILED, SyncPhase.MIGRATING)) {
                item { SyncDetailsCard(state, actions) }
            }

            // ---- not signed in: benefits and sign-in
            if (!state.configured) {
                item { UnconfiguredCard() }
            } else if (state.account.canSignIn || state.account == AccountState.SigningIn) {
                item { SignInCard(state, actions) }
            }

            item { SectionHeader("Lokales Backup") }
            item {
                FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                    NavigationRow(
                        "Lokales Backup exportieren", actions.onExportBackup, Modifier.testTag("account_backup"),
                        subtitle = "Unabhängig vom Konto, als Datei unter „Daten & Backup“", icon = FocusIcons.Download,
                    )
                }
            }
            item { SyncedDataCard(state) }

            if (state.account.user != null && phase != SyncPhase.DELETING) {
                item { SectionHeader("Konto") }
                item {
                    FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
                        NavigationRow("Abmelden", { if (!state.busy) confirmSignOut = true }, Modifier.testTag("account_sign_out"), subtitle = "Weiter im Gastmodus; Cloud-Daten bleiben erhalten", icon = FocusIcons.User)
                        NavigationRow(
                            "Konto löschen", { if (!state.busy) confirmDelete = true }, Modifier.testTag("account_delete"),
                            subtitle = "Konto und alle Cloud-Daten endgültig entfernen", icon = FocusIcons.Trash, iconTint = FocusPalette.Danger,
                        )
                    }
                }
            }
            if (state.busy) item { Text("Wird ausgeführt …", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            modifier = Modifier.testTag("account_sign_out_dialog"),
            title = { Text("Abmelden?", modifier = Modifier.semantics { heading() }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Dein Konto und deine Cloud-Daten bleiben erhalten. Danach nutzt du Focus Mode+ im Gastmodus weiter.")
                    if (state.sync.pendingChanges > 0) {
                        Text("${AccountCopy.changes(state.sync.pendingChanges)} noch nicht synchronisiert. Sie werden bei der nächsten Anmeldung mit diesem Konto hochgeladen.", color = FocusPalette.AmberInk)
                    }
                    ToggleRow(
                        "Lokale Daten von diesem Gerät entfernen", removeLocalOnSignOut, { removeLocalOnSignOut = it },
                        Modifier.testTag("account_sign_out_remove_local"),
                        subtitle = if (removeLocalOnSignOut) "Missionen, Verlauf, App-Auswahl und Einstellungen werden von diesem Gerät gelöscht." else "Lokale Daten bleiben auf diesem Gerät.",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; actions.onSignOut(removeLocalOnSignOut); removeLocalOnSignOut = false }, Modifier.testTag("account_sign_out_confirm")) {
                    Text("Abmelden", color = if (removeLocalOnSignOut) FocusPalette.Danger else FocusPalette.Purple)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("account_delete_dialog"),
            title = { Text("Konto endgültig löschen?", modifier = Modifier.semantics { heading() }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Gelöscht werden dein Focus Mode+-Konto und alle Cloud-Daten: Missionen, Schritte, Sitzungen, Einstellungen und Synchronisierungsdaten. Dein Google-Konto selbst bleibt bestehen.")
                    Text("Zur Bestätigung meldest du dich noch einmal mit Google an. Das kann nicht rückgängig gemacht werden.")
                    TextAction("Vorher lokales Backup exportieren", { confirmDelete = false; actions.onExportBackup() })
                    ToggleRow(
                        "Auch lokale Daten auf diesem Gerät löschen", deleteLocalToo, { deleteLocalToo = it }, Modifier.testTag("account_delete_local"),
                        subtitle = if (deleteLocalToo) "Dieses Gerät wird ebenfalls geleert." else "Lokale Daten bleiben als Gastdaten auf diesem Gerät.",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDeleteAccount(deleteLocalToo); deleteLocalToo = false }, Modifier.testTag("account_delete_confirm")) {
                    Text("Konto löschen", color = FocusPalette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmCloud) {
        AlertDialog(
            onDismissRequest = { confirmCloud = false },
            modifier = Modifier.testTag("account_cloud_dialog"),
            title = { Text("Cloud-Daten verwenden?", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "Die Missionen und der Verlauf auf diesem Gerät (${state.localMissions} Missionen, ${state.localSessions} Sitzungen) sind noch nicht synchronisiert. " +
                        "Sie werden durch die Daten deines Kontos ersetzt und gehen dabei verloren. Exportiere vorher ein Backup, wenn du sie behalten möchtest.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmCloud = false; actions.onUseCloudData() }, Modifier.testTag("account_cloud_confirm")) { Text("Ersetzen", color = FocusPalette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmCloud = false }) { Text("Abbrechen") } },
        )
    }
    confirmSwitch?.let { name ->
        val choice = AccountSwitchChoice.valueOf(name)
        AlertDialog(
            onDismissRequest = { confirmSwitch = null },
            modifier = Modifier.testTag("account_switch_dialog"),
            title = { Text(if (choice == AccountSwitchChoice.DELETE_LOCAL) "Lokale Daten löschen?" else "Daten dieses Kontos verwenden?", modifier = Modifier.semantics { heading() }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        if (choice == AccountSwitchChoice.DELETE_LOCAL) "Missionen, Verlauf, App-Auswahl und Einstellungen werden von diesem Gerät gelöscht. Danach lädt dieses Gerät die Daten des angemeldeten Kontos."
                        else "Missionen und Verlauf auf diesem Gerät werden durch die Daten des angemeldeten Kontos ersetzt. App-Auswahl, Erinnerungen und Geplanter Fokus bleiben.",
                    )
                    val pending = state.switch?.pendingChangesOfPreviousAccount ?: 0
                    if (pending > 0) Text("${AccountCopy.changes(pending)} des anderen Kontos sind noch nicht synchronisiert und gehen dabei verloren.", color = FocusPalette.Danger)
                }
            },
            confirmButton = { TextButton(onClick = { confirmSwitch = null; actions.onResolveSwitch(choice) }, Modifier.testTag("account_switch_confirm")) { Text("Fortfahren", color = FocusPalette.Danger) } },
            dismissButton = { TextButton(onClick = { confirmSwitch = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun StatusCard(state: AccountUiState) {
    val user = state.account.user
    val (label, tone) = AccountCopy.phase(state.sync)
    FocusCard(Modifier.fillMaxWidth().testTag("account_status"), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user?.displayName ?: user?.email)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        user != null -> user.displayName ?: "Google-Konto"
                        state.account == AccountState.SigningIn -> "Anmeldung läuft …"
                        else -> AccountCopy.GUEST_TITLE
                    },
                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("account_name"),
                )
                user?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("account_email")) }
                if (user == null) Text(if (state.configured) "Alles funktioniert ohne Konto." else AccountCopy.UNCONFIGURED, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        StatusPill(label, Modifier.semantics(mergeDescendants = true) {}.testTag("account_sync_pill"), tone = tone, maxLines = 3)
    }
}

/** Initials only: no profile photo is downloaded (no image library, no extra network request). */
@Composable
private fun Avatar(name: String?) {
    val initials = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.take(2)?.joinToString("") { it.first().uppercase() }?.ifEmpty { null }
    Box(Modifier.size(44.dp).clip(Radii.pill).background(FocusPalette.PurpleTint).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (initials != null) Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = FocusPalette.PurpleInk)
        else androidx.compose.material3.Icon(FocusIcons.User, null, tint = FocusPalette.Purple, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FeedbackCard(f: DataFeedback, onDismiss: () -> Unit) {
    FocusCard(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("account_feedback"),
        color = if (f.success) FocusPalette.GreenTint else FocusPalette.DangerTint, contentPadding = PaddingValues(Spacing.md),
    ) {
        Text(f.title, style = MaterialTheme.typography.titleSmall, color = if (f.success) FocusPalette.GreenInk else FocusPalette.Danger)
        Text(f.body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
        TextAction("Ausblenden", onDismiss)
    }
}

@Composable
private fun NoticeCard(title: String, body: String, tag: String) {
    FocusCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag(tag), color = FocusPalette.SurfaceMuted, contentPadding = PaddingValues(Spacing.md)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
    }
}

@Composable
private fun Line(label: String, value: String, tag: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics(mergeDescendants = true) {}.let { if (tag != null) it.testTag(tag) else it }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
    }
}

@Composable
private fun LinkDecisionCard(state: AccountUiState, p: MigrationPreview?, actions: AccountScreenActions, onUseCloud: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("account_link_preview"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Lokale Daten verbinden?", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xxs))
        Text(AccountCopy.LINK_HEADLINE, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
        Spacer(Modifier.height(Spacing.xs))
        if (p == null) {
            Text("Vorschau wird vorbereitet …", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        } else {
            Line("Missionen", "${p.missions} (${p.missionSteps} Schritte)", "preview_missions")
            Line("Abgeschlossene Fokus-Sitzungen", "${p.sessions}", "preview_sessions")
            if (p.historyFrom != null && p.historyTo != null) Line("Verlauf", "${DAY.format(p.historyFrom)} – ${DAY.format(p.historyTo)}", "preview_range")
            Line("Fokuszeit", "${p.focusMinutes} Min. (lokal berechnet)", "preview_minutes")
            Line("Einstellungen", p.includedSettings.joinToString(", "))
            Line("Bleibt auf diesem Gerät", p.excludedLocalData.joinToString(", "))
            if (p.awaitingReview > 0) Line("Später", if (p.awaitingReview == 1) "1 Sitzung wartet auf deine Auswertung und folgt danach." else "${p.awaitingReview} Sitzungen warten auf deine Auswertung und folgen danach.")
            if (p.notSyncable > 0) Line("Nicht übertragbar", "${p.notSyncable} Einträge bleiben nur auf diesem Gerät.")
            Line(
                "In deinem Konto",
                when (p.remoteHasData) {
                    null -> "Konnte gerade nicht geprüft werden (offline)."
                    false -> "Noch keine Cloud-Daten."
                    true -> "${p.remoteMissions} Missionen, ${p.remoteSessions} Sitzungen – sie werden mit deinen lokalen Daten zusammengeführt."
                },
                "preview_remote",
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(AccountCopy.NO_LEAGUE_POINTS, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.testTag("preview_no_league"))
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Lokale Daten mit diesem Konto verbinden", actions.onLinkLocalData, Modifier.fillMaxWidth().testTag("account_link_confirm"), enabled = !state.busy && p != null)
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Cloud-Daten verwenden", onUseCloud, Modifier.fillMaxWidth().testTag("account_use_cloud"), enabled = !state.busy && p?.remoteHasData == true)
        Spacer(Modifier.height(Spacing.xxs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction("Lokales Backup exportieren", actions.onExportBackup)
            TextAction("Später", actions.onPostpone, Modifier.testTag("account_link_later_action"))
        }
    }
}

@Composable
private fun SwitchCard(state: AccountUiState, actions: AccountScreenActions, onConfirm: (AccountSwitchChoice) -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("account_switch"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Daten eines anderen Kontos", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            "Die Missionen und der Verlauf auf diesem Gerät gehören zu einem anderen Google-Konto. Sie werden nicht automatisch mit diesem Konto verbunden, und es wird nichts synchronisiert, bis du entscheidest. " +
                "„Getrennt halten“ meldet dieses Konto wieder ab; die Daten bleiben beim bisherigen Konto.",
            style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite,
        )
        val pending = state.switch?.pendingChangesOfPreviousAccount ?: 0
        if (pending > 0) {
            Spacer(Modifier.height(Spacing.xxs))
            Text("${AccountCopy.changes(pending)} des anderen Kontos sind noch nicht synchronisiert.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.AmberInk)
        }
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Getrennt halten", { actions.onResolveSwitch(AccountSwitchChoice.KEEP_SEPARATE) }, Modifier.fillMaxWidth().testTag("switch_keep"), enabled = !state.busy)
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Lokales Backup exportieren", actions.onExportBackup, Modifier.fillMaxWidth().testTag("switch_backup"))
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Daten dieses Kontos verwenden", { onConfirm(AccountSwitchChoice.USE_NEW_ACCOUNT_DATA) }, Modifier.fillMaxWidth().testTag("switch_use_new"), enabled = !state.busy)
        Spacer(Modifier.height(Spacing.xxs))
        TextAction("Lokale Daten löschen und fortfahren", { onConfirm(AccountSwitchChoice.DELETE_LOCAL) }, Modifier.testTag("switch_delete_local"), color = FocusPalette.Danger)
    }
}

@Composable
private fun MigratingCard(state: AccountUiState) {
    FocusCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("account_migrating"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Datenübernahme läuft", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Text(
            "Deine lokalen Daten werden hochgeladen und danach geprüft. Fehlt die Verbindung, geht es automatisch weiter. Auf diesem Gerät wird nichts gelöscht." +
                if (state.sync.pendingChanges > 0) " Noch offen: ${AccountCopy.changes(state.sync.pendingChanges)}." else "",
            style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite,
        )
    }
}

@Composable
private fun PausedCard(state: AccountUiState, actions: AccountScreenActions) {
    FocusCard(Modifier.fillMaxWidth().testTag("account_paused"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Synchronisierung pausiert", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Text(
            "Die Daten auf diesem Gerät gehören zu deinem zuletzt angemeldeten Konto. Änderungen werden synchronisiert, wenn du dich wieder mit diesem Konto anmeldest." +
                if (state.sync.pendingChanges > 0) " Ausstehend: ${AccountCopy.changes(state.sync.pendingChanges)}." else "",
            style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite,
        )
        TextAction("Verknüpfung lösen (Daten bleiben lokal)", actions.onUnlink, Modifier.testTag("account_unlink"))
    }
}

@Composable
private fun SyncDetailsCard(state: AccountUiState, actions: AccountScreenActions) {
    val s = state.sync
    FocusCard(Modifier.fillMaxWidth().testTag("account_sync"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Synchronisierung", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xxs))
        Line("Zuletzt erfolgreich", state.lastSyncLabel ?: "Noch nicht synchronisiert", "sync_last")
        Line("Ausstehende Änderungen", if (s.pendingChanges == 0) "Keine" else AccountCopy.changes(s.pendingChanges), "sync_pending")
        Line(
            "Datenübernahme",
            when (s.migration) {
                MigrationStatus.COMPLETE -> "Abgeschlossen"
                MigrationStatus.IN_PROGRESS -> "Läuft"
                MigrationStatus.PENDING_DECISION -> "Entscheidung offen"
                MigrationStatus.NOT_REQUIRED -> "Nicht erforderlich"
            },
            "sync_migration",
        )
        if (s.conflictsResolved > 0) Line("Zusammengeführt", "${s.conflictsResolved}× gleichzeitige Änderungen nach festen Regeln gelöst")
        s.failure?.let { Text(AccountCopy.syncFailure(it), style = MaterialTheme.typography.bodySmall, color = FocusPalette.AmberInk, modifier = Modifier.testTag("sync_failure")) }
        if (s.parkedChanges > 0) {
            Text("${AccountCopy.changes(s.parkedChanges)} konnten wiederholt nicht übertragen werden.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.AmberInk)
            TextAction("Erneut versuchen", actions.onRetryParked, Modifier.testTag("sync_retry_parked"))
        }
        Spacer(Modifier.height(Spacing.sm))
        // Soft hyphen: at 320 dp with large fonts the long word breaks as "synchroni-sieren", never mid-syllable.
        PrimaryButton("Jetzt synchroni\u00ADsieren", actions.onSyncNow, Modifier.fillMaxWidth().testTag("account_sync_now"), enabled = !state.busy && s.phase != SyncPhase.SYNCING)
    }
}

@Composable
private fun SignInCard(state: AccountUiState, actions: AccountScreenActions) {
    FocusCard(Modifier.fillMaxWidth().testTag("account_benefits"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Optional: mit Google-Konto", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xxs))
        AccountCopy.BENEFITS.forEach { b ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", style = MaterialTheme.typography.bodySmall, color = FocusPalette.Purple, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(Spacing.xs))
                Text(b, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Vorhandene Daten auf diesem Gerät werden erst nach deiner Bestätigung mit dem Konto verbunden. Fokus-Sitzungen, Missionen, Verlauf und Einblicke funktionieren auch ohne Konto.",
            style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton(
            if (state.account is AccountState.ReauthenticationRequired) "Erneut mit Google anmelden" else "Mit Google anmelden",
            actions.onSignIn, Modifier.fillMaxWidth().testTag("account_sign_in"), enabled = !state.busy && state.account != AccountState.SigningIn,
        )
        if (state.account == AccountState.SignInAvailable) {
            Spacer(Modifier.height(Spacing.xxs))
            TextAction("Ohne Konto fortfahren", actions.onContinueAsGuest, Modifier.testTag("account_guest"))
        }
    }
}

@Composable
private fun UnconfiguredCard() {
    FocusCard(Modifier.fillMaxWidth().testTag("account_unconfigured"), contentPadding = PaddingValues(Spacing.md)) {
        Text(AccountCopy.GUEST_TITLE, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Text(AccountCopy.UNCONFIGURED, style = MaterialTheme.typography.bodyMedium, color = FocusPalette.Graphite)
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            "Alle Funktionen stehen dir ohne Konto zur Verfügung. Ein lokales Backup erstellst du jederzeit unter „Daten & Backup“.",
            style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
        )
    }
}

/** Data categories as they apply right now (v21 privacy UI). */
@Composable
internal fun SyncedDataCard(state: AccountUiState) {
    FocusCard(Modifier.fillMaxWidth().testTag("account_data_categories"), contentPadding = PaddingValues(Spacing.md)) {
        Text("Welche Daten wohin gehen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xxs))
        Line(
            "Mit deinem Konto synchronisiert",
            if (state.sync.phase in setOf(SyncPhase.IDLE, SyncPhase.SYNCING, SyncPhase.OFFLINE, SyncPhase.FAILED, SyncPhase.MIGRATING, SyncPhase.PAUSED))
                "Missionen mit Schritten, abgeschlossene Fokus-Sitzungen (Verlauf), Quick-Focus-Dauer, Bewegung, Sperrbildschirm-Inhalt und technische Synchronisierungsdaten. Serie und Statistiken werden daraus neu berechnet."
            else "Nichts. Erst nach Anmeldung und deiner Bestätigung: Missionen, abgeschlossene Sitzungen und ausgewählte Einstellungen.",
        )
        Line("Nur auf diesem Gerät", "Installierte und ausgewählte Apps, Nutzungsdaten, Berechtigungen, die laufende Sitzung, Erinnerungen, Geplanter Fokus, Entwürfe, Fotos und Anhänge.")
        Line("An die AI – nur auf Anfrage", "Ziel, Zeitplan und ein ausgewähltes Foto, wenn du „Mission planen“ tippst; die Daten einer Mission bei „Plan mit AI anpassen“.")
    }
}
