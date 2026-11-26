package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationHistoryEntry
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationResult
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationStepInput
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.MissionAdaptationRepository
import io.github.illiapohukai.focusmode.core.domain.di.ApplicationScope
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.domain.repository.MissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.domain.repository.SessionHistoryRepository
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdaptationRun {
    data object Idle : AdaptationRun
    data class Running(val sessionId: SessionId) : AdaptationRun
    data class Adapted(val sessionId: SessionId, val message: String, val summary: String) : AdaptationRun
    data class Failed(val sessionId: SessionId, val error: GenerationError) : AdaptationRun
}

/**
 * The one optional-adaptation owner (PWA `optional-adaptation.js`). It runs only on the user's
 * explicit "Plan mit AI anpassen" after the Session result was already committed, so the
 * result is never lost. One request at a time, idempotent per Session id, this Mission's data
 * only. A valid answer replaces only the open steps, in one transaction, and only if the Mission
 * did not change meanwhile; any failure keeps the existing valid plan.
 */
@Singleton
class MissionAdaptationOwner @Inject constructor(
    private val missions: MissionRepository,
    private val history: SessionHistoryRepository,
    private val adapter: MissionAdaptationRepository,
    private val network: NetworkStatus,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var job: Job? = null
    private var token = 0L
    private val adapted = HashSet<String>()
    private val _run = MutableStateFlow<AdaptationRun>(AdaptationRun.Idle)
    val run: StateFlow<AdaptationRun> = _run.asStateFlow()

    /** True when [sessionId]'s Mission still has open steps that could be adapted. */
    suspend fun canAdapt(sessionId: SessionId): Boolean {
        val link = history.getResult(sessionId)?.missionLink ?: return false
        val mission = missions.getMission(link.missionId) ?: return false
        return mission.status == MissionStatus.ACTIVE && mission.steps.any { it.status != MissionStepStatus.COMPLETED }
    }

    suspend fun adapt(sessionId: SessionId): Boolean = mutex.withLock {
        if (job?.isActive == true) return@withLock false
        if (sessionId.value in adapted) return@withLock false
        val result = history.getResult(sessionId) ?: return@withLock fail(sessionId, GenerationError.InvalidRequest)
        // The result must be committed first (PWA commitMissionSessionResult before the request).
        if (result.endReason == SessionEndReason.AWAITING_USER_REVIEW) return@withLock fail(sessionId, GenerationError.InvalidRequest)
        val link = result.missionLink ?: return@withLock fail(sessionId, GenerationError.InvalidRequest)
        val mission = missions.getMission(link.missionId) ?: return@withLock fail(sessionId, GenerationError.InvalidRequest)
        val open = mission.steps.filter { it.status != MissionStepStatus.COMPLETED }.sortedBy { it.position }
        if (mission.status != MissionStatus.ACTIVE || open.isEmpty()) return@withLock fail(sessionId, GenerationError.InvalidRequest)
        if (!network.online.value) return@withLock fail(sessionId, GenerationError.Offline)
        val current = mission.steps.firstOrNull { it.id == link.stepId }
        val request = AdaptationRequest(
            missionId = mission.id.value,
            missionUpdatedAt = mission.updatedAt.toEpochMilli(),
            title = mission.title,
            subject = mission.subject,
            deadlineText = mission.deadlineText,
            currentStep = AdaptationStepInput(link.stepId.value, current?.title ?: result.title, current?.task ?: result.task, result.plannedMinutes, current?.status?.wire ?: "active"),
            plannedMinutes = result.plannedMinutes,
            actualMinutes = result.actualMinutes,
            outcome = result.outcome.wire,
            rating = result.rating ?: 3,
            feedback = result.reviewNote.orEmpty(),
            remainingSteps = open.map { AdaptationStepInput(it.id.value, it.title, it.task, it.durationMinutes, it.status.wire) },
            history = missions.progressHistory(mission.id).map { AdaptationHistoryEntry(it.sessionId.value, it.outcome.wire, it.rating, it.actualMinutes) },
        )
        val myToken = ++token
        _run.value = AdaptationRun.Running(sessionId)
        job = scope.launch { execute(myToken, sessionId, mission.id, mission.updatedAt, request) }
        true
    }

    private suspend fun execute(
        myToken: Long, sessionId: SessionId, missionId: io.github.illiapohukai.focusmode.core.model.MissionId,
        expectedUpdatedAt: java.time.Instant, request: AdaptationRequest,
    ) {
        val result = try { adapter.adapt(request) } catch (c: CancellationException) { throw c } catch (_: Exception) { AdaptationResult.Failure(GenerationError.Unknown(null)) }
        mutex.withLock {
            if (myToken != token) return
            _run.value = when (result) {
                is AdaptationResult.Success -> when (missions.replaceRemainingSteps(missionId, result.adaptation.steps, expectedUpdatedAt)) {
                    is MissionWriteResult.Ok -> {
                        adapted += sessionId.value
                        AdaptationRun.Adapted(sessionId, result.adaptation.message, result.adaptation.summary)
                    }
                    is MissionWriteResult.Rejected -> AdaptationRun.Failed(sessionId, GenerationError.StorageFailed)
                }
                is AdaptationResult.Failure -> AdaptationRun.Failed(sessionId, result.error)
            }
            job = null
        }
    }

    private fun fail(sessionId: SessionId, error: GenerationError): Boolean {
        _run.value = AdaptationRun.Failed(sessionId, error)
        return false
    }

    /** Stops waiting; a late answer is ignored and the plan stays unchanged. */
    suspend fun cancel() = mutex.withLock {
        token++
        job?.cancel()
        job = null
        _run.value = AdaptationRun.Idle
    }

    fun reset() {
        if (_run.value !is AdaptationRun.Running) _run.value = AdaptationRun.Idle
    }
}
