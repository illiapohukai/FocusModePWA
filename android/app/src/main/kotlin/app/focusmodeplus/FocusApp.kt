package app.focusmodeplus

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.focusmodeplus.core.designsystem.component.FloatingBottomBar
import app.focusmodeplus.core.designsystem.component.FloatingNavRail
import app.focusmodeplus.core.designsystem.component.NavDestinationItem
import app.focusmodeplus.core.designsystem.component.NavigationRow
import app.focusmodeplus.core.designsystem.icon.FocusIcons
import app.focusmodeplus.core.designsystem.theme.FocusPalette
import app.focusmodeplus.core.designsystem.theme.Easings
import app.focusmodeplus.core.designsystem.theme.LocalMotion
import app.focusmodeplus.core.designsystem.theme.MotionTokens
import app.focusmodeplus.core.designsystem.theme.NavGeometry
import app.focusmodeplus.core.designsystem.theme.Spacing
import app.focusmodeplus.core.domain.deeplink.DeepLinkTarget
import app.focusmodeplus.core.domain.session.SessionState
import app.focusmodeplus.feature.focus.FocusActions
import app.focusmodeplus.feature.focus.FocusDestination
import app.focusmodeplus.feature.focus.focusScreen
import app.focusmodeplus.feature.history.HistoryActions
import app.focusmodeplus.feature.history.HistoryDestination
import app.focusmodeplus.feature.history.InsightsDestination
import app.focusmodeplus.feature.history.historyScreen
import app.focusmodeplus.feature.home.HomeActions
import app.focusmodeplus.feature.home.HomeDestination
import app.focusmodeplus.feature.home.homeScreen
import app.focusmodeplus.feature.missions.MissionBriefingDestination
import app.focusmodeplus.feature.missions.MissionCreateDestination
import app.focusmodeplus.feature.missions.MissionDetailActions
import app.focusmodeplus.feature.missions.MissionDetailDestination
import app.focusmodeplus.feature.missions.MissionFlowActions
import app.focusmodeplus.feature.missions.MissionGenerateDestination
import app.focusmodeplus.feature.missions.MissionsActions
import app.focusmodeplus.feature.missions.MissionsDestination
import app.focusmodeplus.feature.missions.missionsScreens
import app.focusmodeplus.feature.missions.navigateToMission
import app.focusmodeplus.feature.missions.navigateToMissionCreate
import app.focusmodeplus.feature.quickfocus.QuickFocusActions
import app.focusmodeplus.feature.quickfocus.navigateToQuickFocus
import app.focusmodeplus.feature.quickfocus.quickFocusScreen
import app.focusmodeplus.feature.reminders.ScheduleEditorDestination
import app.focusmodeplus.feature.reminders.SchedulesActions
import app.focusmodeplus.feature.reminders.SchedulesDestination
import app.focusmodeplus.feature.reminders.scheduleScreens
import app.focusmodeplus.feature.result.ResultActions
import app.focusmodeplus.feature.result.ResultDestination
import app.focusmodeplus.feature.result.navigateToResult
import app.focusmodeplus.feature.result.resultScreen
import app.focusmodeplus.feature.settings.AccountDestination
import app.focusmodeplus.feature.settings.AppSelectionDestination
import app.focusmodeplus.feature.settings.DataBackupDestination
import app.focusmodeplus.feature.settings.DataImportDestination
import app.focusmodeplus.feature.settings.PermissionCenterDestination
import app.focusmodeplus.feature.settings.SettingsActions
import app.focusmodeplus.feature.settings.SettingsDestination
import app.focusmodeplus.feature.settings.settingsScreens

/** Root sections in the PWA's logical order: home → missions → history → settings. Plus is an action. */
private enum class Root(val key: String, val label: String) { HOME("home", "Home"), MISSIONS("missions", "Missionen"), HISTORY("history", "Verlauf"), SETTINGS("settings", "Profil") }

private val NAV_ITEMS = listOf(
    NavDestinationItem(Root.HOME.key, Root.HOME.label, FocusIcons.Home),
    NavDestinationItem(Root.MISSIONS.key, Root.MISSIONS.label, FocusIcons.Target),
    NavDestinationItem(Root.HISTORY.key, Root.HISTORY.label, FocusIcons.History),
    NavDestinationItem(Root.SETTINGS.key, Root.SETTINGS.label, FocusIcons.User),
)

private fun NavDestination?.root(): Root? = when {
    this == null -> null
    hasRoute<HomeDestination>() -> Root.HOME
    hasRoute<MissionsDestination>() -> Root.MISSIONS
    hasRoute<HistoryDestination>() -> Root.HISTORY
    hasRoute<SettingsDestination>() -> Root.SETTINGS
    else -> null
}

private fun NavHostController.navigateRoot(root: Root) {
    val destination: Any = when (root) {
        Root.HOME -> HomeDestination
        Root.MISSIONS -> MissionsDestination
        Root.HISTORY -> HistoryDestination
        Root.SETTINGS -> SettingsDestination
    }
    navigate(destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Route motion (the one owner for screen changes, PWA `motion.css` tokens): a quick crossfade
 * between root sections, a short slide-and-fade into flow screens (mirrored on Back), and the
 * Focus environment fading and settling in. Reduced motion keeps only short fades. Only
 * opacity and transforms animate; navigation never waits for an animation.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.routeEnter(motion: MotionTokens, pop: Boolean): EnterTransition {
    val to = targetState.destination
    val betweenRoots = initialState.destination.root() != null && to.root() != null
    return when {
        motion.reduced || betweenRoots -> fadeIn(tween(motion.fast))
        to.hasRoute<FocusDestination>() -> fadeIn(tween(motion.normal)) + scaleIn(tween(motion.focusEnter, easing = Easings.Standard), initialScale = 0.96f)
        else -> fadeIn(tween(motion.normal, easing = Easings.Standard)) +
            slideInHorizontally(tween(motion.normal, easing = Easings.Standard)) { w -> (if (pop) -w else w) / 10 }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.routeExit(motion: MotionTokens, pop: Boolean): ExitTransition {
    val betweenRoots = initialState.destination.root() != null && targetState.destination.root() != null
    return when {
        motion.reduced || betweenRoots -> fadeOut(tween(motion.fast))
        else -> fadeOut(tween(motion.fast, easing = Easings.Exit)) +
            slideOutHorizontally(tween(motion.normal, easing = Easings.Exit)) { w -> (if (pop) w else -w) / 14 }
    }
}

/** Mission creation flow: Creation → Generation → Briefing → the new Mission's Plan. */
private fun NavHostController.missionFlowActions(openMissions: () -> Unit) = MissionFlowActions(
    onBack = { popBackStack() },
    onGenerating = { navigate(MissionGenerateDestination) { launchSingleTop = true } },
    onBriefing = {
        navigate(MissionBriefingDestination) {
            popUpTo<MissionGenerateDestination> { inclusive = true }
            launchSingleTop = true
        }
    },
    onGenerationEnded = { if (!popBackStack<MissionCreateDestination>(inclusive = false)) navigateToMissionCreate() },
    onAccepted = { id ->
        navigate(MissionDetailDestination(id)) {
            popUpTo<MissionCreateDestination> { inclusive = true }
            launchSingleTop = true
        }
    },
    onDiscarded = { if (!popBackStack<MissionCreateDestination>(inclusive = true)) openMissions() },
    onOpenMissions = openMissions,
)

/** Opens the Session screen; Quick Focus and the start sheet are removed from the back stack. */
private fun NavHostController.openSession(openEnd: Boolean = false) {
    navigate(FocusDestination(openEnd)) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusApp(appViewModel: AppViewModel, window: Window) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val deepLink by appViewModel.pendingDeepLink.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoot = backStack?.destination.root()
    val onFocus = backStack?.destination?.hasRoute<FocusDestination>() == true
    var showStartSheet by rememberSaveable { mutableStateOf(false) }
    var routedExistingSession by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val wide = with(density) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp

    // Light system-bar icons only in the dark Focus environment.
    val view = LocalView.current
    SideEffect { WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !onFocus }

    // PWA rule: an existing Session is routed to once at start (cold start or Recovery).
    LaunchedEffect(session) {
        if (!routedExistingSession && session !is SessionState.Loading) {
            routedExistingSession = true
            if ((session is SessionState.Running || session is SessionState.AwaitingResume) && deepLink == null) nav.openSession()
        }
    }

    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        appViewModel.consumeDeepLink()
        when (target) {
            DeepLinkTarget.Home -> nav.navigateRoot(Root.HOME)
            DeepLinkTarget.ActiveSession -> if (session.activeSession != null) nav.openSession() else nav.navigateRoot(Root.HOME)
            DeepLinkTarget.EndSession -> if (session is SessionState.Running) nav.openSession(openEnd = true) else nav.navigateRoot(Root.HOME)
            DeepLinkTarget.Missions -> nav.navigateRoot(Root.MISSIONS)
            is DeepLinkTarget.Mission -> { nav.navigateRoot(Root.MISSIONS); nav.navigateToMission(target.missionId) }
            is DeepLinkTarget.QuickFocus -> { nav.navigateRoot(Root.HOME); nav.navigateToQuickFocus(target.scheduleId) }
            is DeepLinkTarget.Reminder -> nav.navigateRoot(Root.HOME)
            DeepLinkTarget.History -> nav.navigateRoot(Root.HISTORY)
            is DeepLinkTarget.Result -> { nav.navigateRoot(Root.HISTORY); nav.navigateToResult(target.sessionId) }
            DeepLinkTarget.PermissionCenter -> { nav.navigateRoot(Root.SETTINGS); nav.navigate(PermissionCenterDestination) }
        }
    }

    val barVisible = currentRoot != null
    val rootPadding = PaddingValues(
        top = with(density) { WindowInsets.statusBars.getTop(this).toDp() },
        // The floating bar sits above the system navigation inset (gesture pill or three-button bar),
        // so the clearance is added on top of that inset, never instead of it.
        bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + if (barVisible && !wide) NavGeometry.contentClearance else 0.dp,
    )
    val openQuickFocus = { if (session.activeSession != null) nav.openSession() else nav.navigateToQuickFocus() }

    Row(Modifier.fillMaxSize()) {
        if (barVisible && wide) {
            FloatingNavRail(NAV_ITEMS, currentRoot?.key, { key -> nav.navigateRoot(Root.entries.first { it.key == key }) }, { showStartSheet = true },
                Modifier.windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars))
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            val motion = LocalMotion.current
            NavHost(
                nav, startDestination = HomeDestination, modifier = Modifier.fillMaxSize(),
                enterTransition = { routeEnter(motion, pop = false) },
                exitTransition = { routeExit(motion, pop = false) },
                popEnterTransition = { routeEnter(motion, pop = true) },
                popExitTransition = { routeExit(motion, pop = true) },
            ) {
                homeScreen(
                    HomeActions(
                        onQuickFocus = openQuickFocus,
                        onOpenSession = { nav.openSession() },
                        onOpenMission = { nav.navigateToMission(it) },
                        onOpenMissions = { nav.navigateRoot(Root.MISSIONS) },
                        onOpenHistory = { nav.navigateRoot(Root.HISTORY) },
                        onOpenResult = { nav.navigateToResult(it) },
                        onOpenSchedules = { nav.navigate(SchedulesDestination) },
                        onCreateMission = { nav.navigateToMissionCreate() },
                    ),
                    { rootPadding }, snackbar,
                )
                missionsScreens(
                    MissionsActions(
                        onOpenMission = { nav.navigateToMission(it) }, onQuickFocus = openQuickFocus,
                        onImport = { nav.navigate(DataImportDestination) }, onCreate = { nav.navigateToMissionCreate() },
                    ),
                    MissionDetailActions(onBack = { nav.popBackStack() }, onOpenSession = { nav.openSession() }, onOpenResult = { nav.navigateToResult(it) }),
                    nav.missionFlowActions { nav.navigateRoot(Root.MISSIONS) },
                    { rootPadding }, snackbar,
                )
                historyScreen(
                    HistoryActions(onOpenResult = { nav.navigateToResult(it) }, onQuickFocus = openQuickFocus, onOpenInsights = { nav.navigate(InsightsDestination) }),
                    { rootPadding }, onBack = { nav.popBackStack() },
                )
                settingsScreens(
                    SettingsActions(
                        onPermissions = { nav.navigate(PermissionCenterDestination) },
                        onAppSelection = { nav.navigate(AppSelectionDestination) },
                        onSchedules = { nav.navigate(SchedulesDestination) },
                        onImport = { nav.navigate(DataImportDestination) },
                        appVersion = BuildConfig.VERSION_NAME,
                        debugBuild = BuildConfig.DEBUG,
                        onDataBackup = { nav.navigate(DataBackupDestination) },
                        onAccount = { nav.navigate(AccountDestination) },
                    ),
                    onBack = { nav.popBackStack() }, contentPadding = { rootPadding }, snackbar = snackbar,
                )
                quickFocusScreen(
                    QuickFocusActions(
                        onBack = { nav.popBackStack() },
                        onStarted = { nav.openSession() },
                        onOpenAppSelection = { nav.navigate(AppSelectionDestination) },
                        onOpenPermissions = { nav.navigate(PermissionCenterDestination) },
                    ),
                )
                focusScreen(
                    FocusActions(
                        onShowResult = { id -> nav.navigate(ResultDestination(id)) { popUpTo(nav.graph.findStartDestination().id) } },
                        onLeave = { nav.navigateRoot(Root.HOME) },
                    ),
                    snackbar,
                )
                resultScreen(
                    ResultActions(
                        onDone = { if (!nav.popBackStack()) nav.navigateRoot(Root.HOME) },
                        onHistory = { nav.navigateRoot(Root.HISTORY) },
                        onAgain = { nav.navigateRoot(Root.HOME); openQuickFocus() },
                        onOpenMission = { id -> nav.navigateRoot(Root.MISSIONS); nav.navigateToMission(id) },
                    ),
                )
                scheduleScreens(
                    SchedulesActions(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ScheduleEditorDestination(it)) }, onPermissions = { nav.navigate(PermissionCenterDestination) }),
                    onEditorDone = { nav.popBackStack() },
                )
            }
            if (barVisible && !wide) {
                FloatingBottomBar(
                    NAV_ITEMS, currentRoot?.key, { key -> nav.navigateRoot(Root.entries.first { it.key == key }) }, { showStartSheet = true },
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (barVisible && !wide) NavGeometry.contentClearance else Spacing.md),
            )
        }
    }

    if (showStartSheet) {
        ModalBottomSheet(onDismissRequest = { showStartSheet = false }, containerColor = FocusPalette.Surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal).padding(bottom = Spacing.xl)) {
                Text("Was möchtest du starten?", style = MaterialTheme.typography.headlineSmall, color = FocusPalette.Graphite, modifier = Modifier.semantics { heading() })
                if (session.activeSession != null) {
                    NavigationRow("Aktive Sitzung öffnen", { showStartSheet = false; nav.openSession() }, subtitle = "Deine Fokus-Sitzung läuft", icon = FocusIcons.Clock)
                }
                NavigationRow("Quick Focus", { showStartSheet = false; openQuickFocus() }, subtitle = "Sofort fokussieren, 5–60 Minuten", icon = FocusIcons.Play)
                NavigationRow("Missionen", { showStartSheet = false; nav.navigateRoot(Root.MISSIONS) }, subtitle = "Nächsten Missionsschritt starten", icon = FocusIcons.Target)
                NavigationRow("Neue Mission", { showStartSheet = false; nav.navigateToMissionCreate() }, subtitle = "Ziel beschreiben, AI plant die Schritte", icon = FocusIcons.Sparkle)
                NavigationRow("Geplanter Fokus", { showStartSheet = false; nav.navigate(ScheduleEditorDestination(null)) }, subtitle = "Feste Fokuszeit mit Erinnerung", icon = FocusIcons.Calendar)
            }
        }
    }
}
