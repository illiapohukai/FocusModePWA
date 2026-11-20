package io.github.illiapohukai.focusmode.core.domain.schedule

import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.ScheduleZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun schedule(
        days: Set<java.time.DayOfWeek> = setOf(MONDAY, THURSDAY),
        start: LocalTime = LocalTime.of(16, 0),
        end: LocalTime = LocalTime.of(17, 0),
        lead: Int = 10,
        zone: ScheduleZone = ScheduleZone.FollowDevice,
        skip: LocalDate? = null,
        enabled: Boolean = true,
    ) = Schedule(
        ScheduleId("schedule_a"), "Lineare Funktionen", days, start, end, zone, ProtectionMode.PROTECTED,
        setOf("com.example.game"), lead, skip, enabled, Instant.EPOCH, Instant.EPOCH,
    )

    // 2026-11-19 is a Thursday.
    private val thursdayMorning = Instant.parse("2026-11-19T08:00:00Z")

    @Test fun nextReminderIsStartMinusLeadTimeOnSelectedWeekday() {
        val occ = ScheduleCalculator.nextReminderOccurrence(schedule(), thursdayMorning, berlin)!!
        assertEquals(LocalDate.parse("2026-11-19"), occ.date)
        assertEquals(Instant.parse("2026-11-19T14:50:00Z"), occ.reminderAt(10)) // 15:50 Berlin
    }

    @Test fun passedReminderMovesToNextSelectedWeekday() {
        val afterReminder = Instant.parse("2026-11-19T14:55:00Z")
        assertEquals(LocalDate.parse("2026-11-23"), ScheduleCalculator.nextReminderOccurrence(schedule(), afterReminder, berlin)!!.date)
    }

    @Test fun skipDateSkipsOnlyThatOccurrence() {
        val skipped = ScheduleCalculator.skipNext(schedule(), thursdayMorning, berlin)
        assertEquals(LocalDate.parse("2026-11-19"), skipped.skipDate)
        assertEquals(LocalDate.parse("2026-11-23"), ScheduleCalculator.nextReminderOccurrence(skipped, thursdayMorning, berlin)!!.date)
    }

    @Test fun disabledScheduleHasNoOccurrence() {
        assertNull(ScheduleCalculator.nextReminderOccurrence(schedule(enabled = false), thursdayMorning, berlin))
    }

    @Test fun followDeviceZoneReResolvesAfterTimeZoneChange() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val occ = ScheduleCalculator.nextReminderOccurrence(schedule(), thursdayMorning, tokyo)!!
        assertEquals(tokyo, occ.start.zone) // still 16:00 local, now in Tokyo
        assertEquals(LocalTime.of(16, 0), occ.start.toLocalTime())
    }

    @Test fun fixedZoneStaysInItsZone() {
        val s = schedule(zone = ScheduleZone.Fixed("Europe/Berlin"))
        val occ = ScheduleCalculator.nextReminderOccurrence(s, thursdayMorning, ZoneId.of("America/New_York"))!!
        assertEquals(berlin, occ.start.zone)
    }

    @Test fun overnightWindowEndsNextDayAndIsActiveAfterMidnight() {
        val s = schedule(days = setOf(THURSDAY), start = LocalTime.of(22, 0), end = LocalTime.of(1, 0))
        assertEquals(180, ScheduleCalculator.windowMinutes(s))
        val afterMidnight = Instant.parse("2026-11-19T23:30:00Z") // 00:30 Friday Berlin
        assertTrue(ScheduleCalculator.activeOccurrence(s, afterMidnight, berlin) != null)
    }

    @Test fun dstGapResolvesForward() {
        // 2026-03-29 02:00 does not exist in Berlin; java.time moves it to 03:00.
        val s = schedule(days = setOf(SUNDAY), start = LocalTime.of(2, 30), end = LocalTime.of(3, 30), lead = 0)
        val occ = ScheduleCalculator.nextReminderOccurrence(s, Instant.parse("2026-03-28T12:00:00Z"), berlin)!!
        assertEquals(LocalTime.of(3, 30), occ.start.toLocalTime())
    }

    @Test fun suggestedSessionMinutesFollowRemainingWindowInQuickRange() {
        val s = schedule(start = LocalTime.of(16, 0), end = LocalTime.of(18, 0))
        assertEquals(60, ScheduleCalculator.suggestedSessionMinutes(s, thursdayMorning, berlin))
        val inWindow = Instant.parse("2026-11-19T16:38:00Z") // 17:38, 22 min left
        assertEquals(20, ScheduleCalculator.suggestedSessionMinutes(s, inWindow, berlin))
    }

    @Test fun validationRejectsEmptyWeekdaysAndZeroWindow() {
        assertTrue(ScheduleCalculator.validate(schedule(days = emptySet())) is ScheduleValidation.Invalid)
        assertTrue(ScheduleCalculator.validate(schedule(end = LocalTime.of(16, 0))) is ScheduleValidation.Invalid)
        assertTrue(ScheduleCalculator.validate(schedule(lead = 7)) is ScheduleValidation.Invalid)
        assertEquals(ScheduleValidation.Valid, ScheduleCalculator.validate(schedule()))
    }
}
