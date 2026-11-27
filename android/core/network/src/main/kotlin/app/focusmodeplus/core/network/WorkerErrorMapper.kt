package app.focusmodeplus.core.network

import app.focusmodeplus.core.domain.ai.GenerationError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Maps the Worker's error contract (PWA `parseMissionErrorResponseCore`) to safe errors. Server
 * messages are not shown verbatim on Android; only a validated request id is kept as a support
 * reference. 402 is reserved for a future server-authoritative AI quota.
 */
internal object WorkerErrorMapper {
    private val REQUEST_ID = Regex("^[A-Za-z0-9_-]{4,64}$")

    fun code(dto: CreateMissionResponseDto?): String? = code(dto?.error, dto?.code)

    fun code(error: JsonElement?, rootCode: String?): String? =
        ((error as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull ?: rootCode

    fun requestId(dto: CreateMissionResponseDto?): String? = requestId(dto?.error, dto?.requestId)

    fun requestId(error: JsonElement?, rootRequestId: String?): String? {
        val err = error as? JsonObject
        val raw = (err?.get("requestId") as? JsonPrimitive)?.contentOrNull ?: rootRequestId
        return raw?.takeIf(REQUEST_ID::matches)
    }

    fun map(status: Int, dto: CreateMissionResponseDto?): GenerationError = map(status, code(dto))

    /**
     * Status first where it is unambiguous: 401/403 (refused), 402 (billing unavailable) and
     * 408/504/524 (timeout) can never become a retryable "unknown"; 4xx validation is never retried.
     */
    fun map(status: Int, code: String?): GenerationError {
        return when {
            status == 401 || status == 403 -> GenerationError.Refused
            status == 408 -> GenerationError.Timeout
            status == 422 -> GenerationError.InvalidRequest
            code == "UNSUPPORTED_FILE_TYPE" || status == 415 -> GenerationError.UnsupportedFile
            code == "FILE_TOO_LARGE" || status == 413 -> GenerationError.FileTooLarge
            code == "IMAGE_PROCESSING_FAILED" -> GenerationError.ImageProcessingFailed
            status == 402 -> GenerationError.QuotaRequired
            code == "INVALID_REQUEST" || status == 400 -> GenerationError.InvalidRequest
            code == "AI_RATE_LIMITED" || status == 429 -> GenerationError.RateLimited
            code == "AI_TIMEOUT" || status == 504 || status == 524 -> GenerationError.Timeout
            code == "AI_UNAVAILABLE" || status == 502 || status == 503 -> GenerationError.Unavailable
            code == "INVALID_AI_RESPONSE" -> GenerationError.InvalidResponse
            status in 400..499 -> GenerationError.InvalidRequest
            status >= 500 -> GenerationError.Unavailable
            else -> GenerationError.Unknown(code?.takeIf { it.length <= 64 && it.matches(Regex("^[A-Z_]{2,64}$")) })
        }
    }
}
