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

/* Schema version 3 (v19 Missions): the generation draft, its Briefing plan, Mission progress entries. */

/**
 * The one Mission generation draft (`slot` is always 0). It holds the unsent form, the phase of
 * the one request and, in BRIEFING, the validated plan header (steps in [MissionDraftStepEntity]).
 * The photo itself is never stored here; only its processed size and type.
 */
@Entity(tableName = "mission_drafts")
data class MissionDraftEntity(
    @PrimaryKey val slot: Int = 0,
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "mission_id") val missionId: String,
    @ColumnInfo(name = "goal_text") val goalText: String,
    @ColumnInfo(name = "mission_type") val missionType: String?,
    val confidence: String?,
    @ColumnInfo(name = "deadline_date") val deadlineDate: LocalDate?,
    @ColumnInfo(name = "preparation_start_date") val preparationStartDate: LocalDate?,
    @ColumnInfo(name = "start_mode") val startMode: String,
    @ColumnInfo(name = "start_mode_chosen") val startModeChosen: Boolean,
    @ColumnInfo(name = "start_clamped") val startClamped: Boolean,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(name = "preferred_weekdays") val preferredWeekdays: Int,
    @ColumnInfo(name = "preferred_session_minutes") val preferredSessionMinutes: Int?,
    @ColumnInfo(name = "attachment_bytes") val attachmentBytes: Int?,
    @ColumnInfo(name = "attachment_mime") val attachmentMime: String?,
    @ColumnInfo(name = "attachment_width") val attachmentWidth: Int?,
    @ColumnInfo(name = "attachment_height") val attachmentHeight: Int?,
    val phase: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "error_request_id") val errorRequestId: String?,
    @ColumnInfo(name = "plan_title") val planTitle: String?,
    @ColumnInfo(name = "plan_subject") val planSubject: String?,
    @ColumnInfo(name = "plan_type") val planType: String?,
    @ColumnInfo(name = "plan_deadline_text") val planDeadlineText: String?,
    @ColumnInfo(name = "plan_estimate_minutes") val planEstimateMinutes: Int?,
    @ColumnInfo(name = "plan_difficulty") val planDifficulty: String?,
    @ColumnInfo(name = "plan_explanation") val planExplanation: String?,
    @ColumnInfo(name = "plan_warning") val planWarning: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** Steps of the Briefing plan, in order. Ids here are transport ids; stored Missions get local ids. */
@Entity(
    tableName = "mission_draft_steps",
    primaryKeys = ["draft_slot", "position"],
    foreignKeys = [ForeignKey(entity = MissionDraftEntity::class, parentColumns = ["slot"], childColumns = ["draft_slot"], onDelete = ForeignKey.CASCADE)],
)
data class MissionDraftStepEntity(
    @ColumnInfo(name = "draft_slot") val draftSlot: Int = 0,
    val position: Int,
    @ColumnInfo(name = "step_id") val stepId: String,
    val title: String,
    val task: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    val reason: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: LocalDate?,
)

data class MissionDraftWithSteps(
    @Embedded val draft: MissionDraftEntity,
    @Relation(parentColumn = "slot", entityColumn = "draft_slot") val steps: List<MissionDraftStepEntity>,
)

/**
 * One applied Mission Session result (PWA `progress.history`). The Session id is the primary key,
 * so a result can be applied to its Mission only once. Deleted with its Mission; History keeps
 * the Session itself.
 */
@Entity(
    tableName = "mission_progress",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["mission_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mission_id")],
)
data class MissionProgressEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "mission_id") val missionId: String,
    @ColumnInfo(name = "step_id") val stepId: String,
    val outcome: String,
    val rating: Int?,
    @ColumnInfo(name = "actual_minutes") val actualMinutes: Int,
    @ColumnInfo(name = "planned_minutes") val plannedMinutes: Int,
    @ColumnInfo(name = "applied_at") val appliedAt: Instant,
)
