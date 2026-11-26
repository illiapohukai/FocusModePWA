package io.github.illiapohukai.focusmode.feature.missions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Root "missions" (PWA maps it to the plan screen). */
@Serializable data object MissionsDestination

/** Mission Plan of one Mission (PWA `app-screen-plan`). */
@Serializable data class MissionDetailDestination(val missionId: String)

/** Mission Creation (PWA `app-screen-goal`). */
@Serializable data object MissionCreateDestination

/** AI generation with the pixel operator (PWA `app-screen-analyzing`). */
@Serializable data object MissionGenerateDestination

/** Mission Briefing (PWA `app-screen-confirm`). */
@Serializable data object MissionBriefingDestination

fun NavController.navigateToMission(missionId: String) = navigate(MissionDetailDestination(missionId))

fun NavController.navigateToMissionCreate() = navigate(MissionCreateDestination) { launchSingleTop = true }

/** Flow callbacks the app shell wires to its single NavHost. */
data class MissionFlowActions(
    val onBack: () -> Unit,
    /** Creation → generation (the request already started). */
    val onGenerating: () -> Unit,
    /** Generation → Briefing (replaces the generation screen). */
    val onBriefing: () -> Unit,
    /** Generation → back to Creation (failure or cancel). */
    val onGenerationEnded: () -> Unit,
    /** Briefing accepted → the new Mission's Plan (replaces the creation flow). */
    val onAccepted: (String) -> Unit,
    /** Briefing discarded → leaves the creation flow. */
    val onDiscarded: () -> Unit,
    val onOpenMissions: () -> Unit,
)

fun NavGraphBuilder.missionsScreens(
    listActions: MissionsActions,
    detailActions: MissionDetailActions,
    flowActions: MissionFlowActions,
    contentPadding: () -> PaddingValues,
    snackbar: SnackbarHostState,
) {
    composable<MissionsDestination> { MissionsRoute(listActions, contentPadding()) }
    composable<MissionDetailDestination> { MissionPlanRoute(detailActions, snackbar) }
    composable<MissionCreateDestination> { MissionCreateRoute(flowActions, snackbar) }
    composable<MissionGenerateDestination> { MissionGenerateRoute(flowActions) }
    composable<MissionBriefingDestination> { MissionBriefingRoute(flowActions, snackbar) }
}
