package app.focusmodeplus.core.domain.ai

import app.focusmodeplus.core.model.MissionType
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
    val requiredTools: List<String> = emptyList(),
    val recommendedApps: List<String> = emptyList(),
)

/**
 * Worker `plan.sourceAnalysis` (docs/API_CONTRACTS.md §2.3), bounded like the PWA
 * (`AI_TEXT_LIMITS`). Shown in the Briefing under "So wurde der Plan erstellt" ("Aus dem Material
 * erkannt" when a photo was sent). Nothing here is invented on the device: an empty analysis
 * simply shows nothing.
 */
data class SourceAnalysis(
    val detectedTasks: String = "",
    val detectedPages: String = "",
    /** 0–100, or null when the Worker gave no usable confidence. */
    val confidencePercent: Int? = null,
    val missingInformation: List<String> = emptyList(),
    val facts: List<String> = emptyList(),
    val estimates: List<String> = emptyList(),
) {
    /** PWA "Aus dem Material erkannt" / "Aus deinem Ziel erkannt" items. */
    val recognized: List<String>
        get() = listOfNotNull(detectedTasks.takeIf { it.isNotBlank() }?.let { "Aufgaben: $it" }, detectedPages.takeIf { it.isNotBlank() }?.let { "Seiten: $it" }) + facts

    /** PWA `briefConfidence`: hoch ≥ 75 %, mittel ≥ 45 %, otherwise niedrig; none without a value. */
    val confidenceLabel: String?
        get() = confidencePercent?.takeIf { it > 0 }?.let { if (it >= 75) "hoch" else if (it >= 45) "mittel" else "niedrig" }

    val isEmpty: Boolean get() = recognized.isEmpty() && missingInformation.isEmpty() && estimates.isEmpty() && confidencePercent == null

    companion object {
        /** PWA `AI_TEXT_LIMITS`: 20 list items of 500 characters. */
        const val LIST_ITEMS = 20
        const val ITEM_MAX = 500

        /** PWA `briefConfidence` input: 0–1 or 0–100, anything else is no confidence. */
        fun percentOf(raw: Double?): Int? {
            var n = raw?.takeIf { it.isFinite() && it > 0 } ?: return null
            if (n > 1) n /= 100
            return Math.round(n.coerceIn(0.0, 1.0) * 100).toInt()
        }
    }
}

data class GeneratedMissionPlan(
    val title: String,
    val subject: String,
    val type: MissionType,
    val deadlineText: String,
    val steps: List<GeneratedStep>,
    val estimateTotalMinutes: Int,
    val scheduleWarning: String?,
    /** PWA `briefDifficulty` source value (easy/medium/hard …), already bounded. */
    val difficulty: String? = null,
    /** PWA `estimate.explanation`: why the plan is built this way. */
    val explanation: String = "",
    val source: SourceAnalysis = SourceAnalysis(),
)

/** Safe, user-facing errors (PWA `parseMissionErrorResponseCore` wording). Internal text never shown. */
sealed class GenerationError(val code: String, val retryable: Boolean, val message: String) {
    data object Timeout : GenerationError("AI_TIMEOUT", true, "Die Analyse hat zu lange gedauert. Deine Eingaben bleiben erhalten – bitte versuche es erneut.")
    data object RateLimited : GenerationError("AI_RATE_LIMITED", true, "Der AI-Dienst ist kurzzeitig ausgelastet. Bitte warte einen Moment und versuche es erneut.")
    data object Unavailable : GenerationError("AI_UNAVAILABLE", true, "Der AI-Dienst ist momentan nicht erreichbar. Bitte versuche es gleich erneut.")
    /**
     * HTTP 402: AI billing is unavailable on the Worker side (reserved for a future
     * server-authoritative quota or entitlement). Not retried: a retry cannot succeed now.
     */
    data object QuotaRequired : GenerationError("AI_QUOTA_REQUIRED", false, "Die AI-Planung ist momentan nicht verfügbar. Deine Eingaben bleiben erhalten; Quick Focus funktioniert weiter.")
    /** HTTP 401/403: the Worker refused this client. Not retried. */
    data object Refused : GenerationError("AI_REFUSED", false, "Der AI-Dienst hat die Anfrage abgelehnt. Bitte versuche es später erneut.")
    data object InvalidRequest : GenerationError("INVALID_REQUEST", false, "Bitte prüfe deine Eingabe und den gewählten Zeitplan.")
    data object UnsupportedFile : GenerationError("UNSUPPORTED_FILE_TYPE", false, "Nicht unterstütztes Dateiformat. Bitte verwende JPEG, PNG oder WEBP.")
    data object FileTooLarge : GenerationError("FILE_TOO_LARGE", false, "Das Bild ist zu groß. Bitte wähle ein kleineres Foto aus.")
    data object ImageProcessingFailed : GenerationError("IMAGE_PROCESSING_FAILED", false, "Das Bild konnte nicht verarbeitet werden. Bitte wähle ein anderes Foto.")
    data object InvalidResponse : GenerationError("INVALID_AI_RESPONSE", true, "Der erzeugte Plan war unvollständig und wurde nicht gespeichert. Bitte versuche es erneut.")
    data object Offline : GenerationError("OFFLINE", true, "Keine Internetverbindung. Deine Eingaben bleiben erhalten.")
    data object Cancelled : GenerationError("CANCELLED", true, "Die Analyse wurde abgebrochen.")
    data object AlreadyInFlight : GenerationError("IN_FLIGHT", false, "Eine Analyse läuft bereits.")
    data object EmptyInput : GenerationError("EMPTY_INPUT", false, "Beschreibe dein Ziel oder füge ein Foto hinzu.")
    data object GoalTooLong : GenerationError("GOAL_TOO_LONG", false, "Bitte kürze deine Beschreibung (max. 6000 Zeichen). Deine Eingabe bleibt erhalten.")
    data object ScheduleInvalid : GenerationError("SCHEDULE_INVALID", false, "Bitte prüfe den gewählten Zeitplan.")
    /** The active-Mission limit (two) is reached; no paid request is sent. */
    data object MissionLimitReached : GenerationError("ACTIVE_MISSION_LIMIT", false, "Du hast bereits zwei aktive Missionen. Schließe eine ab oder archiviere sie, um eine neue zu planen.")
    /** The app was closed while a request ran; the result is unknown and never retried automatically. */
    data object Interrupted : GenerationError("INTERRUPTED", true, "Die Analyse wurde unterbrochen. Deine Eingaben bleiben erhalten – bitte versuche es erneut.")
    /** A local database write failed; nothing partial was stored. */
    data object StorageFailed : GenerationError("STORAGE_FAILED", true, "Die Mission konnte nicht gespeichert werden. Bitte versuche es erneut.")
    data class Unknown(val serverCode: String?) : GenerationError(serverCode ?: "INTERNAL_ERROR", true, "AI-Analyse momentan nicht verfügbar. Bitte versuche es erneut.")

    companion object {
        private val KNOWN: List<GenerationError> by lazy {
            listOf(
                Timeout, RateLimited, Unavailable, QuotaRequired, Refused, InvalidRequest, UnsupportedFile, FileTooLarge,
                ImageProcessingFailed, InvalidResponse, Offline, Cancelled, AlreadyInFlight, EmptyInput, GoalTooLong,
                ScheduleInvalid, MissionLimitReached, Interrupted, StorageFailed,
            )
        }

        /** Restores a persisted error code (draft table). Unknown codes stay a safe generic error. */
        fun fromCode(code: String?): GenerationError? = code?.let { c -> KNOWN.firstOrNull { it.code == c } ?: Unknown(null) }
    }
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

/**
 * Output bounds mirrored from the PWA (`normalizeAIPlan`, `AI_TEXT_LIMITS`). Android rejects a plan
 * with more than [MAX_STEPS] steps instead of truncating it (a truncated plan would silently lose
 * its final steps); the Worker contract already promises at most six.
 */
object MissionPlanRules {
    const val MAX_STEPS = 6
    /** PWA `normalizeUpdatedSessions` accepts at most 20 adapted steps. */
    const val MAX_ADAPTED_STEPS = 20
    val STEP_MINUTES = 5..60
    const val TITLE_MAX = 200
    const val SHORT_MAX = 120
    const val TEXT_MAX = 2000
    const val ITEM_MAX = 500
    /** PWA `AI_TEXT_LIMITS.tools`: at most 12 tools or apps per step, 120 characters each. */
    const val TOOLS_MAX = 12
}
