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

@Entity(tableName = "missions", indices = [Index("status")])
data class MissionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subject: String,
    val type: String,
    @ColumnInfo(name = "deadline_text") val deadlineText: String,
    @ColumnInfo(name = "deadline_date") val deadlineDate: LocalDate?,
    @ColumnInfo(name = "preparation_start_date") val preparationStartDate: LocalDate?,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "current_step_id") val currentStepId: String?,
    val origin: String,
    /* v3 (v19 Missions): Zeitplan, AI plan metadata, restore point. */
    @ColumnInfo(name = "start_mode") val startMode: String? = null,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(name = "preferred_weekdays", defaultValue = "0") val preferredWeekdays: Int = 0,
    @ColumnInfo(name = "preferred_session_minutes") val preferredSessionMinutes: Int? = null,
    @ColumnInfo(name = "estimate_minutes") val estimateMinutes: Int? = null,
    @ColumnInfo(defaultValue = "''") val summary: String = "",
    @ColumnInfo(name = "schedule_warning") val scheduleWarning: String? = null,
    @ColumnInfo(name = "archived_step_id") val archivedStepId: String? = null,
)

@Entity(
    tableName = "mission_steps",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["mission_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["mission_id", "position"], unique = true)],
)
data class MissionStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "mission_id") val missionId: String,
    val position: Int,
    val title: String,
    val task: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val status: String,
    val reason: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: LocalDate?,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
)

data class MissionWithSteps(
    @Embedded val mission: MissionEntity,
    @Relation(parentColumn = "id", entityColumn = "mission_id") val steps: List<MissionStepEntity>,
)

/**
 * The single active Session. `slot` is always 0, so the table can never hold two active
 * Sessions; a Mission referenced by a running Session cannot be deleted (RESTRICT).
 */
@Entity(
    tableName = "active_session",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["mission_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["session_id"], unique = true), Index("mission_id")],
)
data class ActiveSessionEntity(
    @PrimaryKey val slot: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val type: String,
    val title: String,
    val task: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "mission_id") val missionId: String?,
    @ColumnInfo(name = "mission_step_id") val missionStepId: String?,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    val lifecycle: String,
    @ColumnInfo(name = "accumulated_ms") val accumulatedMs: Long,
    @ColumnInfo(name = "segment_start_wall") val segmentStartWall: Instant?,
    @ColumnInfo(name = "segment_start_realtime_ms") val segmentStartRealtimeMs: Long?,
    @ColumnInfo(name = "segment_boot_count") val segmentBootCount: Int?,
    @ColumnInfo(name = "checkpoint_elapsed_ms") val checkpointElapsedMs: Long,
    @ColumnInfo(name = "checkpoint_wall") val checkpointWall: Instant,
    @ColumnInfo(name = "protection_mode") val protectionMode: String,
    @ColumnInfo(name = "mode_label") val modeLabel: String,
    @ColumnInfo(name = "progress_current") val progressCurrent: Int,
    @ColumnInfo(name = "progress_total") val progressTotal: Int,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
)

/**
 * History: one immutable snapshot per Session id (primary key). Mission ids are kept as plain
 * text so History survives Mission deletion, like the PWA.
 */
@Entity(
    tableName = "session_results",
    indices = [Index("finished_at"), Index("local_date"), Index("mission_id")],
)
data class SessionResultEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    val type: String,
    val status: String,
    val outcome: String,
    @ColumnInfo(name = "end_reason") val endReason: String,
    val title: String,
    val task: String,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "counts_as_focus") val countsAsFocus: Boolean,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "finished_at") val finishedAt: Instant,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "mission_id") val missionId: String?,
    @ColumnInfo(name = "mission_step_id") val missionStepId: String?,
    @ColumnInfo(name = "protection_mode") val protectionMode: String,
    val rating: Int?,
    val origin: String,
    val verification: String,
    @ColumnInfo(name = "saved_at") val savedAt: Instant,
    /** v2: reason chosen at the Protected Focus end barrier. */
    @ColumnInfo(name = "end_note") val endNote: String? = null,
    /** v3: optional Mission review note. */
    @ColumnInfo(name = "review_note") val reviewNote: String? = null,
)

/** Local Activity. (type, ref_id) is unique, so one event is never logged twice. */
@Entity(
    tableName = "activity_log",
    indices = [Index("occurred_at"), Index(value = ["type", "ref_id"], unique = true)],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "ref_id") val refId: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
)

@Entity(tableName = "reminders", indices = [Index("trigger_at")])
data class ReminderEntity(
    @PrimaryKey val key: String,
    val type: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "trigger_at") val triggerAt: Instant,
    val title: String,
    val body: String,
    val precision: String,
    @ColumnInfo(name = "deep_link") val deepLink: String,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Instant?,
)

/** Selected distracting apps: package name, label snapshot, optional icon reference. Nothing else. */
@Entity(tableName = "selected_apps")
data class SelectedAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "label_snapshot") val labelSnapshot: String,
    @ColumnInfo(name = "selected_at") val selectedAt: Instant,
    @ColumnInfo(name = "icon_cache_ref") val iconCacheRef: String?,
)
