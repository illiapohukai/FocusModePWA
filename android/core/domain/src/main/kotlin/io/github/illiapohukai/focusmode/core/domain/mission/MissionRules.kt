package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import java.time.Instant

sealed interface StepCompletion {
    data class Completed(val mission: Mission) : StepCompletion
    /** Already completed: the same step is never completed twice (PWA duplicatePrevented). */
    data class AlreadyCompleted(val mission: Mission) : StepCompletion
    data object StepNotFound : StepCompletion
}

object MissionRules {
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
        val steps = updated.map { if (next != null && it.id == next.id) it.copy(status = MissionStepStatus.ACTIVE) else it }
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
}
