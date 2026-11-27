package app.focusmodeplus.feature.quickfocus

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.focusmodeplus.core.designsystem.component.FocusCard
import app.focusmodeplus.core.designsystem.component.FocusTopBar
import app.focusmodeplus.core.designsystem.component.NavigationRow
import app.focusmodeplus.core.designsystem.component.PrimaryButton
import app.focusmodeplus.core.designsystem.component.ReadableWidth
import app.focusmodeplus.core.designsystem.component.TextAction
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.component.AppIconStack
import app.focusmodeplus.core.designsystem.component.IconTile
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.LabelType
import app.focusmodeplus.core.designsystem.theme.Radii
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.designsystem.util.notificationRationale
import app.focusmodeplus.core.designsystem.util.rememberNotificationPermissionRequest
import app.focusmodeplus.core.model.ProtectionMode

data class QuickFocusActions(
    val onBack: () -> Unit,
    val onStarted: () -> Unit,
    val onOpenAppSelection: () -> Unit,
    val onOpenPermissions: () -> Unit,
)

@Composable
fun QuickFocusRoute(actions: QuickFocusActions, viewModel: QuickFocusViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e -> when (e) { QuickFocusEvent.Started, QuickFocusEvent.AlreadyRunning -> actions.onStarted() } }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh(context.notificationRationale())
        onPauseOrDispose { }
    }
    val requestNotifications = rememberNotificationPermissionRequest { granted, rationale -> viewModel.onNotificationPermissionResult(granted, rationale) }
    QuickFocusScreen(
        state = state,
        actions = actions,
        onMinutes = viewModel::setMinutes,
        onTask = viewModel::setTask,
        onMode = viewModel::setMode,
        onStart = viewModel::start,
        onAllowNotifications = requestNotifications,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFocusScreen(
    state: QuickFocusUiState,
    actions: QuickFocusActions,
    onMinutes: (Int) -> Unit,
    onTask: (String) -> Unit,
    onMode: (ProtectionMode) -> Unit,
    onStart: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    var showProtection by rememberSaveable { mutableStateOf(false) }
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().testTag("quick_focus")) {
            FocusTopBar(onBack = actions.onBack, modifier = Modifier.padding(horizontal = Spacing.xs))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Richte deine Sitzung ein.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = FocusPalette.Ink,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                state.scheduleLabel?.let {
                    Text("Geplanter Fokus · $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.Purple, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Spacing.lg))
                DurationDial(state.minutes, onMinutes, Modifier.fillMaxWidth(0.82f), enabled = !state.starting)
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    "WORAN ARBEITEST DU?", style = LabelType.Kicker.copy(fontWeight = FontWeight.Black, letterSpacing = 0.035.em), color = FocusPalette.Graphite,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = state.task,
                    onValueChange = onTask,
                    modifier = Modifier.fillMaxWidth().testTag("quick_task"),
                    placeholder = { Text("Hier eingeben…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(FocusIcons.Target, null, tint = FocusPalette.Purple) },
                    trailingIcon = { Icon(FocusIcons.Pencil, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(20.dp)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    shape = Radii.lg,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FocusPalette.Surface, unfocusedContainerColor = FocusPalette.Surface,
                        focusedBorderColor = FocusPalette.Purple, unfocusedBorderColor = FocusPalette.Outline,
                    ),
                )
                Spacer(Modifier.height(Spacing.sm))
                FocusCard(
                    Modifier.fillMaxWidth().testTag("protection_row"),
                    onClick = { showProtection = true },
                    onClickLabel = "Fokus-Schutz einstellen",
                    contentPadding = PaddingValues(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Restrained green only when protection is actually ready (protected mode + Usage Access).
                        IconTile(
                            if (state.protectionReady) FocusIcons.ShieldCheck else FocusIcons.Shield,
                            background = SolidColor(if (state.protectionReady) FocusPalette.GreenTint else FocusPalette.PurpleTint),
                            tint = if (state.protectionReady) FocusPalette.GreenInk else FocusPalette.Purple,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("Fokus-Schutz", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = FocusPalette.Graphite)
                            Text(state.protectionSummary, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.selectedApps.isNotEmpty()) {
                            Spacer(Modifier.width(Spacing.xs))
                            AppIconStack(state.selectedApps, state.selectedApps.size, max = 3, size = 28.dp)
                        }
                        Icon(FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft)
                    }
                }
                when (state.notificationPrompt) {
                    NotificationPrompt.ASK -> NotificationAsk(onAllowNotifications)
                    NotificationPrompt.DENIED -> Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("Benachrichtigungen deaktiviert – der Timer läuft trotzdem.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f))
                        TextAction("Einstellungen", actions.onOpenPermissions)
                    }
                    NotificationPrompt.NONE -> Unit
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Danger, modifier = Modifier.padding(top = Spacing.sm)) }
                Spacer(Modifier.height(Spacing.md))
            }
            PrimaryButton(
                "Fokus starten",
                onStart,
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md).testTag("quick_start"),
                enabled = !state.starting,
                icon = FocusIcons.PlayFilled,
            )
        }
    }
    if (showProtection) {
        ModalBottomSheet(onDismissRequest = { showProtection = false }, containerColor = FocusPalette.Surface) {
            ProtectionSheet(state, onMode, actions) { showProtection = false }
        }
    }
}

/** Contextual notification readiness: one compact row, asked only here (never at launch). */
@Composable
private fun NotificationAsk(onAllow: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("notification_prompt"), contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Bell, size = 40.dp, iconSize = 20.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("Fokus-Hinweis anzeigen?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text("Restzeit und Ende als Benachrichtigung.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
            TextAction("Erlauben", onAllow)
        }
    }
}

@Composable
private fun ProtectionSheet(state: QuickFocusUiState, onMode: (ProtectionMode) -> Unit, actions: QuickFocusActions, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal).padding(bottom = Spacing.xl).selectableGroup()) {
        Text("Fokus-Schutz", style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        ModeOption(ProtectionMode.STANDARD, "Timer und Fokus-Hinweis. Beenden jederzeit möglich.", state, onMode)
        ModeOption(ProtectionMode.PROTECTED, "Beenden mit kurzer Bedenkzeit und Grund. Notfall-Ausstieg immer verfügbar.", state, onMode)
        ModeOption(ProtectionMode.STRICT_MANAGED, "Strikte Sperre auf diesem Gerät nicht verfügbar · Verwaltetes Gerät erforderlich.", state, onMode)
        Spacer(Modifier.height(Spacing.xs))
        Text(state.protectionSummary, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        Spacer(Modifier.height(Spacing.sm))
        NavigationRow(
            "Ablenkende Apps auswählen",
            actions.onOpenAppSelection,
            subtitle = if (state.selectedAppLabels.isEmpty()) "App-Auswahl vorbereitet" else state.selectedAppLabels.joinToString(", "),
            icon = FocusIcons.Apps,
        )
        NavigationRow("Berechtigungen", actions.onOpenPermissions, subtitle = "Benachrichtigungen, Nutzungszugriff, Nicht stören", icon = FocusIcons.Lock)
        Text("Focus Mode+ blockiert keine Apps. Deine Auswahl bleibt auf diesem Gerät.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
        PrimaryButton("Fertig", onDone, Modifier.fillMaxWidth().padding(top = Spacing.md).testTag("protection_done"))
    }
}

@Composable
private fun ModeOption(mode: ProtectionMode, body: String, state: QuickFocusUiState, onMode: (ProtectionMode) -> Unit) {
    val available = mode in state.availableModes
    val selected = state.mode == mode
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(Radii.md)
            .selectable(selected = selected, enabled = available, role = Role.RadioButton) { onMode(mode) }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = available, colors = RadioButtonDefaults.colors(selectedColor = FocusPalette.Purple))
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(mode.label, style = MaterialTheme.typography.titleSmall, color = if (available) FocusPalette.Graphite else FocusPalette.GraphiteSoft)
            Text(body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
    }
}
