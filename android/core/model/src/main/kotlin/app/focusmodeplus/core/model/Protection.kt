package app.focusmodeplus.core.model

/**
 * The three protection levels. Only what the platform can really do is ever shown as active.
 * - STANDARD: timer, ongoing notification, optional Do Not Disturb, ordinary end action.
 * - PROTECTED: end barrier (intentional delay + reason), selected-app awareness through Usage
 *   Access where granted, and an always-available emergency exit.
 * - STRICT_MANAGED: capability boundary only. Requires a managed (device-/profile-owner)
 *   deployment; never offered as active on an ordinary consumer phone.
 */
enum class ProtectionMode(val label: String) {
    STANDARD("Standard-Fokus"),
    PROTECTED("Geschützter Fokus"),
    STRICT_MANAGED("Strikter Modus"),
}

/** Capability states (see docs/protection-capabilities.md). Never collapsed into one boolean. */
enum class ProtectionCapability {
    UNSUPPORTED,
    NOT_CONFIGURED,
    NOTIFICATION_PERMISSION_NEEDED,
    USAGE_ACCESS_NEEDED,
    DO_NOT_DISTURB_ACCESS_NEEDED,
    ACCESSIBILITY_NOT_AVAILABLE,
    MANAGED_DEVICE_REQUIRED,
    READY_FOR_SOFT_PROTECTION,
    READY_FOR_USAGE_MONITORING,
    READY_FOR_MANAGED_BLOCKING,
    TEMPORARILY_UNAVAILABLE,
}

/** Status of one system permission or special access as the OS reports it right now. */
enum class ProtectionPermissionState {
    GRANTED,
    NOT_REQUESTED,
    DENIED,
    PERMANENTLY_DENIED,
    /** Granted implicitly by the platform (e.g. notifications before Android 13). */
    NOT_REQUIRED,
    UNSUPPORTED,
}

enum class PermissionKind {
    NOTIFICATIONS, USAGE_ACCESS, DO_NOT_DISTURB, EXACT_ALARM, BATTERY_OPTIMIZATION, MANAGED_DEVICE, ACCESSIBILITY,
}

/** Settings screens the app may open. Mapped to real Intents only in the platform layer. */
enum class SettingsTarget {
    APP_NOTIFICATION_SETTINGS,
    USAGE_ACCESS_SETTINGS,
    DND_ACCESS_SETTINGS,
    EXACT_ALARM_SETTINGS,
    BATTERY_OPTIMIZATION_SETTINGS,
    APP_DETAILS,
}

enum class ManagedDeviceStatus {
    /** Ordinary consumer install: no device/profile owner. */
    NOT_MANAGED,
    /** The app is device or profile owner, but v18 ships no enforcement implementation. */
    OWNER_DETECTED_NOT_SUPPORTED,
    /** Reserved for a future managed implementation; produced only by test fakes in v18. */
    MANAGED_ENFORCEMENT_READY,
}

/** Raw platform readings the capability owner refreshes on resume (never cached as truth). */
data class PermissionSnapshot(
    val notifications: ProtectionPermissionState,
    val usageAccess: ProtectionPermissionState,
    val doNotDisturb: ProtectionPermissionState,
    val exactAlarm: ProtectionPermissionState,
    val batteryOptimizationIgnored: Boolean?,
    val managedDevice: ManagedDeviceStatus,
)

/** One explained capability: what it means, what to do next, and what it cannot do. */
data class CapabilityAssessment(
    val capability: ProtectionCapability,
    val title: String,
    val explanation: String,
    val nextAction: String?,
    val settingsTarget: SettingsTarget?,
    val permission: ProtectionPermissionState?,
    val limitation: String,
)
