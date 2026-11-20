package io.github.illiapohukai.focusmode.core.model

import java.time.Instant
import java.time.LocalDate

enum class MissionStatus(val wire: String) {
    ACTIVE("active"), COMPLETED("completed"), ARCHIVED("archived");
    companion object { fun fromWire(v: String?): MissionStatus? = entries.firstOrNull { it.wire == v } }
}

enum class MissionType(val wire: String, val label: String) {
    EXAM("exam", "Prüfung"), ASSIGNMENT("assignment", "Aufgabe"), PROJECT("project", "Projekt"), CUSTOM("custom", "Eigene Mission");
    companion object { fun fromWire(v: String?): MissionType = entries.firstOrNull { it.wire == v } ?: CUSTOM }
}

enum class MissionStepStatus(val wire: String) {
    PENDING("pending"), ACTIVE("active"), COMPLETED("completed");
    companion object { fun fromWire(v: String?): MissionStepStatus? = entries.firstOrNull { it.wire == v } }
}

/** Stable Mission identity tones (PWA v17.7). Derived from the Mission id, never stored. */
enum class MissionTheme { AUBERGINE, FOREST, EMBER, BURGUNDY }

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
) {
    val currentStep: MissionStep? get() = steps.firstOrNull { it.id == currentStepId }
    val completedStepCount: Int get() = steps.count { it.status == MissionStepStatus.COMPLETED }
    val openStepCount: Int get() = steps.size - completedStepCount
    /** 0–100, from completed steps only (PWA Home Mission percent). */
    val progressPercent: Int get() = if (steps.isEmpty()) 0 else completedStepCount * 100 / steps.size

    companion object {
        /** PWA rule: at most two active Missions at a time. */
        const val MAX_ACTIVE = 2
    }
}
