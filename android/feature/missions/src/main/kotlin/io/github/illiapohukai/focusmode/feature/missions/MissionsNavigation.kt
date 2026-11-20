package io.github.illiapohukai.focusmode.feature.missions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Root "missions" (PWA maps it to the plan screen). */
@Serializable data object MissionsDestination
@Serializable data class MissionDetailDestination(val missionId: String)

fun NavController.navigateToMission(missionId: String) = navigate(MissionDetailDestination(missionId))

fun NavGraphBuilder.missionsScreens(
    listActions: MissionsActions,
    detailActions: MissionDetailActions,
    contentPadding: () -> PaddingValues,
    snackbar: SnackbarHostState,
) {
    composable<MissionsDestination> { MissionsRoute(listActions, contentPadding()) }
    composable<MissionDetailDestination> { MissionDetailRoute(detailActions, snackbar) }
}
