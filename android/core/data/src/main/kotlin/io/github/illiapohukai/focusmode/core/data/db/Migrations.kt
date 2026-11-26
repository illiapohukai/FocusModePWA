package io.github.illiapohukai.focusmode.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, tested migrations. Destructive migration is never enabled: a missing migration is a
 * build/test failure, not silent data loss. SQL mirrors the exported schemas in /schemas.
 */
object Migrations {
    /** v1 -> v2: Scheduled Focus, distraction feedback, DND restore record, end-barrier note. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `focus_schedules` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `weekdays_mask` INTEGER NOT NULL, `start_minute` INTEGER NOT NULL, `end_minute` INTEGER NOT NULL, `zone_id` TEXT, `protection_mode` TEXT NOT NULL, `lead_time_minutes` INTEGER NOT NULL, `skip_date` TEXT, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_schedules_enabled` ON `focus_schedules` (`enabled`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_apps` (`schedule_id` TEXT NOT NULL, `package_name` TEXT NOT NULL, PRIMARY KEY(`schedule_id`, `package_name`), FOREIGN KEY(`schedule_id`) REFERENCES `focus_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `session_distractions` (`session_id` TEXT NOT NULL, `package_name` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `first_at` INTEGER NOT NULL, `last_at` INTEGER NOT NULL, PRIMARY KEY(`session_id`, `package_name`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_distractions_last_at` ON `session_distractions` (`last_at`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `dnd_override` (`slot` INTEGER NOT NULL, `session_id` TEXT NOT NULL, `prior_filter` INTEGER NOT NULL, `applied_filter` INTEGER NOT NULL, `applied_at` INTEGER NOT NULL, PRIMARY KEY(`slot`))")
            db.execSQL("ALTER TABLE `session_results` ADD COLUMN `end_note` TEXT")
        }
    }

    /**
     * v2 -> v3 (v19 Missions): Zeitplan and AI-plan columns on Missions, the review note on
     * History, the single generation draft with its Briefing steps, and Mission progress entries.
     * Existing rows keep every value; already reviewed Mission Sessions are copied into
     * `mission_progress` so their Missions keep a complete progress history.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `start_mode` TEXT")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `preferred_weekdays` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `preferred_session_minutes` INTEGER")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `estimate_minutes` INTEGER")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `summary` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `schedule_warning` TEXT")
            db.execSQL("ALTER TABLE `missions` ADD COLUMN `archived_step_id` TEXT")
            db.execSQL("ALTER TABLE `session_results` ADD COLUMN `review_note` TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS `mission_drafts` (`slot` INTEGER NOT NULL, `draft_id` TEXT NOT NULL, `mission_id` TEXT NOT NULL, `goal_text` TEXT NOT NULL, `mission_type` TEXT, `confidence` TEXT, `deadline_date` TEXT, `preparation_start_date` TEXT, `start_mode` TEXT NOT NULL, `start_mode_chosen` INTEGER NOT NULL, `start_clamped` INTEGER NOT NULL, `preferred_weekdays` INTEGER NOT NULL, `preferred_session_minutes` INTEGER, `attachment_bytes` INTEGER, `attachment_mime` TEXT, `attachment_width` INTEGER, `attachment_height` INTEGER, `phase` TEXT NOT NULL, `error_code` TEXT, `error_request_id` TEXT, `plan_title` TEXT, `plan_subject` TEXT, `plan_type` TEXT, `plan_deadline_text` TEXT, `plan_estimate_minutes` INTEGER, `plan_difficulty` TEXT, `plan_explanation` TEXT, `plan_warning` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`slot`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `mission_draft_steps` (`draft_slot` INTEGER NOT NULL, `position` INTEGER NOT NULL, `step_id` TEXT NOT NULL, `title` TEXT NOT NULL, `task` TEXT NOT NULL, `duration_minutes` INTEGER NOT NULL, `reason` TEXT NOT NULL, `scheduled_date` TEXT, PRIMARY KEY(`draft_slot`, `position`), FOREIGN KEY(`draft_slot`) REFERENCES `mission_drafts`(`slot`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `mission_progress` (`session_id` TEXT NOT NULL, `mission_id` TEXT NOT NULL, `step_id` TEXT NOT NULL, `outcome` TEXT NOT NULL, `rating` INTEGER, `actual_minutes` INTEGER NOT NULL, `planned_minutes` INTEGER NOT NULL, `applied_at` INTEGER NOT NULL, PRIMARY KEY(`session_id`), FOREIGN KEY(`mission_id`) REFERENCES `missions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mission_progress_mission_id` ON `mission_progress` (`mission_id`)")
            db.execSQL(
                "INSERT OR IGNORE INTO `mission_progress` (`session_id`, `mission_id`, `step_id`, `outcome`, `rating`, `actual_minutes`, `planned_minutes`, `applied_at`) " +
                    "SELECT `session_id`, `mission_id`, `mission_step_id`, `outcome`, `rating`, `actual_minutes`, `planned_minutes`, `saved_at` FROM `session_results` " +
                    "WHERE `type` = 'mission' AND `end_reason` = 'user_reviewed' AND `mission_step_id` IS NOT NULL AND `mission_id` IN (SELECT `id` FROM `missions`)",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
