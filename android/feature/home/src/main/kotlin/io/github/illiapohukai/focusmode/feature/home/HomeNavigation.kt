package io.github.illiapohukai.focusmode.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object HomeDestination

fun NavGraphBuilder.homeScreen(actions: HomeActions, contentPadding: () -> PaddingValues, snackbar: SnackbarHostState) {
    composable<HomeDestination> { HomeRoute(actions, contentPadding(), snackbar) }
}
