package app.focusmodeplus.core.domain.session

import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.FocusSession
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class FinalizeTrigger { TIMER_ELAPSED, TIMER_ELAPSED_WHILE_AWAY, USER_END, USER_CANCEL, EMERGENCY_EXIT, RECOVERY_SAVE }

sealed interface FinalizationDecision {
    /** [awaitsReview]: a Mission Session whose step outcome the user still confirms. */
    data class Finalize(val result: FocusSessionResult, val awaitsReview: Boolean) : FinalizationDecision
    data class Rejected(val reason: String) : FinalizationDecision
}

/**
 * Turns an ended Session into exactly one History record (PWA `buildFinalSessionV112` semantics):
 * - below one counted minute, an end is a cancellation (`ended_too_early`), never focus time;
 * - a user cancel never counts as focus;
 * - ending early with >= 1 minute is PARTIAL and counts (Android distinguishes this; see
 *   docs/pwa-parity.md);
 * - the planned duration elapsing is COMPLETED.
 * Mission Sessions with counted time wait for the user's step review (PWA review flow).
 */
object FinalizationPolicy {
    const val MIN_COUNTED_MS = 60_000L

    fun decide(
        session: FocusSession,
        elapsedMs: Long,
        trigger: FinalizeTrigger,
        finishedAt: Instant,
        zone: ZoneId,
        endNote: EndNote? = null,
    ): FinalizationDecision {
        val planned = session.plannedMs
        val elapsed = elapsedMs.coerceIn(0, planned)
        val minutes = (elapsed / 60_000L).toInt().coerceAtMost(session.plannedMinutes)
        val reachedPlan = elapsed >= planned
        val counted = elapsed >= MIN_COUNTED_MS

        fun result(status: SessionStatus, outcome: SessionOutcome, reason: SessionEndReason, counts: Boolean) =
            FocusSessionResult(
                sessionId = session.id,
                type = session.type,
                status = status,
                outcome = outcome,
                endReason = reason,
                title = session.title,
                task = session.task.text,
                plannedMinutes = session.plannedMinutes,
                actualMinutes = minutes,
                elapsedMs = elapsed,
                countsAsFocus = counts,
                startedAt = session.startedAt,
                finishedAt = maxOf(finishedAt, session.startedAt),
                localDate = maxOf(finishedAt, session.startedAt).atZone(zone).toLocalDate(),
                zoneId = zone.id,
                missionLink = session.missionLink,
                protectionMode = session.protectionMode,
                endNote = endNote,
            )

        fun cancelled(reason: SessionEndReason) =
            FinalizationDecision.Finalize(result(SessionStatus.CANCELLED, SessionOutcome.CANCELED, reason, false), awaitsReview = false)

        fun counted(reason: SessionEndReason): FinalizationDecision {
            val outcome = if (reachedPlan) SessionOutcome.COMPLETED else SessionOutcome.PARTIAL
            val mission = session.type == SessionType.MISSION
            val finalReason = if (mission) SessionEndReason.AWAITING_USER_REVIEW else reason
            return FinalizationDecision.Finalize(result(SessionStatus.COMPLETED, outcome, finalReason, true), awaitsReview = mission)
        }

        return when (trigger) {
            FinalizeTrigger.TIMER_ELAPSED, FinalizeTrigger.TIMER_ELAPSED_WHILE_AWAY ->
                if (!reachedPlan) FinalizationDecision.Rejected("not_elapsed")
                else counted(if (trigger == FinalizeTrigger.TIMER_ELAPSED) SessionEndReason.TIMER_FINISHED else SessionEndReason.TIMER_FINISHED_WHILE_AWAY)
            FinalizeTrigger.USER_END ->
                when {
                    !counted -> cancelled(SessionEndReason.ENDED_TOO_EARLY)
                    reachedPlan -> counted(SessionEndReason.TIMER_FINISHED)
                    else -> counted(SessionEndReason.USER_FINISHED)
                }
            FinalizeTrigger.USER_CANCEL -> cancelled(SessionEndReason.USER_CANCELLED)
            FinalizeTrigger.EMERGENCY_EXIT ->
                if (!counted) cancelled(SessionEndReason.EMERGENCY_EXIT) else counted(SessionEndReason.EMERGENCY_EXIT)
            FinalizeTrigger.RECOVERY_SAVE ->
                if (!counted) FinalizationDecision.Rejected("too_short") else counted(SessionEndReason.RECOVERY_CONFIRMED)
        }
    }
}
