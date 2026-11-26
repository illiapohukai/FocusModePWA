package io.github.illiapohukai.focusmode.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.illiapohukai.focusmode.core.designsystem.component.AubergineSurface
import io.github.illiapohukai.focusmode.core.designsystem.component.FocusCard
import io.github.illiapohukai.focusmode.core.designsystem.component.IconTile
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryGradient
import io.github.illiapohukai.focusmode.core.designsystem.component.PillTone
import io.github.illiapohukai.focusmode.core.designsystem.component.PrimaryButton
import io.github.illiapohukai.focusmode.core.designsystem.component.ReadableWidth
import io.github.illiapohukai.focusmode.core.designsystem.component.SectionHeader
import io.github.illiapohukai.focusmode.core.designsystem.component.StatusPill
import io.github.illiapohukai.focusmode.core.designsystem.component.TextAction
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.LabelType
import io.github.illiapohukai.focusmode.core.designsystem.theme.MissionTones
import io.github.illiapohukai.focusmode.core.designsystem.theme.Radii
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.history.HistoryQueries
import io.github.illiapohukai.focusmode.core.domain.history.OutcomeNode
import io.github.illiapohukai.focusmode.core.designsystem.component.OutcomeColors
import io.github.illiapohukai.focusmode.core.designsystem.component.OutcomeDot
import io.github.illiapohukai.focusmode.core.designsystem.component.isTextCompact
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarDayInfo
import io.github.illiapohukai.focusmode.core.domain.mission.CalendarEventKind
import io.github.illiapohukai.focusmode.core.domain.mission.HomeMissionMode
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarDay
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarMark
import io.github.illiapohukai.focusmode.core.designsystem.component.CalendarMarkKind
import io.github.illiapohukai.focusmode.core.designsystem.component.MonthCalendar
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo

data class HomeActions(
    val onQuickFocus: () -> Unit,
    val onOpenSession: () -> Unit,
    val onOpenMission: (String) -> Unit,
    val onOpenMissions: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenResult: (String) -> Unit,
    val onOpenSchedules: () -> Unit,
    val onCreateMission: () -> Unit = onOpenMissions,
)

/** Home Mission calendar interactions (state lives in [HomeViewModel]). */
data class HomeCalendarCallbacks(
    val onSelect: (java.time.LocalDate) -> Unit = {},
    val onShift: (Long) -> Unit = {},
    val onToday: () -> Unit = {},
)

@Composable
fun HomeRoute(actions: HomeActions, contentPadding: PaddingValues, snackbar: SnackbarHostState, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                HomeEvent.OpenSession -> actions.onOpenSession()
                is HomeEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    HomeScreen(state, actions, viewModel::startMissionStep, contentPadding, HomeCalendarCallbacks(viewModel::selectDate, viewModel::shiftMonth, viewModel::calendarToday))
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    onStartStep: (String, String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    calendar: HomeCalendarCallbacks = HomeCalendarCallbacks(),
) {
    ReadableWidth(Modifier.fillMaxSize().background(FocusPalette.Cream)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("home"),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp,
                top = contentPadding.calculateTopPadding() + Spacing.md, bottom = contentPadding.calculateBottomPadding() + Spacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item { Header(state) }
            state.banner?.let { banner -> item { Banner(banner, actions) } }
            item { MissionSection(state, actions, onStartStep) }
            item { SectionHeader("Quick Focus", Modifier.padding(top = Spacing.xs)) }
            item { QuickFocusRow(state.quickMinutes, actions.onQuickFocus) }
            item {
                SectionHeader("Heute", Modifier.padding(top = Spacing.xs)) {
                    Text("Woche: ${state.progress.weekSessions} ${if (state.progress.weekSessions == 1) "Sitzung" else "Sitzungen"} · ${HistoryQueries.formatMinutes(state.progress.weekMinutes)}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                }
            }
            item { TodayCard(state, actions.onOpenHistory) }
            item {
                SectionHeader("Missionskalender", Modifier.padding(top = Spacing.xs)) {
                    if (!state.calendar.empty && (state.calendar.selected != state.calendar.today || state.calendar.month != java.time.YearMonth.from(state.calendar.today))) {
                        TextAction("Heute", calendar.onToday, Modifier.testTag("home_calendar_today"))
                    }
                }
            }
            item { MissionCalendarCard(state.calendar, actions, calendar) }
            item { SectionHeader("Erinnerungen", Modifier.padding(top = Spacing.xs)) }
            item { RemindersCard(state, actions.onOpenSchedules) }
            item { SectionHeader("Aktivität", Modifier.padding(top = Spacing.xs)) { TextAction("Alle anzeigen", actions.onOpenHistory) } }
            item { ActivityTimeline(state.activity, actions.onOpenResult) }
        }
    }
}

/**
 * PWA `.home-header`: slate date over the heavy brand title, the readiness pill on the right. On
 * phones the pill shares the date line so the brand title keeps one line at 360 dp.
 */
@Composable
private fun Header(state: HomeUiState) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.dateLabel, style = LabelType.Date, color = FocusPalette.DateInk, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.xs))
            StatusPill(
                if (state.online) "Bereit" else "Offline",
                tone = PillTone.SURFACE,
                dot = if (state.online) FocusPalette.Green else FocusPalette.GraphiteSoft,
                modifier = Modifier.semantics { contentDescription = if (state.online) "Bereit. AI-Planung erreichbar." else "Offline. Fokus funktioniert ohne Internet." },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("Focus Mode+", style = LabelType.Brand, color = FocusPalette.Ink, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Banner(banner: SessionBanner, actions: HomeActions) {
    val (title, body, action) = when (banner) {
        is SessionBanner.Running -> Triple("Fokus läuft", "Noch ${banner.remainingMinutes} Min. · ${banner.task}", actions.onOpenSession)
        SessionBanner.Frozen -> Triple("Sitzung angehalten", "Fortsetzen, speichern oder verwerfen.", actions.onOpenSession)
        is SessionBanner.Review -> Triple("Ergebnis offen", "Bewerte deinen Missionsschritt.", { actions.onOpenResult(banner.sessionId) })
    }
    AubergineSurface(Modifier.fillMaxWidth().testTag("home_session_banner"), onClick = action, onClickLabel = "Sitzung öffnen", contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(Radii.pill).background(FocusPalette.Green))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(body, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Lavender, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(FocusIcons.ChevronRight, null, tint = Color.White)
        }
    }
}

/**
 * PWA hero kicker: live dot, uppercase label, optional outlined chip on the right. With large
 * fonts on narrow screens the chip wraps below the label instead of squeezing it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroKicker(label: String, chip: String?, accent: Color) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.padding(end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(Radii.pill).background(FocusPalette.Green))
            Spacer(Modifier.width(8.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14.em), color = FocusPalette.HeroKicker, maxLines = 1, softWrap = false)
        }
        chip?.let {
            Text(
                it.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(Radii.pill).background(Color.White.copy(alpha = 0.06f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), Radii.pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** Inner glass card of the hero (PWA `.mh-next`): tile, small label, title, one line of detail. */
@Composable
private fun HeroNextCard(label: String, title: String, detail: String?, accent: Color, onClick: (() -> Unit)?, tile: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Radii.lg).background(Color.White.copy(alpha = 0.06f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), Radii.lg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accent)
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onClick != null) Icon(FocusIcons.ChevronRight, null, tint = Color.White.copy(alpha = 0.72f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MissionSection(state: HomeUiState, actions: HomeActions, onStartStep: (String, String) -> Unit) {
    when (state.mode) {
        HomeMissionMode.FIRST_TIME -> FirstTimeHero(actions)
        HomeMissionMode.RETURNING_EMPTY -> ReturningEmpty(actions)
        HomeMissionMode.ONE -> state.missions.firstOrNull()?.let { MissionHero(it, actions, onStartStep, Modifier.fillMaxWidth().testTag("home_mission")) }
        HomeMissionMode.TWO -> MissionCarousel(state.missions, actions, onStartStep)
    }
}

/** PWA `#emptyMissionCard`: only until the first Mission ever existed. */
@Composable
private fun FirstTimeHero(actions: HomeActions) {
    AubergineSurface(Modifier.fillMaxWidth().testTag("home_mission_empty"), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp)) {
        HeroKicker("Focus Mission", "Bereit", FocusPalette.Lavender)
        Spacer(Modifier.height(Spacing.sm))
        Text("Was möchtest du heute fokussiert schaffen?", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.xs))
        Text("Verwandle eine Prüfung, Hausarbeit oder ein Projekt in klare, machbare Fokus-Schritte.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = FocusPalette.HeroCopy)
        Spacer(Modifier.height(Spacing.md))
        HeroNextCard("Erster Schritt", "Ziel beschreiben oder Material hinzufügen", "Automatische Schrittplanung mit realistischer Dauer", FocusPalette.Lavender, actions.onCreateMission) {
            Column(
                Modifier.size(52.dp).clip(Radii.tile).background(FocusPalette.Surface),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("AI", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = FocusPalette.Purple)
                Text("PLAN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = FocusPalette.GraphiteSoft)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton("Mission erstellen", actions.onCreateMission, Modifier.fillMaxWidth().testTag("home_create_mission"), icon = FocusIcons.Sparkle)
    }
}

/** PWA `#homeReturnEmpty`: a compact, minimal card for a returning user without an active Mission. */
@Composable
private fun ReturningEmpty(actions: HomeActions) {
    AubergineSurface(Modifier.fillMaxWidth().testTag("home_mission_return"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
        HeroKicker("Focus Mission", null, FocusPalette.Lavender)
        Spacer(Modifier.height(Spacing.xs))
        Text("Bereit für ein neues Ziel?", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.semantics { heading() })
        Text("Plane es als Mission oder starte direkt einen Quick Focus.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.HeroCopy)
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.heightIn(min = 48.dp).clip(Radii.pill).background(Color.White.copy(alpha = 0.12f)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), Radii.pill)
                .clickable(role = Role.Button, onClick = actions.onCreateMission).padding(horizontal = 16.dp).testTag("home_create_mission"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FocusIcons.Plus, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Neue Mission", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.width(6.dp))
            Icon(FocusIcons.ArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * One Mission hero (PWA `renderMissionHeroV116`). Hierarchy: kicker and step counter, title,
 * next-step card, then progress on the left and the primary action on the right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionHero(mission: MissionCard, actions: HomeActions, onStartStep: (String, String) -> Unit, modifier: Modifier) {
    val tone = MissionTones.of(mission.theme)
    AubergineSurface(
        modifier, tone = tone, onClick = { actions.onOpenMission(mission.missionId) }, onClickLabel = "Mission ${mission.title} öffnen",
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
    ) {
        HeroKicker(if (mission.primary) "Hauptmission" else "Aktive Mission", mission.stepLabel, tone.accent)
        Spacer(Modifier.height(Spacing.sm))
        Text(mission.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        if (mission.subtitle.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(mission.subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = FocusPalette.HeroCopy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (mission.nextStepTitle != null) {
            Spacer(Modifier.height(Spacing.md))
            HeroNextCard("Nächster Schritt", mission.nextStepTitle, mission.nextStepTask, tone.accent, onClick = null) {
                Column(
                    Modifier.size(52.dp).clip(Radii.tile).background(FocusPalette.Surface),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text("${mission.stepMinutes ?: 0}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = FocusPalette.Ink)
                    Text("MIN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = FocusPalette.GraphiteSoft)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // v17.16: progress reads first (left), the primary action closes the row (right). With large
        // fonts on narrow screens the action wraps below instead of squeezing the progress away.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.padding(end = Spacing.sm).semantics(mergeDescendants = true) {
                    contentDescription = "Fortschritt ${mission.percent} Prozent, ${mission.completedSteps} von ${mission.totalSteps} Schritten erledigt"
                    progressBarRangeInfo = ProgressBarRangeInfo(mission.percent / 100f, 0f..1f)
                }.testTag("home_mission_progress"),
            ) {
                Text("${mission.percent}%", style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1)
                Text("${mission.completedSteps}/${mission.totalSteps} SCHRITTE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = tone.accent, maxLines = 1)
            }
            if (mission.stepId != null) {
                StartStepButton({ onStartStep(mission.missionId, mission.stepId) }, "Schritt starten: ${mission.nextStepTitle}, ${mission.stepMinutes} Minuten")
            } else {
                StartStepButton({ actions.onOpenMission(mission.missionId) }, "Mission öffnen", label = "Mission öffnen", icon = FocusIcons.ArrowRight)
            }
        }
    }
}

/** PWA `.mh-start-btn`: compact gradient pill, filled play, at least 48 dp tall. */
@Composable
private fun StartStepButton(onClick: () -> Unit, description: String, label: String = "Schritt starten", icon: androidx.compose.ui.graphics.vector.ImageVector = FocusIcons.PlayFilled) {
    Row(
        Modifier.heightIn(min = 48.dp).shadow(10.dp, Radii.pill, ambientColor = FocusPalette.Purple.copy(alpha = 0.3f), spotColor = FocusPalette.Purple.copy(alpha = 0.36f))
            .clip(Radii.pill).background(PrimaryGradient)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 18.dp).testTag("home_start_step"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shrinks instead of clipping at large font scales on narrow screens; the icon always stays.
        BasicText(
            label, style = MaterialTheme.typography.labelLarge.copy(color = Color.White), maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/** Two active Missions: a swipeable pager with a peek of the next card (PWA `missionCarouselV116`). */
@Composable
private fun MissionCarousel(missions: List<MissionCard>, actions: HomeActions, onStartStep: (String, String) -> Unit) {
    val pager = rememberPagerState { missions.size }
    Column(Modifier.fillMaxWidth().testTag("home_mission_carousel")) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(end = 28.dp),
            pageSpacing = 10.dp,
            key = { missions[it].missionId },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${missions.size} aktive Missionen, wische für die nächste" },
        ) { page ->
            MissionHero(missions[page], actions, onStartStep, Modifier.fillMaxWidth().testTag("home_mission_page_$page"))
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clearAndSetSemantics { contentDescription = "Mission ${pager.currentPage + 1} von ${missions.size}" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(missions.size) { i ->
                    Box(Modifier.size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp).clip(Radii.pill).background(if (i == pager.currentPage) FocusPalette.Purple else FocusPalette.OutlineStrong))
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            TextAction("Alle ansehen", actions.onOpenMissions, Modifier.testTag("home_mission_more"), color = FocusPalette.GraphiteSoft)
        }
    }
}

/** Home Mission calendar (PWA `renderHomeMissionCalendar`); a compact centred note when empty. */
@Composable
private fun MissionCalendarCard(calendar: HomeCalendar, actions: HomeActions, callbacks: HomeCalendarCallbacks) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_calendar"), contentPadding = PaddingValues(Spacing.md)) {
        if (calendar.empty) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("home_calendar_empty"), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(FocusIcons.Calendar, background = SolidColor(FocusPalette.SurfaceMuted), tint = FocusPalette.GraphiteSoft)
                Spacer(Modifier.height(Spacing.sm))
                Text("Noch keine Missionstermine", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, textAlign = TextAlign.Center)
                Text("Erstelle eine Mission mit Deadline, um Termine hier zu sehen.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
            }
            return@FocusCard
        }
        MonthCalendar(
            month = calendar.month,
            days = calendar.days.map { it.toCell() },
            today = calendar.today,
            focused = calendar.selected,
            onDay = callbacks.onSelect,
            onPrevious = { callbacks.onShift(-1) },
            onNext = { callbacks.onShift(1) },
            subtitle = calendar.nextLine,
        )
        Spacer(Modifier.height(Spacing.sm))
        Column(Modifier.fillMaxWidth().clip(Radii.md).background(FocusPalette.Cream).padding(Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(calendar.selectedTitle, style = MaterialTheme.typography.labelMedium, color = FocusPalette.Graphite)
            if (calendar.selectedEvents.isEmpty()) {
                Text("Keine Missionstermine an diesem Tag.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                calendar.nextDeadline?.let { (date, label) -> TextAction(label, { callbacks.onSelect(date) }, Modifier.testTag("home_calendar_jump")) }
            }
            calendar.selectedEvents.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radii.sm)
                        .clickable(role = Role.Button, onClickLabel = "Mission öffnen") { actions.onOpenMission(e.missionId) }
                        .semantics(mergeDescendants = true) {}.testTag("home_calendar_event"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(Radii.pill).background(MissionTones.of(e.theme).accentStrong))
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.chip} · ${e.detail}", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft)
                    }
                    Icon(FocusIcons.ChevronRight, null, tint = FocusPalette.GraphiteSoft, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Domain day → design-system cell: identity tones become colors here. */
private fun CalendarDayInfo.toCell(): CalendarDay {
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

/** PWA `.home-quick-row`: gradient lightning tile, the prompt, a muted duration chip with an arrow. */
@Composable
private fun QuickFocusRow(minutes: Int, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_quick_focus"), onClick = onClick, onClickLabel = "Quick Focus einrichten", contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(FocusIcons.Zap, Modifier.shadow(8.dp, Radii.tile, ambientColor = FocusPalette.Purple.copy(alpha = 0.25f), spotColor = FocusPalette.Purple.copy(alpha = 0.3f)), background = PrimaryGradient, tint = Color.White)
            Spacer(Modifier.width(Spacing.sm))
            Text("Was möchtest du jetzt erledigen?", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.xs))
            Row(
                Modifier.clip(Radii.sm).background(FocusPalette.SurfaceMuted).border(BorderStroke(1.dp, Color(0x0F2D231E)), Radii.sm).heightIn(min = 32.dp).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$minutes MIN", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 0.04.em), color = FocusPalette.Graphite)
                Spacer(Modifier.width(6.dp))
                Icon(FocusIcons.ArrowRight, null, tint = FocusPalette.Graphite, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** PWA `.home-today`: three centred cells divided by hairlines. */
@Composable
private fun TodayCard(state: HomeUiState, onOpenHistory: () -> Unit) {
    val p = state.progress
    FocusCard(Modifier.fillMaxWidth().testTag("home_today"), onClick = onOpenHistory, onClickLabel = "Verlauf öffnen", contentPadding = PaddingValues(vertical = 16.dp, horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Stat("${p.todayMinutes}m", "Fokus", "${p.todayMinutes} Minuten Fokus heute", Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight(0.7f), color = FocusPalette.Outline)
            Stat("${p.todaySessions}", if (p.todaySessions == 1) "Sitzung" else "Sitzungen", null, Modifier.weight(1f))
            VerticalDivider(Modifier.fillMaxHeight(0.7f), color = FocusPalette.Outline)
            Stat("${p.streak.current}", if (p.streak.current == 1) "Tag Serie" else "Tage Serie", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, description: String?, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 6.dp).then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier.semantics(mergeDescendants = true) {}),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = LabelType.Stat, color = FocusPalette.Graphite, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
    }
}

/** Upcoming reminders; the empty state mirrors the PWA calendar card (tile, title, one line). */
@Composable
private fun RemindersCard(state: HomeUiState, onOpen: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_reminders"), onClick = onOpen, onClickLabel = "Geplanten Fokus öffnen", contentPadding = PaddingValues(Spacing.md)) {
        if (!state.remindersDeliverable && state.reminders.isNotEmpty()) {
            StatusPill("Benachrichtigungen deaktiviert", tone = PillTone.WARNING, icon = FocusIcons.Alert)
            Spacer(Modifier.height(Spacing.xs))
        }
        if (state.reminders.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(FocusIcons.Calendar, background = SolidColor(FocusPalette.SurfaceMuted), tint = FocusPalette.GraphiteSoft)
                Spacer(Modifier.height(Spacing.sm))
                Text("Keine anstehenden Erinnerungen", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, textAlign = TextAlign.Center)
                Text("Geplanter Fokus und Mission-Deadlines erscheinen hier.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
            }
        } else {
            state.reminders.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(FocusIcons.Bell, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(r.whenLabel, style = MaterialTheme.typography.bodySmall, color = FocusPalette.Purple)
                    }
                }
            }
        }
    }
}

/**
 * PWA Home Activity (v17.16): one quiet card, grouped by day. The node shape shows the outcome
 * (filled = abgeschlossen, half = teilweise, open = abgebrochen) next to its text label; Mission
 * rows carry their stable Mission tone, Quick Focus rows the app purple. A thin rail joins the
 * rows of one day.
 */
@Composable
internal fun ActivityTimeline(items: List<ActivityItem>, onOpen: (String) -> Unit) {
    FocusCard(Modifier.fillMaxWidth().testTag("home_activity"), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 6.dp)) {
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(FocusIcons.Clock, background = SolidColor(FocusPalette.SurfaceMuted), tint = FocusPalette.GraphiteSoft)
                Spacer(Modifier.height(Spacing.sm))
                Text("Noch keine Aktivität", style = MaterialTheme.typography.titleSmall, color = FocusPalette.Graphite, textAlign = TextAlign.Center)
                Text("Deine Fokus-Sitzungen erscheinen hier.", style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, textAlign = TextAlign.Center)
            }
            return@FocusCard
        }
        items.forEachIndexed { i, item ->
            if (i == 0 || items[i - 1].day != item.day) {
                Text(
                    item.day.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = FocusPalette.GraphiteSoft,
                    modifier = Modifier.padding(top = if (i == 0) 8.dp else 12.dp, bottom = 2.dp).semantics { heading() },
                )
            }
            ActivityRow(item, joinsNext = items.getOrNull(i + 1)?.day == item.day) { onOpen(item.sessionId) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityRow(item: ActivityItem, joinsNext: Boolean, onClick: () -> Unit) {
    val tone = item.theme?.let { MissionTones.of(it).accentStrong } ?: FocusPalette.Purple
    val open = item.node == OutcomeNode.OPEN
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 320 dp at 130–160 %: duration and outcome move under the task instead of squeezing it.
        val compact = isTextCompact(maxWidth.value, fontScale, 260f)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).height(IntrinsicSize.Min).clip(Radii.sm)
                .clickable(role = Role.Button, onClickLabel = "Ergebnis öffnen", onClick = onClick)
                .semantics(mergeDescendants = true) {}.testTag("home_activity_${item.sessionId}"),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.width(14.dp).fillMaxHeight()) {
                if (joinsNext) Box(Modifier.align(Alignment.TopCenter).padding(top = 24.dp).width(1.5.dp).fillMaxHeight().background(FocusPalette.Outline))
                OutcomeDot(item.node.fill, if (open) FocusPalette.NodeOpen else tone, Modifier.padding(top = 11.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f).padding(vertical = 9.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = if (open) FocusPalette.GraphiteSoft else FocusPalette.Graphite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (compact) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(item.minutes, style = MaterialTheme.typography.titleSmall, color = if (open) FocusPalette.GraphiteSoft else FocusPalette.Graphite, maxLines = 1)
                        Text(item.badge.label, style = MaterialTheme.typography.labelMedium, color = OutcomeColors.ink(item.node.fill), maxLines = 1)
                    }
                    Text(item.context + " · " + item.time, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    Row {
                        Text(item.context, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(" · " + item.time, style = MaterialTheme.typography.bodySmall, color = FocusPalette.GraphiteSoft, maxLines = 1)
                    }
                }
            }
            if (!compact) {
                Spacer(Modifier.width(Spacing.xs))
                Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.End) {
                    Text(item.minutes, style = MaterialTheme.typography.titleSmall, color = if (open) FocusPalette.GraphiteSoft else FocusPalette.Graphite, maxLines = 1)
                    Text(item.badge.label, style = MaterialTheme.typography.labelMedium, color = OutcomeColors.ink(item.node.fill), maxLines = 1)
                }
            }
        }
    }
}
