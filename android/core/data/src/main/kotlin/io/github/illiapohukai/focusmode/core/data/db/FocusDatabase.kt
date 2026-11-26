package io.github.illiapohukai.focusmode.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = FocusDatabase.VERSION,
    exportSchema = true,
    entities = [
        MissionEntity::class, MissionStepEntity::class, ActiveSessionEntity::class, SessionResultEntity::class,
        ActivityEntity::class, ReminderEntity::class, SelectedAppEntity::class,
        // v2
        ScheduleEntity::class, ScheduleAppEntity::class, DistractionEntity::class, DndOverrideEntity::class,
        // v3
        MissionDraftEntity::class, MissionDraftStepEntity::class, MissionProgressEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun sessionDao(): SessionDao
    abstract fun activityDao(): ActivityDao
    abstract fun reminderDao(): ReminderDao
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun distractionDao(): DistractionDao
    abstract fun dndOverrideDao(): DndOverrideDao
    abstract fun missionDraftDao(): MissionDraftDao
    abstract fun missionProgressDao(): MissionProgressDao
    abstract fun personalDataDao(): PersonalDataDao

    companion object {
        const val VERSION = 4
        const val NAME = "focus-mode.db"
    }
}
