package app.focusmodeplus.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/* Transport DTOs. Every field is optional here; [MissionPlanMapper] decides what is valid. */

@Serializable
internal data class CreateMissionResponseDto(
    val ok: Boolean? = null,
    val plan: PlanDto? = null,
    val error: JsonElement? = null,
    val code: String? = null,
    val requestId: String? = null,
)

@Serializable
internal data class PlanDto(
    val goal: GoalDto? = null,
    val sessions: List<StepDto>? = null,
    val estimate: EstimateDto? = null,
    val scheduleWarning: String? = null,
    val warning: String? = null,
    /** `{detectedTasks, detectedPages, confidence, missingInformation, facts, estimates}`; read leniently. */
    val sourceAnalysis: JsonElement? = null,
)

@Serializable
internal data class GoalDto(
    val title: String? = null,
    val subject: String? = null,
    val type: String? = null,
    val deadlineText: String? = null,
)

@Serializable
internal data class StepDto(
    val id: JsonElement? = null,
    val title: String? = null,
    val task: String? = null,
    val reason: String? = null,
    val durationMinutes: Double? = null,
    val scheduledDate: String? = null,
    val date: String? = null,
    val requiredTools: JsonElement? = null,
    val recommendedApps: JsonElement? = null,
)

@Serializable
internal data class EstimateDto(
    val totalMinutes: Double? = null,
    val difficulty: String? = null,
    val explanation: String? = null,
    val scheduleWarning: String? = null,
)

/** PWA `buildMissionSchedulePayload` shape. */
@Serializable
internal data class SchedulePayloadDto(
    val currentLocalDate: String,
    val deadlineDate: String,
    val preparationStartDate: String,
    val preparationDays: Long,
    val startMode: String,
    val timezone: String,
    val preferredWeekdays: List<Int>? = null,
    val preferredSessionMinutes: Int? = null,
)
