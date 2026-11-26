package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationResult
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptedStep
import io.github.illiapohukai.focusmode.core.domain.ai.AiTextSafety
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.MissionAdaptation
import io.github.illiapohukai.focusmode.core.domain.ai.MissionPlanRules
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException

/* PWA `requestAdaptation()` payload (docs/API_CONTRACTS.md §3.2). Only this Mission's fields. */

@Serializable
internal data class AdaptStepDto(val id: String, val title: String, val task: String, val durationMinutes: Int, val status: String? = null)

@Serializable
internal data class AdaptMissionDto(val title: String, val subject: String, val deadlineText: String)

@Serializable
internal data class AdaptResultDto(val outcome: String, val rating: Int, val feedback: String, val plannedMinutes: Int, val actualMinutes: Int)

@Serializable
internal data class AdaptHistoryDto(val sessionId: String, val outcome: String, val rating: Int? = null, val actualMinutes: Int)

@Serializable
internal data class AdaptRequestDto(
    val missionId: String,
    val missionUpdatedAt: Long,
    val mission: AdaptMissionDto,
    val currentSession: AdaptStepDto,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val status: String,
    val rating: Int,
    val feedback: String,
    val result: AdaptResultDto,
    val remainingSessions: List<AdaptStepDto>,
    val history: List<AdaptHistoryDto>,
)

@Serializable
internal data class AdaptedSessionDto(
    val title: String? = null,
    val task: String? = null,
    val durationMinutes: Double? = null,
    val reason: String? = null,
    val requiredTools: kotlinx.serialization.json.JsonElement? = null,
    val recommendedApps: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
internal data class AdaptationDto(
    val message: String? = null,
    val summary: String? = null,
    val mainDifficulty: String? = null,
    val updatedSessions: List<AdaptedSessionDto>? = null,
)

@Serializable
internal data class AdaptResponseDto(
    val ok: Boolean? = null,
    val adaptation: AdaptationDto? = null,
    val error: JsonElement? = null,
    val code: String? = null,
    val requestId: String? = null,
)

/**
 * The one `/adapt-mission` client. Same transport rules as generation: HTTPS, no redirects, no
 * automatic retry, no logging, bounded body, one request at a time, caller cancellation aborts
 * the call. The answer is untrusted: 1–20 complete steps of 5–60 min without markup, otherwise
 * the whole adaptation is rejected and the existing plan stays.
 */
class WorkerMissionAdaptationRepository(
    private val api: WorkerApi,
    /** Below the client's 70 s call timeout (the PWA allows 120 s in the browser). */
    private val timeoutMs: Long = 60_000,
) : io.github.illiapohukai.focusmode.core.domain.ai.MissionAdaptationRepository {

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = false }

    override suspend fun adapt(request: AdaptationRequest): AdaptationResult {
        if (!lock.tryLock()) return AdaptationResult.Failure(GenerationError.AlreadyInFlight)
        try {
            val body = json.encodeToString(AdaptRequestDto.serializer(), toDto(request)).toRequestBody(JSON)
            val response = try {
                withTimeout(timeoutMs) { api.adaptMission(body) }
            } catch (_: TimeoutCancellationException) {
                return AdaptationResult.Failure(GenerationError.Timeout)
            } catch (_: InterruptedIOException) {
                return AdaptationResult.Failure(GenerationError.Timeout)
            } catch (_: IOException) {
                return AdaptationResult.Failure(GenerationError.Offline)
            }
            val raw = try { WorkerBodies.read(response) } catch (_: IOException) { return AdaptationResult.Failure(GenerationError.InvalidResponse) }
            val dto = raw?.let { runCatching { json.decodeFromString(AdaptResponseDto.serializer(), it) }.getOrNull() }
            val requestId = WorkerErrorMapper.requestId(dto?.error, dto?.requestId)
            if (!response.isSuccessful || dto?.ok != true) {
                val error = if (response.isSuccessful && dto == null) GenerationError.InvalidResponse
                else WorkerErrorMapper.map(response.code(), WorkerErrorMapper.code(dto?.error, dto?.code))
                return AdaptationResult.Failure(error, requestId)
            }
            val adaptation = map(dto.adaptation) ?: return AdaptationResult.Failure(GenerationError.InvalidResponse, requestId)
            return AdaptationResult.Success(adaptation)
        } finally {
            lock.unlock()
        }
    }

    internal fun toDto(r: AdaptationRequest): AdaptRequestDto {
        fun t(v: String, max: Int) = MissionPlanMapper.text(v, max)
        fun step(s: io.github.illiapohukai.focusmode.core.domain.ai.AdaptationStepInput) =
            AdaptStepDto(s.id, t(s.title, MissionPlanRules.TITLE_MAX), t(s.task, MissionPlanRules.TEXT_MAX), s.durationMinutes, s.status)
        val feedback = t(r.feedback, 1000)
        val rating = r.rating.coerceIn(1, 5)
        return AdaptRequestDto(
            missionId = r.missionId,
            missionUpdatedAt = r.missionUpdatedAt,
            mission = AdaptMissionDto(t(r.title, MissionPlanRules.TITLE_MAX), t(r.subject, MissionPlanRules.SHORT_MAX), t(r.deadlineText, MissionPlanRules.SHORT_MAX)),
            currentSession = step(r.currentStep).copy(status = null),
            plannedMinutes = r.plannedMinutes,
            actualMinutes = r.actualMinutes,
            status = r.outcome,
            rating = rating,
            feedback = feedback,
            result = AdaptResultDto(r.outcome, rating, feedback, r.plannedMinutes, r.actualMinutes),
            remainingSessions = r.remainingSteps.take(MissionPlanRules.MAX_ADAPTED_STEPS).map(::step),
            history = r.history.takeLast(50).map { AdaptHistoryDto(it.sessionId, it.outcome, it.rating, it.actualMinutes) },
        )
    }

    internal fun map(dto: AdaptationDto?): MissionAdaptation? {
        dto ?: return null
        val raw = dto.updatedSessions ?: return null
        if (raw.isEmpty() || raw.size > MissionPlanRules.MAX_ADAPTED_STEPS) return null
        val steps = raw.map { s ->
            val title = MissionPlanMapper.text(s.title, MissionPlanRules.TITLE_MAX)
            val task = MissionPlanMapper.text(s.task, MissionPlanRules.TEXT_MAX)
            val minutes = s.durationMinutes?.takeIf { it.isFinite() }?.let { Math.round(it).toInt() }
            if (title.isEmpty() || minutes == null || minutes !in MissionPlanRules.STEP_MINUTES) return null
            AdaptedStep(title, task, minutes, MissionPlanMapper.text(s.reason, MissionPlanRules.TEXT_MAX), MissionPlanMapper.tools(s.requiredTools), MissionPlanMapper.tools(s.recommendedApps))
        }
        if (steps.map { it.title.lowercase() to it.task.lowercase() }.toSet().size != steps.size) return null
        val message = MissionPlanMapper.text(dto.message, MissionPlanRules.ITEM_MAX)
        val summary = MissionPlanMapper.text(dto.summary ?: dto.mainDifficulty, MissionPlanRules.TEXT_MAX)
        val texts = listOf(message, summary) + steps.flatMap { listOf(it.title, it.task, it.reason) }
        if (texts.any(AiTextSafety::isUnsafe)) return null
        return MissionAdaptation(message, summary, steps)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
