package io.github.illiapohukai.focusmode.core.domain.ai

/**
 * Optional plan adaptation through the existing Worker route `POST /adapt-mission` (JSON). The
 * payload mirrors the PWA `requestAdaptation()` contract (docs/API_CONTRACTS.md §3) with only
 * this Mission's fields: never other Missions, global History, streak, selected apps, settings
 * or device data.
 */
data class AdaptationStepInput(val id: String, val title: String, val task: String, val durationMinutes: Int, val status: String)

data class AdaptationHistoryEntry(val sessionId: String, val outcome: String, val rating: Int?, val actualMinutes: Int)

data class AdaptationRequest(
    val missionId: String,
    /** Optimistic-concurrency token (PWA `missionUpdatedAt`, epoch milliseconds). */
    val missionUpdatedAt: Long,
    val title: String,
    val subject: String,
    val deadlineText: String,
    val currentStep: AdaptationStepInput,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    /** PWA outcome wire value: completed | partial | aborted. */
    val outcome: String,
    val rating: Int,
    val feedback: String,
    val remainingSteps: List<AdaptationStepInput>,
    val history: List<AdaptationHistoryEntry>,
)

/** One validated replacement step. Ids are always assigned locally when stored. */
data class AdaptedStep(val title: String, val task: String, val durationMinutes: Int, val reason: String)

data class MissionAdaptation(val message: String, val summary: String, val steps: List<AdaptedStep>)

sealed interface AdaptationResult {
    data class Success(val adaptation: MissionAdaptation) : AdaptationResult
    data class Failure(val error: GenerationError, val serverRequestId: String? = null) : AdaptationResult
}

/** Boundary to `/adapt-mission`. One request at a time; a second call fails fast. */
interface MissionAdaptationRepository {
    suspend fun adapt(request: AdaptationRequest): AdaptationResult
}
