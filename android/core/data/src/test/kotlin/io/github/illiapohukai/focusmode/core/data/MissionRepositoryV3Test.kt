package io.github.illiapohukai.focusmode.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.data.backup.BackupImportRepository
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomSessionRepository
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.backup.ImportPreviewResult
import io.github.illiapohukai.focusmode.core.domain.mission.DraftAttachment
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraft
import io.github.illiapohukai.focusmode.core.domain.mission.MissionDraftPhase
import io.github.illiapohukai.focusmode.core.domain.mission.ScheduleDraft
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.session.FinalizationDecision
import io.github.illiapohukai.focusmode.core.domain.session.FinalizationPolicy
import io.github.illiapohukai.focusmode.core.domain.session.FinalizeTrigger
import io.github.illiapohukai.focusmode.core.domain.session.SessionFactory
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionReview
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.PlanFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MissionRepositoryV3Test {
    private lateinit var db: FocusDatabase
    private val clock = FakeClock()
    private val prefs = FakePreferencesRepository()
    private lateinit var missions: RoomMissionRepository
    private lateinit var sessions: RoomSessionRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java).allowMainThreadQueries().build()
        missions = RoomMissionRepository(db, clock, prefs)
        sessions = RoomSessionRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private fun count(table: String) = db.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun briefing(): MissionDraft = MissionDraft.new(clock.now()).copy(
        goalText = "Mathe-Klausur",
        type = MissionType.EXAM,
        confidence = GenerationConfidence.PARTIAL,
        schedule = ScheduleDraft(LocalDate.parse("2026-12-10"), LocalDate.parse("2026-11-26"), ScheduleStartMode.TWO_WEEKS_BEFORE, true, setOf(DayOfWeek.TUESDAY), 45),
        attachment = DraftAttachment(240_000, "image/jpeg", 1600, 1200),
        phase = MissionDraftPhase.BRIEFING,
        plan = PlanFixtures.plan(3),
    )

    @Test fun draftRoundTripsWithPlanScheduleAndAttachment() = runTest {
        val d = briefing().copy(error = GenerationError.RateLimited, errorRequestId = "req_1234")
        missions.saveDraft(d)
        assertEquals(d, missions.getDraft())
        missions.saveDraft(d.copy(phase = MissionDraftPhase.EDITING, plan = null))
        assertEquals(0, count("mission_draft_steps")) // stale plan rows never linger
        missions.discardDraft()
        assertNull(missions.getDraft())
    }

    @Test fun conditionalDraftWritesDropLateResults() = runTest {
        val d = briefing().copy(phase = MissionDraftPhase.GENERATING, plan = null)
        missions.saveDraft(d)
        missions.saveDraft(d.copy(phase = MissionDraftPhase.EDITING)) // the user cancelled
        assertFalse(missions.saveDraftIf(d.copy(phase = MissionDraftPhase.BRIEFING, plan = PlanFixtures.plan(2)), MissionDraftPhase.GENERATING))
        assertEquals(MissionDraftPhase.EDITING, missions.getDraft()!!.phase)
        assertFalse(missions.saveDraftIf(MissionDraft.new(clock.now()).copy(phase = MissionDraftPhase.BRIEFING), MissionDraftPhase.EDITING)) // another draft id
    }

    @Test fun acceptCreatesOneMissionTransactionallyWithLocalStepIds() = runTest {
        val d = briefing()
        missions.saveDraft(d)
        val created = (missions.acceptDraft(d.draftId) as MissionWriteResult.Ok).mission
        assertEquals(d.missionId, created.id)
        assertEquals(3, created.steps.size)
        assertTrue(created.steps.none { it.id.value.startsWith("s") && it.id.value.length <= 3 })
        assertEquals(created.steps[0].id, created.currentStepId)
        assertEquals(LocalDate.parse("2026-12-10"), created.deadlineDate)
        assertEquals(ScheduleStartMode.TWO_WEEKS_BEFORE, created.schedule.startMode)
        assertEquals(45, created.schedule.preferredSessionMinutes)
        assertEquals(75, created.estimateTotalMinutes)
        assertNull(missions.getDraft())
        // A second accept (double tap, restart) never creates a second Mission.
        assertTrue(missions.acceptDraft(d.draftId) is MissionWriteResult.Rejected)
        assertEquals(1, count("missions"))
        // Two plans with the same AI step ids ("s1"…) coexist because stored ids are local.
        val second = briefing()
        missions.saveDraft(second)
        assertTrue(missions.acceptDraft(second.draftId) is MissionWriteResult.Ok)
        assertEquals(6, count("mission_steps"))
    }

    @Test fun acceptAtTheLimitKeepsTheDraftAndCreatesNothing() = runTest {
        missions.create(Fixtures.mission("mission_a"))
        missions.create(Fixtures.mission("mission_b"))
        val d = briefing()
        missions.saveDraft(d)
        assertEquals(MissionWriteResult.Rejected("active_mission_limit_reached"), missions.acceptDraft(d.draftId))
        assertEquals(2, count("missions"))
        assertEquals(MissionDraftPhase.BRIEFING, missions.getDraft()!!.phase)
        // A third active Mission is impossible through any path.
        assertEquals(MissionWriteResult.Rejected("active_mission_limit_reached"), missions.create(Fixtures.mission("mission_c")))
        assertTrue(missions.create(Fixtures.mission("mission_d", status = MissionStatus.COMPLETED)) is MissionWriteResult.Ok)
    }

    private suspend fun finishedMissionSession(m: Mission, stepIndex: Int, minutes: Long = 25): io.github.illiapohukai.focusmode.core.model.SessionId {
        val step = m.steps[stepIndex]
        val s = SessionFactory(clock).mission(MissionLink(m.id, step.id), step.title, step.task, 25, m.subject, stepIndex + 1, m.steps.size, ProtectionMode.STANDARD)
        sessions.save(s)
        val trigger = if (minutes >= 25) FinalizeTrigger.TIMER_ELAPSED else FinalizeTrigger.USER_END
        val r = (FinalizationPolicy.decide(s, minutes * 60_000, trigger, clock.now(), clock.zone()) as FinalizationDecision.Finalize).result
        sessions.finalize(r)
        return s.id
    }

    @Test fun completedReviewAdvancesOnceAndTheFinalStepCompletesTheMission() = runTest {
        val m = Fixtures.mission(steps = 2)
        missions.create(m)
        val first = finishedMissionSession(m, 0)
        val applied = sessions.applyMissionReview(first, MissionReview(SessionOutcome.COMPLETED, 5, "gut"))!!
        assertTrue(applied.applied)
        assertEquals(50, applied.mission!!.progressPercent)
        assertEquals("gut", sessions.getResult(first)!!.reviewNote)
        assertFalse(sessions.applyMissionReview(first, MissionReview(SessionOutcome.COMPLETED, 5))!!.applied)
        assertEquals(1, count("mission_progress"))
        assertEquals(1, db.query("SELECT COUNT(*) FROM activity_log WHERE type='MISSION_STEP_COMPLETED'", null).use { it.moveToFirst(); it.getInt(0) })
        val second = finishedMissionSession(missions.getMission(m.id)!!, 1)
        val done = sessions.applyMissionReview(second, MissionReview(SessionOutcome.COMPLETED, 4))!!.mission!!
        assertEquals(MissionStatus.COMPLETED, done.status)
        assertEquals(100, done.progressPercent)
        assertNull(done.currentStepId)
        assertEquals(2, missions.progressHistory(m.id).size)
    }

    @Test fun partialAndAbortedKeepTheStepAndProgress() = runTest {
        val m = Fixtures.mission(steps = 3)
        missions.create(m)
        val partial = finishedMissionSession(m, 0, minutes = 12)
        val p = sessions.applyMissionReview(partial, MissionReview(SessionOutcome.PARTIAL, 3))!!
        assertEquals(m.steps[0].id, p.mission!!.currentStepId)
        assertEquals(0, p.mission!!.progressPercent)
        assertTrue(p.result.countsAsFocus) // real focused time counts
        // The retry starts after the partial review. Without time passing both reviews would share
        // applied_at and the history order would fall back to comparing random session ids.
        clock.advanceMinutes(30)
        val aborted = finishedMissionSession(missions.getMission(m.id)!!, 0, minutes = 5)
        val a = sessions.applyMissionReview(aborted, MissionReview(SessionOutcome.CANCELED, null))!!
        assertEquals(SessionOutcome.CANCELED, a.result.outcome) // never a false completion
        assertEquals(m.steps[0].id, a.mission!!.currentStepId)
        assertEquals(MissionStepStatus.ACTIVE, a.mission!!.steps[0].status)
        assertEquals(listOf(SessionOutcome.PARTIAL, SessionOutcome.CANCELED), missions.progressHistory(m.id).map { it.outcome })
    }

    @Test fun archiveRestoreAndDeleteKeepHistoryAndCleanUp() = runTest {
        val m = Fixtures.mission(steps = 2)
        missions.create(m)
        prefs.setLastSelectedMission(m.id.value)
        val sid = finishedMissionSession(m, 0)
        sessions.applyMissionReview(sid, MissionReview(SessionOutcome.COMPLETED))
        val archived = (missions.archive(m.id) as MissionWriteResult.Ok).mission
        assertEquals(MissionStatus.ARCHIVED, archived.status)
        assertNull(prefs.state.value.lastSelectedMissionId) // selection never points at an inactive Mission
        val restored = (missions.restore(m.id) as MissionWriteResult.Ok).mission
        assertEquals(MissionStatus.ACTIVE, restored.status)
        assertEquals(m.steps[1].id, restored.currentStepId)
        assertTrue(missions.delete(m.id) is MissionWriteResult.Ok)
        assertEquals(0, count("mission_steps"))
        assertEquals(0, count("mission_progress")) // no orphaned progress
        assertEquals(m.id, sessions.getResult(sid)!!.missionLink!!.missionId) // History keeps the Session
    }

    @Test fun restoreRespectsTheLimit() = runTest {
        val a = Fixtures.mission("mission_a")
        missions.create(a)
        missions.archive(a.id)
        missions.create(Fixtures.mission("mission_b"))
        missions.create(Fixtures.mission("mission_c"))
        assertEquals(MissionWriteResult.Rejected("active_mission_limit_reached"), missions.restore(a.id))
        assertEquals(MissionStatus.ARCHIVED, missions.getMission(a.id)!!.status)
    }

    @Test fun adaptationReplacesOpenStepsOnlyWhenTheMissionIsUnchanged() = runTest {
        val m = Fixtures.mission(steps = 3)
        missions.create(m)
        val sid = finishedMissionSession(m, 0)
        sessions.applyMissionReview(sid, MissionReview(SessionOutcome.COMPLETED))
        val before = missions.getMission(m.id)!!
        val steps = listOf(AdaptedStep("Neu 1", "a", 20, ""), AdaptedStep("Neu 2", "b", 30, ""))
        assertEquals(MissionWriteResult.Rejected("mission_changed"), missions.replaceRemainingSteps(m.id, steps, before.updatedAt.minusSeconds(60)))
        val after = (missions.replaceRemainingSteps(m.id, steps, before.updatedAt) as MissionWriteResult.Ok).mission
        assertEquals(before.steps[0], after.steps[0]) // the completed step is untouched
        assertEquals(listOf("Neu 1", "Neu 2"), after.steps.drop(1).map { it.title })
        assertEquals(listOf(0, 1, 2), after.steps.map { it.position })
        assertEquals(after.steps[1].id, after.currentStepId)
        assertEquals(3, count("mission_steps"))
        // Order is stable through a fresh query.
        assertEquals(after.steps.map { it.id }, missions.getMission(m.id)!!.steps.map { it.id })
    }

    @Test fun backupImportRemapsStepIdsThatAnotherMissionAlreadyUses() = runTest {
        // A local Mission already owns step id "session-1" (the PWA's generated fallback id).
        val local = Fixtures.mission("mission_local", steps = 1).let { it.copy(steps = it.steps.map { s -> s.copy(id = io.github.illiapohukai.focusmode.core.model.MissionStepId("session-1")) }, currentStepId = io.github.illiapohukai.focusmode.core.model.MissionStepId("session-1")) }
        missions.create(local)
        val backup = """
            {"format":"focusmode-backup","exportVersion":1,"data":{
              "missions":{"missions":[{"id":"mission_web","title":"Web","status":"active","createdAt":1790000000000,"currentStepId":"session-1","steps":[{"id":"session-1","title":"A","durationMinutes":25,"status":"active"}]}]},
              "sessionHistory":{"sessions":[{"id":"session_w1","type":"mission","missionId":"mission_web","missionStepId":"session-1","status":"completed","title":"A","task":"A","plannedMinutes":25,"actualMinutes":25,"startedAt":1790000000000,"finishedAt":1790001500000}]}}}
        """.trimIndent().toByteArray()
        val repo = BackupImportRepository(db, clock)
        val plan = (repo.preview(backup) as ImportPreviewResult.Ready).preview.plan
        repo.apply(plan)
        assertEquals(local.steps.single(), missions.getMission(local.id)!!.steps.single()) // the local step is not overwritten
        val web = missions.getMission(io.github.illiapohukai.focusmode.core.model.MissionId("mission_web"))!!
        val newStep = web.steps.single().id
        assertTrue(newStep.value != "session-1")
        assertEquals(newStep, web.currentStepId)
        assertEquals(newStep, sessions.getResult(io.github.illiapohukai.focusmode.core.model.SessionId("session_w1"))!!.missionLink!!.stepId)
    }
}
