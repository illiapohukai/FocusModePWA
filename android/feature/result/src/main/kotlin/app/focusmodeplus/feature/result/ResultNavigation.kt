package app.focusmodeplus.feature.result

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Result of one finalized Session. Unknown or unsafe ids show a safe "not found" state. */
@Serializable data class ResultDestination(val sessionId: String)

fun NavController.navigateToResult(sessionId: String, options: NavOptions? = null) = navigate(ResultDestination(sessionId), options)

fun NavGraphBuilder.resultScreen(actions: ResultActions) {
    composable<ResultDestination> { ResultRoute(actions) }
}
