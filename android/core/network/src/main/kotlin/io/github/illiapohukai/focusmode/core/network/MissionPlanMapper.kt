package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedStep
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationSchedule
import io.github.illiapohukai.focusmode.core.domain.ai.MissionPlanRules
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

internal class InvalidPlanException(reason: String) : Exception(reason)

/** Mirrors the PWA `normalizeAIPlan`: bounded text, 1–6 steps of 5–60 min, no step after the deadline. */
internal object MissionPlanMapper {
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val CONTROL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun text(v: String?, max: Int): String = v.orEmpty().replace(CONTROL, " ").trim().take(max)

    private fun date(v: String?): LocalDate? =
        v?.trim()?.takeIf(ISO_DATE::matches)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun map(plan: PlanDto?, schedule: GenerationSchedule?): GeneratedMissionPlan {
        plan ?: throw InvalidPlanException("plan_missing")
        val title = text(plan.goal?.title, MissionPlanRules.TITLE_MAX).ifEmpty { throw InvalidPlanException("title_missing") }
        val rawSteps = plan.sessions?.takeIf { it.isNotEmpty() } ?: throw InvalidPlanException("sessions_missing")
        val deadline = schedule?.deadlineDate
        val used = HashSet<String>()
        val steps = rawSteps.take(MissionPlanRules.MAX_STEPS).mapIndexed { i, s ->
            val stepTitle = text(s.title, MissionPlanRules.TITLE_MAX)
            val task = text(s.task ?: s.reason, MissionPlanRules.TEXT_MAX)
            val minutes = s.durationMinutes?.takeIf { it.isFinite() }?.let { Math.round(it).toInt() }
            if (stepTitle.isEmpty() || task.isEmpty() || minutes == null || minutes !in MissionPlanRules.STEP_MINUTES) {
                throw InvalidPlanException("step_incomplete")
            }
            val scheduled = date(s.scheduledDate) ?: date(s.date)
            if (deadline != null && scheduled != null && scheduled.isAfter(deadline)) throw InvalidPlanException("step_after_deadline")
            val rawId = (s.id as? JsonPrimitive)?.contentOrNull?.trim()
            var id = rawId?.takeIf { EntityIds.isSafe(it) && it !in used } ?: EntityIds.newStepId().value
            while (!used.add(id)) id = EntityIds.newStepId().value
            GeneratedStep(id, stepTitle, task, minutes, text(s.reason, MissionPlanRules.TEXT_MAX), scheduled)
        }
        val deadlineText = text(plan.goal?.deadlineText, MissionPlanRules.SHORT_MAX)
            .takeIf { it.isNotEmpty() && it != "Nicht angegeben" } ?: deadline?.toString() ?: "Nicht angegeben"
        val warning = text(plan.scheduleWarning ?: plan.estimate?.scheduleWarning ?: plan.warning, MissionPlanRules.ITEM_MAX).ifEmpty { null }
        val total = plan.estimate?.totalMinutes?.takeIf { it.isFinite() && it > 0 }?.toInt()?.coerceAtMost(100_000)
            ?: steps.sumOf { it.durationMinutes }
        return GeneratedMissionPlan(
            title = title,
            subject = text(plan.goal?.subject, MissionPlanRules.SHORT_MAX).ifEmpty { "Allgemein" },
            type = MissionType.fromWire(plan.goal?.type?.trim()?.lowercase()),
            deadlineText = deadlineText,
            steps = steps,
            estimateTotalMinutes = total,
            scheduleWarning = warning,
        )
    }
}
