package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
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

    fun code(dto: CreateMissionResponseDto?): String? {
        val err = dto?.error
        return ((err as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull ?: dto?.code
    }

    fun requestId(dto: CreateMissionResponseDto?): String? {
        val err = dto?.error as? JsonObject
        val raw = (err?.get("requestId") as? JsonPrimitive)?.contentOrNull ?: dto?.requestId
        return raw?.takeIf(REQUEST_ID::matches)
    }

    fun map(status: Int, dto: CreateMissionResponseDto?): GenerationError {
        val code = code(dto)
        return when {
            code == "UNSUPPORTED_FILE_TYPE" || status == 415 -> GenerationError.UnsupportedFile
            code == "FILE_TOO_LARGE" || status == 413 -> GenerationError.FileTooLarge
            code == "IMAGE_PROCESSING_FAILED" -> GenerationError.ImageProcessingFailed
            status == 402 -> GenerationError.QuotaRequired
            code == "INVALID_REQUEST" || status == 400 -> GenerationError.InvalidRequest
            code == "AI_RATE_LIMITED" || status == 429 -> GenerationError.RateLimited
            code == "AI_TIMEOUT" || status == 504 || status == 524 -> GenerationError.Timeout
            code == "AI_UNAVAILABLE" || status == 502 || status == 503 -> GenerationError.Unavailable
            code == "INVALID_AI_RESPONSE" -> GenerationError.InvalidResponse
            else -> GenerationError.Unknown(code?.takeIf { it.length <= 64 })
        }
    }
}
