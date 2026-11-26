package io.github.illiapohukai.focusmode.feature.result

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.EmptyState
import io.github.illiapohukai.focusmode.core.designsystem.component.Eyebrow
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusTopBar
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SecondaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeBadge
import io.github.illiapohukai.focusmode.core.model.SessionOutcome

data class ResultActions(
    val onDone: () -> Unit,
    val onHistory: () -> Unit,
    val onAgain: () -> Unit,
    val onOpenMission: (String) -> Unit = {},
)

/** User actions of the Mission review and the optional adaptation. */
data class ResultCallbacks(
    val onOutcome: (SessionOutcome) -> Unit = {},
    val onRating: (Int) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onAdapt: () -> Unit = {},
    val onRetryAdapt: () -> Unit = {},
    val onSkipAdapt: () -> Unit = {},
)

@Composable
fun ResultRoute(actions: ResultActions, viewModel: ResultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultScreen(
        state, actions,
        ResultCallbacks(viewModel::selectOutcome, viewModel::setRating, viewModel::setNote, viewModel::submit, viewModel::submitAndAdapt, viewModel::retryAdaptation, viewModel::skipAdaptation),
    )
}

@Composable
fun ResultScreen(state: ResultUiState, actions: ResultActions, callbacks: ResultCallbacks) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal).testTag("result")) {
            FocusTopBar(onBack = actions.onDone)
            when {
                state.loading -> Unit
                state.notFound -> EmptyState(FocusIcons.Info, "Ergebnis nicht gefunden", "Diese Sitzung ist nicht (mehr) im Verlauf.") {
                    PrimaryButton("Zur Startseite", actions.onDone)
                }
                else -> Content(state, actions, callbacks)
            }
        }
    }
}

@Composable
private fun Content(state: ResultUiState, actions: ResultActions, callbacks: ResultCallbacks) {
    val tone = state.mission?.let { MissionTones.of(it.theme) } ?: MissionTones.Aubergine
    AubergineSurface(Modifier.fillMaxWidth().testTag("result_summary"), tone = tone, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Restrained green only for a completed Session.
            val dot = when (state.badge) {
                OutcomeBadge.COMPLETED -> FocusPalette.Green
                OutcomeBadge.PARTIAL -> FocusPalette.Lavender
                else -> FocusPalette.StarlightMuted
            }
            Box(Modifier.size(8.dp).clip(Radii.pill).background(dot))
            Spacer(Modifier.width(8.dp))
            val kicker = state.mission?.takeIf { it.stepNumber > 0 }?.let { "Schritt ${it.stepNumber} von ${it.total} · ${it.title}" } ?: state.eyebrow
            Eyebrow(if (state.awaitingReview) kicker else state.eyebrow, color = FocusPalette.HeroKicker)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(state.headline, style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${state.minutes} von ${state.plannedMinutes} Minuten fokussiert" }) {
            Text("${state.minutes}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = Color.White)
            Spacer(Modifier.width(Spacing.xs))
            Text("von ${state.plannedMinutes} Min.", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Lavender, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(state.task, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(state.timeRange, style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroCopy)
        state.endNote?.let { Text("Grund: $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender) }
        if (!state.countsAsFocus) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Unter einer Minute oder abgebrochen – zählt nicht als Fokuszeit.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
        }
    }
    Spacer(Modifier.height(Spacing.md))
    when {
        state.awaitingReview -> {
            Review(state, callbacks)
            Spacer(Modifier.height(Spacing.md))
        }
        state.adaptation != null -> {
            AdaptationCard(state.adaptation, state, callbacks)
            Spacer(Modifier.height(Spacing.md))
        }
        state.mission != null -> {
            MissionProgressCard(state)
            Spacer(Modifier.height(Spacing.md))
        }
    }
    FocusCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Flame)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.streak.current == 1) "1 Tag in Folge" else "${state.streak.current} Tage in Folge",
                    style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite,
                )
                Text("Beste Serie: ${state.streak.best} · lokal berechnet, nicht verifiziert", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
            }
        }
        if (state.imported) {
            Text("Aus einem Web-Backup übernommen.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    if (!state.awaitingReview && state.adaptation !is AdaptationUi.Running) {
        val missionId = state.mission?.missionId
        if (missionId != null) {
            PrimaryButton("Zur Mission", { actions.onOpenMission(missionId) }, Modifier.fillMaxWidth().testTag("result_open_mission"), icon = FocusIcons.Target)
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Fertig", actions.onDone, Modifier.fillMaxWidth().testTag("result_done"))
        } else {
            PrimaryButton("Fertig", actions.onDone, Modifier.fillMaxWidth().testTag("result_done"))
            Spacer(Modifier.height(Spacing.xs))
            // Stacked so neither label wraps at 320 dp or large font scales.
            SecondaryButton("Noch eine Runde", actions.onAgain, Modifier.fillMaxWidth(), icon = FocusIcons.Play)
        }
        Spacer(Modifier.height(Spacing.xs))
        SecondaryButton("Verlauf", actions.onHistory, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(Spacing.xl))
}

/** PWA rating labels (`CONFIDENCE_RATING_STATES`). */
internal val RATING_LABELS = mapOf(1 to "Gar nichts verstanden", 2 to "Sehr wenig verstanden", 3 to "Mittel verstanden", 4 to "Ziemlich gut verstanden", 5 to "Vollständig verstanden")

private data class OutcomeOption(val outcome: SessionOutcome, val title: String, val sub: String, val tag: String)

private val OUTCOMES = listOf(
    OutcomeOption(SessionOutcome.COMPLETED, "Abgeschlossen", "Schritt erledigt", "review_completed"),
    OutcomeOption(SessionOutcome.PARTIAL, "Teilweise", "Später weiter", "review_partial"),
    OutcomeOption(SessionOutcome.CANCELED, "Abgebrochen", "Neu ansetzen", "review_aborted"),
)

/** PWA session review: three outcomes, a 1–5 confidence scale, an optional note, then the actions. */
@Composable
private fun Review(state: ResultUiState, callbacks: ResultCallbacks) {
    var noteOpen by rememberSaveable { mutableStateOf(state.note.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().testTag("mission_review"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Bewerte das Ergebnis ehrlich. Früher fertig zu sein ist völlig in Ordnung.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        Column {
            Text("Wie weit bist du gekommen?", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OUTCOMES.forEach { o ->
                    val on = state.selectedOutcome == o.outcome
                    Column(
                        Modifier.weight(1f).heightIn(min = 76.dp).clip(Radii.lg)
                            .background(if (on) FocusPalette.PurpleTint else FocusPalette.Surface)
                            .border(BorderStroke(if (on) 2.dp else 1.dp, if (on) FocusPalette.Purple else FocusPalette.Outline), Radii.lg)
                            .clickable(enabled = !state.reviewSaving, role = Role.RadioButton) { callbacks.onOutcome(o.outcome) }
                            .semantics(mergeDescendants = true) { selected = on }
                            .testTag(o.tag)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            when (o.outcome) { SessionOutcome.COMPLETED -> FocusIcons.Check; SessionOutcome.PARTIAL -> FocusIcons.Clock; SessionOutcome.CANCELED -> FocusIcons.Close },
                            null, tint = if (on) FocusPalette.Purple else FocusPalette.GraphiteSoft, modifier = Modifier.size(18.dp),
                        )
                        // One word per label ("Abgeschlossen"): shrink to fit instead of truncating (as in History).
                        BasicText(
                            o.title, style = MaterialTheme.typography.labelMedium.copy(color = FocusPalette.Graphite, textAlign = TextAlign.Center), maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize),
                        )
                        Text(o.sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        state.mission?.let { m ->
            val line = when (state.selectedOutcome) {
                SessionOutcome.COMPLETED -> if (m.completed + 1 >= m.total) "Danach: Alle Schritte dieser Mission sind erledigt." else "Mission: ${m.completed} → ${m.completed + 1} von ${m.total} Schritten"
                SessionOutcome.PARTIAL -> "Dieser Schritt bleibt offen. Dein Fortschritt wird gespeichert, du kannst ihn erneut starten."
                SessionOutcome.CANCELED -> "Abgebrochen · Fortschritt bleibt. Der Schritt bleibt offen."
            }
            Text(line, style = MaterialTheme.typography.bodySmall, color = FocusPalette.PurpleInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("review_effect"))
        }
        RatingScale(state.rating, !state.reviewSaving, callbacks.onRating)
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radii.md).clickable(role = Role.Button, onClickLabel = "Notiz hinzufügen") { noteOpen = !noteOpen }
                    .semantics { stateDescription = if (noteOpen) "geöffnet" else "geschlossen" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FocusIcons.Pencil, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.weight(1f)) {
                    Text("Notiz hinzufügen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
                    Text("Was war schwierig oder ist noch unklar?", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            }
            if (noteOpen) {
                Box(Modifier.fillMaxWidth().clip(Radii.md).background(FocusPalette.Surface).border(BorderStroke(1.dp, FocusPalette.Outline), Radii.md).padding(Spacing.sm)) {
                    if (state.note.isEmpty()) Text("Zum Beispiel: Die Begriffe kann ich erklären, bei den Fällen bin ich noch unsicher.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft.copy(alpha = 0.7f))
                    BasicTextField(
                        state.note, callbacks.onNote, enabled = !state.reviewSaving,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = FocusPalette.Graphite), cursorBrush = SolidColor(FocusPalette.Purple),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("review_note").semantics { contentDescription = "Was war schwierig oder ist noch unklar?" },
                    )
                }
            }
        }
        if (state.canOfferAdaptation) {
            Row(
                Modifier.fillMaxWidth().clip(Radii.lg).background(FocusPalette.PurpleTint).border(BorderStroke(1.dp, FocusPalette.Purple.copy(alpha = 0.3f)), Radii.lg)
                    .clickable(enabled = !state.reviewSaving, role = Role.Button, onClick = callbacks.onAdapt)
                    .heightIn(min = 64.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("review_adapt"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FocusIcons.Wand, null, tint = FocusPalette.Purple, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("Plan mit AI anpassen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.PurpleInk)
                    Text("Optional · passt nur offene Schritte dieser Mission an", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
                Icon(FocusIcons.ChevronRight, null, tint = FocusPalette.Purple, modifier = Modifier.size(18.dp))
            }
        }
        Column {
            PrimaryButton("Ohne Anpassung fortfahren", callbacks.onSubmit, Modifier.fillMaxWidth().testTag("review_submit"), enabled = !state.reviewSaving, icon = FocusIcons.ArrowRight)
            Text("Speichert dein Ergebnis. Der Plan bleibt unverändert.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/** PWA `sr-scale`: a 1–5 slider with stops; adjustable for TalkBack and keyboards. */
@Composable
private fun RatingScale(value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wie sicher fühlst du dich jetzt?", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Text(RATING_LABELS[value] ?: "", style = MaterialTheme.typography.bodySmall, color = FocusPalette.PurpleInk)
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth().testTag("review_rating").semantics(mergeDescendants = true) {
                contentDescription = "Wie sicher fühlst du dich jetzt?"
                stateDescription = "$value von 5 · ${RATING_LABELS[value]}"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 1f..5f, steps = 3)
                setProgress { target -> val v = Math.round(target).coerceIn(1, 5); if (v != value) onChange(v); true }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..5).forEach { r ->
                val on = r <= value
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(Radii.sm)
                        .clickable(enabled = enabled, role = Role.Button) { onChange(r) }
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(Radii.pill).background(if (on) FocusPalette.Purple else FocusPalette.SurfaceMuted))
                    if (r == value) Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White).border(BorderStroke(3.dp, FocusPalette.Purple), CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            Text("Kaum verstanden", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.GraphiteSoft, modifier = Modifier.weight(1f))
            Text("Vollständig", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = FocusPalette.GraphiteSoft)
        }
    }
}

/** After an explicit adaptation request: processing, updated plan, or a safe failure. */
@Composable
private fun AdaptationCard(adaptation: AdaptationUi, state: ResultUiState, callbacks: ResultCallbacks) {
    FocusCard(Modifier.fillMaxWidth().testTag("result_adaptation"), contentPadding = PaddingValues(Spacing.md)) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            when (adaptation) {
                AdaptationUi.Running -> {
                    Eyebrow("Feedback wird ausgewertet", color = FocusPalette.PurpleInk)
                    Text("Plan wird angepasst …", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                    Text("Nur die offenen Schritte dieser Mission werden angepasst. Dein Ergebnis ist bereits gespeichert.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
                is AdaptationUi.Adapted -> {
                    Eyebrow("Mission aktualisiert", color = FocusPalette.PurpleInk)
                    Text("Deine Mission passt sich deinem Ergebnis an.", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                    Text(adaptation.message.ifBlank { "Dein Feedback wurde ausgewertet und die offenen Schritte wurden aktualisiert." }, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    if (adaptation.summary.isNotBlank()) Text(adaptation.summary, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Graphite, modifier = Modifier.padding(top = 4.dp))
                }
                is AdaptationUi.Failed -> {
                    Eyebrow(if (adaptation.offline) "Keine Internetverbindung" else "Anpassung nicht verfügbar", color = FocusPalette.Danger)
                    Text("Dein Fortschritt ist trotzdem sicher.", style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
                    Text("Dein Ergebnis wurde gespeichert und dein bisheriger Plan bleibt unverändert.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            }
        }
        state.mission?.nextTitle?.takeIf { adaptation !is AdaptationUi.Running }?.let { next ->
            Spacer(Modifier.height(Spacing.sm))
            Text("NÄCHSTER SCHRITT", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
            Text(next + (state.mission.nextMinutes?.let { " · $it Min." } ?: ""), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
        }
        Spacer(Modifier.height(Spacing.sm))
        when (adaptation) {
            AdaptationUi.Running -> TextAction("Ohne Anpassung fortfahren", callbacks.onSkipAdapt, Modifier.testTag("adapt_skip"))
            is AdaptationUi.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextAction("Erneut versuchen", callbacks.onRetryAdapt, Modifier.testTag("adapt_retry"))
                TextAction("Ohne Anpassung fortfahren", callbacks.onSkipAdapt, Modifier.testTag("adapt_skip"), color = FocusPalette.GraphiteSoft)
            }
            is AdaptationUi.Adapted -> Unit
        }
    }
}

@Composable
private fun MissionProgressCard(state: ResultUiState) {
    val m = state.mission ?: return
    val tone = MissionTones.of(m.theme)
    FocusCard(
        Modifier.fillMaxWidth().testTag("result_mission_progress").semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(m.percent / 100f, 0f..1f)
        },
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Eyebrow(if (m.missionCompleted) "Mission abgeschlossen" else "Mission · ${m.percent} %", color = tone.ink)
        Text(m.title, style = MaterialTheme.typography.titleMedium, color = FocusPalette.Graphite)
        Text("${m.completed} von ${m.total} Schritten erledigt", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(m.total) { i -> Box(Modifier.weight(1f).height(6.dp).clip(Radii.pill).background(if (i < m.completed) tone.accentStrong else FocusPalette.SurfaceMuted)) }
        }
        if (!m.missionCompleted && m.nextTitle != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text("ALS NÄCHSTES", style = MaterialTheme.typography.labelSmall, color = FocusPalette.GraphiteSoft)
            Text(m.nextTitle + (m.nextMinutes?.let { " · $it Min." } ?: ""), style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite)
        }
        state.reviewNote?.let { Text("Notiz: $it", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, modifier = Modifier.padding(top = Spacing.xs)) }
    }
}
