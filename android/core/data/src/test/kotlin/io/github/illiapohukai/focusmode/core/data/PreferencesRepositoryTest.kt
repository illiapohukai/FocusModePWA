package io.github.illiapohukai.focusmode.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.data.prefs.DataStorePreferencesRepository
import io.github.illiapohukai.focusmode.core.data.prefs.FeatureFlagDefaults
import io.github.illiapohukai.focusmode.core.data.prefs.PreferencesSchemaMigration
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(file: File, scope: CoroutineScope, migrate: Boolean = true) = PreferenceDataStoreFactory.create(
        migrations = if (migrate) listOf(PreferencesSchemaMigration) else emptyList(),
        scope = scope,
        produceFile = { file },
    )

    @Test fun defaultsUpdatesAndSanitizedReads() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStorePreferencesRepository(store(File(tmp.root, "a.preferences_pb"), scope), FeatureFlagDefaults(pwaImportEnabled = false))
        assertEquals(AppPreferences(), repo.current())
        repo.setDefaultQuickFocusMinutes(42) // snapped to the 5-minute contract
        repo.setNotificationDetail(NotificationDetailLevel.HIDDEN)
        repo.setDefaultProtectionMode(ProtectionMode.STRICT_MANAGED) // never persisted
        repo.setLastSelectedMission("bad id!")
        repeat(2) { repo.incrementNotificationPermissionRequests() }
        val p = repo.current()
        assertEquals(40, p.defaultQuickFocusMinutes)
        assertEquals(NotificationDetailLevel.HIDDEN, p.notificationDetail)
        assertEquals(ProtectionMode.STANDARD, p.defaultProtectionMode)
        assertEquals(null, p.lastSelectedMissionId)
        assertEquals(2, p.notificationPermissionRequests)
        assertFalse(p.featureFlags.pwaImportEnabled)
        scope.cancel()
    }

    @Test fun featureFlagDefaultComesFromBuild() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DataStorePreferencesRepository(store(File(tmp.root, "b.preferences_pb"), scope), FeatureFlagDefaults(pwaImportEnabled = true))
        assertTrue(repo.current().featureFlags.pwaImportEnabled)
        repo.setPwaImportEnabled(false)
        assertFalse(repo.current().featureFlags.pwaImportEnabled)
        scope.cancel()
    }

    @Test fun schemaMigrationNormalizesLegacyValues() = runTest {
        val file = File(tmp.root, "c.preferences_pb")
        val legacyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store(file, legacyScope, migrate = false).edit {
            it[intPreferencesKey("quick_focus_minutes")] = 27
            it[stringPreferencesKey("protection_mode")] = "UNKNOWN"
        }
        // Wait until the first DataStore has really released the file (cancel() alone returns early).
        legacyScope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val migrated = store(file, scope)
        val repo = DataStorePreferencesRepository(migrated, FeatureFlagDefaults(false))
        assertEquals(25, repo.current().defaultQuickFocusMinutes)
        assertEquals(ProtectionMode.STANDARD, repo.current().defaultProtectionMode)
        scope.cancel()
    }
}
