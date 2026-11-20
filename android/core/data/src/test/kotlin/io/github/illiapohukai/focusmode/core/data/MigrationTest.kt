package io.github.illiapohukai.focusmode.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.Migrations
import io.github.illiapohukai.focusmode.core.data.repository.RoomSessionRepository
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusDatabase::class.java)

    @Test fun migrate1To2PreservesDataAndAddsTables() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO missions (id,title,subject,type,deadline_text,deadline_date,preparation_start_date,status,created_at,updated_at,completed_at,archived_at,current_step_id,origin) VALUES ('mission_a','Mathe','Mathematik','exam','2026-12-01','2026-12-01',NULL,'active',1,1,NULL,NULL,'step_a','LOCAL')")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at) VALUES ('step_a','mission_a',0,'Kapitel 1','Lesen',25,'active','',NULL,NULL)")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at) VALUES ('session_a','quick','completed','completed','timer_finished','T','T',25,25,1500000,1,1790000000000,1790001500000,'2026-09-21','Europe/Berlin',NULL,NULL,'STANDARD',NULL,'LOCAL','LOCAL_UNVERIFIED',1790001500000)")
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2)
        migrated.query("SELECT end_note, actual_minutes FROM session_results WHERE session_id='session_a'").use { c ->
            c.moveToFirst()
            assertNull(c.getString(0))
            assertEquals(25, c.getInt(1))
        }
        migrated.query("SELECT COUNT(*) FROM mission_steps").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        migrated.query("SELECT COUNT(*) FROM focus_schedules").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        migrated.close()

        // The production builder (no destructive fallback) opens the migrated file and reads History.
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL).build()
        val results = RoomSessionRepository(db, FakeClock()).observeResults().first()
        assertEquals(listOf("session_a"), results.map { it.sessionId.value })
        db.close()
    }
}
