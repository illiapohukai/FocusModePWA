package app.focusmodeplus.core.domain.mission

import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionTheme
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MissionScheduleTest {
    private val today = LocalDate.parse("2026-11-25") // a Wednesday

    @Test fun recommendedStartModeFollowsThePwaThresholds() {
        assertEquals(ScheduleStartMode.IMMEDIATE, MissionSchedulePlanner.recommendedStartMode(today.plusDays(7), today))
        assertEquals(ScheduleStartMode.ONE_WEEK_BEFORE, MissionSchedulePlanner.recommendedStartMode(today.plusDays(21), today))
        assertEquals(ScheduleStartMode.TWO_WEEKS_BEFORE, MissionSchedulePlanner.recommendedStartMode(today.plusDays(60), today))
        assertEquals(ScheduleStartMode.CUSTOM, MissionSchedulePlanner.recommendedStartMode(today.plusDays(61), today))
    }

    @Test fun selectingADeadlineDerivesTheStartAndRefusesThePast() {
        assertNull(MissionSchedulePlanner.selectDeadline(ScheduleDraft(), today.minusDays(1), today))
        val s = MissionSchedulePlanner.selectDeadline(ScheduleDraft(), today.plusDays(20), today)!!
        assertEquals(ScheduleStartMode.ONE_WEEK_BEFORE, s.startMode)
        assertEquals(today.plusDays(13), s.preparationStart)
        // Far deadline: the recommendation "custom" becomes two weeks before (PWA).
        val far = MissionSchedulePlanner.selectDeadline(ScheduleDraft(), today.plusDays(90), today)!!
        assertEquals(ScheduleStartMode.TWO_WEEKS_BEFORE, far.startMode)
        assertEquals(today.plusDays(76), far.preparationStart)
    }

    @Test fun aStartBeforeTodayIsClampedAndReported() {
        val s = MissionSchedulePlanner.selectStartMode(ScheduleDraft(deadline = today.plusDays(3)), ScheduleStartMode.TWO_WEEKS_BEFORE, today)
        assertEquals(today, s.preparationStart)
        assertTrue(s.startClamped)
        assertEquals("Der Zeitplan beginnt heute.", MissionSchedulePlanner.validate(s, today).message)
    }

    @Test fun customStartIsKeptAndMustNotPassTheDeadline() {
        val base = MissionSchedulePlanner.selectDeadline(ScheduleDraft(), today.plusDays(30), today)!!
        val custom = MissionSchedulePlanner.selectPreparationStart(base, today.plusDays(5), today)!!
        assertEquals(ScheduleStartMode.CUSTOM, custom.startMode)
        assertEquals(today.plusDays(5), custom.preparationStart)
        assertNull(MissionSchedulePlanner.selectPreparationStart(base, today.plusDays(31), today))
        assertNull(MissionSchedulePlanner.selectPreparationStart(ScheduleDraft(), today.plusDays(1), today)) // no deadline yet
        // The user's explicit start choice survives a new deadline (PWA userManuallyChoseStartMode).
        val moved = MissionSchedulePlanner.selectDeadline(custom, today.plusDays(40), today)!!
        assertEquals(ScheduleStartMode.CUSTOM, moved.startMode)
        assertEquals(today.plusDays(5), moved.preparationStart)
    }

    @Test fun validationMessagesMatchThePwa() {
        assertTrue(MissionSchedulePlanner.validate(ScheduleDraft(), today).valid) // the Zeitplan is optional
        val past = ScheduleDraft(deadline = today.minusDays(1))
        assertEquals("Das Prüfungs- oder Abgabedatum darf nicht in der Vergangenheit liegen.", MissionSchedulePlanner.validate(past, today).message)
        val inverted = ScheduleDraft(deadline = today.plusDays(3), preparationStart = today.plusDays(5))
        assertFalse(MissionSchedulePlanner.validate(inverted, today).valid)
        val noDay = ScheduleDraft(deadline = today.plusDays(1), preparationStart = today, preferredWeekdays = setOf(DayOfWeek.SATURDAY))
        assertEquals("Im gewählten Zeitraum liegt kein ausgewählter Lerntag.", MissionSchedulePlanner.validate(noDay, today).message)
        val short = ScheduleDraft(deadline = today.plusDays(2), preparationStart = today)
        assertEquals(ScheduleNoticeLevel.WARN, MissionSchedulePlanner.validate(short, today).level)
        val todayDeadline = ScheduleDraft(deadline = today, preparationStart = today)
        assertEquals(ScheduleNoticeLevel.INFO, MissionSchedulePlanner.validate(todayDeadline, today).level)
    }

    @Test fun payloadUsesLocalDatesAndTheDeviceZone() {
        val s = ScheduleDraft(today.plusDays(10), today.plusDays(3), ScheduleStartMode.ONE_WEEK_BEFORE, false, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), 45)
        val p = MissionSchedulePlanner.payload(s, today, ZoneId.of("Europe/Berlin"))!!
        assertEquals(LocalDate.parse("2026-12-05"), p.deadlineDate)
        assertEquals(LocalDate.parse("2026-11-28"), p.preparationStartDate)
        assertEquals(7, p.preparationDays)
        assertEquals("one_week_before", p.startMode)
        assertEquals(listOf(1, 5), p.preferredWeekdays)
        assertEquals(45, p.preferredSessionMinutes)
        assertNull(MissionSchedulePlanner.payload(ScheduleDraft(), today, ZoneId.of("Europe/Berlin")))
    }

    @Test fun weekdayLabels() {
        assertEquals("Flexibel", MissionSchedulePlanner.weekdayLabel(emptySet()))
        assertEquals("Unter der Woche", MissionSchedulePlanner.weekdayLabel(MissionSchedulePlanner.WEEKDAYS))
        assertEquals("Wochenende", MissionSchedulePlanner.weekdayLabel(MissionSchedulePlanner.WEEKEND))
        assertEquals("Mo, Mi", MissionSchedulePlanner.weekdayLabel(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
    }
}

class MissionCalendarTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-11-25")

    @Test fun emptyWhenThereAreNoMissions() {
        assertTrue(MissionCalendar.events(emptyList(), emptyList(), emptyMap(), zone).isEmpty())
    }

    @Test fun deadlinesStartsCompletionAndFocusDaysComeFromRealData() {
        val a = Fixtures.mission("mission_a", deadline = LocalDate.parse("2026-12-03")).copy(preparationStartDate = LocalDate.parse("2026-11-26"))
        val b = Fixtures.mission("mission_b", deadline = null).copy(deadlineText = "Abgabe am 15.01.2027")
        val c = Fixtures.mission("mission_c", status = MissionStatus.COMPLETED).copy(completedAt = Instant.parse("2026-11-20T10:00:00Z"))
        val archived = Fixtures.mission("mission_d", status = MissionStatus.ARCHIVED)
        val focus = Fixtures.result("session_m", localDate = LocalDate.parse("2026-11-24")).copy(type = SessionType.MISSION, missionLink = MissionLink(a.id, a.steps[0].id))
        val events = MissionCalendar.events(listOf(a, b, c, archived), listOf(focus), emptyMap(), zone)
        val byDate = MissionCalendar.byDate(events)
        assertEquals(CalendarEventKind.DEADLINE, byDate[LocalDate.parse("2026-12-03")]!!.single().kind)
        assertTrue(byDate[LocalDate.parse("2026-11-26")]!!.single().preparation)
        assertEquals(CalendarEventKind.DEADLINE, byDate[LocalDate.parse("2027-01-15")]!!.single().kind) // parsed from text
        assertEquals(CalendarEventKind.DONE, byDate[LocalDate.parse("2026-11-20")]!!.single().kind)
        val focusDay = byDate[LocalDate.parse("2026-11-24")]!!.single()
        assertEquals(CalendarEventKind.FOCUS, focusDay.kind)
        assertEquals(25, focusDay.minutes)
        assertTrue(events.none { it.missionId == archived.id }) // archived Missions leave the calendar
        // Preferred weekdays never create events.
        assertEquals(setOf(CalendarEventKind.DEADLINE, CalendarEventKind.START, CalendarEventKind.DONE, CalendarEventKind.FOCUS), events.map { it.kind }.toSet())
    }

    @Test fun twoMissionsOnOneDateKeepTheirOwnTonesAndOrder() {
        val day = LocalDate.parse("2026-12-10")
        val a = Fixtures.mission("mission_a", title = "Zeta", deadline = day)
        val b = Fixtures.mission("mission_b", title = "Alpha", deadline = day)
        val themes = mapOf(a.id to MissionTheme.FOREST, b.id to MissionTheme.EMBER)
        val list = MissionCalendar.byDate(MissionCalendar.events(listOf(a, b), emptyList(), themes, zone))[day]!!
        assertEquals(listOf("Alpha", "Zeta"), list.map { it.title })
        assertEquals(listOf(MissionTheme.EMBER, MissionTheme.FOREST), list.map { it.theme })
    }

    @Test fun creationDayUsesTheLocalZoneNotUtc() {
        // 23:30 UTC on 24 Nov is already 25 Nov in Berlin.
        val m = Fixtures.mission(createdAt = Instant.parse("2026-11-24T23:30:00Z"), deadline = null)
        val start = MissionCalendar.events(listOf(m), emptyList(), emptyMap(), zone).single { it.kind == CalendarEventKind.START }
        assertEquals(LocalDate.parse("2026-11-25"), start.date)
    }

    @Test fun monthGridIsMondayFirstAcrossMonthAndYearBoundaries() {
        val nov = MissionCalendar.monthCells(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(LocalDate.parse("2026-10-26"), nov.first().date)
        assertFalse(nov.first().inMonth)
        assertEquals(DayOfWeek.MONDAY, nov.first().date.dayOfWeek)
        assertEquals(0, nov.size % 7)
        assertTrue(nov.single { it.date == LocalDate.parse("2026-11-30") }.inMonth)
        val jan = MissionCalendar.monthCells(YearMonth.of(2027, 1)) // 1 Jan 2027 is a Friday
        assertEquals(LocalDate.parse("2026-12-28"), jan.first().date)
        assertEquals(LocalDate.parse("2027-01-31"), jan.last { it.inMonth }.date)
        val feb = MissionCalendar.monthCells(YearMonth.of(2028, 2)) // leap year
        assertEquals(29, feb.count { it.inMonth })
    }

    @Test fun plannerDaysNeverAllowThePastAndLabelExistingDates() {
        val m = Fixtures.mission("mission_a", title = "Mathe", deadline = LocalDate.parse("2026-11-27"))
        val byDate = MissionCalendar.plannerEvents(MissionCalendar.events(listOf(m), emptyList(), emptyMap(), zone))
        val days = MissionCalendar.monthDays(YearMonth.of(2026, 11), byDate, today, { MissionCalendar.selectable(it, today, false, null) })
        assertFalse(days.single { it.date == today.minusDays(1) }.enabled)
        assertTrue(days.single { it.date == today }.label.contains("Heute"))
        val withDeadline = days.single { it.date == LocalDate.parse("2026-11-27") }
        assertTrue(withDeadline.label.contains("Prüfung: Mathe"))
        // A preparation start can never be after the new deadline.
        assertFalse(MissionCalendar.selectable(today.plusDays(10), today, true, today.plusDays(5)))
        assertTrue(MissionCalendar.selectable(today.plusDays(5), today, true, today.plusDays(5)))
    }

    @Test fun nextDeadlineAndRelativeCopy() {
        val soon = Fixtures.mission("mission_a", deadline = today.plusDays(2))
        val later = Fixtures.mission("mission_b", deadline = today.plusDays(9))
        val events = MissionCalendar.events(listOf(later, soon), emptyList(), emptyMap(), zone)
        assertEquals(soon.id, MissionCalendar.nextDeadline(events, today)!!.missionId)
        assertEquals("heute", MissionCalendar.relative(today, today))
        assertEquals("morgen", MissionCalendar.relative(today.plusDays(1), today))
        assertEquals("in 9 Tagen", MissionCalendar.relative(today.plusDays(9), today))
        assertEquals("Abgabe", MissionCalendar.deadlineLabel(MissionType.ASSIGNMENT))
    }

    @Test fun deadlineTextParserAcceptsOnlyExplicitDates() {
        assertEquals(LocalDate.parse("2026-12-01"), MissionCalendar.parseDeadlineText("2026-12-01"))
        assertEquals(LocalDate.parse("2026-11-27"), MissionCalendar.parseDeadlineText("27. November 2026"))
        assertEquals(LocalDate.parse("2027-03-04"), MissionCalendar.parseDeadlineText("am 4.3.27"))
        assertNull(MissionCalendar.parseDeadlineText("nächste Woche"))
        assertNull(MissionCalendar.parseDeadlineText("31.02.2026"))
        assertNull(MissionCalendar.parseDeadlineText("Nicht angegeben"))
    }
}

class HomeMissionClassifierTest {
    @Test fun firstTimeOnlyWithoutAnyMissionEvidence() {
        assertEquals(HomeMissionMode.FIRST_TIME, HomeMissionClassifier.classify(emptyList(), onboardingDone = false, evidence = false))
    }

    @Test fun returningUserStaysReturningAfterCompletionArchiveOrDeletion() {
        val completed = Fixtures.mission(status = MissionStatus.COMPLETED)
        assertEquals(HomeMissionMode.RETURNING_EMPTY, HomeMissionClassifier.classify(listOf(completed), false, HomeMissionClassifier.hasMissionEvidence(listOf(completed), emptyList())))
        // Deleted last Mission, but the mark was stored: never the onboarding card again.
        assertEquals(HomeMissionMode.RETURNING_EMPTY, HomeMissionClassifier.classify(emptyList(), onboardingDone = true, evidence = false))
        // An existing user with only a Mission Session in History is returning too.
        val missionSession = Fixtures.result().copy(type = SessionType.MISSION, missionLink = MissionLink(MissionId("mission_gone"), MissionStepId("step_gone")))
        assertTrue(HomeMissionClassifier.hasMissionEvidence(emptyList(), listOf(missionSession)))
        assertFalse(HomeMissionClassifier.hasMissionEvidence(emptyList(), listOf(Fixtures.result())))
        assertTrue(HomeMissionClassifier.shouldMarkReturning(onboardingDone = false, evidence = true))
        assertFalse(HomeMissionClassifier.shouldMarkReturning(onboardingDone = true, evidence = true))
    }

    @Test fun oneAndTwoActiveMissions() {
        val a = Fixtures.mission("mission_a")
        val b = Fixtures.mission("mission_b")
        val archived = Fixtures.mission("mission_c", status = MissionStatus.ARCHIVED)
        assertEquals(HomeMissionMode.ONE, HomeMissionClassifier.classify(listOf(a, archived), false, true))
        assertEquals(HomeMissionMode.TWO, HomeMissionClassifier.classify(listOf(a, b, archived), false, true))
    }
}
