package io.github.illiapohukai.focusmode.core.session

import io.github.illiapohukai.focusmode.core.domain.session.RestoreTrigger
import io.github.illiapohukai.focusmode.core.domain.session.SessionState
import io.github.illiapohukai.focusmode.core.domain.session.StartResult
import io.github.illiapohukai.focusmode.core.model.MissionReview
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSessionStore
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v19: Mission Focus through the one Session owner keeps its Mission linkage on every recovery path. */
class MissionSessionRecoveryTest {
    private val clock = FakeClock()
    private val store = FakeSessionStore(clock)
    private val missions = FakeMissionRepository(clock).also { store.missions = it }
    private val prefs = FakePreferencesRepository()
    private val platform = FakePlatform()
    private val mission = Fixtures.mission("mission_a", steps = 2)

    private fun manager() = FocusSessionManager(store, store, missions, prefs, clock, platform, platform, platform, platform)

    private suspend fun startedStep(mode: ProtectionMode = ProtectionMode.PROTECTED): FocusSessionManager {
        missions.create(mission)
        val m = manager()
        m.restore(RestoreTrigger.APP_START)
        assertTrue(m.startMissionStep(mission.id, mission.steps[0].id, mode) is StartResult.Started)
        return m
    }

    @Test fun processRecreationKeepsTheMissionLinkTaskDurationAndProtection() = runTest {
        startedStep()
        clock.advanceMinutes(7)
        val recreated = manager()
        recreated.restore(RestoreTrigger.APP_START)
        val s = (recreated.state.value as SessionState.Running).session
        assertEquals(SessionType.MISSION, s.type)
        assertEquals(mission.id, s.missionLink!!.missionId)
        assertEquals(mission.steps[0].id, s.missionLink!!.stepId)
        assertEquals(mission.steps[0].task, s.task.text)
        assertEquals(25, s.plannedMinutes)
        assertEquals(ProtectionMode.PROTECTED, s.protectionMode)
        assertEquals(7 * 60_000L, recreated.read()!!.elapsedMs)
        assertEquals(mission.id.value, prefs.state.value.lastSelectedMissionId)
    }

    @Test fun expiryWhileAwayWaitsForTheReviewAndAppliesItOnce() = runTest {
        startedStep()
        clock.advanceMinutes(40) // the planned time passed while the app was gone
        val recreated = manager()
        recreated.restore(RestoreTrigger.APP_START)
        val pending = (recreated.state.value as SessionState.AwaitingReview).result
        assertEquals(SessionEndReason.AWAITING_USER_REVIEW, pending.endReason)
        assertEquals(mission.steps[0].id, pending.missionLink!!.stepId)
        assertEquals(1, store.results.value.size)
        // Another restart before the review: still exactly one History record, still pending.
        val again = manager()
        again.restore(RestoreTrigger.APP_START)
        assertTrue(again.state.value is SessionState.AwaitingReview)
        assertEquals(1, store.results.value.size)

        assertTrue(again.submitMissionReview(MissionReview(SessionOutcome.COMPLETED, 4, "lief gut")))
        assertFalse(again.submitMissionReview(MissionReview(SessionOutcome.COMPLETED, 4))) // a second tap changes nothing
        val m = missions.getMission(mission.id)!!
        assertEquals(MissionStepStatus.COMPLETED, m.steps[0].status)
        assertEquals(m.steps[1].id, m.currentStepId)
        assertEquals(1, missions.progress.value.size)
        assertEquals("lief gut", store.results.value.single().reviewNote)
    }

    @Test fun abortedReviewNeverCompletesTheStep() = runTest {
        val m = startedStep(ProtectionMode.STANDARD)
        clock.advanceMinutes(6)
        m.endByUser()
        assertTrue(m.submitMissionReview(MissionReview(SessionOutcome.CANCELED, null)))
        val after = missions.getMission(mission.id)!!
        assertEquals(mission.steps[0].id, after.currentStepId)
        assertEquals(MissionStepStatus.ACTIVE, after.steps[0].status)
        assertEquals(SessionOutcome.CANCELED, store.results.value.single().outcome)
        assertEquals(MissionStatus.ACTIVE, after.status)
    }

    @Test fun rebootFreezesTheMissionSessionWithoutLosingTheLink() = runTest {
        startedStep()
        clock.advanceMinutes(3)
        manager().onRuntimeTick()
        clock.reboot()
        val afterBoot = manager()
        afterBoot.restore(RestoreTrigger.BOOT_COMPLETED)
        val frozen = (afterBoot.state.value as SessionState.AwaitingResume).session
        assertEquals(mission.steps[0].id, frozen.missionLink!!.stepId)
        assertTrue(afterBoot.resumeRecovered())
        assertEquals(SessionType.MISSION, (afterBoot.state.value as SessionState.Running).session.type)
    }

    @Test fun onlyTheCurrentStepStartsAndOnlyOneSessionAtATime() = runTest {
        val m = startedStep()
        assertTrue(m.startMissionStep(mission.id, mission.steps[0].id, ProtectionMode.STANDARD) is StartResult.Rejected)
        val other = Fixtures.mission("mission_b", steps = 2)
        missions.create(other)
        assertTrue(m.startMissionStep(other.id, other.steps[0].id, ProtectionMode.STANDARD) is StartResult.Rejected)
    }
}
