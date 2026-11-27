package app.focusmodeplus.feature.focus

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** The active (or frozen) Session. [openEnd] opens the safe end flow (notification action). */
@Serializable data class FocusDestination(val openEnd: Boolean = false)

fun NavGraphBuilder.focusScreen(actions: FocusActions, snackbar: SnackbarHostState) {
    composable<FocusDestination> { FocusRoute(actions, snackbar) }
}
