package app.focusmodeplus.core.data.prefs

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.session.QuickFocusDuration
import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.EntityIds
import app.focusmodeplus.core.model.FeatureFlags
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.ProtectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Build-dependent defaults for feature flags (development features default off in release). */
data class FeatureFlagDefaults(val pwaImportEnabled: Boolean)

internal object PrefKeys {
    val VERSION = intPreferencesKey("prefs_version")
    val QUICK_MINUTES = intPreferencesKey("quick_focus_minutes")
    val MOTION = stringPreferencesKey("motion")
    val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    val NOTIFICATION_DETAIL = stringPreferencesKey("notification_detail")
    val MISSION_REMINDERS = booleanPreferencesKey("mission_reminders")
    val DAILY_PLANNING = booleanPreferencesKey("daily_planning")
    val DAILY_PLANNING_MINUTE = intPreferencesKey("daily_planning_minute")
    val PROTECTION_MODE = stringPreferencesKey("protection_mode")
    val USAGE_MONITORING = booleanPreferencesKey("usage_monitoring")
    val DND = booleanPreferencesKey("dnd_during_focus")
    val ONBOARDING = booleanPreferencesKey("onboarding_completed")
    val HOME_ONBOARDING_DONE = booleanPreferencesKey("home_mission_onboarding_done")
    val LAST_MISSION = stringPreferencesKey("last_selected_mission")
    val NOTIFICATION_REQUESTS = intPreferencesKey("notification_permission_requests")
    val FLAG_PWA_IMPORT = booleanPreferencesKey("flag_pwa_import")
    val LAST_EXPORT_AT = androidx.datastore.preferences.core.longPreferencesKey("last_export_at")

    /** User preferences removed by full data deletion (everything except technical metadata). */
    val USER_KEYS = listOf(
        QUICK_MINUTES, MOTION, NOTIFICATIONS, NOTIFICATION_DETAIL, MISSION_REMINDERS, DAILY_PLANNING, DAILY_PLANNING_MINUTE,
        PROTECTION_MODE, USAGE_MONITORING, DND, ONBOARDING, HOME_ONBOARDING_DONE, LAST_MISSION, LAST_EXPORT_AT,
    )
}

/** v0 -> v1: snaps a stored Quick Focus duration onto the 5-minute contract. */
object PreferencesSchemaMigration : DataMigration<Preferences> {
    const val CURRENT = 1
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = (currentData[PrefKeys.VERSION] ?: 0) < CURRENT
    override suspend fun migrate(currentData: Preferences): Preferences {
        val p = currentData.toMutablePreferences()
        p[PrefKeys.QUICK_MINUTES]?.let { p[PrefKeys.QUICK_MINUTES] = QuickFocusDuration.normalize(it) }
        p[PrefKeys.VERSION] = CURRENT
        return p.toPreferences()
    }
    override suspend fun cleanUp() = Unit
}

/** Small, non-sensitive settings. Values are re-validated on every read. */
@Singleton
class DataStorePreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val flagDefaults: FeatureFlagDefaults,
) : PreferencesRepository {

    override val preferences: Flow<AppPreferences> = store.data.map(::read)

    override suspend fun current(): AppPreferences = preferences.first()

    private fun read(p: Preferences): AppPreferences {
        val d = AppPreferences()
        return AppPreferences(
            defaultQuickFocusMinutes = p[PrefKeys.QUICK_MINUTES]?.takeIf(QuickFocusDuration::isValid) ?: d.defaultQuickFocusMinutes,
            motion = enumValueOrNull<MotionPreference>(p[PrefKeys.MOTION]) ?: d.motion,
            notificationsEnabled = p[PrefKeys.NOTIFICATIONS] ?: d.notificationsEnabled,
            notificationDetail = enumValueOrNull<NotificationDetailLevel>(p[PrefKeys.NOTIFICATION_DETAIL]) ?: d.notificationDetail,
            missionRemindersEnabled = p[PrefKeys.MISSION_REMINDERS] ?: d.missionRemindersEnabled,
            dailyPlanningEnabled = p[PrefKeys.DAILY_PLANNING] ?: d.dailyPlanningEnabled,
            dailyPlanningTime = p[PrefKeys.DAILY_PLANNING_MINUTE]?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) } ?: d.dailyPlanningTime,
            defaultProtectionMode = enumValueOrNull<ProtectionMode>(p[PrefKeys.PROTECTION_MODE])?.takeIf { it != ProtectionMode.STRICT_MANAGED } ?: d.defaultProtectionMode,
            usageMonitoringEnabled = p[PrefKeys.USAGE_MONITORING] ?: d.usageMonitoringEnabled,
            doNotDisturbDuringFocus = p[PrefKeys.DND] ?: d.doNotDisturbDuringFocus,
            onboardingCompleted = p[PrefKeys.ONBOARDING] ?: d.onboardingCompleted,
            homeOnboardingDone = p[PrefKeys.HOME_ONBOARDING_DONE] ?: d.homeOnboardingDone,
            lastSelectedMissionId = p[PrefKeys.LAST_MISSION]?.takeIf(EntityIds::isSafe),
            notificationPermissionRequests = (p[PrefKeys.NOTIFICATION_REQUESTS] ?: 0).coerceAtLeast(0),
            featureFlags = FeatureFlags(pwaImportEnabled = p[PrefKeys.FLAG_PWA_IMPORT] ?: flagDefaults.pwaImportEnabled),
            lastExportAt = p[PrefKeys.LAST_EXPORT_AT]?.takeIf { it > 0 }?.let(java.time.Instant::ofEpochMilli),
        )
    }

    private inline fun <reified E : Enum<E>> enumValueOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    override suspend fun setDefaultQuickFocusMinutes(minutes: Int) {
        store.edit { it[PrefKeys.QUICK_MINUTES] = QuickFocusDuration.normalize(minutes) }
    }
    override suspend fun setMotion(preference: MotionPreference) { store.edit { it[PrefKeys.MOTION] = preference.name } }
    override suspend fun setNotificationsEnabled(enabled: Boolean) { store.edit { it[PrefKeys.NOTIFICATIONS] = enabled } }
    override suspend fun setNotificationDetail(level: NotificationDetailLevel) { store.edit { it[PrefKeys.NOTIFICATION_DETAIL] = level.name } }
    override suspend fun setMissionRemindersEnabled(enabled: Boolean) { store.edit { it[PrefKeys.MISSION_REMINDERS] = enabled } }
    override suspend fun setDailyPlanning(enabled: Boolean, time: LocalTime) {
        store.edit { it[PrefKeys.DAILY_PLANNING] = enabled; it[PrefKeys.DAILY_PLANNING_MINUTE] = time.hour * 60 + time.minute }
    }
    override suspend fun setDefaultProtectionMode(mode: ProtectionMode) {
        if (mode == ProtectionMode.STRICT_MANAGED) return // never persisted as a consumer default
        store.edit { it[PrefKeys.PROTECTION_MODE] = mode.name }
    }
    override suspend fun setUsageMonitoringEnabled(enabled: Boolean) { store.edit { it[PrefKeys.USAGE_MONITORING] = enabled } }
    override suspend fun setDoNotDisturbDuringFocus(enabled: Boolean) { store.edit { it[PrefKeys.DND] = enabled } }
    override suspend fun setOnboardingCompleted(done: Boolean) { store.edit { it[PrefKeys.ONBOARDING] = done } }
    /** One-way: set once, never cleared by Mission completion, archive or delete. */
    override suspend fun markHomeOnboardingDone() { store.edit { if (it[PrefKeys.HOME_ONBOARDING_DONE] != true) it[PrefKeys.HOME_ONBOARDING_DONE] = true } }
    override suspend fun setLastSelectedMission(id: String?) {
        store.edit { if (id != null && EntityIds.isSafe(id)) it[PrefKeys.LAST_MISSION] = id else it.remove(PrefKeys.LAST_MISSION) }
    }
    override suspend fun incrementNotificationPermissionRequests() {
        store.edit { it[PrefKeys.NOTIFICATION_REQUESTS] = (it[PrefKeys.NOTIFICATION_REQUESTS] ?: 0) + 1 }
    }
    override suspend fun setPwaImportEnabled(enabled: Boolean) { store.edit { it[PrefKeys.FLAG_PWA_IMPORT] = enabled } }
    override suspend fun setLastExportAt(at: java.time.Instant) { store.edit { it[PrefKeys.LAST_EXPORT_AT] = at.toEpochMilli() } }
    /** One edit, so a crash never leaves half of the user settings behind. */
    override suspend fun resetUserPreferences() { store.edit { p -> PrefKeys.USER_KEYS.forEach { p.remove(it) } } }
}
