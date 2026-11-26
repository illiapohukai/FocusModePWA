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

    /**
     * Real v2 file (created from the exported v2 schema) with every kind of v18.1 data, migrated
     * to v3: nothing is lost, the new columns get their defaults, reviewed Mission Sessions are
     * backfilled into progress entries, and the production builder reads the result.
     */
    @Test fun migrate2To3PreservesEveryV18Row() = runTest {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO missions (id,title,subject,type,deadline_text,deadline_date,preparation_start_date,status,created_at,updated_at,completed_at,archived_at,current_step_id,origin) VALUES ('mission_a','Mathe','Mathematik','exam','2026-12-01','2026-12-01','2026-11-20','active',1,2,NULL,NULL,'step_b','LOCAL')")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at) VALUES ('step_a','mission_a',0,'Kapitel 1','Lesen',25,'completed','',NULL,1790001500000)")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at) VALUES ('step_b','mission_a',1,'Kapitel 2','Üben',30,'active','',NULL,NULL)")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at,end_note) VALUES ('session_m','mission','completed','completed','user_reviewed','Kapitel 1','Lesen',25,25,1500000,1,1790000000000,1790001500000,'2026-09-21','Europe/Berlin','mission_a','step_a','STANDARD',4,'LOCAL','LOCAL_UNVERIFIED',1790001500000,NULL)")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at,end_note) VALUES ('session_q','quick','completed','partial','user_finished','Q','Q',25,12,720000,1,1790000000000,1790000720000,'2026-09-21','Europe/Berlin',NULL,NULL,'PROTECTED',NULL,'LOCAL','LOCAL_UNVERIFIED',1790000720000,'NEED_BREAK')")
            execSQL("INSERT INTO active_session (slot,session_id,type,title,task,planned_minutes,mission_id,mission_step_id,started_at,lifecycle,accumulated_ms,segment_start_wall,segment_start_realtime_ms,segment_boot_count,checkpoint_elapsed_ms,checkpoint_wall,protection_mode,mode_label,progress_current,progress_total,schedule_id) VALUES (0,'session_live','mission','Kapitel 2','Üben',30,'mission_a','step_b',1790002000000,'RUNNING',0,1790002000000,5000,3,0,1790002000000,'STANDARD','Mathematik',2,2,NULL)")
            execSQL("INSERT INTO reminders (`key`,type,source_id,trigger_at,title,body,precision,deep_link,delivered_at) VALUES ('mission_deadline:mission_a:2026-11-29','MISSION_DEADLINE','mission_a',1795000000000,'Deadline in 2 Tagen','x','DEFERRABLE','focusmode://mission/mission_a',NULL)")
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            execSQL("INSERT INTO focus_schedules (id,label,weekdays_mask,start_minute,end_minute,zone_id,protection_mode,lead_time_minutes,skip_date,enabled,created_at,updated_at) VALUES ('schedule_a','Abend',31,1080,1140,NULL,'STANDARD',10,NULL,1,1,1)")
            execSQL("INSERT INTO dnd_override (slot,session_id,prior_filter,applied_filter,applied_at) VALUES (0,'session_live',1,2,1790002000000)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3)
        fun count(sql: String) = migrated.query(sql).use { it.moveToFirst(); it.getInt(0) }
        migrated.query("SELECT preferred_weekdays, summary, start_mode, archived_step_id FROM missions WHERE id='mission_a'").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
            assertNull(c.getString(2))
            assertNull(c.getString(3))
        }
        assertEquals(2, count("SELECT COUNT(*) FROM mission_steps"))
        assertEquals(2, count("SELECT COUNT(*) FROM session_results"))
        assertEquals(1, count("SELECT COUNT(*) FROM active_session WHERE mission_id='mission_a' AND mission_step_id='step_b'"))
        assertEquals(1, count("SELECT COUNT(*) FROM reminders"))
        assertEquals(1, count("SELECT COUNT(*) FROM selected_apps"))
        assertEquals(1, count("SELECT COUNT(*) FROM focus_schedules"))
        assertEquals(1, count("SELECT COUNT(*) FROM dnd_override"))
        assertEquals(0, count("SELECT COUNT(*) FROM mission_drafts"))
        assertEquals(1, count("SELECT COUNT(*) FROM mission_progress WHERE session_id='session_m' AND step_id='step_a' AND rating=4"))
        migrated.close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL).build()
        val mission = io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository(db, FakeClock(), io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository())
            .getMission(io.github.illiapohukai.focusmode.core.model.MissionId("mission_a"))!!
        assertEquals(listOf("step_a", "step_b"), mission.steps.map { it.id.value })
        assertEquals(50, mission.progressPercent)
        assertEquals(java.time.LocalDate.parse("2026-11-20"), mission.preparationStartDate)
        val active = RoomSessionRepository(db, FakeClock()).load()
        assertEquals("mission_a", (active as io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionLoad.Loaded).session.missionLink!!.missionId.value)
        db.close()
    }

    @Test fun migrate1To3ChainsBothMigrations() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
        migrated.query("SELECT COUNT(*) FROM selected_apps").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        migrated.close()
    }
}
