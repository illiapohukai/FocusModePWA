package app.focusmodeplus.core.model

import java.time.Instant
import java.time.LocalDate

/** Local streak: consecutive local days with counted focus. Never verified competitive data. */
data class LocalStreak(
    val current: Int,
    val best: Int,
    val todayActive: Boolean,
    val trust: TrustLevel = TrustLevel.LOCAL_DERIVED,
)

data class LocalProgress(
    val todayMinutes: Int,
    val todaySessions: Int,
    val weekMinutes: Int,
    val weekSessions: Int,
    val totalMinutes: Int,
    val countedSessions: Int,
    val streak: LocalStreak,
    val trust: TrustLevel = TrustLevel.LOCAL_DERIVED,
) {
    companion object {
        val EMPTY = LocalProgress(0, 0, 0, 0, 0, 0, LocalStreak(0, 0, false))
    }
}

enum class ActivityType { SESSION_FINISHED, MISSION_STEP_COMPLETED, MISSION_IMPORTED, DATA_IMPORTED }

/** Local Activity entry. Holds references only; text lives in the referenced record. */
data class ActivityRecord(
    val id: Long,
    val type: ActivityType,
    val refId: String,
    val occurredAt: Instant,
    val localDate: LocalDate,
)
