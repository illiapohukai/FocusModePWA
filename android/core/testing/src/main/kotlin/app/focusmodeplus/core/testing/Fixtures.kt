package app.focusmodeplus.core.testing

import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStep
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionType
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
