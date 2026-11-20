package io.github.illiapohukai.focusmode.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Time zone policy of a schedule: follow the device (default) or stay in a fixed zone. */
sealed interface ScheduleZone {
    data object FollowDevice : ScheduleZone
    data class Fixed(val zoneId: String) : ScheduleZone
}

/**
 * Scheduled Focus window ("Geplanter Fokus"). In v18 a schedule notifies, prepares a Session and
 * offers a one-tap start; protection only becomes active after the user explicitly starts.
 */
data class Schedule(
    val id: ScheduleId,
    val label: String,
    val weekdays: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val zone: ScheduleZone,
    val protectionMode: ProtectionMode,
    val appPackages: Set<String>,
    val leadTimeMinutes: Int,
    /** Temporary skip: occurrences on this local date are skipped. */
    val skipDate: LocalDate?,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        val LEAD_TIME_OPTIONS = listOf(0, 5, 10, 15, 30)
        const val MIN_WINDOW_MINUTES = 5
        const val MAX_WINDOW_MINUTES = 240
    }
}
