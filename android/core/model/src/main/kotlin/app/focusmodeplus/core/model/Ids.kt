package app.focusmodeplus.core.model

import java.util.UUID

@JvmInline value class SessionId(val value: String)
@JvmInline value class MissionId(val value: String)
@JvmInline value class MissionStepId(val value: String)
@JvmInline value class ScheduleId(val value: String)

/** Safe-ID policy shared with the PWA (`schema.isValidEntityId`): 2–160 chars of `[A-Za-z0-9_.:-]`. */
object EntityIds {
    private val SAFE = Regex("^[A-Za-z0-9_.:-]{2,160}$")
    fun isSafe(id: String?): Boolean = id != null && SAFE.matches(id)
    fun newSessionId(): SessionId = SessionId("session_" + UUID.randomUUID())
    fun newMissionId(): MissionId = MissionId("mission_" + UUID.randomUUID())
    fun newStepId(): MissionStepId = MissionStepId("step_" + UUID.randomUUID())
    fun newScheduleId(): ScheduleId = ScheduleId("schedule_" + UUID.randomUUID())
}
