package app.focusmodeplus.core.domain.reminder

import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ReminderPrecision
import app.focusmodeplus.core.model.ReminderType
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.ScheduleZone
import app.focusmodeplus.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlannerTest {
    private val now = Instant.parse("2026-11-19T08:00:00Z")
    private val zone = ZoneId.of("Europe/Berlin")
    private val schedule = Schedule(
        ScheduleId("schedule_a"), "Lineare Funktionen", setOf(DayOfWeek.THURSDAY), LocalTime.of(16, 0), LocalTime.of(16, 25),
        ScheduleZone.FollowDevice, ProtectionMode.STANDARD, emptySet(), 10, null, true, Instant.EPOCH, Instant.EPOCH,
    )

    private fun plan(exact: Boolean = false, prefs: AppPreferences = AppPreferences(), missions: List<app.focusmodeplus.core.model.Mission> = emptyList()) =
        ReminderPlanner.plan(PlanningInputs(now, zone, listOf(schedule), missions, prefs, exact))

    @Test fun scheduledFocusUsesConciseGermanCopyAndStableKey() {
        val r = plan().single()
        assertEquals(ReminderType.SCHEDULED_FOCUS, r.type)
        assertEquals("scheduled_focus:schedule_a:2026-11-19", r.key)
        assertEquals("Bereit für deinen Fokus?", r.title)
        assertEquals("25 Minuten · Lineare Funktionen", r.body)
        assertEquals("focusmode://quickfocus?schedule=schedule_a", r.deepLink)
        assertTrue(ReminderKeys.isValid(r.key))
    }

    @Test fun exactAlarmUnavailableFallsBackToInexactAlarm() {
        assertEquals(ReminderPrecision.INEXACT_ALARM, plan(exact = false).single().precision)
        assertEquals(ReminderPrecision.EXACT_ALARM, plan(exact = true).single().precision)
    }

    @Test fun deadlineReminderOnlyForActiveMissionsWithOpenSteps() {
        val open = Fixtures.mission("mission_open", "Mathe-Klausur", steps = 4, completed = 1, deadline = LocalDate.parse("2026-11-28"))
        val done = Fixtures.mission("mission_done", "Bio", steps = 2, completed = 2, deadline = LocalDate.parse("2026-11-28"))
        val reminders = plan(missions = listOf(open, done))
        val deadline = reminders.single { it.type == ReminderType.MISSION_DEADLINE }
        assertEquals("Deadline in 2 Tagen", deadline.title)
        assertEquals("Für „Mathe-Klausur“ sind noch 3 Schritte offen.", deadline.body)
        assertEquals(ReminderPrecision.DEFERRABLE, deadline.precision)
        assertFalse(reminders.any { it.sourceId == "mission_done" })
    }

    @Test fun missionRemindersRespectPreferenceAndDailyPlanningIsOptIn() {
        val m = Fixtures.mission(deadline = LocalDate.parse("2026-11-28"))
        assertFalse(plan(prefs = AppPreferences(missionRemindersEnabled = false), missions = listOf(m)).any { it.type == ReminderType.MISSION_DEADLINE })
        assertFalse(plan().any { it.type == ReminderType.DAILY_PLANNING })
        assertTrue(plan(prefs = AppPreferences(dailyPlanningEnabled = true)).any { it.type == ReminderType.DAILY_PLANNING })
    }

    @Test fun diffReplacesChangedCancelsRemovedAndNeverRedeliversDelivered() {
        val desired = plan()
        val first = ReminderDiffer.diff(emptyList(), desired)
        assertEquals(desired, first.toSchedule)

        val same = ReminderDiffer.diff(desired, desired)
        assertTrue(same.toSchedule.isEmpty() && same.toCancel.isEmpty())

        val moved = desired.map { it.copy(triggerAt = it.triggerAt.plusSeconds(600)) }
        assertEquals(1, ReminderDiffer.diff(desired, moved).toSchedule.size)

        val delivered = desired.map { it.copy(deliveredAt = now) }
        assertTrue(ReminderDiffer.diff(delivered, moved).toSchedule.isEmpty())

        val removed = ReminderDiffer.diff(desired, emptyList())
        assertEquals(desired, removed.toCancel)
    }
}
