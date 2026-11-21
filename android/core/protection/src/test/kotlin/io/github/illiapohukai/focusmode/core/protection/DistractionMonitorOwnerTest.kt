package io.github.illiapohukai.focusmode.core.protection

import io.github.illiapohukai.focusmode.core.domain.notification.NotificationCapability
import io.github.illiapohukai.focusmode.core.domain.platform.UsageAccessGateway
import io.github.illiapohukai.focusmode.core.domain.protection.CapabilityResolver
import io.github.illiapohukai.focusmode.core.domain.protection.ProtectionInputs
import io.github.illiapohukai.focusmode.core.domain.repository.DistractionLog
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusRequest
import io.github.illiapohukai.focusmode.core.domain.session.SessionFactory
import io.github.illiapohukai.focusmode.core.model.AppAvailability
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.ManagedDeviceStatus
import io.github.illiapohukai.focusmode.core.model.PermissionSnapshot
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ProtectionPermissionState
import io.github.illiapohukai.focusmode.core.model.ResolvedSelectedApp
import io.github.illiapohukai.focusmode.core.model.SelectedDistractingApp
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** v18.1: one monitor job per protected Session, always cleaned up, never on the main thread. */
@OptIn(ExperimentalCoroutinesApi::class)
class DistractionMonitorOwnerTest {
    private val clock = FakeClock()
    private val factory = SessionFactory(clock)
    private val protectedSession = factory.quick(QuickFocusRequest("Lesen", 25, ProtectionMode.PROTECTED))
    private val standardSession = factory.quick(QuickFocusRequest("Lesen", 25, ProtectionMode.STANDARD))

    private class CountingUsage(var access: Boolean = true) : UsageAccessGateway {
        var accessChecks = 0
        var queries = 0
        override fun isSupported() = true
        override fun hasAccess(): Boolean { accessChecks++; return access }
        override fun foregroundEvents(fromMs: Long, toMs: Long, watched: Set<String>): List<Pair<String, Long>> { queries++; return emptyList() }
    }

    private class Screen(on: Boolean = true) : ScreenState {
        val on = MutableStateFlow(on)
        override fun isInteractive() = on.value
        override suspend fun awaitInteractive() { on.first { it } }
    }

    private object NoLog : DistractionLog {
        override suspend fun record(sessionId: SessionId, packageName: String, at: Instant) = Unit
        override fun observeCount(sessionId: SessionId): Flow<Int> = flowOf(0)
        override suspend fun purgeOlderThan(cutoff: Instant) = 0
        override suspend fun purgeAll() = 0
    }

    private fun state(access: Boolean = true, enabled: Boolean = true, apps: List<String> = listOf("com.example.video")): ProtectionState {
        val snapshot = PermissionSnapshot(
            notifications = ProtectionPermissionState.GRANTED,
            usageAccess = if (access) ProtectionPermissionState.GRANTED else ProtectionPermissionState.DENIED,
            doNotDisturb = ProtectionPermissionState.DENIED,
            exactAlarm = ProtectionPermissionState.DENIED,
            batteryOptimizationIgnored = false,
            managedDevice = ManagedDeviceStatus.NOT_MANAGED,
        )
        val selected = apps.map { ResolvedSelectedApp(SelectedDistractingApp(it, it, Instant.EPOCH), AppAvailability.INSTALLED, it, false) }
        return ProtectionState(
            overview = CapabilityResolver.resolve(ProtectionInputs(snapshot, selected.size, enabled, false)),
            notifications = NotificationCapability(ProtectionPermissionState.GRANTED, true, emptySet(), true),
            snapshot = snapshot,
            selectedApps = selected,
            preferences = AppPreferences(usageMonitoringEnabled = enabled),
        )
    }

    private class Harness(scope: TestScope, initial: ProtectionState, val usage: CountingUsage = CountingUsage(), val screen: Screen = Screen()) {
        val states = MutableStateFlow<ProtectionState?>(initial)
        val revokedCalls = mutableListOf<Boolean>()
        val platform = FakePlatform()
        val monitor = DistractionMonitor(usage, NoLog, platform, FakeClock(), screen)
        val owner = DistractionMonitorOwner(monitor, states, { revokedCalls += it }, StandardTestDispatcher(scope.testScheduler))
    }

    private fun TestScope.poll(times: Int) { advanceTimeBy(DistractionMonitor.POLL_INTERVAL_MS * times + 1); runCurrent() }

    @Test fun exactlyOneJobPerSessionEvenWhenTheRunningSessionIsReEmitted() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        assertTrue(h.owner.start(service, protectedSession) { 10 })
        // Every minute checkpoint re-emits Running with an updated copy of the same Session.
        repeat(5) { assertFalse(h.owner.start(service, protectedSession.copy(title = "checkpoint $it")) { 10 }) }
        poll(3)
        assertEquals(protectedSession.id, h.owner.monitoredSessionId)
        assertEquals(3, h.usage.queries) // one poll per interval, not one per start call
        h.owner.stop()
    }

    @Test fun cleanupAfterCompletion() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        h.owner.start(service, protectedSession) { 10 }
        poll(1)
        h.owner.stop() // Session completed: the service shuts down
        val polled = h.usage.queries
        poll(10)
        assertEquals(polled, h.usage.queries)
        assertNull(h.owner.monitoredSessionId)
        assertEquals(listOf(false), h.revokedCalls) // the per-Session revoked flag is reset
    }

    @Test fun cleanupAfterCancellationOfTheServiceScope() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        h.owner.start(service, protectedSession) { 10 }
        poll(1)
        service.cancel() // Session cancelled: the service is destroyed with its lifecycle scope
        val polled = h.usage.queries
        poll(10)
        assertEquals(polled, h.usage.queries)
        assertNull(h.owner.monitoredSessionId)
        h.owner.stop() // safe after the scope is gone
    }

    @Test fun processRestorationStartsOneFreshJobWithoutDuplicates() = runTest {
        val before = Harness(this, state())
        val oldProcess = CoroutineScope(backgroundScope.coroutineContext + Job())
        before.owner.start(oldProcess, protectedSession) { 10 }
        poll(1)
        oldProcess.cancel() // process death

        val after = Harness(this, state()) // new process: new singletons
        val restored = CoroutineScope(backgroundScope.coroutineContext + Job())
        assertTrue(after.owner.start(restored, protectedSession) { 10 })
        assertFalse(after.owner.start(restored, protectedSession) { 10 })
        val oldPolls = before.usage.queries
        poll(4)
        assertEquals(oldPolls, before.usage.queries)
        assertEquals(4, after.usage.queries)
        after.owner.stop()
    }

    @Test fun revokedPermissionStopsPollingUntilAccessReturns() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        h.owner.start(service, protectedSession) { 10 }
        poll(2)
        h.usage.access = false // the user revokes Usage Access in system settings
        poll(1)
        assertEquals(listOf(true), h.revokedCalls)
        h.states.value = state(access = false) // the capability owner re-reads the platform state
        val checks = h.usage.accessChecks
        poll(20)
        assertEquals("no polling and no restart loop while access is missing", checks, h.usage.accessChecks)
        // Access granted again (re-read on app resume): monitoring resumes within the same Session.
        h.usage.access = true
        h.states.value = state(access = true)
        poll(1)
        assertTrue(h.usage.accessChecks > checks)
        h.owner.stop()
    }

    @Test fun screenOffSuspendsPollingWithoutPeriodicWakeups() = runTest {
        val screen = Screen(on = true)
        val h = Harness(this, state(), screen = screen)
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        h.owner.start(service, protectedSession) { 10 }
        poll(1)
        assertEquals(1, h.usage.accessChecks)
        screen.on.value = false
        advanceTimeBy(30 * 60_000L); runCurrent() // half an hour with the screen off
        assertEquals(1, h.usage.accessChecks)
        assertEquals(1, h.usage.queries)
        screen.on.value = true
        poll(1)
        assertEquals(2, h.usage.queries)
        h.owner.stop()
    }

    @Test fun standardSessionAndUnplannableStatesNeverPoll() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        assertFalse(h.owner.start(service, standardSession) { 10 })
        poll(5)
        assertEquals(0, h.usage.accessChecks)
        assertNull(h.owner.monitoredSessionId)

        assertNull(MonitorPolicy.plan(protectedSession, state(enabled = false)))
        assertNull(MonitorPolicy.plan(protectedSession, state(access = false)))
        assertNull(MonitorPolicy.plan(protectedSession, state(apps = emptyList())))
        assertEquals(setOf("com.example.video"), MonitorPolicy.plan(protectedSession, state())?.watched)

        // Monitoring switched off: the protected Session owns a job, but it never polls.
        h.states.value = state(enabled = false)
        assertTrue(h.owner.start(service, protectedSession) { 10 })
        poll(5)
        assertEquals(0, h.usage.accessChecks)
        h.owner.stop()
    }

    @Test fun aNewSessionReplacesThePreviousJob() = runTest {
        val h = Harness(this, state())
        val service = CoroutineScope(backgroundScope.coroutineContext + Job())
        h.owner.start(service, protectedSession) { 10 }
        val next: FocusSession = factory.quick(QuickFocusRequest("Schreiben", 25, ProtectionMode.PROTECTED))
        assertTrue(h.owner.start(service, next) { 10 })
        poll(2)
        assertEquals(next.id, h.owner.monitoredSessionId)
        assertEquals(2, h.usage.queries)
        h.owner.stop()
    }
}
