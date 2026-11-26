package io.github.illiapohukai.focusmode.core.model

import java.time.LocalTime

/** How much of the task text a Focus Mode+ notification may show. */
enum class NotificationDetailLevel(val label: String) {
    /** Task text in the shade; the lock screen gets a generic public version. */
    FULL_TASK("Aufgabe anzeigen"),
    /** Only generic Focus status, never the task. */
    GENERIC("Nur Fokus-Status"),
    /** Minimal text; hidden on the lock screen. */
    HIDDEN("Inhalte ausblenden"),
}

enum class MotionPreference(val label: String) { SYSTEM("Wie System"), REDUCED("Reduziert") }

/** Small non-sensitive settings (DataStore). Never holds History, Missions or secrets. */
data class AppPreferences(
    val defaultQuickFocusMinutes: Int = DEFAULT_QUICK_MINUTES,
    val motion: MotionPreference = MotionPreference.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val notificationDetail: NotificationDetailLevel = NotificationDetailLevel.FULL_TASK,
    val missionRemindersEnabled: Boolean = true,
    val dailyPlanningEnabled: Boolean = false,
    val dailyPlanningTime: LocalTime = LocalTime.of(18, 0),
    val defaultProtectionMode: ProtectionMode = ProtectionMode.STANDARD,
    val usageMonitoringEnabled: Boolean = false,
    val doNotDisturbDuringFocus: Boolean = false,
    val onboardingCompleted: Boolean = false,
    /**
     * PWA `focus-mode-home-onboarding-v17-16`: set once the user has created a Mission (or has
     * Mission evidence). The large first-time Home card never returns after that.
     */
    val homeOnboardingDone: Boolean = false,
    val lastSelectedMissionId: String? = null,
    val notificationPermissionRequests: Int = 0,
    val featureFlags: FeatureFlags = FeatureFlags(),
) {
    companion object {
        const val DEFAULT_QUICK_MINUTES = 25
    }
}

/** Small feature flags. Development-only features stay off in release builds by default. */
data class FeatureFlags(
    val pwaImportEnabled: Boolean = false,
)
