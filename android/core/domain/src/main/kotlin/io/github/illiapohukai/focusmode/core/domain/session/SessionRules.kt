package io.github.illiapohukai.focusmode.core.domain.session

import io.github.illiapohukai.focusmode.core.domain.time.FocusClock
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusTask
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import io.github.illiapohukai.focusmode.core.model.SessionType

/** Quick Focus duration contract (PWA v17.16): 5–60 minutes in 5-minute steps. */
object QuickFocusDuration {
    const val MIN = 5
    const val MAX = 60
    const val STEP = 5
    val range: IntRange = MIN..MAX

    fun isValid(minutes: Int): Boolean = minutes in range && minutes % STEP == 0

    /** Snap any value to the nearest valid step and clamp (PWA `normalizeQuickDuration`). */
    fun normalize(minutes: Int): Int {
        val snapped = Math.round(minutes / STEP.toDouble()).toInt() * STEP
        return snapped.coerceIn(MIN, MAX)
    }

    /** Static pre-launch readout, e.g. "25:00" (the real countdown exists only once started). */
    fun staticReadout(minutes: Int): String = minutes.toString().padStart(2, '0') + ":00"
}

/** Mission step durations accepted for a Mission Session. */
object MissionStepDuration {
    val range: IntRange = 1..FocusSession.MAX_PLANNED_MINUTES
}

data class QuickFocusRequest(
    val task: String?,
    val minutes: Int,
    val protectionMode: ProtectionMode,
    val scheduleId: ScheduleId? = null,
)

sealed interface SessionValidationError {
    data object DurationInvalid : SessionValidationError
    data object ModeUnavailable : SessionValidationError
    data object MissionNotFound : SessionValidationError
    data object StepNotStartable : SessionValidationError
    data object SessionAlreadyActive : SessionValidationError
}

/** Builds new Sessions. The title of a Quick Focus equals its task (PWA `startConfiguredQuickFocus`). */
class SessionFactory(private val clock: FocusClock, private val newId: () -> SessionId = EntityIds::newSessionId) {

    fun quick(request: QuickFocusRequest): FocusSession {
        require(QuickFocusDuration.isValid(request.minutes)) { "duration_invalid" }
        val task = FocusTask.of(request.task)
        return FocusSession(
            id = newId(),
            type = SessionType.QUICK,
            title = task.text.take(180),
            task = task,
            plannedMinutes = request.minutes,
            missionLink = null,
            startedAt = clock.now(),
            lifecycle = SessionLifecycle.RUNNING,
            timing = SessionTimeline.newRunningTiming(clock),
            protectionMode = request.protectionMode,
            modeLabel = "Kurzer Fokus",
            scheduleId = request.scheduleId,
        )
    }

    fun mission(
        link: MissionLink,
        title: String,
        task: String,
        minutes: Int,
        modeLabel: String,
        progressCurrent: Int,
        progressTotal: Int,
        protectionMode: ProtectionMode,
    ): FocusSession {
        require(minutes in MissionStepDuration.range) { "duration_invalid" }
        return FocusSession(
            id = newId(),
            type = SessionType.MISSION,
            title = title.ifBlank { "Missionsschritt" }.take(180),
            task = FocusTask.of(task.ifBlank { title }),
            plannedMinutes = minutes,
            missionLink = link,
            startedAt = clock.now(),
            lifecycle = SessionLifecycle.RUNNING,
            timing = SessionTimeline.newRunningTiming(clock),
            protectionMode = protectionMode,
            modeLabel = modeLabel.ifBlank { "Focus Mission" },
            progressCurrent = progressCurrent.coerceAtLeast(1),
            progressTotal = progressTotal.coerceAtLeast(1),
        )
    }
}
