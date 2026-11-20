package io.github.illiapohukai.focusmode.core.domain.session

import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionId
import kotlinx.coroutines.flow.StateFlow

enum class FreezeCause { DEVICE_RESTARTED, STORED_FROZEN }

/** The single authoritative view of the active Session. Screens never keep their own copy. */
sealed interface SessionState {
    data object Loading : SessionState
    /** No active Session. [lastFinalized] lets a screen route to the Result after a finish. */
    data class Idle(val lastFinalized: SessionId? = null) : SessionState
    data class Running(val session: FocusSession) : SessionState
    data class AwaitingResume(val session: FocusSession, val cause: FreezeCause) : SessionState
    /** A Mission Session ended; its History record exists and awaits the step review. */
    data class AwaitingReview(val result: FocusSessionResult) : SessionState

    val activeSession: FocusSession?
        get() = when (this) {
            is Running -> session
            is AwaitingResume -> session
            else -> null
        }
}

enum class RestoreTrigger { APP_START, BOOT_COMPLETED, PACKAGE_REPLACED, TIME_CHANGED, END_ALARM, RUNTIME_TICK }

sealed interface StartResult {
    data class Started(val session: FocusSession) : StartResult
    data class Rejected(val error: SessionValidationError) : StartResult
}

sealed interface FinishResult {
    data class Finalized(val result: FocusSessionResult, val awaitsReview: Boolean) : FinishResult
    data class Rejected(val reason: String) : FinishResult
    data object NoActiveSession : FinishResult
}

/**
 * Contract of the one Focus Session owner (implemented in :core:session). Every start, end,
 * recovery and review goes through it; it alone writes the active Session and finalizes it.
 */
interface FocusSessionController {
    val state: StateFlow<SessionState>

    suspend fun restore(trigger: RestoreTrigger)
    suspend fun startQuick(request: QuickFocusRequest): StartResult
    suspend fun startMissionStep(missionId: MissionId, stepId: MissionStepId, mode: ProtectionMode): StartResult
    suspend fun endByUser(note: EndNote? = null): FinishResult
    suspend fun cancel(note: EndNote? = null): FinishResult
    suspend fun emergencyExit(): FinishResult
    suspend fun resumeRecovered(): Boolean
    suspend fun saveRecovered(): FinishResult
    suspend fun discardRecovered(): Boolean
    suspend fun submitMissionReview(stepCompleted: Boolean, rating: Int?): Boolean
    /** Coarse runtime tick (minute cadence): checkpoint and detect completion. */
    suspend fun onRuntimeTick()
    fun read(): ElapsedReading?
}
