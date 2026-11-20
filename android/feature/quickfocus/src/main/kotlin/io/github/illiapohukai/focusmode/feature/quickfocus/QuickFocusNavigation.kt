package io.github.illiapohukai.focusmode.feature.quickfocus

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Quick Focus setup; [scheduleId] prefills from a Scheduled Focus reminder. */
@Serializable data class QuickFocusDestination(val scheduleId: String? = null)

fun NavController.navigateToQuickFocus(scheduleId: String? = null, options: NavOptions? = null) =
    navigate(QuickFocusDestination(scheduleId), options)

fun NavGraphBuilder.quickFocusScreen(actions: QuickFocusActions) {
    composable<QuickFocusDestination> { QuickFocusRoute(actions) }
}
