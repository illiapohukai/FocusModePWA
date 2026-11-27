package app.focusmodeplus.core.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.data.backup.BackupImportRepository
import app.focusmodeplus.core.domain.backup.ImportPreviewResult
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupImportRepositoryTest {
    private lateinit var db: FocusDatabase
    private lateinit var repo: BackupImportRepository
    private val fixture = """
        {"format":"focusmode-backup","exportVersion":1,"data":{
          "missions":{"missions":[{"id":"mission_a","title":"Mathe","status":"active","createdAt":1790000000000,"steps":[{"id":"step_a1","title":"A","durationMinutes":25,"status":"active"}]}]},
          "sessionHistory":{"sessions":[{"id":"session_q1","type":"quick","status":"completed","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":25,"startedAt":1790000000000,"finishedAt":1790001500000}]}}}
    """.trimIndent().toByteArray()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java).allowMainThreadQueries().build()
        repo = BackupImportRepository(db, FakeClock())
    }

    @After fun tearDown() = db.close()

    private fun count(table: String) = db.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    @Test fun previewThenApplyIsIdempotent() = runTest {
        val preview = (repo.preview(fixture) as ImportPreviewResult.Ready).preview
        assertEquals(1, preview.newMissions)
        assertEquals(1, preview.newSessions)
        val applied = repo.apply(preview.plan)
        assertEquals(1, applied.missionsImported)
        assertEquals(1, applied.sessionsImported)
        val again = (repo.preview(fixture) as ImportPreviewResult.Ready).preview
        assertEquals(1, again.duplicateMissions)
        assertEquals(1, again.duplicateSessions)
        assertEquals(0, repo.apply(again.plan).missionsImported)
        assertEquals(1, count("session_results"))
    }

    @Test fun failingImportRollsBackCompletely() = runTest {
        val plan = (repo.preview(fixture) as ImportPreviewResult.Ready).preview.plan
        val broken = plan.copy(
            missions = plan.missions + plan.missions.first().let { m ->
                m.copy(id = MissionId("mission_b"), steps = m.steps.map { it.copy(id = app.focusmodeplus.core.model.MissionStepId("step_b1"), missionId = MissionId("mission_ghost")) })
            },
        )
        try {
            repo.apply(broken)
            fail("foreign key violation expected")
        } catch (_: SQLiteConstraintException) { }
        assertEquals(0, count("missions"))
        assertEquals(0, count("mission_steps"))
        assertEquals(0, count("session_results"))
        assertEquals(0, count("activity_log"))
    }

    @Test fun rejectedFilesNeverTouchTheDatabase() = runTest {
        assertTrue(repo.preview("{}".toByteArray()) is ImportPreviewResult.Rejected)
        assertEquals(0, count("missions"))
    }
}
