package io.github.illiapohukai.focusmode.core.domain.ai

import io.github.illiapohukai.focusmode.core.model.MissionType
import java.time.LocalDate

/** PWA `confidenceForAPI` values. */
enum class GenerationConfidence(val wire: String, val label: String) {
    UNCERTAIN("uncertain", "Unsicher"), PARTIAL("partial", "Teilsicher"), CONFIDENT("confident", "Sicher")
}

/** PWA `buildMissionSchedulePayload`. */
data class GenerationSchedule(
    val currentLocalDate: LocalDate,
    val deadlineDate: LocalDate,
    val preparationStartDate: LocalDate,
    val startMode: String,
    val timezone: String,
    val preferredWeekdays: List<Int> = emptyList(),
    val preferredSessionMinutes: Int? = null,
) {
    val preparationDays: Long get() = java.time.temporal.ChronoUnit.DAYS.between(preparationStartDate, deadlineDate).coerceAtLeast(0)
}

/** Optional photo of study material. Sent only to the existing Cloudflare Worker. */
class MissionMaterial(val bytes: ByteArray, val mimeType: String) {
    companion object {
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
        const val MAX_BYTES = 1_500_000
    }
    fun validationError(): GenerationError? = when {
        mimeType !in ALLOWED_MIME -> GenerationError.UnsupportedFile
        bytes.isEmpty() || bytes.size > MAX_BYTES -> GenerationError.FileTooLarge
        else -> null
    }
    /** Generic upload name only; no user file name leaves the device (PWA policy). */
    val uploadName: String get() = if (mimeType == "image/png") "material.png" else "material.jpg"
}

/**
 * Mission generation input. Contains only what the Worker contract needs. Never includes
 * selected apps, usage data, History or any identifier of the user.
 */
data class MissionGenerationRequest(
    val goalText: String,
    val type: MissionType,
    val confidence: GenerationConfidence,
    val schedule: GenerationSchedule? = null,
    val material: MissionMaterial? = null,
) {
    companion object { const val MAX_GOAL_CHARS = 6000 }
}

data class GeneratedStep(
    val id: String,
    val title: String,
    val task: String,
    val durationMinutes: Int,
    val reason: String,
    val scheduledDate: LocalDate?,
)

data class GeneratedMissionPlan(
    val title: String,
    val subject: String,
    val type: MissionType,
    val deadlineText: String,
    val steps: List<GeneratedStep>,
    val estimateTotalMinutes: Int,
    val scheduleWarning: String?,
)

/** Safe, user-facing errors (PWA `parseMissionErrorResponseCore` wording). Internal text never shown. */
sealed class GenerationError(val code: String, val retryable: Boolean, val message: String) {
    data object Timeout : GenerationError("AI_TIMEOUT", true, "Die Analyse hat zu lange gedauert. Deine Eingaben bleiben erhalten – bitte versuche es erneut.")
    data object RateLimited : GenerationError("AI_RATE_LIMITED", true, "Der AI-Dienst ist kurzzeitig ausgelastet. Bitte warte einen Moment und versuche es erneut.")
    data object Unavailable : GenerationError("AI_UNAVAILABLE", true, "Der AI-Dienst ist momentan nicht erreichbar. Bitte versuche es gleich erneut.")
    /** HTTP 402: reserved for a future server-authoritative AI quota or entitlement. */
    data object QuotaRequired : GenerationError("AI_QUOTA_REQUIRED", false, "Dein AI-Kontingent ist aufgebraucht. Du kannst ohne AI weiterplanen.")
    data object InvalidRequest : GenerationError("INVALID_REQUEST", false, "Bitte prüfe deine Eingabe und den gewählten Zeitplan.")
    data object UnsupportedFile : GenerationError("UNSUPPORTED_FILE_TYPE", false, "Nicht unterstütztes Dateiformat. Bitte verwende JPEG, PNG oder WEBP.")
    data object FileTooLarge : GenerationError("FILE_TOO_LARGE", false, "Das Bild ist zu groß. Bitte wähle ein kleineres Foto aus.")
    data object ImageProcessingFailed : GenerationError("IMAGE_PROCESSING_FAILED", false, "Das Bild konnte nicht verarbeitet werden. Bitte wähle ein anderes Foto.")
    data object InvalidResponse : GenerationError("INVALID_AI_RESPONSE", true, "Der erzeugte Plan war unvollständig und wurde nicht gespeichert. Bitte versuche es erneut.")
    data object Offline : GenerationError("OFFLINE", true, "Keine Internetverbindung. Deine Eingaben bleiben erhalten.")
    data object Cancelled : GenerationError("CANCELLED", true, "Die Analyse wurde abgebrochen.")
    data object AlreadyInFlight : GenerationError("IN_FLIGHT", false, "Eine Analyse läuft bereits.")
    data object EmptyInput : GenerationError("EMPTY_INPUT", false, "Beschreibe dein Ziel oder füge Material hinzu.")
    data class Unknown(val serverCode: String?) : GenerationError(serverCode ?: "INTERNAL_ERROR", true, "AI-Analyse momentan nicht verfügbar. Bitte versuche es erneut.")
}

sealed interface GenerationResult {
    data class Success(val plan: GeneratedMissionPlan) : GenerationResult
    /** [serverRequestId] is a support reference from the Worker's error contract, if any. */
    data class Failure(val error: GenerationError, val serverRequestId: String? = null) : GenerationResult
}

/**
 * Boundary to the existing Cloudflare Worker (`/create-mission`). One request at a time;
 * a second call while one is in flight fails fast with [GenerationError.AlreadyInFlight].
 */
interface MissionGenerationRepository {
    suspend fun generate(request: MissionGenerationRequest): GenerationResult
}

/** Output bounds mirrored from the PWA (`normalizeAIPlan`, `AI_TEXT_LIMITS`). */
object MissionPlanRules {
    const val MAX_STEPS = 6
    val STEP_MINUTES = 5..60
    const val TITLE_MAX = 200
    const val SHORT_MAX = 120
    const val TEXT_MAX = 2000
    const val ITEM_MAX = 500
}
