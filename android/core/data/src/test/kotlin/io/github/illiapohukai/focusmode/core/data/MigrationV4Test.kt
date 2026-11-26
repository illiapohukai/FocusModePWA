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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Room 3 -> 4 (v20): Worker step tools/apps and the draft's source analysis, columns only. */
@RunWith(AndroidJUnit4::class)
class MigrationV4Test {
    private val dbName = "migration-v4-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FocusDatabase::class.java)

    @Test fun migrate3To4KeepsEveryV19RowAndAddsEmptyLists() = runTest {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO missions (id,title,subject,type,deadline_text,deadline_date,preparation_start_date,status,created_at,updated_at,completed_at,archived_at,current_step_id,origin,start_mode,preferred_weekdays,preferred_session_minutes,estimate_minutes,summary,schedule_warning,archived_step_id) VALUES ('mission_a','Mathe','Mathematik','exam','2026-12-01','2026-12-01','2026-11-20','active',1,2,NULL,NULL,'step_b','LOCAL','one_week_before',9,30,55,'Erst Grundlagen.',NULL,NULL)")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at) VALUES ('step_a','mission_a',0,'Kapitel 1','Lesen',25,'completed','',NULL,1790001500000)")
            execSQL("INSERT INTO mission_steps (id,mission_id,position,title,task,duration_minutes,status,reason,scheduled_date,completed_at) VALUES ('step_b','mission_a',1,'Kapitel 2','Üben',30,'active','weil',NULL,NULL)")
            execSQL("INSERT INTO mission_progress (session_id,mission_id,step_id,outcome,rating,actual_minutes,planned_minutes,applied_at) VALUES ('session_m','mission_a','step_a','completed',4,25,25,1790001500000)")
            execSQL("INSERT INTO session_results (session_id,type,status,outcome,end_reason,title,task,planned_minutes,actual_minutes,elapsed_ms,counts_as_focus,started_at,finished_at,local_date,zone_id,mission_id,mission_step_id,protection_mode,rating,origin,verification,saved_at,end_note,review_note) VALUES ('session_m','mission','completed','completed','user_reviewed','Kapitel 1','Lesen',25,25,1500000,1,1790000000000,1790001500000,'2026-09-21','Europe/Berlin','mission_a','step_a','STANDARD',4,'LOCAL','LOCAL_UNVERIFIED',1790001500000,NULL,'gut')")
            execSQL("INSERT INTO mission_drafts (slot,draft_id,mission_id,goal_text,mission_type,confidence,deadline_date,preparation_start_date,start_mode,start_mode_chosen,start_clamped,preferred_weekdays,preferred_session_minutes,attachment_bytes,attachment_mime,attachment_width,attachment_height,phase,error_code,error_request_id,plan_title,plan_subject,plan_type,plan_deadline_text,plan_estimate_minutes,plan_difficulty,plan_explanation,plan_warning,created_at,updated_at) VALUES (0,'draft_1','mission_new','Bio lernen','exam','partial',NULL,NULL,'immediate',0,0,0,NULL,NULL,NULL,NULL,NULL,'BRIEFING',NULL,NULL,'Bio','Biologie','exam','Nicht angegeben',25,NULL,'',NULL,1,1)")
            execSQL("INSERT INTO mission_draft_steps (draft_slot,position,step_id,title,task,duration_minutes,reason,scheduled_date) VALUES (0,0,'s1','Zelle','Lesen',25,'',NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4)
        migrated.query("SELECT required_tools, recommended_apps, reason FROM mission_steps WHERE id = 'step_b'").use {
            it.moveToFirst(); assertEquals("", it.getString(0)); assertEquals("", it.getString(1)); assertEquals("weil", it.getString(2))
        }
        migrated.query("SELECT source_facts, source_confidence, plan_title FROM mission_drafts").use {
            it.moveToFirst(); assertEquals("", it.getString(0)); assertTrue(it.isNull(1)); assertEquals("Bio", it.getString(2))
        }
        migrated.query("SELECT COUNT(*) FROM mission_progress").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        migrated.query("SELECT review_note FROM session_results").use { it.moveToFirst(); assertEquals("gut", it.getString(0)) }
        migrated.close()

        // The migrated file opens with the current schema and maps to models with empty lists.
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java, dbName).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        val mission = db.missionDao().get("mission_a")!!.toModel()!!
        assertEquals(listOf("step_a", "step_b"), mission.steps.map { it.id.value })
        assertTrue(mission.steps.all { it.requiredTools.isEmpty() && it.recommendedApps.isEmpty() })
        val draft = db.missionDraftDao().get()!!.toModel()!!
        assertTrue(draft.plan!!.source.isEmpty)
        db.close()
    }

    @Test fun migrate1To4ChainsEveryMigration() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO selected_apps (package_name,label_snapshot,selected_at,icon_cache_ref) VALUES ('com.example.video','Video',1,NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL)
        migrated.query("SELECT COUNT(*) FROM selected_apps").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        migrated.close()
    }
}
