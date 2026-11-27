package app.focusmodeplus.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.data.backup.BackupImportRepository
import app.focusmodeplus.core.data.backup.RoomBackupSnapshotSource
import app.focusmodeplus.core.data.backup.RoomPersonalDataStore
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.MissionProgressEntity
import app.focusmodeplus.core.data.db.toEntity
import app.focusmodeplus.core.data.prefs.DataStorePreferencesRepository
import app.focusmodeplus.core.data.prefs.FeatureFlagDefaults
import app.focusmodeplus.core.data.prefs.PreferencesSchemaMigration
import app.focusmodeplus.core.data.repository.RoomMissionRepository
import app.focusmodeplus.core.data.repository.RoomReminderStore
import app.focusmodeplus.core.data.repository.RoomSelectedAppRepository
import app.focusmodeplus.core.domain.backup.AndroidBackupExporter
import app.focusmodeplus.core.domain.backup.BackupScope
import app.focusmodeplus.core.domain.backup.ImportPreviewResult
import app.focusmodeplus.core.domain.backup.IntegrityStatus
import app.focusmodeplus.core.domain.mission.MissionThemes
import app.focusmodeplus.core.domain.repository.MissionWriteResult
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.Reminder
import app.focusmodeplus.core.model.ReminderPrecision
import app.focusmodeplus.core.model.ReminderType
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** v20 data owners on a real Room database: rename, export snapshot, restore, full deletion. */
@RunWith(AndroidJUnit4::class)
class LocalDataV20Test {
    @get:Rule val tmp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock(wall = Instant.parse("2026-11-26T09:00:00Z"))
    private lateinit var db: FocusDatabase
    private lateinit var missions: RoomMissionRepository

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java).allowMainThreadQueries().build()

    @Before fun setUp() {
        db = newDb()
        missions = RoomMissionRepository(db, clock, FakePreferencesRepository())
    }

    @After fun tearDown() = db.close()

    private fun seed() = runBlocking {
        val base = Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3, completed = 1, deadline = LocalDate.parse("2026-12-10"))
        val mission = base.copy(steps = base.steps.map { if (it.position == 1) it.copy(requiredTools = listOf("Lehrbuch"), recommendedApps = listOf("Anki")) else it })
        assertTrue(missions.create(mission) is MissionWriteResult.Ok)
        val r = Fixtures.result("s_mission", LocalDate.parse("2026-11-25"), 25)
            .copy(type = SessionType.MISSION, missionLink = MissionLink(mission.id, MissionStepId("mission_bio_step_0")), rating = 4, reviewNote = "gut")
        db.sessionDao().insertResults(listOf(r.toEntity(savedAt = clock.now()), Fixtures.result("s_quick", LocalDate.parse("2026-11-26"), 15).toEntity(savedAt = clock.now())))
        db.missionProgressDao().insert(MissionProgressEntity("s_mission", "mission_bio", "mission_bio_step_0", "completed", 4, 25, 25, clock.now()))
        RoomSelectedAppRepository(db, clock).setSelected("com.example.video", "Video", true)
        RoomReminderStore(db).upsert(Reminder("mission_deadline:mission_bio:2026-12-08", ReminderType.MISSION_DEADLINE, "mission_bio", clock.now().plusSeconds(3600), "Deadline", "x", ReminderPrecision.DEFERRABLE, "focusmode://mission/mission_bio"))
    }

    @Test fun renameKeepsIdStepsProgressAndColour() = runBlocking {
        seed()
        val before = missions.getMission(app.focusmodeplus.core.model.MissionId("mission_bio"))!!
        val r = missions.rename(before.id, "Bio: Zellatmung") as MissionWriteResult.Ok
        val after = missions.getMission(before.id)!!
        assertEquals("Bio: Zellatmung", after.title)
        assertEquals(before.steps, after.steps)
        assertEquals(MissionThemes.resolve(listOf(before)), MissionThemes.resolve(listOf(after)))
        assertEquals(1, missions.progressHistory(before.id).size)
        assertEquals(r.mission, after)
        assertTrue(missions.rename(before.id, "  ") is MissionWriteResult.Rejected)
    }

    @Test fun exportedSnapshotRestoresIntoAFreshDeviceIncludingProgressAndTools() = runBlocking {
        seed()
        val content = RoomBackupSnapshotSource(db, FakePreferencesRepository()).snapshot()
        assertEquals(1, content.progress.size)
        val file = AndroidBackupExporter("20.0.0").export(content, BackupScope.FULL, clock.now(), clock.zone())

        val target = newDb()
        val importer = BackupImportRepository(target, clock)
        val preview = (importer.preview(file.json.toByteArray()) as ImportPreviewResult.Ready).preview
        assertEquals(IntegrityStatus.VERIFIED, preview.plan.report.integrity)
        assertEquals(1, preview.newMissions)
        assertEquals(2, preview.newSessions)
        val applied = importer.apply(preview.plan)
        assertEquals(1, applied.missionsImported)
        assertEquals(2, applied.sessionsImported)
        val restored = RoomMissionRepository(target, clock, FakePreferencesRepository())
        val m = restored.getMission(app.focusmodeplus.core.model.MissionId("mission_bio"))!!
        assertEquals(listOf("Lehrbuch"), m.steps[1].requiredTools)
        assertEquals(listOf("Anki"), m.steps[1].recommendedApps)
        assertEquals(1, restored.progressHistory(m.id).size)
        assertEquals("gut", target.sessionDao().getResult("s_mission")!!.reviewNote)
        // A second import of the same file changes nothing (ids are never overwritten).
        val again = importer.apply((importer.preview(file.json.toByteArray()) as ImportPreviewResult.Ready).preview.plan)
        assertEquals(0, again.missionsImported + again.sessionsImported)
        target.close()
    }

    @Test fun wipeRemovesEveryUserRecordInOneTransaction() = runBlocking {
        seed()
        val store = RoomPersonalDataStore(db)
        assertTrue(store.remainingRows() > 0)
        store.wipeRecords()
        assertEquals("only the reminder row waits for the scheduler", 1, store.remainingRows())
        store.clearReminderRows()
        assertEquals(0, store.remainingRows())
        assertTrue(db.missionDao().getAll().isEmpty())
        assertTrue(db.sessionDao().allResults().isEmpty())
        assertNull(db.missionDraftDao().get())
        assertTrue(RoomSelectedAppRepository(db, clock).getSelected().isEmpty())
    }

    @Test fun resetUserPreferencesKeepsOnlyTechnicalMetadata() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(migrations = listOf(PreferencesSchemaMigration), scope = scope, produceFile = { File(tmp.root, "reset.preferences_pb") })
        val repo = DataStorePreferencesRepository(store, FeatureFlagDefaults(pwaImportEnabled = false))
        store.edit { it[stringPreferencesKey("install_id")] = "device-1" }
        repo.setMotion(MotionPreference.REDUCED)
        repo.setDefaultQuickFocusMinutes(45)
        repo.markHomeOnboardingDone()
        repo.setLastSelectedMission("mission_bio")
        repo.incrementNotificationPermissionRequests()
        repo.setLastExportAt(clock.now())
        assertEquals(clock.now(), repo.current().lastExportAt)

        repo.resetUserPreferences()
        val p = repo.current()
        assertEquals(MotionPreference.SYSTEM, p.motion)
        assertEquals(25, p.defaultQuickFocusMinutes)
        assertFalse(p.homeOnboardingDone)
        assertNull(p.lastSelectedMissionId)
        assertNull(p.lastExportAt)
        assertEquals(1, p.notificationPermissionRequests)
        assertEquals("device-1", store.data.first()[stringPreferencesKey("install_id")])
    }
}
