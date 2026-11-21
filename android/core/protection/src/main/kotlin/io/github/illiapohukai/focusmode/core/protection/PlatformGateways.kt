package io.github.illiapohukai.focusmode.core.protection

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.domain.platform.DndApplyResult
import io.github.illiapohukai.focusmode.core.domain.platform.DoNotDisturbController
import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.InstalledAppCatalog
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedDeviceGateway
import io.github.illiapohukai.focusmode.core.domain.platform.ManagedFocusEnforcer
import io.github.illiapohukai.focusmode.core.domain.platform.SystemSettingsNavigator
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway
import io.github.illiapohukai.focusmode.core.domain.protection.AppSelectionReconciler
import io.github.illiapohukai.focusmode.core.domain.repository.DndOverride
import io.github.illiapohukai.focusmode.core.domain.repository.DndOverrideStore
import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.InstalledApp
import io.github.illiapohukai.focusmode.core.model.ManagedDeviceStatus
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SettingsTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Usage Access (PACKAGE_USAGE_STATS, granted by the user in system settings). Only queried during
 * an active protected Session for the selected packages; results never leave the device.
 */
@Singleton
class AndroidUsageAccessGateway @Inject constructor(@ApplicationContext private val context: Context) : UsageAccessGateway {
    override fun isSupported(): Boolean =
        context.getSystemService(UsageStatsManager::class.java) != null &&
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).resolveActivity(context.packageManager) != null

    override fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    override fun foregroundEvents(fromMs: Long, toMs: Long, watched: Set<String>): List<Pair<String, Long>> {
        if (watched.isEmpty() || toMs <= fromMs || !hasAccess()) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = runCatching { manager.queryEvents(fromMs, toMs) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val foreground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (foreground && event.packageName in watched) out += event.packageName to event.timeStamp
        }
        return out
    }
}

/**
 * Optional Do Not Disturb. Changes the interruption filter only (the user's own priority
 * exceptions are preserved), records the prior state durably first, and restores only if the
 * filter is still the one Focus Mode+ set. Never touches DND the user had already enabled.
 */
@Singleton
class AndroidDndController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DndOverrideStore,
    private val clock: FocusClock,
) : DoNotDisturbController {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun isSupported(): Boolean =
        manager != null && context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true

    override fun hasAccess(): Boolean = isSupported() && manager?.isNotificationPolicyAccessGranted == true

    override suspend fun applyForSession(sessionId: SessionId): DndApplyResult {
        if (!isSupported()) return DndApplyResult.Unsupported
        if (!hasAccess()) return DndApplyResult.NoAccess
        val nm = manager ?: return DndApplyResult.Unsupported
        val prior = nm.currentInterruptionFilter
        if (prior != NotificationManager.INTERRUPTION_FILTER_ALL) return DndApplyResult.AlreadyActive
        store.put(DndOverride(sessionId, prior, NotificationManager.INTERRUPTION_FILTER_PRIORITY, clock.now()))
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return DndApplyResult.Applied
    }

    override suspend fun restoreIfChanged(): Boolean {
        val override = store.get() ?: return false
        val nm = manager
        var restored = false
        if (nm != null && hasAccess() && nm.currentInterruptionFilter == override.appliedFilter) {
            nm.setInterruptionFilter(override.priorFilter)
            restored = true
        }
        store.clear() // the user changed DND meanwhile, or access was revoked: never fight the user
        return restored
    }
}

/** Consumer implementation: detects owner status only; never provisions, never enforces. */
@Singleton
class ConsumerManagedDeviceGateway @Inject constructor(@ApplicationContext private val context: Context) : ManagedDeviceGateway {
    override fun status(): ManagedDeviceStatus {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return ManagedDeviceStatus.NOT_MANAGED
        val owner = dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
        return if (owner) ManagedDeviceStatus.OWNER_DETECTED_NOT_SUPPORTED else ManagedDeviceStatus.NOT_MANAGED
    }
}

/** No managed enforcement ships in v18; see docs/protection-capabilities.md. */
@Singleton
class NoOpManagedFocusEnforcer @Inject constructor() : ManagedFocusEnforcer {
    override fun startEnforcement(packages: Set<String>): Boolean = false
    override fun stopEnforcement() = Unit
}

/** SCHEDULE_EXACT_ALARM is intentionally not declared, so Android 12+ reports false. */
@Singleton
class AndroidExactAlarmCapability @Inject constructor(@ApplicationContext private val context: Context) : ExactAlarmCapability {
    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
}

/**
 * Launchable apps through the narrow launcher `<queries>` declaration (no QUERY_ALL_PACKAGES).
 * The list is used only inside the selection screen and is never stored or transmitted.
 */
@Singleton
class AndroidInstalledAppCatalog @Inject constructor(@ApplicationContext private val context: Context) : InstalledAppCatalog {
    override suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            InstalledApp(appInfo.packageName, appInfo.loadLabel(pm).toString().take(120), appInfo.enabled)
        }
        AppSelectionReconciler.normalizeCatalog(apps, context.packageName)
    }

    override fun resolve(packageName: String): InstalledApp? = try {
        val pm = context.packageManager
        val info: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
        InstalledApp(packageName, info.loadLabel(pm).toString().take(120), info.enabled)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

@Singleton
class AndroidSystemSettingsNavigator @Inject constructor(@ApplicationContext private val context: Context) : SystemSettingsNavigator {
    override fun open(target: SettingsTarget): Boolean {
        val pkg = context.packageName
        val primary = when (target) {
            SettingsTarget.APP_NOTIFICATION_SETTINGS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            SettingsTarget.USAGE_ACCESS_SETTINGS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SettingsTarget.DND_ACCESS_SETTINGS -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SettingsTarget.BATTERY_OPTIMIZATION_SETTINGS -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            SettingsTarget.EXACT_ALARM_SETTINGS, SettingsTarget.APP_DETAILS -> appDetails(pkg)
        }
        return launch(primary) || launch(appDetails(pkg))
    }

    private fun appDetails(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/** Screen state: no app can be in the foreground while the screen is off, so nothing is polled. */
interface ScreenState {
    fun isInteractive(): Boolean

    /** Suspends until the screen is on; returns at once when it already is. */
    suspend fun awaitInteractive() {
        while (!isInteractive()) delay(SCREEN_FALLBACK_POLL_MS)
    }

    companion object {
        /** Only for implementations without a screen-on signal (tests); Android waits for the broadcast. */
        const val SCREEN_FALLBACK_POLL_MS = 60_000L
    }
}

@Singleton
class AndroidScreenState @Inject constructor(@ApplicationContext private val context: Context) : ScreenState {
    override fun isInteractive(): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive != false

    /** Waits for `ACTION_SCREEN_ON` (a protected system broadcast) instead of waking periodically. */
    override suspend fun awaitInteractive() {
        if (isInteractive()) return
        var receiver: BroadcastReceiver? = null
        try {
            suspendCancellableCoroutine { cont ->
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) { if (cont.isActive) cont.resume(Unit) }
                }
                receiver = r
                ContextCompat.registerReceiver(context, r, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
                if (isInteractive() && cont.isActive) cont.resume(Unit) // turned on while registering
            }
        } finally {
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
        }
    }
}

/** Battery-optimization exemption status (read-only; Focus Mode+ never requests the exemption). */
@Singleton
class BatteryOptimizationReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun isIgnoringOptimizations(): Boolean? =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
}
