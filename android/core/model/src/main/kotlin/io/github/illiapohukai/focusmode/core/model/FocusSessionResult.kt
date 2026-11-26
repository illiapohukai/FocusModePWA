package io.github.illiapohukai.focusmode.core.model

import java.time.Instant
import java.time.LocalDate

/** PWA terminal `status` (kept for backup compatibility). */
enum class SessionStatus(val wire: String) {
    COMPLETED("completed"), CANCELLED("cancelled"), INTERRUPTED("interrupted");
    companion object { fun fromWire(v: String?): SessionStatus? = entries.firstOrNull { it.wire == v } }
}

/**
 * What the Session amounted to.
 * - COMPLETED: the planned duration elapsed.
 * - PARTIAL: ended early with at least one counted minute (focus time counts).
 * - CANCELED: cancelled, or ended below one minute (focus time does not count).
 */
enum class SessionOutcome(val wire: String) {
    COMPLETED("completed"), PARTIAL("partial"), CANCELED("aborted");
    companion object { fun fromWire(v: String?): SessionOutcome? = entries.firstOrNull { it.wire == v } }
}

/** PWA `result.reason` values plus Android-specific ones. */
enum class SessionEndReason(val wire: String) {
    TIMER_FINISHED("timer_finished"),
    TIMER_FINISHED_WHILE_AWAY("timer_finished_while_away"),
    USER_FINISHED("user_finished"),
    ENDED_TOO_EARLY("ended_too_early"),
    USER_CANCELLED("user_cancelled"),
    EMERGENCY_EXIT("emergency_exit"),
    RECOVERY_CONFIRMED("recovery_confirmed"),
    AWAITING_USER_REVIEW("awaiting_user_review"),
    USER_REVIEWED("user_reviewed"),
    IMPORTED("imported");
    companion object { fun fromWire(v: String?): SessionEndReason? = entries.firstOrNull { it.wire == v } }
}

/** Reason picked at the Protected Focus end barrier. Stored locally, never uploaded. */
enum class EndNote(val label: String) {
    TASK_DONE("Aufgabe erledigt"),
    NEED_BREAK("Pause nötig"),
    DISTRACTED("Abgelenkt"),
    OTHER("Anderer Grund"),
    EMERGENCY("Notfall"),
}

/** One finalized History record. History holds at most one record per Session id. */
data class FocusSessionResult(
    val sessionId: SessionId,
    val type: SessionType,
    val status: SessionStatus,
    val outcome: SessionOutcome,
    val endReason: SessionEndReason,
    val title: String,
    val task: String,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val elapsedMs: Long,
    val countsAsFocus: Boolean,
    val startedAt: Instant,
    val finishedAt: Instant,
    /** Local calendar day of [finishedAt] in the zone at finalization (stable across travel). */
    val localDate: LocalDate,
    val zoneId: String,
    val missionLink: MissionLink?,
    val protectionMode: ProtectionMode,
    val endNote: EndNote? = null,
    val rating: Int? = null,
    val origin: DataOrigin = DataOrigin.LOCAL,
    val verification: Verification = Verification.LOCAL_UNVERIFIED,
    /** Optional Mission review note ("Was war schwierig?"). Local; sent only to an explicit adaptation. */
    val reviewNote: String? = null,
)

/** The user's Mission step review: one of the three PWA outcomes, an optional rating and note. */
data class MissionReview(val outcome: SessionOutcome, val rating: Int? = null, val note: String? = null) {
    val completesStep: Boolean get() = outcome == SessionOutcome.COMPLETED

    companion object {
        const val NOTE_MAX = 1000
        val RATINGS = 1..5
        /** Control characters removed, trimmed, bounded (PWA `maxlength="1000"`). */
        fun cleanNote(raw: String?): String? =
            raw?.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), " ")?.trim()?.take(NOTE_MAX)?.ifEmpty { null }
    }
}
