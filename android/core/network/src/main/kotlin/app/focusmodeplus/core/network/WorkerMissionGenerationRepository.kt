package app.focusmodeplus.core.network

import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.GenerationResult
import app.focusmodeplus.core.domain.ai.MissionGenerationRepository
import app.focusmodeplus.core.domain.ai.MissionGenerationRequest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException

data class WorkerTimeouts(val textMs: Long = 38_000, val imageMs: Long = 52_000)

/**
 * The one Mission-generation client. A single request may be in flight; a second call fails fast
 * with [GenerationError.AlreadyInFlight] (duplicate-request prevention). Caller cancellation
 * propagates and aborts the HTTP call. The multipart body carries only the Worker contract fields
 * (goal text, type, confidence, optional schedule and image) — never selected apps, usage data,
 * History or identifiers.
 */
class WorkerMissionGenerationRepository(
    private val api: WorkerApi,
    private val timeouts: WorkerTimeouts = WorkerTimeouts(),
) : MissionGenerationRepository {

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = false }

    override suspend fun generate(request: MissionGenerationRequest): GenerationResult {
        if (!lock.tryLock()) return GenerationResult.Failure(GenerationError.AlreadyInFlight)
        try {
            val goal = MissionPlanMapper.text(request.goalText, MissionGenerationRequest.MAX_GOAL_CHARS)
            if (goal.isEmpty() && request.material == null) return GenerationResult.Failure(GenerationError.EmptyInput)
            request.material?.validationError()?.let { return GenerationResult.Failure(it) }

            val parts = buildParts(request.copy(goalText = goal))
            val timeout = if (request.material != null) timeouts.imageMs else timeouts.textMs
            val response = try {
                withTimeout(timeout) { api.createMission(parts) }
            } catch (_: TimeoutCancellationException) {
                return GenerationResult.Failure(GenerationError.Timeout)
            } catch (_: InterruptedIOException) {
                return GenerationResult.Failure(GenerationError.Timeout) // OkHttp call/read timeout
            } catch (_: IOException) {
                return GenerationResult.Failure(GenerationError.Offline)
            }
            val raw = try {
                WorkerBodies.read(response)
            } catch (_: IOException) {
                return GenerationResult.Failure(GenerationError.InvalidResponse)
            }
            val dto = raw?.let { runCatching { json.decodeFromString(CreateMissionResponseDto.serializer(), it) }.getOrNull() }
            if (!response.isSuccessful || dto?.ok != true) {
                val error = if (response.isSuccessful) {
                    if (dto == null) GenerationError.InvalidResponse else WorkerErrorMapper.map(response.code(), dto)
                } else WorkerErrorMapper.map(response.code(), dto)
                return GenerationResult.Failure(error, WorkerErrorMapper.requestId(dto))
            }
            return try {
                GenerationResult.Success(MissionPlanMapper.map(dto.plan, request.schedule))
            } catch (_: InvalidPlanException) {
                GenerationResult.Failure(GenerationError.InvalidResponse, WorkerErrorMapper.requestId(dto))
            } catch (_: SerializationException) {
                GenerationResult.Failure(GenerationError.InvalidResponse)
            }
        } finally {
            lock.unlock()
        }
    }

    internal fun buildParts(request: MissionGenerationRequest): List<MultipartBody.Part> = buildList {
        add(MultipartBody.Part.createFormData("goalText", request.goalText))
        add(MultipartBody.Part.createFormData("missionType", request.type.wire))
        add(MultipartBody.Part.createFormData("confidence", request.confidence.wire))
        request.schedule?.let { s ->
            val payload = SchedulePayloadDto(
                currentLocalDate = s.currentLocalDate.toString(),
                deadlineDate = s.deadlineDate.toString(),
                preparationStartDate = s.preparationStartDate.toString(),
                preparationDays = s.preparationDays,
                startMode = s.startMode.takeIf { it in START_MODES } ?: "immediate",
                timezone = s.timezone,
                preferredWeekdays = s.preferredWeekdays.filter { it in 1..7 }.distinct().sorted().ifEmpty { null },
                preferredSessionMinutes = s.preferredSessionMinutes?.takeIf { it in setOf(20, 30, 45) },
            )
            add(MultipartBody.Part.createFormData("schedule", json.encodeToString(SchedulePayloadDto.serializer(), payload)))
        }
        request.material?.let { m ->
            add(MultipartBody.Part.createFormData("image", m.uploadName, m.bytes.toRequestBody(m.mimeType.toMediaType())))
        }
    }

    private companion object {
        val START_MODES = setOf("immediate", "one_week_before", "two_weeks_before", "custom")
    }
}
