package app.focusmodeplus.core.model

import java.time.Instant

/** PWA `type`: 'quick' never touches Missions; 'mission' requires a valid Mission link. */
enum class SessionType(val wire: String) {
    QUICK("quick"), MISSION("mission");
    companion object { fun fromWire(v: String?): SessionType? = entries.firstOrNull { it.wire == v } }
}

/**
 * Lifecycle of the single active Session (PWA `lifecycleState`).
 * - RUNNING: time is advancing.
 * - AWAITING_RESUME: time is frozen at the last checkpoint (PWA: document terminated;
 *   Android: device rebooted). The user resumes, saves or discards.
 * - AWAITING_REVIEW: a Mission Session ended; its record is finalized and waits for the
 *   user's outcome review.
 */
enum class SessionLifecycle { RUNNING, AWAITING_RESUME, AWAITING_REVIEW }

/** User-entered task text. Optional; the PWA default applies when empty. */
@JvmInline
value class FocusTask private constructor(val text: String) {
    companion object {
        const val DEFAULT = "Konzentriert arbeiten"
        const val MAX_INPUT_LENGTH = 200
        const val MAX_STORED_LENGTH = 1200
        fun of(raw: String?): FocusTask {
            val clean = raw.orEmpty().replace(Regex("[\\u0000-\\u001f\\u007f]"), " ").trim()
            return FocusTask(if (clean.isEmpty()) DEFAULT else clean.take(MAX_STORED_LENGTH))
        }
    }
}

data class MissionLink(val missionId: MissionId, val stepId: MissionStepId)

/**
 * Durable timing of the active Session. No per-second writes: a record changes on start,
 * resume, a coarse (minute) checkpoint and finalization only.
 *
 * Time is measured on the monotonic `elapsedRealtime` clock (includes deep sleep and ignores
 * wall-clock changes) while the device stays on the same boot. Wall-clock values are kept for
 * display and History. After a reboot the monotonic base is lost, so the Session freezes at the
 * last durable checkpoint and awaits the user's decision, like the PWA after a reload.
 */
data class SessionTiming(
    /** Elapsed time banked by finished running segments (before [segmentStartRealtimeMs]). */
    val accumulatedMs: Long,
    val segmentStartWall: Instant?,
    val segmentStartRealtimeMs: Long?,
    val segmentBootCount: Int?,
    /** Last durable elapsed value; authoritative when no reliable monotonic reading exists. */
    val checkpointElapsedMs: Long,
    val checkpointWall: Instant,
)

data class FocusSession(
    val id: SessionId,
    val type: SessionType,
    val title: String,
    val task: FocusTask,
    val plannedMinutes: Int,
    val missionLink: MissionLink?,
    val startedAt: Instant,
    val lifecycle: SessionLifecycle,
    val timing: SessionTiming,
    val protectionMode: ProtectionMode,
    val modeLabel: String,
    val progressCurrent: Int = 1,
    val progressTotal: Int = 1,
    /** Optional origin (e.g. a scheduled Focus window) for display only. */
    val scheduleId: ScheduleId? = null,
) {
    val plannedMs: Long get() = plannedMinutes * 60_000L

    init {
        require(plannedMinutes in 1..MAX_PLANNED_MINUTES) { "duration_invalid" }
        require(title.isNotBlank()) { "title_missing" }
        when (type) {
            SessionType.QUICK -> require(missionLink == null) { "quick_mission_link_forbidden" }
            SessionType.MISSION -> require(missionLink != null) { "mission_link_required" }
        }
    }

    companion object { const val MAX_PLANNED_MINUTES = 240 }
}
