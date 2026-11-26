package io.github.illiapohukai.focusmode.feature.missions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarDay
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarMark
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarMarkKind
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarSelection
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import io.github.illiapohukai.focusmode.core.designsystem.component.MonthCalendar
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarDayInfo
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarEventKind
import io.github.illiapohukai.focusmode.core.domain.mission.MissionSchedulePlanner
import io.github.illiapohukai.focusmode.core.domain.mission.ScheduleNoticeLevel
import io.github.illiapohukai.focusmode.core.model.MissionSchedule
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MissionCreateRoute(actions: MissionFlowActions, snackbar: SnackbarHostState, viewModel: MissionCreateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                CreateEvent.Generating -> actions.onGenerating()
                CreateEvent.ShowBriefing -> actions.onBriefing()
                is CreateEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    // System photo picker: no storage permission; one image (PWA MISSION_IMAGE_MAX_ATTACHMENTS).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> viewModel.onPhotoPicked(uri?.toString()) }
    MissionCreateScreen(
        state = state,
        onBack = actions.onBack,
        onOpenMissions = actions.onOpenMissions,
        callbacks = CreateCallbacks(
            onGoal = viewModel::onGoalChange,
            onType = viewModel::selectType,
            onConfidence = viewModel::selectConfidence,
            onTogglePlanner = viewModel::togglePlanner,
            onClearDeadline = viewModel::clearDeadline,
            onDay = viewModel::onDay,
            onPick = viewModel::pick,
            onShiftMonth = viewModel::shiftMonth,
            onPlannerDone = viewModel::plannerDone,
            onStartMode = viewModel::selectStartMode,
            onToggleRhythm = viewModel::toggleRhythm,
            onWeekday = viewModel::toggleWeekday,
            onWeekdayPreset = viewModel::weekdayPreset,
            onSessionMinutes = viewModel::selectSessionMinutes,
            onAddPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::removePhoto,
            onDismissPhotoMessage = viewModel::dismissPhotoMessage,
            onPlan = viewModel::plan,
            onOpenBriefing = viewModel::openBriefing,
            onDismissError = viewModel::dismissError,
        ),
    )
}

data class CreateCallbacks(
    val onGoal: (String) -> Unit = {},
    val onType: (MissionType) -> Unit = {},
    val onConfidence: (GenerationConfidence) -> Unit = {},
    val onTogglePlanner: () -> Unit = {},
    val onClearDeadline: () -> Unit = {},
    val onDay: (java.time.LocalDate) -> Unit = {},
    val onPick: (java.time.LocalDate) -> Unit = {},
    val onShiftMonth: (Long) -> Unit = {},
    val onPlannerDone: () -> Unit = {},
    val onStartMode: (ScheduleStartMode) -> Unit = {},
    val onToggleRhythm: () -> Unit = {},
    val onWeekday: (DayOfWeek) -> Unit = {},
    val onWeekdayPreset: (Set<DayOfWeek>) -> Unit = {},
    val onSessionMinutes: (Int?) -> Unit = {},
    val onAddPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onDismissPhotoMessage: () -> Unit = {},
    val onPlan: () -> Unit = {},
    val onOpenBriefing: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMMM yyyy", Locale.GERMAN)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd. MMM", Locale.GERMAN)

/** Mission type cards (PWA `mg-type-card`; emoji are decorative). */
private val TYPES = listOf(MissionType.EXAM to "📔", MissionType.ASSIGNMENT to "📋", MissionType.PROJECT to "🧊", MissionType.CUSTOM to "✨")

@Composable
fun MissionCreateScreen(
    state: CreateUiState,
    onBack: () -> Unit,
    onOpenMissions: () -> Unit,
    callbacks: CreateCallbacks,
) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            FlowTopBar("Focus Mission", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).testTag("mission_create"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column {
                    Text("Was ist dein Ziel heute?", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Beschreibe, was du schaffen möchtest, oder füge relevantes Material hinzu. Du kannst den Vorschlag später ändern.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft)
                }
                if (state.limitReached) LimitCard(state.activeCount, onOpenMissions)
                GoalInput(state, callbacks)
                state.error?.let { ErrorCard(it, state.errorRequestId, state.canPlan, callbacks) }
                state.photoMessage?.let { Notice(it, ScheduleNoticeLevel.ERROR, onDismiss = callbacks.onDismissPhotoMessage) }
                state.attachment?.let { PhotoCard(it.byteCount, callbacks.onRemovePhoto) }
                Section("Mission-Typ") { TypeGrid(state.type, callbacks.onType) }
                Section("Wie sicher fühlst du dich?") { ConfidenceRow(state.confidence, callbacks.onConfidence) }
                ScheduleSection(state, callbacks)
                PrivacyNote()
                Spacer(Modifier.height(Spacing.xs))
            }
            Column(Modifier.fillMaxWidth().background(FocusPalette.Cream).padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.xs, bottom = Spacing.sm).navigationBarsPadding()) {
                if (state.briefingReady) {
                    SecondaryButton("Briefing öffnen", callbacks.onOpenBriefing, Modifier.fillMaxWidth().testTag("create_open_briefing"), icon = FocusIcons.Sparkle)
                    Spacer(Modifier.height(Spacing.xs))
                }
                PrimaryButton(
                    if (state.briefingReady) "Neu planen" else "Mission planen", callbacks.onPlan,
                    Modifier.fillMaxWidth().testTag("create_plan"), enabled = state.canPlan, icon = FocusIcons.Sparkle,
                )
                if (!state.canPlan && !state.loading && !state.limitReached) {
                    val missing = when {
                        state.goalText.isBlank() && state.attachment == null -> "Beschreibe dein Ziel oder füge ein Foto hinzu."
                        state.type == null -> "Wähle einen Mission-Typ."
                        state.confidence == null -> "Wähle, wie sicher du dich fühlst."
                        state.goalTooLong -> "Bitte kürze deine Beschreibung (max. 6000 Zeichen)."
                        !state.validation.valid -> "Bitte prüfe den Zeitplan."
                        else -> null
                    }
                    missing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
                }
            }
        }
    }
}

/** PWA `mg-topbar`: back button and the centred "✦ Focus Mission" badge. */
@Composable
internal fun FlowTopBar(badge: String, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp)) {
            if (onBack != null) io.github.illiapohukai.focusmode.core.designsystem.component.BackButton(onBack)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(Radii.pill).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.pill).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FocusIcons.Sparkle, null, tint = FocusPalette.Purple, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(badge, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite)
            }
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

@Composable
private fun LimitCard(active: Int, onOpenMissions: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("create_limit"), color = FocusPalette.PurpleTint, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(FocusIcons.Layers, null, tint = FocusPalette.PurpleInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text("Du hast bereits $active aktive Missionen.", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text("Schließe eine Mission ab oder archiviere sie, um eine neue zu planen. Dein Entwurf bleibt erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
        }
        TextAction("Missionen verwalten", onOpenMissions, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GoalInput(state: CreateUiState, callbacks: CreateCallbacks) {
    FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 104.dp)) {
            if (state.goalText.isEmpty()) {
                Text("Beschreibe dein Ziel, die Frist und was du schaffen möchtest …", style = MaterialTheme.typography.bodyLarge, color = FocusPalette.GraphiteSoft.copy(alpha = 0.7f))
            }
            BasicTextField(
                value = state.goalText,
                onValueChange = { callbacks.onGoal(it.take(GOAL_INPUT_CAP)) },
                enabled = !state.generating,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = FocusPalette.Graphite),
                cursorBrush = SolidColor(FocusPalette.Purple),
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).testTag("create_goal").semantics { contentDescription = "Lernziel" },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(Radii.control).background(FocusPalette.SurfaceMuted)
                    .clickable(enabled = !state.preparingPhoto && !state.generating, role = Role.Button, onClickLabel = "Foto hinzufügen", onClick = callbacks.onAddPhoto)
                    .semantics { contentDescription = "Foto hinzufügen" }.testTag("create_add_photo"),
                contentAlignment = Alignment.Center,
            ) { Icon(FocusIcons.Paperclip, null, tint = FocusPalette.Graphite, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(Spacing.sm))
            val hint = when {
                state.preparingPhoto -> "Foto wird vorbereitet …"
                state.attachment != null -> "Foto hinzugefügt"
                state.goalText.isNotBlank() -> "Text bereit"
                else -> "Text oder Foto hinzufügen"
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            if (state.goalText.length > 5000) {
                Text("${state.goalText.length}/6000", style = MaterialTheme.typography.labelMedium, color = if (state.goalTooLong) FocusPalette.Danger else FocusPalette.GraphiteSoft)
            }
        }
    }
}

/** Input never grows unbounded; above 6000 characters the plan button explains the limit. */
private const val GOAL_INPUT_CAP = 6500

@Composable
private fun ErrorCard(error: GenerationError, requestId: String?, canRetry: Boolean, callbacks: CreateCallbacks) {
    val offline = error == GenerationError.Offline
    FocusCard(Modifier.fillMaxWidth().testTag("create_error"), color = if (offline) FocusPalette.Surface else FocusPalette.DangerTint, contentPadding = PaddingValues(Spacing.md)) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
            if (offline) {
                Text("Keine Internetverbindung", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text("Die AI-Mission kann erstellt werden, sobald du wieder online bist. Deine Eingaben bleiben erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            } else {
                Text(error.message + (requestId?.let { " (Anfrage-ID: $it)" } ?: ""), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = FocusPalette.Graphite)
            }
        }
        Row(Modifier.padding(top = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (error.retryable && canRetry) TextAction("Erneut versuchen", callbacks.onPlan, Modifier.testTag("create_retry"))
            TextAction("Schließen", callbacks.onDismissError, color = FocusPalette.GraphiteSoft)
        }
    }
}

@Composable
private fun Notice(text: String, level: ScheduleNoticeLevel, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    val (bg, fg) = when (level) {
        ScheduleNoticeLevel.ERROR -> FocusPalette.DangerTint to FocusPalette.Danger
        ScheduleNoticeLevel.WARN -> Color(0xFFFFF4E5) to Color(0xFF8A4B0F)
        ScheduleNoticeLevel.INFO -> FocusPalette.PurpleTint to FocusPalette.PurpleInk
    }
    Row(
        modifier.fillMaxWidth().clip(Radii.md).background(bg).padding(horizontal = Spacing.sm, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (level == ScheduleNoticeLevel.INFO) FocusIcons.Info else FocusIcons.Alert, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Hinweis schließen", onClick = onDismiss), contentAlignment = Alignment.Center) {
                Icon(FocusIcons.Close, "Hinweis schließen", tint = fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PhotoCard(bytes: Int, onRemove: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("create_photo"), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Image, background = SolidColor(Color(0xFFFFF1DE)), tint = Color(0xFFB45309))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text("Lernmaterial", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text(formatBytes(bytes) + " · bereit für AI-Analyse · ohne Metadaten", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Foto entfernen", onClick = onRemove).testTag("create_remove_photo"), contentAlignment = Alignment.Center) {
                Icon(FocusIcons.Close, "Foto entfernen", tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** PWA `formatBytes`. */
internal fun formatBytes(bytes: Int): String =
    if (bytes < 1_048_576) "${maxOf(1, Math.round(bytes / 1024.0).toInt())} KB" else String.format(Locale.GERMAN, "%.1f MB", bytes / 1_048_576.0)

@Composable
private fun Section(title: String, trailing: String? = null, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft) }
        }
        Spacer(Modifier.height(Spacing.xs))
        content()
    }
}

@Composable
private fun SelectCard(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier, tag: String, leading: @Composable () -> Unit) {
    val shape = Radii.lg
    Column(
        modifier.heightIn(min = 72.dp).clip(shape)
            .background(if (selected) FocusPalette.PurpleTint else FocusPalette.Surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FocusPalette.Purple else FocusPalette.Outline), shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        leading()
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (selected) FocusPalette.PurpleInk else FocusPalette.Graphite, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TypeGrid(selected: MissionType?, onSelect: (MissionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TYPES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                row.forEach { (type, emoji) ->
                    SelectCard(selected == type, type.label, { onSelect(type) }, Modifier.weight(1f), "type_${type.wire}") {
                        Text(emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { }) // decorative
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceRow(selected: GenerationConfidence?, onSelect: (GenerationConfidence) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GenerationConfidence.entries.forEach { c ->
            SelectCard(selected == c, c.label, { onSelect(c) }, Modifier.weight(1f), "confidence_${c.wire}") {
                when (c) {
                    GenerationConfidence.UNCERTAIN -> Text("–", style = MaterialTheme.typography.titleMedium, color = FocusPalette.GraphiteSoft)
                    GenerationConfidence.PARTIAL -> Text("50/50", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                    GenerationConfidence.CONFIDENT -> Icon(FocusIcons.Check, null, tint = FocusPalette.Green, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(state: CreateUiState, callbacks: CreateCallbacks) {
    val schedule = state.schedule
    Section("Zeitplan", trailing = "optional") {
        FocusCard(Modifier.fillMaxWidth().testTag("create_schedule"), contentPadding = PaddingValues(Spacing.md)) {
            // Deadline disclosure row (PWA mgDeadlineTrigger).
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radii.md)
                    .clickable(role = Role.Button, onClickLabel = if (state.planner.expanded) "Kalender schließen" else "Kalender öffnen", onClick = callbacks.onTogglePlanner)
                    .semantics { stateDescription = if (state.planner.expanded) "Kalender geöffnet" else "Kalender geschlossen" }
                    .testTag("create_deadline_trigger"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(FocusIcons.Flag, size = 40.dp, iconSize = 18.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("PRÜFUNG / ABGABE", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
                    Text(schedule.deadline?.let { LONG_DATE.format(it) } ?: "Datum auswählen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                    state.deadlineSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.PurpleInk) }
                }
                if (schedule.deadline != null) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Frist entfernen", onClick = callbacks.onClearDeadline).testTag("create_clear_deadline"), contentAlignment = Alignment.Center) {
                        Icon(FocusIcons.Close, "Frist entfernen", tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Icon(if (state.planner.expanded) FocusIcons.ChevronDown else FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
                }
            }
            if (state.planner.expanded) {
                Spacer(Modifier.height(Spacing.sm))
                Planner(state, callbacks)
            }
            if (schedule.deadline != null) {
                Spacer(Modifier.height(Spacing.md))
                Route(state)
                Spacer(Modifier.height(Spacing.sm))
                Text("Wann startest du?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ScheduleStartMode.entries.forEach { mode ->
                        val preview = state.startPreviews[mode]
                        val chosen = schedule.startMode == mode
                        val recommended = mode == state.recommendedMode
                        Column(
                            Modifier.clip(Radii.md).background(if (chosen) FocusPalette.PurpleTint else FocusPalette.SurfaceMuted)
                                .border(BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) FocusPalette.Purple else FocusPalette.Outline), Radii.md)
                                .clickable(role = Role.RadioButton) { callbacks.onStartMode(mode) }
                                .semantics(mergeDescendants = true) { selected = chosen }
                                .testTag("start_mode_${mode.wire}")
                                .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium, color = if (chosen) FocusPalette.PurpleInk else FocusPalette.Graphite)
                            val sub = listOfNotNull(preview?.let { if (it == state.today) "Heute" else SHORT_DATE.format(it) }, if (recommended) "Empfohlen" else null).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.GraphiteSoft)
                        }
                    }
                }
            }
            state.validation.message?.let { msg ->
                Spacer(Modifier.height(Spacing.sm))
                Notice(msg, state.validation.level ?: ScheduleNoticeLevel.INFO, Modifier.testTag("create_schedule_notice"))
            }
            Spacer(Modifier.height(Spacing.sm))
            Rhythm(state, callbacks)
        }
    }
}

@Composable
private fun Planner(state: CreateUiState, callbacks: CreateCallbacks) {
    val p = state.planner
    Column(Modifier.testTag("create_planner")) {
        if (p.target == PlannerTarget.START) {
            Row(Modifier.fillMaxWidth().clip(Radii.md).background(FocusPalette.PurpleTint).padding(start = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Wähle den Start deiner Vorbereitung.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.PurpleInk, modifier = Modifier.weight(1f))
                TextAction("Fertig", callbacks.onPlannerDone)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        val start = if (state.schedule.deadline != null) state.schedule.preparationStart ?: state.today else null
        MonthCalendar(
            month = p.month,
            days = p.days.map { it.toCell() },
            today = state.today,
            focused = p.focused,
            onDay = callbacks.onDay,
            onPrevious = { callbacks.onShiftMonth(-1) },
            onNext = { callbacks.onShiftMonth(1) },
            previousEnabled = p.previousEnabled,
            selection = CalendarSelection(state.schedule.deadline, start),
        )
        Spacer(Modifier.height(Spacing.xs))
        Legend()
        Spacer(Modifier.height(Spacing.xs))
        Column(Modifier.fillMaxWidth().clip(Radii.md).background(FocusPalette.Cream).padding(Spacing.sm).semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite }) {
            p.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft) }
            p.detailTitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite) }
            if (p.detailTitle != null && p.rows.isEmpty()) Text("Keine Missionstermine an diesem Tag.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            p.rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(row.theme?.let { MissionTones.of(it).accentStrong } ?: FocusPalette.Purple))
                    Spacer(Modifier.width(Spacing.xs))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 2)
                        Text("${row.chip} · ${row.detail}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    }
                }
            }
            p.pick?.let { date ->
                TextAction(if (p.target == PlannerTarget.START) "Als Vorbereitungsstart wählen" else "Als Frist wählen", { callbacks.onPick(date) }, Modifier.testTag("planner_pick"))
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "" }, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LegendItem(FocusPalette.Purple, "Neue Frist", filled = true)
        LegendItem(FocusPalette.GraphiteSoft, "Mission-Frist", filled = true, square = true)
        LegendItem(FocusPalette.GraphiteSoft, "Start", filled = false)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, filled: Boolean, square: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = if (square) Radii.sm else CircleShape
        Box(Modifier.size(8.dp).clip(shape).then(if (filled) Modifier.background(color) else Modifier.border(BorderStroke(1.dp, color), shape)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.GraphiteSoft)
    }
}

/** PWA `mg-route`: START → n Tage → ZIEL. */
@Composable
private fun Route(state: CreateUiState) {
    val deadline = state.schedule.deadline ?: return
    val start = state.schedule.preparationStart ?: state.today
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().clip(Radii.md).background(FocusPalette.Cream).padding(Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = "Vorbereitung von ${LONG_DATE.format(start)} bis ${LONG_DATE.format(deadline)}, ${if (days == 1L) "1 Tag" else "$days Tage"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("START", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
            Text(if (start == state.today) "Heute" else SHORT_DATE.format(start), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
        }
        Box(Modifier.weight(1f).padding(horizontal = Spacing.sm), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(FocusPalette.Purple.copy(alpha = 0.25f)))
            Text(if (days == 1L) "1 Tag" else "$days Tage", style = MaterialTheme.typography.labelMedium, color = FocusPalette.PurpleInk, modifier = Modifier.background(FocusPalette.Cream).padding(horizontal = 6.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("ZIEL", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
            Text(SHORT_DATE.format(deadline), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Rhythm(state: CreateUiState, callbacks: CreateCallbacks) {
    val s = state.schedule
    val summary = MissionSchedulePlanner.weekdayLabel(s.preferredWeekdays) + " · " + (s.preferredSessionMinutes?.let { "$it Min.-Blöcke" } ?: "AI entscheidet")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radii.md).clickable(role = Role.Button, onClickLabel = "Lernrhythmus anpassen", onClick = callbacks.onToggleRhythm)
            .semantics { stateDescription = if (state.rhythmOpen) "geöffnet" else "geschlossen" }.testTag("create_rhythm"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Lernrhythmus anpassen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
        Icon(if (state.rhythmOpen) FocusIcons.ChevronDown else FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
    }
    if (!state.rhythmOpen) return
    Spacer(Modifier.height(Spacing.xs))
    Text("An welchen Tagen kannst du lernen?", style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { d ->
            val on = d in s.preferredWeekdays
            SmallChip(MissionSchedulePlanner.SHORT[d.value - 1], on, "weekday_${d.value}") { callbacks.onWeekday(d) }
        }
    }
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallChip("Unter der Woche", s.preferredWeekdays == MissionSchedulePlanner.WEEKDAYS, "preset_weekdays") { callbacks.onWeekdayPreset(MissionSchedulePlanner.WEEKDAYS) }
        SmallChip("Wochenende", s.preferredWeekdays == MissionSchedulePlanner.WEEKEND, "preset_weekend") { callbacks.onWeekdayPreset(MissionSchedulePlanner.WEEKEND) }
        SmallChip("Flexibel", s.preferredWeekdays.isEmpty(), "preset_flexible") { callbacks.onWeekdayPreset(emptySet()) }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text("Wie lang soll eine Sitzung sein?", style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MissionSchedule.SESSION_MINUTES.forEach { m -> SmallChip("$m Min.", s.preferredSessionMinutes == m, "minutes_$m") { callbacks.onSessionMinutes(m) } }
        SmallChip("AI wählt", s.preferredSessionMinutes == null, "minutes_auto") { callbacks.onSessionMinutes(null) }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(Radii.pill)
            .background(if (selected) FocusPalette.Purple else FocusPalette.SurfaceMuted)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else FocusPalette.Graphite) }
}

@Composable
private fun PrivacyNote() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(Radii.md).clickable(role = Role.Button, onClickLabel = "Datenschutz-Hinweis", onClick = { open = !open }).padding(vertical = Spacing.xs).testTag("create_privacy")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Lock, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Was wird an die AI gesendet?", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f))
            Icon(if (open) FocusIcons.ChevronDown else FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(16.dp))
        }
        if (open) {
            Text(
                "Erst nach „Mission planen“: dein Ziel-Text, ein ausgewähltes Foto sowie Mission-Typ, Sicherheit und der gewählte Zeitplan gehen an den AI-Dienst (unser Server bei Cloudflare, Modell: Google Gemini). Fotos werden vorher verkleinert und ohne Metadaten (z. B. Standort) übertragen. " +
                    "Missionen, Sitzungsverlauf, Serie, ausgewählte Apps und Einstellungen bleiben auf diesem Gerät.",
                style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Domain day → design-system cell: identity tones become colors here and only here. */
internal fun CalendarDayInfo.toCell(): CalendarDay {
    val deadline = events.firstOrNull { it.kind == CalendarEventKind.DEADLINE }
    return CalendarDay(
        date = date,
        inMonth = inMonth,
        marks = events.map {
            CalendarMark(
                when (it.kind) {
                    CalendarEventKind.DEADLINE -> CalendarMarkKind.DEADLINE
                    CalendarEventKind.START -> CalendarMarkKind.START
                    CalendarEventKind.DONE -> CalendarMarkKind.DONE
                    CalendarEventKind.FOCUS -> CalendarMarkKind.FOCUS
                },
                MissionTones.of(it.theme).accentStrong,
            )
        },
        deadlineColor = deadline?.let { MissionTones.of(it.theme).accentStrong },
        enabled = enabled,
        label = label,
    )
}

