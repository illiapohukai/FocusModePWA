package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import io.github.illiapohukai.focusmode.core.testing.PlanFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class MissionRulesV19Test {
    private val now = Instant.parse("2026-11-25T09:00:00Z")

    @Test fun progressRoundsLikeThePwaAndIsTheSameValueEverywhere() {
        assertEquals(0, Fixtures.mission(steps = 3, completed = 0).progressPercent)
        assertEquals(33, Fixtures.mission(steps = 3, completed = 1).progressPercent)
        assertEquals(67, Fixtures.mission(steps = 3, completed = 2).progressPercent) // Math.round, not floor
        assertEquals(100, Fixtures.mission(steps = 3, completed = 3).progressPercent)
        assertEquals(0, Fixtures.mission(steps = 0).progressPercent)
    }

    @Test fun completingTheFinalStepCompletesTheMissionOnce() {
        val m = Fixtures.mission(steps = 2, completed = 1)
        val done = (MissionRules.completeStep(m, m.steps[1].id, now) as StepCompletion.Completed).mission
        assertEquals(MissionStatus.COMPLETED, done.status)
        assertEquals(now, done.completedAt)
        assertNull(done.currentStepId)
        assertTrue(MissionRules.completeStep(done, m.steps[1].id, now) is StepCompletion.AlreadyCompleted)
    }

    @Test fun nextStepAdvancesAndOnlyTheCurrentStepCanStart() {
        val m = Fixtures.mission(steps = 3)
        assertTrue(MissionRules.canStartStep(m, m.steps[0].id))
        assertFalse(MissionRules.canStartStep(m, m.steps[1].id)) // future steps cannot bypass the order
        val next = (MissionRules.completeStep(m, m.steps[0].id, now) as StepCompletion.Completed).mission
        assertEquals(m.steps[1].id, next.currentStepId)
        assertEquals(listOf(MissionStepStatus.COMPLETED, MissionStepStatus.ACTIVE, MissionStepStatus.PENDING), next.steps.map { it.status })
        assertFalse(MissionRules.canStartStep(next, m.steps[0].id)) // a completed step never starts again
    }

    @Test fun partialAndAbortedKeepTheSameStepCurrentWithoutProgress() {
        val m = Fixtures.mission(steps = 3, completed = 1)
        val kept = MissionRules.keepStepCurrent(m, m.steps[1].id, now)!!
        assertEquals(m.steps[1].id, kept.currentStepId)
        assertEquals(m.progressPercent, kept.progressPercent)
        assertNull(MissionRules.keepStepCurrent(m, m.steps[0].id, now)) // a completed step is never reopened
    }

    @Test fun stepStatesReflectTheSessionOwner() {
        val m = Fixtures.mission(steps = 3, completed = 1)
        val running = MissionLink(m.id, m.steps[1].id)
        assertEquals(StepState.IN_PROGRESS, MissionRules.stepStates(m, running, null)[m.steps[1].id])
        assertEquals(StepState.NEEDS_REVIEW, MissionRules.stepStates(m, null, running)[m.steps[1].id])
        val idle = MissionRules.stepStates(m, null, null)
        assertEquals(listOf(StepState.COMPLETED, StepState.CURRENT, StepState.UPCOMING), m.steps.map { idle[it.id] })
        // Another Mission's Session never marks this Mission's steps.
        val other = MissionLink(MissionId("mission_other"), m.steps[1].id)
        assertEquals(StepState.CURRENT, MissionRules.stepStates(m, other, null)[m.steps[1].id])
    }

    @Test fun activeLimitIsTwo() {
        assertTrue(MissionRules.canCreateActive(0))
        assertTrue(MissionRules.canCreateActive(1))
        assertFalse(MissionRules.canCreateActive(2))
    }

    @Test fun acceptedPlanGetsLocalIdsFirstStepCurrentAndTheChosenSchedule() {
        val draft = MissionDraft.new(now).copy(
            schedule = ScheduleDraft(LocalDate.parse("2026-12-10"), LocalDate.parse("2026-11-26"), ScheduleStartMode.TWO_WEEKS_BEFORE, true, setOf(DayOfWeek.MONDAY), 30),
        )
        val m = MissionRules.missionFromPlan(draft, PlanFixtures.plan(3), now)
        assertEquals(draft.missionId, m.id)
        assertTrue(m.steps.none { it.id.value in setOf("s1", "s2", "s3") }) // AI ids are transport-only
        assertEquals(3, m.steps.map { it.id }.toSet().size)
        assertEquals(listOf(0, 1, 2), m.steps.map { it.position })
        assertEquals(m.steps[0].id, m.currentStepId)
        assertEquals(MissionStatus.ACTIVE, m.status)
        assertEquals(LocalDate.parse("2026-12-10"), m.deadlineDate)
        assertEquals(LocalDate.parse("2026-11-26"), m.preparationStartDate)
        assertEquals(ScheduleStartMode.TWO_WEEKS_BEFORE, m.schedule.startMode)
        assertEquals(setOf(DayOfWeek.MONDAY), m.schedule.preferredWeekdays)
        assertEquals(30, m.schedule.preferredSessionMinutes)
        assertEquals("Erst Grundlagen, dann Übung.", m.summary)
    }

    @Test fun adaptationReplacesOnlyOpenStepsAndKeepsOrder() {
        val m = Fixtures.mission(steps = 4, completed = 2)
        val adapted = MissionRules.replaceRemainingSteps(m, listOf(AdaptedStep("Neu A", "a", 20, ""), AdaptedStep("Neu B", "b", 30, ""), AdaptedStep("Neu C", "c", 15, "")), now)!!
        assertEquals(m.steps.take(2), adapted.steps.take(2)) // completed steps untouched
        assertEquals(listOf(0, 1, 2, 3, 4), adapted.steps.map { it.position })
        assertEquals("Neu A", adapted.currentStep!!.title)
        assertTrue(adapted.steps.drop(2).none { s -> m.steps.any { it.id == s.id } })
        assertEquals(40, adapted.progressPercent) // 2 of 5
        assertNull(MissionRules.replaceRemainingSteps(m, emptyList(), now))
        assertNull(MissionRules.replaceRemainingSteps(m, listOf(AdaptedStep("x", "", 90, "")), now))
        assertNull(MissionRules.replaceRemainingSteps(m.copy(status = MissionStatus.ARCHIVED), listOf(AdaptedStep("x", "", 20, "")), now))
    }

    @Test fun archiveRemembersTheStepAndRestoreReturnsToIt() {
        val m = Fixtures.mission(steps = 3, completed = 1)
        val archived = MissionRules.archive(m, now)
        assertEquals(MissionStatus.ARCHIVED, archived.status)
        assertNull(archived.currentStepId)
        assertEquals(m.steps[1].id, archived.archivedStepId)
        val restored = MissionRules.restore(archived, now)!!
        assertEquals(MissionStatus.ACTIVE, restored.status)
        assertEquals(m.steps[1].id, restored.currentStepId)
        assertNull(MissionRules.restore(m, now)) // only archived Missions restore
        val done = MissionRules.restore(MissionRules.archive(Fixtures.mission(steps = 2, completed = 2), now), now)!!
        assertEquals(MissionStatus.COMPLETED, done.status)
    }

    @Test fun draftThemePreviewEqualsTheStoredMissionsTone() {
        val existing = listOf(Fixtures.mission("mission_a"), Fixtures.mission("mission_b", createdAt = Instant.parse("2026-11-02T08:00:00Z")))
        val draftId = MissionId("mission_new_1")
        val preview = MissionThemes.preview(existing, draftId, now)
        val stored = MissionThemes.resolve(existing + Fixtures.mission(draftId.value, createdAt = now))[draftId]
        assertEquals(stored, preview)
        val held = MissionThemes.resolve(existing).values.toSet()
        if (held.size < MissionTheme.entries.size) assertTrue(preview !in held)
        assertNotEquals(null, preview)
    }

    @Test fun unknownStepIdIsNotFound() {
        val m = Fixtures.mission()
        assertEquals(StepCompletion.StepNotFound, MissionRules.completeStep(m, MissionStepId("missing"), now))
    }
}
