package io.github.illiapohukai.focusmode.core.domain.reminder

import io.github.illiapohukai.focusmode.core.domain.deeplink.DeepLinkTarget
import io.github.illiapohukai.focusmode.core.domain.deeplink.DeepLinks
import io.github.illiapohukai.focusmode.core.domain.notification.NotificationCopy
import io.github.illiapohukai.focusmode.core.domain.schedule.ScheduleCalculator
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderPrecision
import io.github.illiapohukai.focusmode.core.model.ReminderType
import io.github.illiapohukai.focusmode.core.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Stable reminder identity: one key per event occurrence. */
object ReminderKeys {
    private val PATTERN = Regex("^[a-z_]{4,32}:[A-Za-z0-9_.:-]{2,160}:\\d{4}-\\d{2}-\\d{2}$")
    fun of(type: ReminderType, sourceId: String, date: LocalDate): String = "${type.name.lowercase()}:$sourceId:$date"
    fun isValid(key: String?): Boolean = key != null && PATTERN.matches(key)
    /** WorkManager / alarm unique names derive from the key, so scheduling twice replaces. */
    fun workName(key: String): String = "reminder:$key"
}

data class PlanningInputs(
    val now: Instant,
    val deviceZone: ZoneId,
    val schedules: List<Schedule>,
    val missions: List<Mission>,
    val preferences: AppPreferences,
    val exactAlarmsAvailable: Boolean,
)

/**
 * Computes the desired set of upcoming reminders (one per event occurrence). Pure: the
 * scheduler diffs this set with what is stored and (re)schedules only the differences.
 * At most one reminder per event; no reminder for a Mission without open steps.
 */
object ReminderPlanner {
    val PREPARATION_TIME: LocalTime = LocalTime.of(8, 0)
    val DEADLINE_TIME: LocalTime = LocalTime.of(17, 0)
    const val DEADLINE_DAYS_BEFORE = 2L

    fun plan(inputs: PlanningInputs): List<Reminder> = buildList {
        inputs.schedules.forEach { schedule -> scheduledFocus(schedule, inputs)?.let(::add) }
        if (inputs.preferences.missionRemindersEnabled) {
            inputs.missions.filter { it.status == MissionStatus.ACTIVE && it.openStepCount > 0 }.forEach { mission ->
                preparationStart(mission, inputs)?.let(::add)
                deadline(mission, inputs)?.let(::add)
            }
        }
        if (inputs.preferences.dailyPlanningEnabled) add(dailyPlanning(inputs))
    }.distinctBy { it.key }

    private fun scheduledFocus(schedule: Schedule, inputs: PlanningInputs): Reminder? {
        val occ = ScheduleCalculator.nextReminderOccurrence(schedule, inputs.now, inputs.deviceZone) ?: return null
        val minutes = ScheduleCalculator.suggestedSessionMinutes(schedule, occ.start.toInstant(), inputs.deviceZone)
        val text = NotificationCopy.scheduledFocus(minutes, schedule.label)
        return Reminder(
            key = ReminderKeys.of(ReminderType.SCHEDULED_FOCUS, schedule.id.value, occ.date),
            type = ReminderType.SCHEDULED_FOCUS,
            sourceId = schedule.id.value,
            triggerAt = occ.reminderAt(schedule.leadTimeMinutes),
            title = text.title,
            body = text.body,
            precision = if (inputs.exactAlarmsAvailable) ReminderPrecision.EXACT_ALARM else ReminderPrecision.INEXACT_ALARM,
            deepLink = DeepLinks.build(DeepLinkTarget.QuickFocus(schedule.id.value)),
        )
    }

    private fun preparationStart(mission: Mission, inputs: PlanningInputs): Reminder? {
        val date = mission.preparationStartDate ?: return null
        val at = ZonedDateTime.of(date, PREPARATION_TIME, inputs.deviceZone).toInstant()
        if (at.isBefore(inputs.now)) return null
        val text = NotificationCopy.preparationStart(mission.title)
        return Reminder(
            key = ReminderKeys.of(ReminderType.PREPARATION_START, mission.id.value, date),
            type = ReminderType.PREPARATION_START,
            sourceId = mission.id.value,
            triggerAt = at,
            title = text.title,
            body = text.body,
            precision = ReminderPrecision.DEFERRABLE,
            deepLink = DeepLinks.build(DeepLinkTarget.Mission(mission.id.value)),
        )
    }

    private fun deadline(mission: Mission, inputs: PlanningInputs): Reminder? {
        val deadline = mission.deadlineDate ?: return null
        val date = deadline.minusDays(DEADLINE_DAYS_BEFORE)
        val at = ZonedDateTime.of(date, DEADLINE_TIME, inputs.deviceZone).toInstant()
        if (at.isBefore(inputs.now)) return null
        val text = NotificationCopy.deadline(DEADLINE_DAYS_BEFORE.toInt(), mission.title, mission.openStepCount)
        return Reminder(
            key = ReminderKeys.of(ReminderType.MISSION_DEADLINE, mission.id.value, date),
            type = ReminderType.MISSION_DEADLINE,
            sourceId = mission.id.value,
            triggerAt = at,
            title = text.title,
            body = text.body,
            precision = ReminderPrecision.DEFERRABLE,
            deepLink = DeepLinks.build(DeepLinkTarget.Mission(mission.id.value)),
        )
    }

    private fun dailyPlanning(inputs: PlanningInputs): Reminder {
        val time = inputs.preferences.dailyPlanningTime
        val today = inputs.now.atZone(inputs.deviceZone).toLocalDate()
        val todayAt = ZonedDateTime.of(today, time, inputs.deviceZone).toInstant()
        val date = if (todayAt.isBefore(inputs.now)) today.plusDays(1) else today
        val text = NotificationCopy.dailyPlanning()
        return Reminder(
            key = ReminderKeys.of(ReminderType.DAILY_PLANNING, "planning", date),
            type = ReminderType.DAILY_PLANNING,
            sourceId = "planning",
            triggerAt = ZonedDateTime.of(date, time, inputs.deviceZone).toInstant(),
            title = text.title,
            body = text.body,
            precision = ReminderPrecision.DEFERRABLE,
            deepLink = DeepLinks.build(DeepLinkTarget.Missions),
        )
    }
}

/** Difference between stored and desired reminders. */
data class ReminderDiff(val toSchedule: List<Reminder>, val toCancel: List<Reminder>)

object ReminderDiffer {
    /**
     * A desired reminder is (re)scheduled when it is new or its time/precision changed.
     * Delivered occurrences are never scheduled again (duplicate prevention). Stored reminders
     * that are no longer desired and not delivered are cancelled.
     */
    fun diff(stored: List<Reminder>, desired: List<Reminder>): ReminderDiff {
        val storedByKey = stored.associateBy { it.key }
        val desiredKeys = desired.map { it.key }.toSet()
        val toSchedule = desired.filter { want ->
            val have = storedByKey[want.key]
            have == null || (have.deliveredAt == null && (have.triggerAt != want.triggerAt || have.precision != want.precision || have.body != want.body))
        }
        val toCancel = stored.filter { it.key !in desiredKeys && it.deliveredAt == null }
        return ReminderDiff(toSchedule, toCancel)
    }
}
