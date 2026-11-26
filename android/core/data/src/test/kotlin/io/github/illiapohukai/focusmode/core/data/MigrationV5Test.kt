package io.github.illiapohukai.focusmode.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.Migrations
import io.github.illiapohukai.focusmode.core.data.db.toModel
import io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository
import io.github.illiapohukai.focusmode.core.data.repository.RoomSessionRepository
import io.github.illiapohukai.focusmode.core.data.sync.RoomSyncLocalStore
import io.github.illiapohukai.focusmode.core.domain.repository.ActiveSessionLoad
import io.github.illiapohukai.focusmode.core.model.BindingStatus
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 4 -> 5 (v21 account & sync): new tables only. Every v20 row survives, nothing becomes
 * linked to an account, and a migration interrupted by process death resumes from the database.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV5Test {
    private val dbName = "migration-v5-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusDatabase::class.java)

    private fun open() = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java, dbName)
        .addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String) = db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun migrate4To5KeepsEveryV20RowIncludingImportedHistoryAndAnActiveSession() = runTest {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO missions (id,title,subject,type,deadline_text,deadline_date,preparation_start_date,status,created_at,updated_at,completed_at,archived_at,current_step_id,origin,start_mode,preferred_weekdays,preferred_session_minutes,estimate_minutes,summary,schedule_warning,archived_step_id) VALUES ('mission_a','Mathe','Mathematik','exam','2026-12-01','2026-12-01','2026-11-20','active',1790000000000,1790000000000,NULL,NULL,'step_b','LOCAL','one_week_before',9,30,55,'Erst Grundlagen.',NULL,NULL)")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at,required_tools,recommended_apps) VALUES ('step_a','mission_a',0,'Kapitel 1','Lesen',25,'completed','',NULL,1790001500000,'Lehrbuch','')")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at,required_tools,recommended_apps) VALUES ('step_b','mission_a',1,'Kapitel 2','Üben',30,'active','weil',NULL,NULL,'','Notizen')")
            execSQL("INSERT INTO mission_progress (session_id,mission_id,step_id,outcome,rating,actual_minutes,planned_minutes,applied_at) VALUES ('session_m','mission_a','step_a','completed',4,25,25,1790001500000)")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at,end_note,review_note) VALUES ('session_m','mission','completed','completed','user_reviewed','Kapitel 1','Lesen',25,25,1500000,1,1790000000000,1790001500000,'2026-09-21','Europe/Berlin','mission_a','step_a','STANDARD',4,'LOCAL','LOCAL_UNVERIFIED',1790001500000,NULL,'gut')")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at,end_note,review_note) VALUES ('session_pwa','quick','completed','completed','imported','Lesen','Lesen',25,25,1500000,1,1780000000000,1780001500000,'2026-05-29','Europe/Berlin',NULL,NULL,'STANDARD',NULL,'IMPORTED_PWA','LOCAL_UNVERIFIED',1790001500000,NULL,NULL)")
            execSQL("INSERT INTO active_session (slot,session_id,type,title,task,planned_minutes,mission_id,mission_step_id,started_at,lifecycle,accumulated_ms,segment_start_wall,segment_start_realtime_ms,segment_boot_count,checkpoint_elapsed_ms,checkpoint_wall,protection_mode,mode_label,progress_current,progress_total,schedule_id) VALUES (0,'session_live','mission','Kapitel 2','Üben',30,'mission_a','step_b',1790002000000,'RUNNING',0,1790002000000,1000,7,0,1790002000000,'STANDARD','Standard-Fokus',2,2,NULL)")
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, Migrations.MIGRATION_4_5)
        assertEquals(1, count(migrated, "missions"))
        assertEquals(2, count(migrated, "mission_steps"))
        assertEquals(1, count(migrated, "mission_progress"))
        assertEquals(2, count(migrated, "session_results"))
        assertEquals(1, count(migrated, "active_session"))
        assertEquals(1, count(migrated, "selected_apps"))
        // Nothing is linked to an account by the upgrade: existing data stays guest data.
        assertEquals(0, count(migrated, "account_binding"))
        assertEquals(0, count(migrated, "sync_outbox"))
        migrated.close()

        val db = open()
        val clock = FakeClock()
        val mission = db.missionDao().get("mission_a")!!.toModel()!!
        assertEquals(listOf("Lehrbuch"), mission.steps.first().requiredTools)
        val history = RoomSessionRepository(db, clock).observeResults().first()
        assertEquals(DataOrigin.IMPORTED_PWA, history.first { it.sessionId.value == "session_pwa" }.origin)
        assertTrue(RoomSessionRepository(db, clock).load() is ActiveSessionLoad.Loaded)
        assertNull(RoomSyncLocalStore(db, clock).binding())
        // A guest change after the upgrade records nothing.
        RoomMissionRepository(db, clock, FakePreferencesRepository()).rename(MissionId("mission_a"), "Mathe-Klausur")
        assertEquals(0, db.query("SELECT COUNT(*) FROM sync_outbox", null).use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }

    @Test fun migrate1To5ChainsEveryMigration() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, *Migrations.ALL)
        assertEquals(1, count(migrated, "selected_apps"))
        assertEquals(0, count(migrated, "account_binding"))
        migrated.close()
    }

    @Test fun migrateAnEmptyV4Database() = runTest {
        helper.createDatabase(dbName, 4).close()
        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, Migrations.MIGRATION_4_5)
        assertEquals(0, count(migrated, "missions"))
        assertEquals(0, count(migrated, "sync_conflicts"))
        migrated.close()
    }

    @Test fun anInterruptedAccountMigrationResumesFromTheDatabase() = runTest {
        helper.createDatabase(dbName, 4).close()
        helper.runMigrationsAndValidate(dbName, 5, true, Migrations.MIGRATION_4_5).close()
        val clock = FakeClock()
        var db = open()
        RoomMissionRepository(db, clock, FakePreferencesRepository()).create(Fixtures.mission("mission_x", steps = 2))
        RoomSessionRepository(db, clock).finalize(Fixtures.result("session_x"))
        assertTrue(RoomSyncLocalStore(db, clock).beginMigration("aliceUid0001", "migration0001", clock.now()))
        db.close() // process death after the confirmation, before any upload
        db = open()
        val store = RoomSyncLocalStore(db, clock)
        val binding = store.binding()!!
        assertEquals(BindingStatus.MIGRATING, binding.status)
        assertEquals("migration0001", binding.migrationId)
        assertEquals(2, store.pendingCount("aliceUid0001"))
        // Confirming again (duplicate tap after restart) does not enqueue a second time.
        assertTrue(store.beginMigration("aliceUid0001", "migration0001", clock.now()))
        assertEquals(2, store.pendingCount("aliceUid0001"))
        // Another account can never take over the dataset.
        assertTrue(!store.beginMigration("bobUid000002", "migration0002", clock.now()))
        db.close()
    }
}
