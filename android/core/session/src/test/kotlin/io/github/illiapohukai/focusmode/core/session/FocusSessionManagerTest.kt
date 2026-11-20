package io.github.illiapohukai.focusmode.core.session

import io.github.illiapohukai.focusmode.core.domain.session.FinishResult
import io.github.illiapohukai.focusmode.core.domain.session.FreezeCause
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusRequest
import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.SessionValidationError
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSessionStore
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionManagerTest {
    private val clock = FakeClock()
    private val store = FakeSessionStore(clock)
    private val missions = FakeMissionRepository(clock).also { store.missions = it }
    private val prefs = FakePreferencesRepository()
    private val platform = FakePlatform()

    /** A new manager over the same durable store models process death and recreation. */
    private fun manager() = FocusSessionManager(store, store, missions, prefs, clock, platform, platform, platform, platform)

    private suspend fun started(m: FocusSessionManager = manager(), minutes: Int = 25, mode: ProtectionMode = ProtectionMode.STANDARD): FocusSessionManager {
        m.restore(RestoreTrigger.APP_START)
        assertTrue(m.startQuick(QuickFocusRequest("Lineare Funktionen", minutes, mode)) is StartResult.Started)
        return m
    }

    @Test fun startPersistsOnceAndStartsVisibleRuntime() = runTest {
        val m = started()
        assertTrue(m.state.value is SessionState.Running)
        assertEquals(1, store.saves)
        assertTrue(platform.runtimeRunning)
        assertEquals(clock.realtimeMs + 25 * 60_000L, platform.alarmAt)
        assertEquals(25, prefs.current().defaultQuickFocusMinutes)
    }

    @Test fun secondStartAndInvalidInputsAreRejected() = runTest {
        val m = started()
        assertEquals(StartResult.Rejected(SessionValidationError.SessionAlreadyActive), m.startQuick(QuickFocusRequest(null, 25, ProtectionMode.STANDARD)))
        val fresh = FocusSessionManager(FakeSessionStore(clock).let { s -> s }, store, missions, prefs, clock, platform, platform, platform, platform)
        fresh.restore(RestoreTrigger.APP_START)
        assertEquals(StartResult.Rejected(SessionValidationError.DurationInvalid), fresh.startQuick(QuickFocusRequest(null, 7, ProtectionMode.STANDARD)))
        assertEquals(StartResult.Rejected(SessionValidationError.ModeUnavailable), fresh.startQuick(QuickFocusRequest(null, 25, ProtectionMode.STRICT_MANAGED)))
    }

    @Test fun ticksNeverWritePerSecondOnlyMinuteCheckpoints() = runTest {
        val m = started()
        repeat(30) { clock.advanceMs(1_000); m.onRuntimeTick() }
        assertEquals(1, store.saves)
        clock.advanceMs(31_000); m.onRuntimeTick()
        assertEquals(2, store.saves)
        assertEquals(61_000L, store.active!!.timing.checkpointElapsedMs)
    }

    @Test fun backgroundAndProcessRecreationKeepExactRemainingTime() = runTest {
        started()
        clock.advanceMinutes(9) // backgrounded, process killed
        val recreated = manager()
        recreated.restore(RestoreTrigger.APP_START)
        val running = recreated.state.value as SessionState.Running
        assertEquals(9 * 60_000L, recreated.read()!!.elapsedMs)
        assertEquals(16, recreated.read()!!.remainingMinutesCeil)
        assertTrue(platform.runtimeRunning)
        assertEquals(running.session.timing.segmentStartRealtimeMs!! + 25 * 60_000L, platform.alarmAt)
    }

    @Test fun completionWhileAwayFinalizesOnceWithPlannedEnd() = runTest {
        val m = started()
        val startedAt = (m.state.value as SessionState.Running).session.startedAt
        clock.advanceMinutes(70)
        val recreated = manager()
        recreated.restore(RestoreTrigger.APP_START)
        recreated.restore(RestoreTrigger.END_ALARM) // late alarm after the app already finalized
        val result = store.results.value.single()
        assertEquals(SessionEndReason.TIMER_FINISHED_WHILE_AWAY, result.endReason)
        assertEquals(SessionOutcome.COMPLETED, result.outcome)
        assertEquals(startedAt.plusSeconds(25 * 60), result.finishedAt)
        assertEquals(25, result.actualMinutes)
        assertNull(store.active)
        assertFalse(platform.runtimeRunning)
        assertNull(platform.alarmAt)
        assertEquals(1, platform.finishedShown.size)
        assertEquals(result.sessionId, (recreated.state.value as SessionState.Idle).lastFinalized ?: result.sessionId)
    }

    @Test fun endAlarmFinalizesRunningSession() = runTest {
        val m = started(minutes = 5)
        clock.advanceMinutes(5)
        m.restore(RestoreTrigger.END_ALARM)
        assertEquals(SessionEndReason.TIMER_FINISHED, store.results.value.single().endReason)
        assertEquals(SessionState.Idle(store.results.value.single().sessionId), m.state.value)
    }

    @Test fun userEndAndCancelDistinguishOutcomesAndFinalizeOnce() = runTest {
        val m = started()
        clock.advanceMinutes(12)
        val end = m.endByUser(EndNote.NEED_BREAK) as FinishResult.Finalized
        assertEquals(SessionOutcome.PARTIAL, end.result.outcome)
        assertEquals(EndNote.NEED_BREAK, end.result.endNote)
        assertEquals(FinishResult.NoActiveSession, m.endByUser())
        assertEquals(FinishResult.NoActiveSession, m.cancel())
        assertEquals(1, store.results.value.size)
        assertTrue(platform.cleared > 0)

        val m2 = started(m)
        clock.advanceMinutes(10)
        val cancelled = m2.cancel() as FinishResult.Finalized
        assertEquals(SessionStatus.CANCELLED, cancelled.result.status)
        assertFalse(cancelled.result.countsAsFocus)
        assertEquals(2, store.results.value.size)
        assertTrue(platform.finishedShown.isEmpty()) // user ends never notify
    }

    @Test fun endingBelowOneMinuteIsCancelled() = runTest {
        val m = started()
        clock.advanceMs(40_000)
        val r = (m.endByUser() as FinishResult.Finalized).result
        assertEquals(SessionEndReason.ENDED_TOO_EARLY, r.endReason)
        assertEquals(SessionOutcome.CANCELED, r.outcome)
    }

    @Test fun rebootFreezesAtCheckpointThenResumeContinuesExactly() = runTest {
        val m = started()
        clock.advanceMinutes(7); m.onRuntimeTick() // checkpoint at 7 min
        clock.advanceMs(40_000)
        clock.reboot()
        val afterBoot = manager()
        afterBoot.restore(RestoreTrigger.BOOT_COMPLETED)
        val frozen = afterBoot.state.value as SessionState.AwaitingResume
        assertEquals(FreezeCause.DEVICE_RESTARTED, frozen.cause)
        assertEquals(SessionLifecycle.AWAITING_RESUME, store.active!!.lifecycle)
        assertEquals(7 * 60_000L, afterBoot.read()!!.elapsedMs)
        assertEquals(1, platform.frozenShown.size)
        assertFalse(platform.runtimeRunning)

        clock.advanceMinutes(30) // frozen time never counts
        assertTrue(afterBoot.resumeRecovered())
        clock.advanceMinutes(3)
        assertEquals(10 * 60_000L, afterBoot.read()!!.elapsedMs)
        assertTrue(platform.runtimeRunning)
    }

    @Test fun frozenSessionCanBeSavedOrDiscarded() = runTest {
        val m = started()
        clock.advanceMinutes(4); m.onRuntimeTick()
        clock.reboot()
        val a = manager(); a.restore(RestoreTrigger.BOOT_COMPLETED)
        val saved = (a.saveRecovered() as FinishResult.Finalized).result
        assertEquals(SessionEndReason.RECOVERY_CONFIRMED, saved.endReason)
        assertEquals(4, saved.actualMinutes)

        val b = started(a)
        clock.advanceMs(30_000)
        clock.reboot()
        val c = manager(); c.restore(RestoreTrigger.BOOT_COMPLETED)
        assertTrue(c.saveRecovered() is FinishResult.Rejected) // below one minute, like the PWA
        assertTrue(c.discardRecovered())
        assertNull(store.active)
        assertEquals(1, store.results.value.size) // discard writes no History
        assertTrue(b.state.value !is SessionState.AwaitingResume || c.state.value is SessionState.Idle)
    }

    @Test fun dndIsAppliedOnlyWhenEnabledAndRestoredOnFinish() = runTest {
        prefs.state.value = AppPreferences(doNotDisturbDuringFocus = true)
        platform.dndAccess = true
        val m = started()
        assertTrue(platform.dndApplied)
        clock.advanceMinutes(3)
        m.endByUser()
        assertFalse(platform.dndApplied)
    }

    @Test fun missionSessionAwaitsReviewAndCompletesStepOnce() = runTest {
        val mission = Fixtures.mission(steps = 2)
        missions.create(mission)
        val m = manager(); m.restore(RestoreTrigger.APP_START)
        assertEquals(StartResult.Rejected(SessionValidationError.StepNotStartable), m.startMissionStep(mission.id, mission.steps[1].id, ProtectionMode.STANDARD))
        assertTrue(m.startMissionStep(mission.id, mission.steps[0].id, ProtectionMode.STANDARD) is StartResult.Started)
        clock.advanceMinutes(25); m.onRuntimeTick()
        assertTrue(m.state.value is SessionState.AwaitingReview)

        val recreated = manager(); recreated.restore(RestoreTrigger.APP_START) // review survives process death
        assertTrue(recreated.state.value is SessionState.AwaitingReview)
        assertTrue(recreated.submitMissionReview(stepCompleted = true, rating = 4))
        assertFalse(recreated.submitMissionReview(stepCompleted = true, rating = 4))
        val after = missions.getMission(mission.id)!!
        assertEquals(MissionStepStatus.COMPLETED, after.steps[0].status)
        assertEquals(MissionStepStatus.ACTIVE, after.steps[1].status)
        assertEquals(SessionOutcome.COMPLETED, store.results.value.single().outcome)
    }
}
