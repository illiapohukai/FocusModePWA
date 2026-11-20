package io.github.illiapohukai.focusmode.core.testing

import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.SessionType
import java.time.Instant
import java.time.LocalDate

/** Test-only fixtures. Never used by production code; never shipped as sample data. */
object Fixtures {
    fun result(
        id: String = "session_test_1",
        localDate: LocalDate = LocalDate.parse("2026-11-19"),
        minutes: Int = 25,
        planned: Int = 25,
        outcome: SessionOutcome = SessionOutcome.COMPLETED,
        status: SessionStatus = SessionStatus.COMPLETED,
        counts: Boolean = true,
        finishedAt: Instant = localDate.atTime(10, 0).atZone(java.time.ZoneId.of("Europe/Berlin")).toInstant(),
        task: String = "Lineare Funktionen",
    ) = FocusSessionResult(
        sessionId = SessionId(id),
        type = SessionType.QUICK,
        status = status,
        outcome = outcome,
        endReason = SessionEndReason.TIMER_FINISHED,
        title = task,
        task = task,
        plannedMinutes = planned,
        actualMinutes = minutes,
        elapsedMs = minutes * 60_000L,
        countsAsFocus = counts,
        startedAt = finishedAt.minusSeconds(minutes * 60L),
        finishedAt = finishedAt,
        localDate = localDate,
        zoneId = "Europe/Berlin",
        missionLink = null,
        protectionMode = ProtectionMode.STANDARD,
    )

    fun mission(
        id: String = "mission_test_1",
        title: String = "Mathe-Klausur",
        steps: Int = 3,
        completed: Int = 0,
        status: MissionStatus = MissionStatus.ACTIVE,
        createdAt: Instant = Instant.parse("2026-11-01T08:00:00Z"),
        deadline: LocalDate? = LocalDate.parse("2026-11-28"),
    ): Mission {
        val missionId = MissionId(id)
        val list = (0 until steps).map { i ->
            MissionStep(
                id = MissionStepId("${id}_step_$i"),
                missionId = missionId,
                position = i,
                title = "Schritt ${i + 1}",
                task = "Aufgabe ${i + 1}",
                durationMinutes = 25,
                status = when { i < completed -> MissionStepStatus.COMPLETED; i == completed -> MissionStepStatus.ACTIVE; else -> MissionStepStatus.PENDING },
            )
        }
        return Mission(
            id = missionId, title = title, subject = "Mathematik", type = MissionType.EXAM,
            deadlineText = deadline?.toString() ?: "Nicht angegeben", deadlineDate = deadline, preparationStartDate = null,
            status = status, createdAt = createdAt, updatedAt = createdAt,
            currentStepId = list.getOrNull(completed)?.id, steps = list, origin = DataOrigin.LOCAL,
        )
    }
}
