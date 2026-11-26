package io.github.illiapohukai.focusmode.feature.missions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionRules
import io.github.illiapohukai.focusmode.core.domain.mission.MissionSchedulePlanner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionThemes
import io.github.illiapohukai.focusmode.core.domain.mission.ScheduleDraft
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class BriefingUiState(
    val loading: Boolean = true,
    val plan: GeneratedMissionPlan? = null,
    val type: MissionType? = null,
    val confidence: GenerationConfidence? = null,
    val schedule: ScheduleDraft = ScheduleDraft(),
    val theme: MissionTheme = MissionTheme.AUBERGINE,
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    val activeCount: Int = 0,
    val accepting: Boolean = false,
    /** A photo was sent with the request (PWA `hasMissionImageAttached`). */
    val hadPhoto: Boolean = false,
) {
    val limitReached: Boolean get() = !MissionRules.canCreateActive(activeCount)
    val totalMinutes: Int get() = plan?.let { p -> p.estimateTotalMinutes.takeIf { it > 0 } ?: p.steps.sumOf { it.durationMinutes } } ?: 0
}

sealed interface BriefingEvent {
    data class Accepted(val missionId: String) : BriefingEvent
    data object Discarded : BriefingEvent
    data object Missing : BriefingEvent
    data class Message(val text: String) : BriefingEvent
}

/**
 * Mission Briefing (PWA v17.7 `renderAIConfirmation`): presents the validated plan without
 * changing it. The Mission does not exist yet; "Mission übernehmen" creates exactly one through
 * the generation owner (one transaction, active limit checked inside it).
 */
@HiltViewModel
class MissionBriefingViewModel @Inject constructor(
    missions: MissionRepository,
    private val owner: MissionGenerationOwner,
    private val clock: FocusClock,
) : ViewModel() {
    private val accepting = MutableStateFlow(false)
    private val _events = MutableSharedFlow<BriefingEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<BriefingEvent> = _events

    val uiState: StateFlow<BriefingUiState> = combine(missions.observeDraft(), missions.observeMissions(), accepting) { draft, all, busy ->
        val plan = draft?.plan?.takeIf { draft.phase == MissionDraftPhase.BRIEFING }
        if (draft == null || plan == null) {
            if (!busy) _events.tryEmit(BriefingEvent.Missing)
            return@combine BriefingUiState(loading = false, accepting = busy)
        }
        BriefingUiState(
            loading = false,
            plan = plan,
            type = draft.type,
            confidence = draft.confidence,
            schedule = draft.schedule,
            theme = MissionThemes.preview(all, draft.missionId, clock.now()),
            today = clock.today(),
            hadPhoto = draft.attachment != null,
            activeCount = all.count { it.status == MissionStatus.ACTIVE },
            accepting = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BriefingUiState())

    fun accept() {
        if (accepting.value) return
        accepting.value = true
        viewModelScope.launch {
            when (val r = owner.accept()) {
                is MissionWriteResult.Ok -> _events.emit(BriefingEvent.Accepted(r.mission.id.value))
                is MissionWriteResult.Rejected -> {
                    accepting.value = false
                    _events.emit(
                        BriefingEvent.Message(
                            if (r.reason == "active_mission_limit_reached") "Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie."
                            else "Die Mission konnte nicht gespeichert werden. Bitte versuche es erneut.",
                        ),
                    )
                }
            }
        }
    }

    fun discard() {
        accepting.value = true
        viewModelScope.launch {
            owner.discard()
            _events.emit(BriefingEvent.Discarded)
        }
    }
}

@Composable
fun MissionBriefingRoute(actions: MissionFlowActions, snackbar: SnackbarHostState, viewModel: MissionBriefingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var left by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is BriefingEvent.Accepted -> if (!left) { left = true; actions.onAccepted(e.missionId) }
                BriefingEvent.Discarded -> if (!left) { left = true; actions.onDiscarded() }
                BriefingEvent.Missing -> if (!left) { left = true; actions.onBack() }
                is BriefingEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    MissionBriefingScreen(state, onBack = actions.onBack, onAccept = viewModel::accept, onEdit = actions.onBack, onDiscard = viewModel::discard, onOpenMissions = actions.onOpenMissions)
}

private val BRIEF_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
private val BRIEF_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

/** PWA `formatMinutes`: "2h 5m" or "45 Min.". */
internal fun formatTotal(total: Int): String {
    val m = total.coerceAtLeast(0)
    val h = m / 60
    val r = m % 60
    return if (h > 0) "${h}h" + (if (r > 0) " ${r}m" else "") else "$r Min."
}

/** PWA `briefDifficulty`. */
internal fun difficultyLabel(raw: String?): String? = when (raw?.lowercase()) {
    "easy", "low" -> "Leicht"
    "medium", "moderate" -> "Mittel"
    "hard", "high", "difficult" -> "Anspruchsvoll"
    else -> null
}

internal fun typeKicker(type: MissionType): String = when (type) {
    MissionType.EXAM -> "Prüfung"
    MissionType.ASSIGNMENT -> "Aufgabe"
    MissionType.PROJECT -> "Projekt"
    MissionType.CUSTOM -> "Mission"
}

@Composable
fun MissionBriefingScreen(
    state: BriefingUiState,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
    onOpenMissions: () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val plan = state.plan
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            FlowTopBar("Mission-Briefing", onBack)
            if (plan == null) return@Column
            val tone = MissionTones.of(state.theme)
            val deadline = state.schedule.deadline
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).testTag("mission_briefing"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column {
                    Text("Dein Plan ist bereit.", style = MaterialTheme.typography.headlineLarge, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Prüfe kurz, was erkannt wurde. Du kannst die Angaben vor dem Start noch ändern.", style = MaterialTheme.typography.bodyMedium, color = FocusPalette.GraphiteSoft)
                }
                // Hero + key summary
                AubergineSurface(Modifier.fillMaxWidth().testTag("briefing_hero"), tone = tone, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(FocusPalette.Green))
                        Spacer(Modifier.width(8.dp))
                        Text("NEUE MISSION · ${typeKicker(plan.type).uppercase()}", style = MaterialTheme.typography.labelSmall, color = FocusPalette.HeroKicker, modifier = Modifier.weight(1f))
                        deadline?.let {
                            Row(Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(FocusIcons.Flag, null, tint = tone.accent, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(BRIEF_DATE.format(it), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = tone.accent)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(plan.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
                    if (plan.subject.isNotBlank() && plan.subject != "Allgemein") Text(plan.subject, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = FocusPalette.HeroCopy)
                    if (plan.explanation.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(plan.explanation, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SummaryItem("${plan.steps.size}", if (plan.steps.size == 1) "Sitzung" else "Sitzungen", Modifier.weight(1f))
                        if (state.totalMinutes > 0) SummaryItem(formatTotal(state.totalMinutes), "geschätzt", Modifier.weight(1f))
                        difficultyLabel(plan.difficulty)?.let { SummaryItem(it, "Niveau", Modifier.weight(1f)) }
                    }
                }
                if (deadline != null) BriefingRoute(state, deadline)
                // PWA "Noch unklar": the schedule warning plus the Worker's `missingInformation`.
                val unclear = listOfNotNull(plan.scheduleWarning) + plan.source.missingInformation
                if (unclear.isNotEmpty()) {
                    FocusCard(Modifier.fillMaxWidth().testTag("briefing_unclear"), color = Color(0xFFFFF7EB), contentPadding = PaddingValues(Spacing.md)) {
                        Text("Noch unklar", style = MaterialTheme.typography.titleSmall, color = FocusPalette.AmberInk, modifier = Modifier.semantics { heading() })
                        unclear.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite) }
                        Text("Ergänze es über „Angaben ändern“ oder passe den Plan später an.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Deine Sitzungen", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
                    Text("${plan.steps.size} Schritte", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                }
                Column(Modifier.semantics { collectionInfo = CollectionInfo(plan.steps.size, 1) }, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    plan.steps.forEachIndexed { i, s -> BriefingStep(i, s, tone.accentStrong) }
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(FocusPalette.SurfaceMuted), contentAlignment = Alignment.Center) {
                            Icon(FocusIcons.Flag, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(deadline?.let { "Bereit bis ${BRIEF_SHORT.format(it)}" } ?: "Mission abgeschlossen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                    }
                }
                SourceCard(state)
                if (state.limitReached) {
                    FocusCard(Modifier.fillMaxWidth().testTag("briefing_limit"), color = FocusPalette.PurpleTint, contentPadding = PaddingValues(Spacing.md)) {
                        Text("Zwei aktive Missionen erreicht", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                        Text("Schließe eine Mission ab oder archiviere sie. Dieser Plan bleibt als Entwurf erhalten.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                        TextAction("Missionen verwalten", onOpenMissions)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal).padding(top = Spacing.xs, bottom = Spacing.sm).navigationBarsPadding()) {
                PrimaryButton("Mission übernehmen", onAccept, Modifier.fillMaxWidth().testTag("briefing_accept"), enabled = !state.accepting && !state.limitReached, icon = FocusIcons.ArrowRight)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SecondaryButton("Angaben ändern", onEdit, Modifier.weight(1f).testTag("briefing_edit"), enabled = !state.accepting, icon = FocusIcons.Pencil)
                    SecondaryButton("Verwerfen", { confirmDiscard = true }, Modifier.weight(1f).testTag("briefing_discard"), enabled = !state.accepting, icon = FocusIcons.Trash)
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Entwurf verwerfen?") },
            text = { Text("Der erzeugte Plan, deine Eingaben und das vorbereitete Foto werden gelöscht. Es wird keine Mission angelegt.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }, Modifier.testTag("briefing_discard_confirm")) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Behalten") } },
        )
    }
}

@Composable
private fun SummaryItem(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(Radii.md).background(Color.White.copy(alpha = 0.07f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), Radii.md).padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.HeroCopy, maxLines = 1)
    }
}

@Composable
private fun BriefingRoute(state: BriefingUiState, deadline: LocalDate) {
    val start = state.schedule.preparationStart ?: state.today
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, deadline).coerceAtLeast(0)
    val rhythm = listOfNotNull(
        state.schedule.preferredWeekdays.takeIf { it.isNotEmpty() }?.let(MissionSchedulePlanner::weekdayLabel),
        state.schedule.preferredSessionMinutes?.let { "$it Min. pro Sitzung" },
    ).joinToString(" · ").ifEmpty { "Flexibler Lernrhythmus" }
    FocusCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("briefing_route"), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Start", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
                Text(if (start == state.today) "Heute" else BRIEF_SHORT.format(start), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
            }
            Box(Modifier.weight(1f).padding(horizontal = Spacing.sm), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(FocusPalette.Purple.copy(alpha = 0.25f)))
                Text(if (days == 1L) "1 Tag" else "$days Tage", style = MaterialTheme.typography.labelMedium, color = FocusPalette.PurpleInk, modifier = Modifier.background(FocusPalette.Surface).padding(horizontal = 6.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (state.plan?.type == MissionType.EXAM) "Prüfung" else "Ziel", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
                Text(BRIEF_SHORT.format(deadline), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FocusIcons.Calendar, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(rhythm, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        }
    }
}

@Composable
private fun BriefingStep(index: Int, step: io.github.illiapohukai.focusmode.core.domain.ai.GeneratedStep, accent: Color) {
    var open by rememberSaveable(index) { mutableStateOf(false) }
    val first = index == 0
    FocusCard(
        Modifier.fillMaxWidth().semantics { collectionItemInfo = CollectionItemInfo(index, 1, 0, 1) }.testTag("briefing_step_$index"),
        color = if (first) FocusPalette.PurpleTint else FocusPalette.Surface,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (first) accent else FocusPalette.SurfaceMuted).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (first) Color.White else FocusPalette.GraphiteSoft)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Schritt ${index + 1}: ${step.title}, ${step.durationMinutes} Minuten" + if (first) ", erster Schritt" else "" }) {
                if (first) Text("ERSTER SCHRITT", style = MaterialTheme.typography.labelSmall, color = FocusPalette.PurpleInk)
                Row(verticalAlignment = Alignment.Top) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("${step.durationMinutes} Min.", style = MaterialTheme.typography.labelMedium, color = FocusPalette.GraphiteSoft)
                }
                if (step.task.isNotBlank()) Text(step.task, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                step.scheduledDate?.let { Text(BRIEF_DATE.format(it), style = MaterialTheme.typography.bodySmall, color = FocusPalette.PurpleInk) }
            }
        }
        if (step.reason.isNotBlank() || step.requiredTools.isNotEmpty() || step.recommendedApps.isNotEmpty()) {
            Row(
                Modifier.padding(start = 40.dp).heightIn(min = 44.dp).clip(Radii.sm).clickable(role = Role.Button, onClickLabel = "Begründung anzeigen") { open = !open }
                    .semantics { stateDescription = if (open) "geöffnet" else "geschlossen" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Warum so?", style = MaterialTheme.typography.labelMedium, color = FocusPalette.Purple)
                Icon(FocusIcons.ChevronDown, null, tint = FocusPalette.Purple, modifier = Modifier.size(14.dp))
            }
            if (open) {
                Column(Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (step.reason.isNotBlank()) Text(step.reason, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite)
                    if (step.requiredTools.isNotEmpty()) StepTags("Brauchst du", step.requiredTools)
                    // Text only: recommended apps are never selected, opened or blocked.
                    if (step.recommendedApps.isNotEmpty()) StepTags("Empfohlene Apps", step.recommendedApps)
                }
            }
        }
    }
}

/** PWA "So wurde der Plan erstellt": what came from the user and what is an estimate. */
@Composable
private fun SourceCard(state: BriefingUiState) {
    var open by rememberSaveable { mutableStateOf(false) }
    val plan = state.plan ?: return
    val user = listOfNotNull(
        state.type?.let { "Mission-Typ: ${it.label}" },
        state.confidence?.let { "Deine Sicherheit: ${it.label}" },
        state.schedule.deadline?.let { "Termin: ${BRIEF_DATE.format(it)}" },
        state.schedule.preferredWeekdays.takeIf { it.isNotEmpty() }?.let { "Lerntage: ${MissionSchedulePlanner.weekdayLabel(it)}" },
        state.schedule.preferredSessionMinutes?.let { "Sitzungsdauer: $it Min." },
    )
    val recognized = plan.source.recognized
    val estimated = plan.source.estimates + listOfNotNull(
        state.totalMinutes.takeIf { it > 0 }?.let { "Gesamtaufwand ca. ${formatTotal(it)}" },
        plan.explanation.takeIf { it.isNotBlank() },
    )
    FocusCard(Modifier.fillMaxWidth().testTag("briefing_source"), contentPadding = PaddingValues(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Radii.sm).clickable(role = Role.Button, onClickLabel = "So wurde der Plan erstellt") { open = !open }
                .semantics { stateDescription = if (open) "geöffnet" else "geschlossen" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("So wurde der Plan erstellt", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                Text(
                    plan.source.confidenceLabel?.let { "AI-Erkennung: $it" } ?: "Angaben, Erkanntes und Schätzungen",
                    style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft,
                )
            }
            Icon(if (open) FocusIcons.ChevronDown else FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp))
        }
        if (open) {
            if (user.isNotEmpty()) SourceGroup("Aus deinen Angaben", user)
            when {
                // PWA: a photo whose content the AI could not read is said plainly, never invented.
                state.hadPhoto && recognized.isEmpty() -> SourceGroup("Material", listOf("Im Foto wurde nichts eindeutig erkannt. Der Plan stützt sich auf deine Beschreibung."))
                recognized.isNotEmpty() -> SourceGroup(if (state.hadPhoto) "Aus dem Material erkannt" else "Aus deinem Ziel erkannt", recognized)
            }
            if (estimated.isNotEmpty()) SourceGroup("Geschätzt", estimated)
            Text("Zeiten und Umfang sind Schätzungen und werden nach deinen Sitzungen genauer.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/** PWA `mb-step-tags`: a small label and wrapped text chips. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StepTags(label: String, items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = FocusPalette.GraphiteSoft)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach {
                Text(
                    it, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite,
                    modifier = Modifier.clip(Radii.pill).background(FocusPalette.SurfaceMuted).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceGroup(title: String, items: List<String>) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite, modifier = Modifier.padding(top = Spacing.xs))
    items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft) }
}

