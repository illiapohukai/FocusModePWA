package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.AiTextSafety
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedStep
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationSchedule
import io.github.illiapohukai.focusmode.core.domain.ai.MissionPlanRules
import io.github.illiapohukai.focusmode.core.domain.ai.SourceAnalysis
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate

internal class InvalidPlanException(reason: String) : Exception(reason)

/**
 * Mirrors the PWA `normalizeAIPlan`: bounded text, 1–6 steps of 5–60 min, no step after the
 * deadline, safe unique step ids. Stricter than the PWA where truncation or acceptance would be
 * unsafe: more than six steps, markup or script in any text, or duplicate steps reject the whole
 * plan (nothing partial is ever stored; the draft keeps the input for a retry).
 */
internal object MissionPlanMapper {
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val CONTROL = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    /** PWA `briefDifficulty` keys; anything else is not shown. */
    private val DIFFICULTIES = setOf("easy", "low", "medium", "moderate", "hard", "high", "difficult")

    fun text(v: String?, max: Int): String = v.orEmpty().replace(CONTROL, " ").trim().take(max)

    /** A string or number primitive as text (PWA `aiText`); objects, arrays and null give "". */
    private fun primitiveText(e: JsonElement?, max: Int): String =
        (e as? JsonPrimitive)?.takeIf { it.isString || it.content.toDoubleOrNull() != null }?.content?.let { text(it, max) }.orEmpty()

    /**
     * PWA `aiList`: at most [maxItems] text items of [maxLen] characters. Unlike the plan's own
     * texts, an unsafe item (markup, script) drops only that item: these lists are auxiliary and
     * must not reject an otherwise valid plan. Duplicates are removed.
     */
    fun list(e: JsonElement?, maxItems: Int, maxLen: Int): List<String> =
        (e as? JsonArray).orEmpty().take(maxItems).map { primitiveText(it, maxLen) }.filter { it.isNotEmpty() && !AiTextSafety.isUnsafe(it) }.distinct()

    fun tools(e: JsonElement?): List<String> = list(e, MissionPlanRules.TOOLS_MAX, MissionPlanRules.SHORT_MAX)

    /** Worker `plan.sourceAnalysis`; anything malformed simply yields an empty analysis. */
    fun source(e: JsonElement?): SourceAnalysis {
        val o = e as? JsonObject ?: return SourceAnalysis()
        fun safe(s: String) = s.takeUnless(AiTextSafety::isUnsafe).orEmpty()
        return SourceAnalysis(
            detectedTasks = safe(primitiveText(o["detectedTasks"], SourceAnalysis.ITEM_MAX)),
            detectedPages = safe(primitiveText(o["detectedPages"], SourceAnalysis.ITEM_MAX)),
            confidencePercent = SourceAnalysis.percentOf((o["confidence"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()),
            missingInformation = list(o["missingInformation"], SourceAnalysis.LIST_ITEMS, SourceAnalysis.ITEM_MAX),
            facts = list(o["facts"], SourceAnalysis.LIST_ITEMS, SourceAnalysis.ITEM_MAX),
            estimates = list(o["estimates"], SourceAnalysis.LIST_ITEMS, SourceAnalysis.ITEM_MAX),
        )
    }

    private fun date(v: String?): LocalDate? =
        v?.trim()?.takeIf(ISO_DATE::matches)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun map(plan: PlanDto?, schedule: GenerationSchedule?): GeneratedMissionPlan {
        plan ?: throw InvalidPlanException("plan_missing")
        val title = text(plan.goal?.title, MissionPlanRules.TITLE_MAX).ifEmpty { throw InvalidPlanException("title_missing") }
        val rawSteps = plan.sessions?.takeIf { it.isNotEmpty() } ?: throw InvalidPlanException("sessions_missing")
        if (rawSteps.size > MissionPlanRules.MAX_STEPS) throw InvalidPlanException("too_many_steps")
        val deadline = schedule?.deadlineDate
        val used = HashSet<String>()
        val steps = rawSteps.mapIndexed { i, s ->
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
            GeneratedStep(id, stepTitle, task, minutes, text(s.reason, MissionPlanRules.TEXT_MAX), scheduled, tools(s.requiredTools), tools(s.recommendedApps))
        }
        if (steps.map { it.title.lowercase() to it.task.lowercase() }.toSet().size != steps.size) throw InvalidPlanException("duplicate_steps")
        val deadlineText = text(plan.goal?.deadlineText, MissionPlanRules.SHORT_MAX)
            .takeIf { it.isNotEmpty() && it != "Nicht angegeben" } ?: deadline?.toString() ?: "Nicht angegeben"
        val warning = text(plan.scheduleWarning ?: plan.estimate?.scheduleWarning ?: plan.warning, MissionPlanRules.ITEM_MAX).ifEmpty { null }
        val total = plan.estimate?.totalMinutes?.takeIf { it.isFinite() && it > 0 }?.toInt()?.coerceAtMost(100_000)
            ?: steps.sumOf { it.durationMinutes }
        val result = GeneratedMissionPlan(
            title = title,
            subject = text(plan.goal?.subject, MissionPlanRules.SHORT_MAX).ifEmpty { "Allgemein" },
            type = MissionType.fromWire(plan.goal?.type?.trim()?.lowercase()),
            deadlineText = deadlineText,
            steps = steps,
            estimateTotalMinutes = total,
            scheduleWarning = warning,
            difficulty = text(plan.estimate?.difficulty, 40).lowercase().takeIf { it in DIFFICULTIES },
            explanation = text(plan.estimate?.explanation, MissionPlanRules.TEXT_MAX),
            source = source(plan.sourceAnalysis),
        )
        val texts = listOf(result.title, result.subject, result.deadlineText, result.scheduleWarning, result.explanation) +
            result.steps.flatMap { listOf(it.title, it.task, it.reason) }
        if (texts.any(AiTextSafety::isUnsafe)) throw InvalidPlanException("unsafe_content")
        return result
    }
}
