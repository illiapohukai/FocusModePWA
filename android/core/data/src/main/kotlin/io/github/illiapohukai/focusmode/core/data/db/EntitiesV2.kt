package io.github.illiapohukai.focusmode.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.time.LocalDate

/* Schema version 2 (v18 protection foundation): Scheduled Focus, distraction feedback, DND restore. */

@Entity(tableName = "focus_schedules", indices = [Index("enabled")])
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val label: String,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(name = "weekdays_mask") val weekdaysMask: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    /** Null: follow the device time zone. */
    @ColumnInfo(name = "zone_id") val zoneId: String?,
    @ColumnInfo(name = "protection_mode") val protectionMode: String,
    @ColumnInfo(name = "lead_time_minutes") val leadTimeMinutes: Int,
    @ColumnInfo(name = "skip_date") val skipDate: LocalDate?,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "schedule_apps",
    primaryKeys = ["schedule_id", "package_name"],
    foreignKeys = [ForeignKey(entity = ScheduleEntity::class, parentColumns = ["id"], childColumns = ["schedule_id"], onDelete = ForeignKey.CASCADE)],
)
data class ScheduleAppEntity(
    @ColumnInfo(name = "schedule_id") val scheduleId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
)

data class ScheduleWithApps(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "schedule_id") val apps: List<ScheduleAppEntity>,
)

/** Aggregated, per-Session distraction feedback. No timeline of app usage is kept. */
@Entity(tableName = "session_distractions", primaryKeys = ["session_id", "package_name"], indices = [Index("last_at")])
data class DistractionEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    val attempts: Int,
    @ColumnInfo(name = "first_at") val firstAt: Instant,
    @ColumnInfo(name = "last_at") val lastAt: Instant,
)

/** Durable record of a DND change made by Focus Mode+, so it can be undone after a crash. */
@Entity(tableName = "dnd_override")
data class DndOverrideEntity(
    @PrimaryKey val slot: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "prior_filter") val priorFilter: Int,
    @ColumnInfo(name = "applied_filter") val appliedFilter: Int,
    @ColumnInfo(name = "applied_at") val appliedAt: Instant,
)
