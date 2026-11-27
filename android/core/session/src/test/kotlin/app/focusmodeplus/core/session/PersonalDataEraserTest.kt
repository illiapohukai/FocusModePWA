package app.focusmodeplus.core.session

import app.focusmodeplus.core.domain.backup.EraseOutcome
import app.focusmodeplus.core.domain.backup.PersonalDataEraser
import app.focusmodeplus.core.domain.backup.PersonalDataStore
import app.focusmodeplus.core.domain.mission.MissionAdaptationOwner
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.domain.platform.ReminderScheduler
import app.focusmodeplus.core.domain.platform.ReminderSyncReason
import app.focusmodeplus.core.domain.session.QuickFocusRequest
import app.focusmodeplus.core.domain.session.RestoreTrigger
import app.focusmodeplus.core.domain.session.SessionState
import app.focusmodeplus.core.domain.session.StartResult
import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMaterialStore
import app.focusmodeplus.core.testing.FakeMissionAdapter
import app.focusmodeplus.core.testing.FakeMissionGenerator
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakePlatform
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.FakeSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Alle persönlichen und nutzergenerierten Daten löschen" through the real Session owner. */
class PersonalDataEraserTest {
    private val clock = FakeClock()
    private val store = FakeSessionStore(clock)
    private val missions = FakeMissionRepository(clock).also { store.missions = it }
    private val prefs = FakePreferencesRepository(AppPreferences(defaultQuickFocusMinutes = 45, homeOnboardingDone = true, doNotDisturbDuringFocus = true, notificationPermissionRequests = 2))
    private val platform = FakePlatform().apply { dndAccess = true }
    private val material = FakeMaterialStore()
    private val calls = mutableListOf<String>()

    private val records = object : PersonalDataStore {
        var rows = 7
        override suspend fun wipeRecords() { calls += "wipe"; rows = 1 }
        override suspend fun clearReminderRows() { calls += "reminders"; rows = 0 }
        override suspend fun remainingRows() = rows
    }
    private val scheduler = object : ReminderScheduler {
        override suspend fun sync(reason: ReminderSyncReason) { calls += "sync:$reason" }
        override suspend fun deliver(key: String) = Unit
    }

    private fun TestScope.eraser(session: FocusSessionManager) = PersonalDataEraser(
        session,
        MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, FakeNetworkStatus(), clock, backgroundScope),
        MissionAdaptationOwner(missions, store, FakeMissionAdapter(), FakeNetworkStatus(), backgroundScope),
        records, prefs, material, scheduler, platform,
    )

    @Test fun runningSessionIsAbandonedWithoutHistoryAndEverythingIsCleared() = runTest {
        val session = FocusSessionManager(store, store, missions, prefs, clock, platform, platform, platform, platform)
        session.restore(RestoreTrigger.APP_START)
        assertTrue(session.startQuick(QuickFocusRequest("Lineare Funktionen", 25, ProtectionMode.STANDARD)) is StartResult.Started)
        assertTrue(platform.runtimeRunning)
        clock.advanceMinutes(10)

        assertEquals(EraseOutcome.COMPLETE, eraser(session).eraseAll())

        assertTrue(session.state.value is SessionState.Idle)
        assertFalse("foreground service stopped", platform.runtimeRunning)
        assertNull("end alarm cancelled", platform.alarmAt)
        assertTrue(platform.dndRestores >= 1)
        assertTrue("no History record is written for an erased Session", store.observeResults().first().isEmpty())
        assertEquals(listOf("wipe", "sync:DATA_CHANGED", "reminders"), calls)
        assertEquals(1, platform.allCleared)
        assertEquals(1, material.cleared)
        val p = prefs.current()
        assertEquals(AppPreferences.DEFAULT_QUICK_MINUTES, p.defaultQuickFocusMinutes)
        assertFalse("the first-time Home returns", p.homeOnboardingDone)
        assertFalse(p.doNotDisturbDuringFocus)
        assertEquals("technical metadata stays", 2, p.notificationPermissionRequests)
    }

    @Test fun idleAppErasesToo() = runTest {
        val session = FocusSessionManager(store, store, missions, prefs, clock, platform, platform, platform, platform)
        session.restore(RestoreTrigger.APP_START)
        assertEquals(EraseOutcome.COMPLETE, eraser(session).eraseAll())
        assertEquals(listOf("wipe", "sync:DATA_CHANGED", "reminders"), calls)
    }

    @Test fun leftoverRowsAreReportedAsIncomplete() = runTest {
        val session = FocusSessionManager(store, store, missions, prefs, clock, platform, platform, platform, platform)
        session.restore(RestoreTrigger.APP_START)
        val stubborn = object : PersonalDataStore {
            override suspend fun wipeRecords() = Unit
            override suspend fun clearReminderRows() = Unit
            override suspend fun remainingRows() = 3
        }
        val e = PersonalDataEraser(
            session,
            MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, FakeNetworkStatus(), clock, backgroundScope),
            MissionAdaptationOwner(missions, store, FakeMissionAdapter(), FakeNetworkStatus(), backgroundScope),
            stubborn, prefs, material, scheduler, platform,
        )
        assertEquals(EraseOutcome.INCOMPLETE, e.eraseAll())
    }
}
