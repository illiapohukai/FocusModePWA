package io.github.illiapohukai.focusmode.feature.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object HistoryDestination

/** PWA `app-screen-stats` ("Dein Fokusverlauf"), a child of History. */
@Serializable data object InsightsDestination

fun NavGraphBuilder.historyScreen(actions: HistoryActions, contentPadding: () -> PaddingValues, onBack: () -> Unit = {}) {
    composable<HistoryDestination> { HistoryRoute(actions, contentPadding()) }
    composable<InsightsDestination> { InsightsRoute(onBack, actions.onQuickFocus) }
}
