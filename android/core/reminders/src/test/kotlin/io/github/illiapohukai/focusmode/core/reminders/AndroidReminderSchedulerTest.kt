package io.github.illiapohukai.focusmode.core.reminders

import io.github.illiapohukai.focusmode.core.domain.platform.ExactAlarmCapability
import io.github.illiapohukai.focusmode.core.domain.platform.ReminderSyncReason
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderPrecision
import io.github.illiapohukai.focusmode.core.model.ReminderType
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.ScheduleZone
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakeMissionRepository
import io.github.illiapohukai.focusmode.core.testing.FakePlatform
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.FakeReminderStore
import io.github.illiapohukai.focusmode.core.testing.FakeScheduleRepository
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AndroidReminderSchedulerTest {
    private val clock = FakeClock(wall = Instant.parse("2026-11-19T08:00:00Z"))
    private val store = FakeReminderStore()
    private val schedules = FakeScheduleRepository()
    private val missions = FakeMissionRepository(clock)
    private val prefs = FakePreferencesRepository()
    private val platform = FakePlatform()
    private var exact = false
    private val backend = object : ReminderBackend {
        val deferred = mutableMapOf<String, Long>()
        val alarms = mutableMapOf<String, Boolean>()
        var cancels = 0
        override fun enqueueDeferrable(reminder: Reminder, delayMs: Long) { deferred[reminder.key] = delayMs }
        override fun scheduleAlarm(reminder: Reminder, exact: Boolean) { alarms[reminder.key] = exact }
        override fun cancel(key: String) { cancels++; deferred.remove(key); alarms.remove(key) }
    }
    private val scheduler = AndroidReminderScheduler(store, schedules, missions, prefs, clock, object : ExactAlarmCapability {
        override fun canScheduleExactAlarms() = exact
    }, backend, platform)

    private val schedule = Schedule(ScheduleId("schedule_a"), "Lineare Funktionen", setOf(DayOfWeek.THURSDAY), LocalTime.of(16, 0), LocalTime.of(16, 25),
        ScheduleZone.FollowDevice, ProtectionMode.STANDARD, emptySet(), 10, null, true, Instant.EPOCH, Instant.EPOCH)
    private val key = "scheduled_focus:schedule_a:2026-11-19"

    @Test fun scheduleUsesInexactAlarmWhenExactIsUnavailableAndSyncIsIdempotent() = runTest {
        schedules.upsert(schedule)
        repeat(3) { scheduler.sync(ReminderSyncReason.BOOT) }
        assertEquals(mapOf(key to false), backend.alarms)
        assertEquals(1, store.getAll().size)
        assertEquals(ReminderPrecision.INEXACT_ALARM, store.get(key)!!.precision)
    }

    @Test fun exactAlarmOnlyWhenPlatformAllows() = runTest {
        exact = true
        schedules.upsert(schedule)
        scheduler.sync(ReminderSyncReason.APP_START)
        assertEquals(true, backend.alarms[key])
    }

    @Test fun updateReplacesAndDisableCancels() = runTest {
        schedules.upsert(schedule)
        scheduler.sync(ReminderSyncReason.DATA_CHANGED)
        schedules.upsert(schedule.copy(start = LocalTime.of(17, 0), end = LocalTime.of(17, 25)))
        scheduler.sync(ReminderSyncReason.DATA_CHANGED)
        assertEquals(Instant.parse("2026-11-19T15:50:00Z"), store.get(key)!!.triggerAt)
        schedules.upsert(schedule.copy(enabled = false))
        scheduler.sync(ReminderSyncReason.DATA_CHANGED)
        assertTrue(store.getAll().isEmpty())
        assertTrue(backend.alarms.isEmpty())
    }

    @Test fun timezoneChangeReschedulesInNewZone() = runTest {
        schedules.upsert(schedule)
        scheduler.sync(ReminderSyncReason.APP_START)
        clock.zoneId = ZoneId.of("Europe/London")
        scheduler.sync(ReminderSyncReason.TIMEZONE_CHANGED)
        assertEquals(Instant.parse("2026-11-19T15:50:00Z"), store.get(key)!!.triggerAt) // 15:50 London
    }

    @Test fun deliveryHappensOnceThenPlansNextOccurrence() = runTest {
        schedules.upsert(schedule)
        scheduler.sync(ReminderSyncReason.APP_START)
        clock.wall = Instant.parse("2026-11-19T14:50:00Z")
        scheduler.deliver(key)
        scheduler.deliver(key)
        assertEquals(1, platform.reminders.size)
        assertEquals("Bereit für deinen Fokus?", platform.reminders.single().title)
        assertTrue(store.getAll().any { it.key == "scheduled_focus:schedule_a:2026-11-26" })
    }

    @Test fun deadlineRemindersAreDeferrableWork() = runTest {
        missions.create(Fixtures.mission(deadline = LocalDate.parse("2026-11-28")))
        scheduler.sync(ReminderSyncReason.APP_START)
        val deadline = store.getAll().single { it.type == ReminderType.MISSION_DEADLINE }
        assertEquals(ReminderPrecision.DEFERRABLE, deadline.precision)
        assertTrue(backend.deferred.containsKey(deadline.key))
    }

    @Test fun undeliverableNotificationsDoNotCrashAndAreNotRetriedAsDuplicates() = runTest {
        platform.notificationsDeliverable = false
        schedules.upsert(schedule)
        scheduler.sync(ReminderSyncReason.APP_START)
        clock.wall = Instant.parse("2026-11-19T14:50:00Z")
        scheduler.deliver(key)
        scheduler.deliver(key)
        assertTrue(platform.reminders.isEmpty())
        assertTrue(store.get(key)!!.deliveredAt != null)
    }

    @Test fun invalidKeysAreIgnored() = runTest {
        scheduler.deliver("../../etc")
        assertTrue(platform.reminders.isEmpty())
    }
}
