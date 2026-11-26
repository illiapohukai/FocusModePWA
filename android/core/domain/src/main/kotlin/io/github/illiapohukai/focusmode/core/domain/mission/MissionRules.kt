package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.MissionPlanRules
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import java.time.Instant

sealed interface StepCompletion {
    data class Completed(val mission: Mission) : StepCompletion
    /** Already completed: the same step is never completed twice (PWA duplicatePrevented). */
    data class AlreadyCompleted(val mission: Mission) : StepCompletion
    data object StepNotFound : StepCompletion
}

/** What a step looks like right now (stored status plus the Session owner's live state). */
enum class StepState { COMPLETED, CURRENT, IN_PROGRESS, NEEDS_REVIEW, UPCOMING }

object MissionRules {
    /** PWA rule: at most two active Missions at a time. */
    const val MAX_ACTIVE = Mission.MAX_ACTIVE

    fun canCreateActive(activeCount: Int): Boolean = activeCount < MAX_ACTIVE

    /** Only the current step of an active Mission can be started (PWA `createMissionSession`). */
    fun canStartStep(mission: Mission, stepId: MissionStepId): Boolean =
        mission.status == MissionStatus.ACTIVE && mission.currentStepId == stepId &&
            mission.steps.any { it.id == stepId && it.status != MissionStepStatus.COMPLETED }

    /** PWA `completeStep`: completes the step, activates the next one, completes the Mission at the end. */
    fun completeStep(mission: Mission, stepId: MissionStepId, at: Instant): StepCompletion {
        val step = mission.steps.firstOrNull { it.id == stepId } ?: return StepCompletion.StepNotFound
        if (step.status == MissionStepStatus.COMPLETED) return StepCompletion.AlreadyCompleted(mission)
        val updated = mission.steps.map {
            if (it.id == stepId) it.copy(status = MissionStepStatus.COMPLETED, completedAt = at) else it
        }.sortedBy { it.position }
        val next = updated.firstOrNull { it.status != MissionStepStatus.COMPLETED }
        val steps = updated.map {
            when {
                it.status == MissionStepStatus.COMPLETED -> it
                next != null && it.id == next.id -> it.copy(status = MissionStepStatus.ACTIVE)
                else -> it.copy(status = MissionStepStatus.PENDING)
            }
        }
        return StepCompletion.Completed(
            mission.copy(
                steps = steps,
                currentStepId = next?.id,
                status = if (next == null) MissionStatus.COMPLETED else mission.status,
                completedAt = if (next == null) at else mission.completedAt,
                updatedAt = at,
            ),
        )
    }

    /**
     * PWA `commitMissionSessionResult` for "Teilweise" and "Abgebrochen": the reviewed step stays
     * (or becomes again) the current step; nothing is completed and progress does not move.
     */
    fun keepStepCurrent(mission: Mission, stepId: MissionStepId, at: Instant): Mission? {
        if (mission.status != MissionStatus.ACTIVE) return null
        val step = mission.steps.firstOrNull { it.id == stepId } ?: return null
        if (step.status == MissionStepStatus.COMPLETED) return null
        val steps = mission.steps.map {
            when {
                it.status == MissionStepStatus.COMPLETED -> it
                it.id == stepId -> it.copy(status = MissionStepStatus.ACTIVE)
                else -> it.copy(status = MissionStepStatus.PENDING)
            }
        }
        return mission.copy(steps = steps, currentStepId = stepId, updatedAt = at)
    }

    /** Live state of every step, for the Plan route and its accessibility descriptions. */
    fun stepStates(mission: Mission, running: MissionLink?, awaitingReview: MissionLink?): Map<MissionStepId, StepState> =
        mission.steps.associate { s ->
            s.id to when {
                s.status == MissionStepStatus.COMPLETED -> StepState.COMPLETED
                awaitingReview?.missionId == mission.id && awaitingReview.stepId == s.id -> StepState.NEEDS_REVIEW
                running?.missionId == mission.id && running.stepId == s.id -> StepState.IN_PROGRESS
                s.id == mission.currentStepId -> StepState.CURRENT
                else -> StepState.UPCOMING
            }
        }

    /**
     * Builds the Mission a validated Briefing plan becomes (PWA `confirmAIPlan` + `normalizeMissionV115`).
     * Step ids are always generated locally: AI ids are untrusted and must be unique across all
     * Missions on this device. The first step is current.
     */
    fun missionFromPlan(draft: MissionDraft, plan: GeneratedMissionPlan, now: Instant, newStepId: () -> MissionStepId = EntityIds::newStepId): Mission {
        require(plan.steps.size in 1..MissionPlanRules.MAX_STEPS) { "plan_steps_invalid" }
        val missionId = draft.missionId
        val steps = plan.steps.mapIndexed { i, s ->
            MissionStep(
                id = newStepId(),
                missionId = missionId,
                position = i,
                title = s.title,
                task = s.task,
                durationMinutes = s.durationMinutes,
                status = if (i == 0) MissionStepStatus.ACTIVE else MissionStepStatus.PENDING,
                reason = s.reason,
                scheduledDate = s.scheduledDate,
                requiredTools = s.requiredTools,
                recommendedApps = s.recommendedApps,
            )
        }
        val schedule = draft.schedule
        return Mission(
            id = missionId,
            title = plan.title,
            subject = plan.subject,
            type = plan.type,
            deadlineText = plan.deadlineText,
            deadlineDate = schedule.deadline,
            preparationStartDate = if (schedule.deadline != null) schedule.preparationStart else null,
            status = MissionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            currentStepId = steps.first().id,
            steps = steps,
            origin = DataOrigin.LOCAL,
            schedule = MissionSchedulePlanner.toMissionSchedule(schedule),
            summary = plan.explanation,
            estimateTotalMinutes = plan.estimateTotalMinutes.takeIf { it > 0 },
            scheduleWarning = plan.scheduleWarning,
        )
    }

    /**
     * PWA `replaceRemainingSteps`: completed steps (and their History) stay exactly as they are;
     * every open step is replaced by the adapted ones, which continue the order after the
     * completed steps. The first adapted step becomes current.
     */
    fun replaceRemainingSteps(mission: Mission, adapted: List<AdaptedStep>, at: Instant, newStepId: () -> MissionStepId = EntityIds::newStepId): Mission? {
        if (mission.status != MissionStatus.ACTIVE) return null
        if (adapted.isEmpty() || adapted.size > MissionPlanRules.MAX_ADAPTED_STEPS) return null
        if (adapted.any { it.durationMinutes !in MissionPlanRules.STEP_MINUTES || it.title.isBlank() }) return null
        val completed = mission.steps.filter { it.status == MissionStepStatus.COMPLETED }.sortedBy { it.position }
        val base = (completed.maxOfOrNull { it.position } ?: -1) + 1
        val fresh = adapted.mapIndexed { i, a ->
            MissionStep(
                id = newStepId(), missionId = mission.id, position = base + i, title = a.title, task = a.task,
                durationMinutes = a.durationMinutes, status = if (i == 0) MissionStepStatus.ACTIVE else MissionStepStatus.PENDING, reason = a.reason,
                requiredTools = a.requiredTools, recommendedApps = a.recommendedApps,
            )
        }
        return mission.copy(steps = completed + fresh, currentStepId = fresh.first().id, updatedAt = at)
    }

    /** PWA `renameMissionV116`: at most 80 characters, never empty. */
    const val TITLE_MAX = 80
    private val CONTROL = Regex("[\\u0000-\\u001F\\u007F]")

    /** The cleaned title, or null when nothing usable remains. */
    fun cleanTitle(raw: String): String? = raw.replace(CONTROL, " ").trim().take(TITLE_MAX).trim().ifEmpty { null }

    /**
     * PWA `renameMissionV116`: only the title changes. The Mission id (and therefore its identity
     * colour, reminders, History links and progress) stays exactly the same.
     */
    fun rename(mission: Mission, title: String, at: Instant): Mission? {
        val clean = cleanTitle(title) ?: return null
        if (clean == mission.title) return mission
        return mission.copy(title = clean, updatedAt = at)
    }

    /** PWA `archiveMissionV116`: keeps the current step for a later restore. */
    fun archive(mission: Mission, at: Instant): Mission =
        mission.copy(status = MissionStatus.ARCHIVED, archivedAt = at, updatedAt = at, archivedStepId = mission.currentStepId, currentStepId = null)

    /** PWA `restoreMissionV116`: back to active at the remembered (or first open) step, or completed. */
    fun restore(mission: Mission, at: Instant): Mission? {
        if (mission.status != MissionStatus.ARCHIVED) return null
        val open = mission.steps.filter { it.status != MissionStepStatus.COMPLETED }.sortedBy { it.position }
        val stepId = mission.archivedStepId?.takeIf { id -> open.any { it.id == id } } ?: open.firstOrNull()?.id
        val steps = mission.steps.map {
            when {
                it.status == MissionStepStatus.COMPLETED -> it
                it.id == stepId -> it.copy(status = MissionStepStatus.ACTIVE)
                else -> it.copy(status = MissionStepStatus.PENDING)
            }
        }
        return mission.copy(
            status = if (stepId != null) MissionStatus.ACTIVE else MissionStatus.COMPLETED,
            archivedAt = null, archivedStepId = null, currentStepId = stepId, steps = steps, updatedAt = at,
            completedAt = if (stepId == null) (mission.completedAt ?: at) else mission.completedAt,
        )
    }
}
