package io.github.illiapohukai.focusmode

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
import io.github.illiapohukai.focusmode.core.designsystem.component.FloatingBottomBar
import io.github.illiapohukai.focusmode.core.designsystem.component.FloatingNavRail
import io.github.illiapohukai.focusmode.core.designsystem.component.NavDestinationItem
import io.github.illiapohukai.focusmode.core.designsystem.component.NavigationRow
import io.github.illiapohukai.focusmode.core.designsystem.icon.FocusIcons
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusPalette
import io.github.illiapohukai.focusmode.core.designsystem.theme.Spacing
import io.github.illiapohukai.focusmode.core.domain.deeplink.DeepLinkTarget
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.feature.focus.FocusActions
import io.github.illiapohukai.focusmode.feature.focus.FocusDestination
import io.github.illiapohukai.focusmode.feature.focus.focusScreen
import io.github.illiapohukai.focusmode.feature.history.HistoryActions
import io.github.illiapohukai.focusmode.feature.history.HistoryDestination
import io.github.illiapohukai.focusmode.feature.history.historyScreen
import io.github.illiapohukai.focusmode.feature.home.HomeActions
import io.github.illiapohukai.focusmode.feature.home.HomeDestination
import io.github.illiapohukai.focusmode.feature.home.homeScreen
import io.github.illiapohukai.focusmode.feature.missions.MissionDetailActions
import io.github.illiapohukai.focusmode.feature.missions.MissionsActions
import io.github.illiapohukai.focusmode.feature.missions.MissionsDestination
import io.github.illiapohukai.focusmode.feature.missions.missionsScreens
import io.github.illiapohukai.focusmode.feature.missions.navigateToMission
import io.github.illiapohukai.focusmode.feature.quickfocus.QuickFocusActions
import io.github.illiapohukai.focusmode.feature.quickfocus.navigateToQuickFocus
import io.github.illiapohukai.focusmode.feature.quickfocus.quickFocusScreen
import io.github.illiapohukai.focusmode.feature.reminders.ScheduleEditorDestination
import io.github.illiapohukai.focusmode.feature.reminders.SchedulesActions
import io.github.illiapohukai.focusmode.feature.reminders.SchedulesDestination
import io.github.illiapohukai.focusmode.feature.reminders.scheduleScreens
import io.github.illiapohukai.focusmode.feature.result.ResultActions
import io.github.illiapohukai.focusmode.feature.result.ResultDestination
import io.github.illiapohukai.focusmode.feature.result.navigateToResult
import io.github.illiapohukai.focusmode.feature.result.resultScreen
import io.github.illiapohukai.focusmode.feature.settings.AppSelectionDestination
import io.github.illiapohukai.focusmode.feature.settings.DataImportDestination
import io.github.illiapohukai.focusmode.feature.settings.PermissionCenterDestination
import io.github.illiapohukai.focusmode.feature.settings.SettingsActions
import io.github.illiapohukai.focusmode.feature.settings.SettingsDestination
import io.github.illiapohukai.focusmode.feature.settings.settingsScreens

/** Root sections in the PWA's logical order: home → missions → history → settings. Plus is an action. */
private enum class Root(val key: String, val label: String) { HOME("home", "Start"), MISSIONS("missions", "Missionen"), HISTORY("history", "Verlauf"), SETTINGS("settings", "Mehr") }

private val NAV_ITEMS = listOf(
    NavDestinationItem(Root.HOME.key, Root.HOME.label, FocusIcons.Home),
    NavDestinationItem(Root.MISSIONS.key, Root.MISSIONS.label, FocusIcons.Target),
    NavDestinationItem(Root.HISTORY.key, Root.HISTORY.label, FocusIcons.History),
    NavDestinationItem(Root.SETTINGS.key, Root.SETTINGS.label, FocusIcons.Settings),
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
        bottom = if (barVisible && !wide) 108.dp else with(density) { WindowInsets.navigationBars.getBottom(this).toDp() },
    )
    val openQuickFocus = { if (session.activeSession != null) nav.openSession() else nav.navigateToQuickFocus() }

    Row(Modifier.fillMaxSize()) {
        if (barVisible && wide) {
            FloatingNavRail(NAV_ITEMS, currentRoot?.key, { key -> nav.navigateRoot(Root.entries.first { it.key == key }) }, { showStartSheet = true },
                Modifier.windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars))
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            NavHost(nav, startDestination = HomeDestination, modifier = Modifier.fillMaxSize()) {
                homeScreen(
                    HomeActions(
                        onQuickFocus = openQuickFocus,
                        onOpenSession = { nav.openSession() },
                        onOpenMission = { nav.navigateToMission(it) },
                        onOpenMissions = { nav.navigateRoot(Root.MISSIONS) },
                        onOpenHistory = { nav.navigateRoot(Root.HISTORY) },
                        onOpenResult = { nav.navigateToResult(it) },
                        onOpenSchedules = { nav.navigate(SchedulesDestination) },
                    ),
                    { rootPadding }, snackbar,
                )
                missionsScreens(
                    MissionsActions(onOpenMission = { nav.navigateToMission(it) }, onQuickFocus = openQuickFocus, onImport = { nav.navigate(DataImportDestination) }),
                    MissionDetailActions(onBack = { nav.popBackStack() }, onOpenSession = { nav.openSession() }),
                    { rootPadding }, snackbar,
                )
                historyScreen(HistoryActions(onOpenResult = { nav.navigateToResult(it) }, onQuickFocus = openQuickFocus), { rootPadding })
                settingsScreens(
                    SettingsActions(
                        onPermissions = { nav.navigate(PermissionCenterDestination) },
                        onAppSelection = { nav.navigate(AppSelectionDestination) },
                        onSchedules = { nav.navigate(SchedulesDestination) },
                        onImport = { nav.navigate(DataImportDestination) },
                        appVersion = BuildConfig.VERSION_NAME,
                        debugBuild = BuildConfig.DEBUG,
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
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (barVisible && !wide) 110.dp else Spacing.md))
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
                NavigationRow("Geplanter Fokus", { showStartSheet = false; nav.navigate(ScheduleEditorDestination(null)) }, subtitle = "Feste Fokuszeit mit Erinnerung", icon = FocusIcons.Calendar)
            }
        }
    }
}
