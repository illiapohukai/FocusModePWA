package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationResult
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedStep
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationResult
import io.github.illiapohukai.focusmode.core.domain.ai.MaterialPreparation
import io.github.illiapohukai.focusmode.core.domain.ai.MissionAdaptationRepository
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRepository
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterial
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterialStore
import io.github.illiapohukai.focusmode.core.domain.mission.DraftAttachment
import io.github.illiapohukai.focusmode.core.domain.platform.NetworkStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Test-only Worker stand-in. Each call waits for [respond] (or answers immediately with [next]).
 * With [ignoreCancellation] it keeps waiting after the caller is cancelled, like a response that
 * is already on the wire, so tests can prove late answers are dropped.
 */
class FakeMissionGenerator : MissionGenerationRepository {
    val requests = mutableListOf<MissionGenerationRequest>()
    var next: GenerationResult? = null
    var ignoreCancellation = false
    private var pending: CompletableDeferred<GenerationResult>? = null
    private var queued: GenerationResult? = null

    override suspend fun generate(request: MissionGenerationRequest): GenerationResult {
        requests += request
        next?.let { return it }
        queued?.let { queued = null; return it }
        val d = CompletableDeferred<GenerationResult>().also { pending = it }
        return if (ignoreCancellation) withContext(NonCancellable) { d.await() } else d.await()
    }

    /** Completes the waiting call, or answers the next call if none is waiting yet. */
    fun respond(result: GenerationResult) {
        val p = pending
        if (p != null && !p.isCompleted) p.complete(result) else queued = result
    }
}

class FakeMissionAdapter : MissionAdaptationRepository {
    val requests = mutableListOf<AdaptationRequest>()
    var next: AdaptationResult? = null
    private var pending: CompletableDeferred<AdaptationResult>? = null
    private var queued: AdaptationResult? = null
    override suspend fun adapt(request: AdaptationRequest): AdaptationResult {
        requests += request
        next?.let { return it }
        queued?.let { queued = null; return it }
        return CompletableDeferred<AdaptationResult>().also { pending = it }.await()
    }
    fun respond(result: AdaptationResult) {
        val p = pending
        if (p != null && !p.isCompleted) p.complete(result) else queued = result
    }
}

class FakeMaterialStore : MissionMaterialStore {
    var stored: MissionMaterial? = null
    var prepareResult: MaterialPreparation = MaterialPreparation.Ready(DraftAttachment(120_000, "image/jpeg", 1200, 900))
    var cleared = 0
    override suspend fun prepare(source: String): MaterialPreparation = prepareResult.also {
        if (it is MaterialPreparation.Ready) stored = MissionMaterial(ByteArray(it.attachment.byteCount) { 1 }, it.attachment.mime)
    }
    override suspend fun load(): MissionMaterial? = stored
    override suspend fun clear() { stored = null; cleared++ }
}

class FakeNetworkStatus(online: Boolean = true) : NetworkStatus {
    override val online = MutableStateFlow(online)
}

/** Test-only plans. Never used by production code. */
object PlanFixtures {
    fun plan(steps: Int = 3, title: String = "Mathe-Klausur", deadline: LocalDate? = null) = GeneratedMissionPlan(
        title = title,
        subject = "Mathematik",
        type = MissionType.EXAM,
        deadlineText = deadline?.toString() ?: "Nicht angegeben",
        steps = (1..steps).map { GeneratedStep("s$it", "Schritt $it", "Aufgabe $it bearbeiten", 25, "Warum $it", null) },
        estimateTotalMinutes = steps * 25,
        scheduleWarning = null,
        difficulty = "medium",
        explanation = "Erst Grundlagen, dann Übung.",
    )
}
