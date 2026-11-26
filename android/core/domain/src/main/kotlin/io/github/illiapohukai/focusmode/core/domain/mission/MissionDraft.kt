package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.domain.ai.GeneratedMissionPlan
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Lifecycle of the one Mission generation draft (before a Mission exists):
 * EDITING (form, also after a cancel) → GENERATING (one Worker request) → BRIEFING (a fully
 * validated plan waits for "Mission übernehmen") or FAILED (safe error, input kept, retry).
 * Accepting the Briefing creates the Mission (status ACTIVE) and deletes the draft in one
 * transaction; discarding deletes the draft and its processed photo.
 */
enum class MissionDraftPhase { EDITING, GENERATING, BRIEFING, FAILED }

/** The processed (re-encoded, metadata-free) photo kept for the draft. No file name is stored. */
data class DraftAttachment(val byteCount: Int, val mime: String, val width: Int, val height: Int)

/** PWA `missionScheduleState` plus its private flags (`userManuallyChoseStartMode`, `startClampedNotice`). */
data class ScheduleDraft(
    val deadline: LocalDate? = null,
    val preparationStart: LocalDate? = null,
    val startMode: ScheduleStartMode = ScheduleStartMode.IMMEDIATE,
    val startModeChosen: Boolean = false,
    val preferredWeekdays: Set<DayOfWeek> = emptySet(),
    val preferredSessionMinutes: Int? = null,
    val startClamped: Boolean = false,
)

data class MissionDraft(
    val draftId: String,
    /**
     * Assigned when the draft starts, so the Briefing shows the identity color the stored Mission
     * will keep (PWA v17.7 `pendingMissionDraftId`) and one Briefing can create one Mission only.
     */
    val missionId: MissionId,
    val goalText: String = "",
    val type: MissionType? = null,
    val confidence: GenerationConfidence? = null,
    val schedule: ScheduleDraft = ScheduleDraft(),
    val attachment: DraftAttachment? = null,
    val phase: MissionDraftPhase = MissionDraftPhase.EDITING,
    val error: GenerationError? = null,
    val errorRequestId: String? = null,
    val plan: GeneratedMissionPlan? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val hasInput: Boolean get() = goalText.isNotBlank() || attachment != null

    companion object {
        fun new(now: Instant): MissionDraft =
            MissionDraft(draftId = "draft_" + UUID.randomUUID(), missionId = EntityIds.newMissionId(), createdAt = now, updatedAt = now)
    }
}
