package io.github.illiapohukai.focusmode.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object SettingsDestination
@Serializable data object PermissionCenterDestination
@Serializable data object AppSelectionDestination
@Serializable data object DataImportDestination

fun NavGraphBuilder.settingsScreens(actions: SettingsActions, onBack: () -> Unit, contentPadding: () -> PaddingValues, snackbar: SnackbarHostState) {
    composable<SettingsDestination> { SettingsRoute(actions, contentPadding(), snackbar) }
    composable<PermissionCenterDestination> { PermissionCenterRoute(onBack) }
    composable<AppSelectionDestination> { AppSelectionRoute(onBack) }
    composable<DataImportDestination> { DataImportRoute(onBack) }
}
