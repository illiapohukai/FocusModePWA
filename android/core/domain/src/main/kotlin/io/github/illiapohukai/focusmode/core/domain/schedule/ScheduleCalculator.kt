package io.github.illiapohukai.focusmode.core.domain.schedule

import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusDuration
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleZone
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ScheduleValidation {
    data object Valid : ScheduleValidation
    data class Invalid(val message: String) : ScheduleValidation
}

data class ScheduleOccurrence(val date: LocalDate, val start: ZonedDateTime, val end: ZonedDateTime) {
    val reminderAt: (Int) -> Instant = { lead -> start.minusMinutes(lead.toLong()).toInstant() }
}

/**
 * Pure scheduling math for Scheduled Focus windows. Occurrences are resolved in the schedule's
 * zone (device zone by default), so a time-zone change simply re-resolves the next occurrence.
 * DST gaps resolve forward (java.time ZonedDateTime rules); overnight windows end the next day.
 */
object ScheduleCalculator {
    private const val SEARCH_DAYS = 8L

    fun zoneOf(schedule: Schedule, deviceZone: ZoneId): ZoneId = when (val z = schedule.zone) {
        ScheduleZone.FollowDevice -> deviceZone
        is ScheduleZone.Fixed -> runCatching { ZoneId.of(z.zoneId) }.getOrDefault(deviceZone)
    }

    fun windowMinutes(schedule: Schedule): Int {
        val startMin = schedule.start.toSecondOfDay() / 60
        val endMin = schedule.end.toSecondOfDay() / 60
        return if (endMin > startMin) endMin - startMin else endMin + 24 * 60 - startMin
    }

    fun validate(schedule: Schedule): ScheduleValidation = when {
        schedule.label.isBlank() -> ScheduleValidation.Invalid("Bitte gib einen Namen ein.")
        schedule.weekdays.isEmpty() -> ScheduleValidation.Invalid("Wähle mindestens einen Wochentag.")
        schedule.start == schedule.end -> ScheduleValidation.Invalid("Start und Ende dürfen nicht gleich sein.")
        windowMinutes(schedule) < Schedule.MIN_WINDOW_MINUTES ->
            ScheduleValidation.Invalid("Das Zeitfenster muss mindestens ${Schedule.MIN_WINDOW_MINUTES} Minuten lang sein.")
        windowMinutes(schedule) > Schedule.MAX_WINDOW_MINUTES ->
            ScheduleValidation.Invalid("Das Zeitfenster darf höchstens ${Schedule.MAX_WINDOW_MINUTES / 60} Stunden lang sein.")
        schedule.leadTimeMinutes !in Schedule.LEAD_TIME_OPTIONS -> ScheduleValidation.Invalid("Ungültige Vorlaufzeit.")
        else -> ScheduleValidation.Valid
    }

    fun occurrenceOn(schedule: Schedule, date: LocalDate, zone: ZoneId): ScheduleOccurrence {
        val start = ZonedDateTime.of(date, schedule.start, zone)
        val end = start.plusMinutes(windowMinutes(schedule).toLong())
        return ScheduleOccurrence(date, start, end)
    }

    /**
     * Next occurrence whose reminder time (start minus lead time) is strictly after [now], so the
     * occurrence being delivered right now is never planned again and the following one is.
     */
    fun nextReminderOccurrence(schedule: Schedule, now: Instant, deviceZone: ZoneId): ScheduleOccurrence? {
        if (!schedule.enabled || schedule.weekdays.isEmpty()) return null
        val zone = zoneOf(schedule, deviceZone)
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0..SEARCH_DAYS) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in schedule.weekdays || date == schedule.skipDate) continue
            val occ = occurrenceOn(schedule, date, zone)
            if (occ.reminderAt(schedule.leadTimeMinutes).isAfter(now)) return occ
        }
        return null
    }

    /** The window that contains [now], if any (also checks yesterday for overnight windows). */
    fun activeOccurrence(schedule: Schedule, now: Instant, deviceZone: ZoneId): ScheduleOccurrence? {
        if (!schedule.enabled) return null
        val zone = zoneOf(schedule, deviceZone)
        val today = now.atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today)
            .filter { it.dayOfWeek in schedule.weekdays && it != schedule.skipDate }
            .map { occurrenceOn(schedule, it, zone) }
            .firstOrNull { !now.isBefore(it.start.toInstant()) && now.isBefore(it.end.toInstant()) }
    }

    /**
     * Session length offered when starting from a schedule: the remaining window, rounded down
     * to the Quick Focus step and clamped to the Quick Focus range (5–60 min).
     */
    fun suggestedSessionMinutes(schedule: Schedule, now: Instant, deviceZone: ZoneId): Int {
        val occ = activeOccurrence(schedule, now, deviceZone)
        val minutes = if (occ != null) Duration.between(now, occ.end.toInstant()).toMinutes().toInt() else windowMinutes(schedule)
        val stepped = (minutes / QuickFocusDuration.STEP) * QuickFocusDuration.STEP
        return stepped.coerceIn(QuickFocusDuration.MIN, QuickFocusDuration.MAX)
    }

    /** Temporary skip of the next occurrence. */
    fun skipNext(schedule: Schedule, now: Instant, deviceZone: ZoneId): Schedule {
        val next = nextReminderOccurrence(schedule.copy(skipDate = null), now, deviceZone) ?: return schedule
        return schedule.copy(skipDate = next.date)
    }
}
