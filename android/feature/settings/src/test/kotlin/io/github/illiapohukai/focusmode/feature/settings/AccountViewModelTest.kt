package io.github.illiapohukai.focusmode.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.domain.account.AccountLifecycle
import io.github.illiapohukai.focusmode.core.domain.backup.PersonalDataEraser
import io.github.illiapohukai.focusmode.core.domain.backup.PersonalDataStore
import io.github.illiapohukai.focusmode.core.domain.mission.MissionAdaptationOwner
import io.github.illiapohukai.focusmode.core.domain.mission.MissionGenerationOwner
import io.github.illiapohukai.focusmode.core.domain.sync.SyncLocalStore
import io.github.illiapohukai.focusmode.core.model.AccountResult
import io.github.illiapohukai.focusmode.core.model.AccountState
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import io.github.illiapohukai.focusmode.core.model.SyncPhase
import io.github.illiapohukai.focusmode.core.model.SyncResult
import io.github.illiapohukai.focusmode.core.testing.FakeAccountRepository
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeHost
import io.github.illiapohukai.focusmode.core.testing.FakeMaterialStore
import io.github.illiapohukai.focusmode.core.testing.FakeMissionAdapter
import io.github.illiapohukai.focusmode.core.testing.FakeMissionGenerator
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakeNetworkStatus
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSessionStore
import io.github.illiapohukai.focusmode.core.testing.FakeSyncRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSyncScheduler
import io.github.illiapohukai.focusmode.core.testing.FakeRemoteSyncStore
import io.github.illiapohukai.focusmode.core.testing.FakeUsers
import io.github.illiapohukai.focusmode.core.testing.IdleSessionController
import io.github.illiapohukai.focusmode.core.testing.MainDispatcherRule
import io.github.illiapohukai.focusmode.core.testing.NoopReminderScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** The account ViewModel only orchestrates domain owners; one action at a time, honest feedback. */
@RunWith(AndroidJUnit4::class)
class AccountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val accounts = FakeAccountRepository()
    private val sync = FakeSyncRepository()
    private val clock = FakeClock()

    /** The lifecycle's local store is never reached by these tests. */
    private val unusedLocal = Proxy.newProxyInstance(SyncLocalStore::class.java.classLoader, arrayOf(SyncLocalStore::class.java)) { _, _, _ -> error("unused") } as SyncLocalStore

    private fun TestScope.vm(): AccountViewModel {
        val prefs = FakePreferencesRepository()
        val missions = FakeMissionRepository(clock)
        val sessions = FakeSessionStore(clock)
        val material = FakeMaterialStore()
        val records = object : PersonalDataStore {
            override suspend fun wipeRecords() = Unit
            override suspend fun clearReminderRows() = Unit
            override suspend fun remainingRows() = 0
        }
        val eraser = PersonalDataEraser(
            IdleSessionController(),
            MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, FakeNetworkStatus(), clock, backgroundScope),
            MissionAdaptationOwner(missions, sessions, FakeMissionAdapter(), FakeNetworkStatus(), backgroundScope),
            records, prefs, material, NoopReminderScheduler(), FakePlatform(),
        )
        val lifecycle = AccountLifecycle(accounts, sync, eraser, FakeRemoteSyncStore(), unusedLocal, FakeSyncScheduler())
        return AccountViewModel(accounts, sync, lifecycle, missions, sessions, clock).also { it.uiState.launchIn(backgroundScope) }
    }

    @Test fun duplicateTapsStartOneMigration() = runTest {
        accounts.signInAs(FakeUsers.ALICE)
        val gate = CompletableDeferred<Unit>()
        sync.gate = gate
        val vm = vm()
        vm.linkLocalData()
        vm.linkLocalData()
        vm.linkLocalData()
        gate.complete(Unit)
        val state = vm.uiState.first { it.feedback != null }
        assertEquals(listOf("migrate:${FakeUsers.ALICE.uid}"), sync.calls.filter { it.startsWith("migrate") })
        assertEquals("Lokale Daten verbunden", state.feedback!!.title)
    }

    @Test fun offlineMigrationSaysItContinues() = runTest {
        accounts.signInAs(FakeUsers.ALICE)
        sync.nextResult = SyncResult.Failed(SyncFailure.OFFLINE, retryable = true)
        val vm = vm()
        vm.linkLocalData()
        val state = vm.uiState.first { it.feedback != null }
        assertEquals("Datenübernahme läuft weiter", state.feedback!!.title)
        assertTrue(state.feedback!!.body.contains("automatisch fortgesetzt"))
    }

    @Test fun aCanceledChooserChangesNothingAndNeedsNoBanner() = runTest {
        accounts.nextSignIn = AccountResult.Failure(AccountState.SignInCanceled)
        val vm = vm()
        vm.signIn(FakeHost)
        val state = vm.uiState.first { it.account == AccountState.SignInCanceled && !it.busy }
        assertNull(state.feedback)
        assertEquals(1, accounts.signIns)
    }

    @Test fun anOpenDecisionLoadsThePreview() = runTest {
        accounts.signInAs(FakeUsers.ALICE)
        val vm = vm()
        sync.syncState.value = sync.syncState.value.copy(phase = SyncPhase.DECISION_REQUIRED)
        val state = vm.uiState.first { it.preview != null }
        assertEquals(18, state.preview!!.sessions)
        vm.postpone()
        assertTrue(vm.uiState.first { it.decisionPostponed }.decisionPostponed)
        assertTrue("postponing never links", sync.calls.none { it.startsWith("migrate") })
    }

    @Test fun unconfiguredSignInExplainsItself() = runTest {
        val unconfigured = FakeAccountRepository(configured = false)
        val prefs = FakePreferencesRepository()
        val missions = FakeMissionRepository(clock)
        val sessions = FakeSessionStore(clock)
        val material = FakeMaterialStore()
        val eraser = PersonalDataEraser(
            IdleSessionController(),
            MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, FakeNetworkStatus(), clock, backgroundScope),
            MissionAdaptationOwner(missions, sessions, FakeMissionAdapter(), FakeNetworkStatus(), backgroundScope),
            object : PersonalDataStore {
                override suspend fun wipeRecords() = Unit
                override suspend fun clearReminderRows() = Unit
                override suspend fun remainingRows() = 0
            },
            prefs, material, NoopReminderScheduler(), FakePlatform(),
        )
        val vm = AccountViewModel(unconfigured, sync, AccountLifecycle(unconfigured, sync, eraser, FakeRemoteSyncStore(), unusedLocal, FakeSyncScheduler()), missions, sessions, clock)
        vm.uiState.launchIn(backgroundScope)
        vm.signIn(FakeHost)
        val state = vm.uiState.first { it.feedback != null }
        assertEquals(AccountCopy.UNCONFIGURED, state.feedback!!.body)
        assertEquals(AccountState.FirebaseUnconfigured, state.account)
    }
}
