package io.github.illiapohukai.focusmode.core.protection

import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.notification.NotificationCapability
import io.github.illiapohukai.focusmode.core.domain.platform.DoNotDisturbController
import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.InstalledAppCatalog
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedDeviceGateway
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway
import io.github.illiapohukai.focusmode.core.domain.protection.AppSelectionReconciler
import io.github.illiapohukai.focusmode.core.domain.protection.CapabilityResolver
import io.github.illiapohukai.focusmode.core.domain.protection.ProtectionInputs
import io.github.illiapohukai.focusmode.core.domain.protection.ProtectionOverview
import io.github.illiapohukai.focusmode.core.domain.repository.PreferencesRepository
import io.github.illiapohukai.focusmode.core.domain.repository.SelectedAppRepository
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.PermissionSnapshot
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.ResolvedSelectedApp
import io.github.illiapohukai.focusmode.core.notifications.NotificationCapabilityReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class ProtectionState(
    val overview: ProtectionOverview,
    val notifications: NotificationCapability,
    val snapshot: PermissionSnapshot,
    val selectedApps: List<ResolvedSelectedApp>,
    val preferences: AppPreferences,
)

/**
 * The one protection-capability owner. Platform state is re-read on every [refresh] (app resume,
 * return from a settings screen, permission dialog result); nothing is assumed from earlier reads.
 */
@Singleton
class ProtectionCapabilityOwner @Inject constructor(
    private val usage: UsageAccessGateway,
    private val dnd: DoNotDisturbController,
    private val managed: ManagedDeviceGateway,
    private val exactAlarms: ExactAlarmCapability,
    private val battery: BatteryOptimizationReader,
    private val notifications: NotificationCapabilityReader,
    private val catalog: InstalledAppCatalog,
    preferences: PreferencesRepository,
    selectedApps: SelectedAppRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    private val tick = MutableStateFlow(0L)
    private val rationale = MutableStateFlow(false)
    private val usageRevoked = MutableStateFlow(false)

    val state: StateFlow<ProtectionState?> =
        combine(tick, preferences.preferences, selectedApps.observeSelected(), rationale, usageRevoked) { _, prefs, selected, showRationale, revoked ->
            val snapshot = snapshot(prefs, showRationale)
            val resolved = AppSelectionReconciler.reconcile(selected, catalog::resolve)
            val inputs = ProtectionInputs(
                snapshot = snapshot,
                selectedAppCount = AppSelectionReconciler.watchablePackages(resolved).size,
                usageMonitoringEnabled = prefs.usageMonitoringEnabled,
                doNotDisturbEnabled = prefs.doNotDisturbDuringFocus,
                usageRevokedDuringSession = revoked && snapshot.usageAccess != ProtectionPermissionState.GRANTED,
            )
            ProtectionState(
                overview = CapabilityResolver.resolve(inputs),
                notifications = notifications.read(prefs.notificationsEnabled, prefs.notificationPermissionRequests, showRationale),
                snapshot = snapshot,
                selectedApps = resolved,
                preferences = prefs,
            )
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    /** Re-read all platform state. Pass the Activity's rationale flag when it is known. */
    fun refresh(shouldShowNotificationRationale: Boolean? = null) {
        shouldShowNotificationRationale?.let { rationale.value = it }
        tick.update { it + 1 }
    }

    fun markUsageRevoked(revoked: Boolean) {
        usageRevoked.value = revoked
        refresh()
    }

    fun snapshot(prefs: AppPreferences, shouldShowRationale: Boolean = rationale.value): PermissionSnapshot = PermissionSnapshot(
        notifications = NotificationCapability.resolvePermission(
            notifications.runtimePermissionRequired, notifications.permissionGranted(), prefs.notificationPermissionRequests, shouldShowRationale,
        ),
        usageAccess = when {
            !usage.isSupported() -> ProtectionPermissionState.UNSUPPORTED
            usage.hasAccess() -> ProtectionPermissionState.GRANTED
            else -> ProtectionPermissionState.DENIED
        },
        doNotDisturb = when {
            !dnd.isSupported() -> ProtectionPermissionState.UNSUPPORTED
            dnd.hasAccess() -> ProtectionPermissionState.GRANTED
            else -> ProtectionPermissionState.DENIED
        },
        exactAlarm = if (exactAlarms.canScheduleExactAlarms()) ProtectionPermissionState.GRANTED else ProtectionPermissionState.DENIED,
        batteryOptimizationIgnored = battery.isIgnoringOptimizations(),
        managedDevice = managed.status(),
    )
}
