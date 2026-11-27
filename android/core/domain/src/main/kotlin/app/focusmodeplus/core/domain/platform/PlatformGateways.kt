package app.focusmodeplus.core.domain.platform

import app.focusmodeplus.core.model.FocusSession
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.InstalledApp
import app.focusmodeplus.core.model.ManagedDeviceStatus
import app.focusmodeplus.core.model.SessionId

/** Keeps a running Session visible: foreground service + one ongoing notification. */
interface SessionRuntime {
    fun ensureRunning(session: FocusSession)
    fun stop()
}

/** Wakes the Session owner when the planned duration ends, even if the process was killed. */
interface SessionEndAlarm {
    fun schedule(session: FocusSession, endRealtimeMs: Long)
    fun cancel()
}

/** Session-related notifications. The single notification owner implements all of them. */
interface SessionNotifications {
    fun showFinished(result: FocusSessionResult)
    fun showFrozen(session: FocusSession)
    fun clearSessionNotifications()
}

/** Reminder and protection notifications (same single owner). Return false when not deliverable. */
interface ReminderNotifications {
    fun showReminder(reminder: app.focusmodeplus.core.model.Reminder): Boolean
    fun showDistractionHint(remainingMinutes: Int): Boolean
    /** Removes every notification Focus Mode+ has posted (used by full data deletion). */
    fun clearAll() {}
}

/** Opens a system settings screen; false when the device offers none. */
interface SystemSettingsNavigator {
    fun open(target: app.focusmodeplus.core.model.SettingsTarget): Boolean
}

enum class ReminderSyncReason { APP_START, DATA_CHANGED, BOOT, TIME_CHANGED, TIMEZONE_CHANGED, PACKAGE_REPLACED, DELIVERED }

/** The one reminder scheduler: plans, (re)schedules, cancels and delivers reminder occurrences. */
interface ReminderScheduler {
    suspend fun sync(reason: ReminderSyncReason)
    suspend fun deliver(key: String)
}

sealed interface DndApplyResult {
    /** Focus Mode+ changed the filter and recorded the prior state for restoration. */
    data object Applied : DndApplyResult
    /** The user already had DND on: nothing changed, nothing will be restored. */
    data object AlreadyActive : DndApplyResult
    data object NoAccess : DndApplyResult
    data object Unsupported : DndApplyResult
}

/** Optional Do Not Disturb integration through Notification Policy Access. */
interface DoNotDisturbController {
    fun isSupported(): Boolean
    fun hasAccess(): Boolean
    suspend fun applyForSession(sessionId: SessionId): DndApplyResult
    /** Restores the prior filter if (and only if) Focus Mode+ changed it. Safe to call anytime. */
    suspend fun restoreIfChanged(): Boolean
}

/** Detects foreground use of selected apps during an active protected Session only. */
interface UsageAccessGateway {
    fun isSupported(): Boolean
    fun hasAccess(): Boolean
    /** Packages among [watched] that moved to the foreground in [fromMs, toMs). */
    fun foregroundEvents(fromMs: Long, toMs: Long, watched: Set<String>): List<Pair<String, Long>>
}

/** Narrow app discovery: launchable apps visible through the launcher `<queries>` intent. */
interface InstalledAppCatalog {
    suspend fun launchableApps(): List<InstalledApp>
    fun resolve(packageName: String): InstalledApp?
}

/**
 * Managed-device boundary (DevicePolicyManager). v18 ships only a detecting consumer
 * implementation; it never provisions, never requests owner status and never enforces.
 */
interface ManagedDeviceGateway {
    fun status(): ManagedDeviceStatus
}

/** Future managed enforcement (lock task, package suspension). No implementation in v18. */
interface ManagedFocusEnforcer {
    fun startEnforcement(packages: Set<String>): Boolean
    fun stopEnforcement()
}

/** Whether the platform currently lets us schedule exact alarms. */
interface ExactAlarmCapability {
    fun canScheduleExactAlarms(): Boolean
}

/** Connectivity, used only to say whether AI features are reachable. Focus works fully offline. */
interface NetworkStatus {
    val online: kotlinx.coroutines.flow.StateFlow<Boolean>
}
