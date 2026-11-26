package io.github.illiapohukai.focusmode.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Stored Mission states (PWA `MISSION_STATUSES`). A Mission row exists only from "Plan übernehmen"
 * onward; the earlier states (Draft, Generating, Briefing, GenerationFailed) belong to the one
 * Mission generation draft (`MissionDraftPhase`) and never create a Mission.
 */
enum class MissionStatus(val wire: String) {
    ACTIVE("active"), COMPLETED("completed"), ARCHIVED("archived");
    companion object { fun fromWire(v: String?): MissionStatus? = entries.firstOrNull { it.wire == v } }
}

enum class MissionType(val wire: String, val label: String) {
    EXAM("exam", "Prüfung"), ASSIGNMENT("assignment", "Aufgabe"), PROJECT("project", "Projekt"), CUSTOM("custom", "Eigene Mission");
    companion object { fun fromWire(v: String?): MissionType = entries.firstOrNull { it.wire == v } ?: CUSTOM }
}

/**
 * Stored step states (PWA `MISSION_STEP_STATUSES`). ACTIVE is the current step. "In progress"
 * (a running Session) and "needs review" (a finished Session awaiting its result) are derived
 * from the Session owner, never stored (see `MissionRules.stepStates`). Skipping is not a PWA
 * product action and is not offered.
 */
enum class MissionStepStatus(val wire: String) {
    PENDING("pending"), ACTIVE("active"), COMPLETED("completed");
    companion object { fun fromWire(v: String?): MissionStepStatus? = entries.firstOrNull { it.wire == v } }
}

/** Stable Mission identity tones (PWA v17.7). Derived from the Mission id, never stored. */
enum class MissionTheme { AUBERGINE, FOREST, EMBER, BURGUNDY }

/** PWA Zeitplan "Wann startest du?" (`missionScheduleState.startMode`). */
enum class ScheduleStartMode(val wire: String, val label: String) {
    IMMEDIATE("immediate", "Sofort"),
    ONE_WEEK_BEFORE("one_week_before", "1 Woche vorher"),
    TWO_WEEKS_BEFORE("two_weeks_before", "2 Wochen vorher"),
    CUSTOM("custom", "Eigene Wahl");
    companion object { fun fromWire(v: String?): ScheduleStartMode? = entries.firstOrNull { it.wire == v } }
}

/**
 * The Zeitplan a Mission was planned with. Preferred weekdays and session length are planning
 * preferences sent to the AI; they never create scheduled Sessions or reminders on their own.
 */
data class MissionSchedule(
    val startMode: ScheduleStartMode? = null,
    val preferredWeekdays: Set<DayOfWeek> = emptySet(),
    val preferredSessionMinutes: Int? = null,
) {
    companion object {
        /** PWA `selectMissionPreferredDuration`: 20, 30 or 45 minutes, or null (AI decides). */
        val SESSION_MINUTES = listOf(20, 30, 45)
    }
}

data class MissionStep(
    val id: MissionStepId,
    val missionId: MissionId,
    val position: Int,
    val title: String,
    val task: String,
    val durationMinutes: Int,
    val status: MissionStepStatus,
    val reason: String = "",
    val scheduledDate: LocalDate? = null,
    val completedAt: Instant? = null,
)

data class Mission(
    val id: MissionId,
    val title: String,
    val subject: String,
    val type: MissionType,
    val deadlineText: String,
    val deadlineDate: LocalDate?,
    val preparationStartDate: LocalDate?,
    val status: MissionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val archivedAt: Instant? = null,
    val currentStepId: MissionStepId?,
    val steps: List<MissionStep>,
    val origin: DataOrigin = DataOrigin.LOCAL,
    val schedule: MissionSchedule = MissionSchedule(),
    /** AI plan explanation ("So ist deine Mission aufgebaut"), bounded, local only. */
    val summary: String = "",
    /** AI estimate of the total effort in minutes, if the plan had one. */
    val estimateTotalMinutes: Int? = null,
    val scheduleWarning: String? = null,
    /** Current step at the time of archiving (PWA `archivedCurrentStepId`), used by restore. */
    val archivedStepId: MissionStepId? = null,
) {
    val currentStep: MissionStep? get() = steps.firstOrNull { it.id == currentStepId }
    val completedStepCount: Int get() = steps.count { it.status == MissionStepStatus.COMPLETED }
    val openStepCount: Int get() = steps.size - completedStepCount
    /** Planned minutes of the steps that are still open. */
    val remainingMinutes: Int get() = steps.filter { it.status != MissionStepStatus.COMPLETED }.sumOf { it.durationMinutes }

    /**
     * 0–100 from completed steps only, rounded like the PWA (`Math.round(done / total * 100)`).
     * This is the one progress value: Home, Mission Plan, Result and History all read it.
     */
    val progressPercent: Int get() = if (steps.isEmpty()) 0 else Math.round(completedStepCount * 100.0 / steps.size).toInt().coerceIn(0, 100)

    companion object {
        /** PWA rule: at most two active Missions at a time. */
        const val MAX_ACTIVE = 2
    }
}
