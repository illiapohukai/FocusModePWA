package app.focusmodeplus.core.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.toEntity
import app.focusmodeplus.core.data.repository.RoomDistractionLog
import app.focusmodeplus.core.data.repository.RoomMissionRepository
import app.focusmodeplus.core.data.repository.RoomReminderStore
import app.focusmodeplus.core.data.repository.RoomScheduleRepository
import app.focusmodeplus.core.data.repository.RoomSelectedAppRepository
import app.focusmodeplus.core.data.repository.RoomSessionRepository
import app.focusmodeplus.core.domain.repository.ActiveSessionLoad
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.domain.repository.PreferencesRepository
import app.focusmodeplus.core.domain.session.FinalizationDecision
import app.focusmodeplus.core.domain.session.FinalizationPolicy
import app.focusmodeplus.core.domain.session.FinalizeTrigger
import app.focusmodeplus.core.domain.session.QuickFocusRequest
import app.focusmodeplus.core.domain.session.SessionFactory
import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionReview
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.Reminder
import app.focusmodeplus.core.model.ReminderPrecision
import app.focusmodeplus.core.model.ReminderType
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.ScheduleZone
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class FocusDatabaseTest {
    private lateinit var db: FocusDatabase
    private val clock = FakeClock()
    private lateinit var sessions: RoomSessionRepository
    private lateinit var missions: RoomMissionRepository

    private val prefs = object : PreferencesRepository {
        val state = MutableStateFlow(AppPreferences())
        override val preferences: Flow<AppPreferences> = state
        override suspend fun current() = state.value
        override suspend fun setDefaultQuickFocusMinutes(minutes: Int) = Unit
        override suspend fun setMotion(preference: MotionPreference) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setNotificationDetail(level: NotificationDetailLevel) = Unit
        override suspend fun setMissionRemindersEnabled(enabled: Boolean) = Unit
        override suspend fun setDailyPlanning(enabled: Boolean, time: LocalTime) = Unit
        override suspend fun setDefaultProtectionMode(mode: ProtectionMode) = Unit
        override suspend fun setUsageMonitoringEnabled(enabled: Boolean) = Unit
        override suspend fun setDoNotDisturbDuringFocus(enabled: Boolean) = Unit
        override suspend fun setOnboardingCompleted(done: Boolean) = Unit
        override suspend fun markHomeOnboardingDone() = Unit
        override suspend fun setLastSelectedMission(id: String?) { state.value = state.value.copy(lastSelectedMissionId = id) }
        override suspend fun incrementNotificationPermissionRequests() = Unit
        override suspend fun setPwaImportEnabled(enabled: Boolean) = Unit
        override suspend fun setLastExportAt(at: java.time.Instant) = Unit
        override suspend fun resetUserPreferences() = Unit
    }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java).allowMainThreadQueries().build()
        sessions = RoomSessionRepository(db, clock)
        missions = RoomMissionRepository(db, clock, prefs)
    }

    @After fun tearDown() = db.close()

    private fun quickSession() = SessionFactory(clock).quick(QuickFocusRequest("Lineare Funktionen", 25, ProtectionMode.STANDARD))
    private fun finalResult(s: app.focusmodeplus.core.model.FocusSession, minutes: Long = 25, trigger: FinalizeTrigger = FinalizeTrigger.TIMER_ELAPSED) =
        (FinalizationPolicy.decide(s, minutes * 60_000, trigger, clock.now(), clock.zone()) as FinalizationDecision.Finalize).result

    @Test fun missionCrudRelationsAndCascade() = runTest {
        val m = Fixtures.mission(steps = 3)
        assertTrue(missions.create(m) is MissionWriteResult.Ok)
        val stored = missions.getMission(m.id)!!
        assertEquals(3, stored.steps.size)
        assertEquals(listOf(0, 1, 2), stored.steps.map { it.position })
        assertTrue(missions.create(m) is MissionWriteResult.Rejected) // duplicate id
        assertTrue(missions.delete(m.id) is MissionWriteResult.Ok)
        assertNull(missions.getMission(m.id))
        assertEquals(0, db.query("SELECT * FROM mission_steps", null).use { it.count })
    }

    @Test fun atMostTwoActiveMissions() = runTest {
        assertTrue(missions.create(Fixtures.mission("mission_a")) is MissionWriteResult.Ok)
        assertTrue(missions.create(Fixtures.mission("mission_b")) is MissionWriteResult.Ok)
        assertEquals(MissionWriteResult.Rejected("active_mission_limit_reached"), missions.create(Fixtures.mission("mission_c")))
        assertTrue(missions.create(Fixtures.mission("mission_d", status = MissionStatus.COMPLETED)) is MissionWriteResult.Ok)
    }

    @Test fun onlyOneActiveSessionCanExist() = runTest {
        val first = quickSession()
        sessions.save(first)
        try {
            sessions.save(quickSession())
            fail("second active session must be rejected")
        } catch (e: IllegalStateException) {
            assertEquals("another_session_active", e.message)
        }
        try {
            db.sessionDao().insertActive(quickSession().toEntity())
            fail("database must reject a second active row")
        } catch (_: SQLiteConstraintException) { }
        assertEquals(first.id, (sessions.load() as ActiveSessionLoad.Loaded).session.id)
    }

    @Test fun finalizeIsTransactionalAndIdempotent() = runTest {
        val s = quickSession()
        sessions.save(s)
        val result = finalResult(s)
        val first = sessions.finalize(result)
        val second = sessions.finalize(result.copy(actualMinutes = 3))
        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(25, second.result.actualMinutes) // first record kept, never overwritten
        assertEquals(ActiveSessionLoad.None, sessions.load())
        assertEquals(1, sessions.observeResults().first().size)
        assertEquals(1, sessions.observeActivity(10).first().size)
    }

    @Test fun historyIsOrderedNewestFirst() = runTest {
        listOf("2026-11-17T10:00:00Z", "2026-11-19T08:00:00Z", "2026-11-18T10:00:00Z").forEachIndexed { i, t ->
            sessions.finalize(Fixtures.result("session_$i", finishedAt = Instant.parse(t)))
        }
        assertEquals(listOf("session_1", "session_2", "session_0"), sessions.observeResults().first().map { it.sessionId.value })
    }

    @Test fun missionReviewCompletesStepAndActiveSessionBlocksDeletion() = runTest {
        val m = Fixtures.mission(steps = 2)
        missions.create(m)
        val step = m.steps.first()
        val s = SessionFactory(clock).mission(MissionLink(m.id, step.id), step.title, step.task, 25, m.subject, 1, 2, ProtectionMode.STANDARD)
        sessions.save(s)
        assertEquals(MissionWriteResult.Rejected("active_session_exists"), missions.delete(m.id))
        val result = finalResult(s)
        sessions.finalize(result)
        val reviewed = sessions.applyMissionReview(s.id, MissionReview(SessionOutcome.COMPLETED, 4))!!.result
        assertEquals(4, reviewed.rating)
        val after = missions.getMission(m.id)!!
        assertEquals(MissionStepStatus.COMPLETED, after.steps[0].status)
        assertEquals(after.steps[1].id, after.currentStepId)
        // Reviewing again never completes a second step.
        sessions.applyMissionReview(s.id, MissionReview(SessionOutcome.COMPLETED, 4))
        assertEquals(MissionStepStatus.ACTIVE, missions.getMission(m.id)!!.steps[1].status)
        assertTrue(missions.delete(m.id) is MissionWriteResult.Ok)
        assertEquals(m.id, sessions.getResult(s.id)!!.missionLink!!.missionId) // History survives deletion
    }

    @Test fun selectedAppsPersistOnlyPlausiblePackages() = runTest {
        val apps = RoomSelectedAppRepository(db, clock)
        apps.setSelected("com.example.video", "Video", true)
        apps.setSelected("not a package", "Bad", true)
        apps.setSelected("com.example.chat", "Chat", true)
        apps.setSelected("com.example.chat", "Chat", false)
        assertEquals(listOf("com.example.video"), apps.getSelected().map { it.packageName })
    }

    @Test fun reminderDeliveryIsMarkedOnlyOnce() = runTest {
        val store = RoomReminderStore(db)
        val r = Reminder("scheduled_focus:schedule_a:2026-11-19", ReminderType.SCHEDULED_FOCUS, "schedule_a", clock.now(), "T", "B", ReminderPrecision.INEXACT_ALARM, "focusmode://home")
        store.upsert(r)
        assertTrue(store.markDelivered(r.key, clock.now()))
        assertFalse(store.markDelivered(r.key, clock.now()))
        assertTrue(store.observeUpcoming(5).first().isEmpty())
    }

    @Test fun distractionsAggregateAndPurge() = runTest {
        val log = RoomDistractionLog(db)
        val id = SessionId("session_x")
        repeat(3) { log.record(id, "com.example.video", clock.now()) }
        log.record(id, "com.example.chat", clock.now())
        assertEquals(4, log.observeCount(id).first())
        assertEquals(2, log.purgeOlderThan(clock.now().plusSeconds(1)))
        assertEquals(0, log.observeCount(id).first())
    }

    @Test fun schedulesStoreAppsAndCascade() = runTest {
        val repo = RoomScheduleRepository(db)
        val s = Schedule(ScheduleId("schedule_a"), "Mathe", setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), LocalTime.of(22, 0), LocalTime.of(1, 30),
            ScheduleZone.Fixed("Europe/Berlin"), ProtectionMode.PROTECTED, setOf("com.example.video", "bad pkg"), 10, null, true, clock.now(), clock.now())
        repo.upsert(s)
        val stored = repo.getSchedule(s.id)!!
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), stored.weekdays)
        assertEquals(setOf("com.example.video"), stored.appPackages)
        assertEquals(LocalTime.of(1, 30), stored.end)
        repo.delete(s.id)
        assertEquals(0, db.query("SELECT * FROM schedule_apps", null).use { it.count })
    }
}
