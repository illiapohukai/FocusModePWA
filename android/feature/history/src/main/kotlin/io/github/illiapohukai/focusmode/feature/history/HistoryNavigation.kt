package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object HistoryDestination

fun NavGraphBuilder.historyScreen(actions: HistoryActions, contentPadding: () -> PaddingValues) {
    composable<HistoryDestination> { HistoryRoute(actions, contentPadding()) }
}
