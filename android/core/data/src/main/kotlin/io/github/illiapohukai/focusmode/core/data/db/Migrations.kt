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

    val ALL = arrayOf(MIGRATION_1_2)
}
