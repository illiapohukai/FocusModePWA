package io.github.illiapohukai.focusmode.feature.reminders

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object SchedulesDestination
@Serializable data class ScheduleEditorDestination(val scheduleId: String? = null)

fun NavGraphBuilder.scheduleScreens(actions: SchedulesActions, onEditorDone: () -> Unit) {
    composable<SchedulesDestination> { SchedulesRoute(actions) }
    composable<ScheduleEditorDestination> { ScheduleEditorRoute(onEditorDone) }
}
