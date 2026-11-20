package io.github.illiapohukai.focusmode.core.data.db

import io.github.illiapohukai.focusmode.core.model.ActivityRecord
import io.github.illiapohukai.focusmode.core.model.ActivityType
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.FocusSession
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.FocusTask
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.Reminder
import io.github.illiapohukai.focusmode.core.model.ReminderPrecision
import io.github.illiapohukai.focusmode.core.model.ReminderType
import io.github.illiapohukai.focusmode.core.model.Schedule
import io.github.illiapohukai.focusmode.core.model.ScheduleId
import io.github.illiapohukai.focusmode.core.model.ScheduleZone
import io.github.illiapohukai.focusmode.core.model.SelectedDistractingApp
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionLifecycle
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.SessionTiming
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.model.Verification
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/* Entity <-> model mapping. Rows that fail validation map to null and are skipped, never crash. */

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

fun MissionWithSteps.toModel(): Mission? = runCatching {
    Mission(
        id = MissionId(mission.id),
        title = mission.title,
        subject = mission.subject,
        type = MissionType.fromWire(mission.type),
        deadlineText = mission.deadlineText,
        deadlineDate = mission.deadlineDate,
        preparationStartDate = mission.preparationStartDate,
        status = MissionStatus.fromWire(mission.status) ?: return null,
        createdAt = mission.createdAt,
        updatedAt = mission.updatedAt,
        completedAt = mission.completedAt,
        archivedAt = mission.archivedAt,
        currentStepId = mission.currentStepId?.let(::MissionStepId),
        steps = steps.sortedBy { it.position }.mapNotNull { it.toModel() },
        origin = enumOrNull<DataOrigin>(mission.origin) ?: DataOrigin.LOCAL,
    )
}.getOrNull()

fun MissionStepEntity.toModel(): MissionStep? = MissionStep(
    id = MissionStepId(id),
    missionId = MissionId(missionId),
    position = position,
    title = title,
    task = task,
    durationMinutes = durationMinutes,
    status = MissionStepStatus.fromWire(status) ?: return null,
    reason = reason,
    scheduledDate = scheduledDate,
    completedAt = completedAt,
)

fun Mission.toEntity() = MissionEntity(
    id = id.value, title = title, subject = subject, type = type.wire, deadlineText = deadlineText,
    deadlineDate = deadlineDate, preparationStartDate = preparationStartDate, status = status.wire,
    createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt, archivedAt = archivedAt,
    currentStepId = currentStepId?.value, origin = origin.name,
)

fun MissionStep.toEntity() = MissionStepEntity(
    id = id.value, missionId = missionId.value, position = position, title = title, task = task,
    durationMinutes = durationMinutes, status = status.wire, reason = reason, scheduledDate = scheduledDate,
    completedAt = completedAt,
)

fun ActiveSessionEntity.toModel(): FocusSession? = runCatching {
    val type = SessionType.fromWire(type) ?: return null
    FocusSession(
        id = SessionId(sessionId),
        type = type,
        title = title,
        task = FocusTask.of(task),
        plannedMinutes = plannedMinutes,
        missionLink = if (missionId != null && missionStepId != null) MissionLink(MissionId(missionId), MissionStepId(missionStepId)) else null,
        startedAt = startedAt,
        lifecycle = enumOrNull<SessionLifecycle>(lifecycle) ?: return null,
        timing = SessionTiming(accumulatedMs, segmentStartWall, segmentStartRealtimeMs, segmentBootCount, checkpointElapsedMs, checkpointWall),
        protectionMode = enumOrNull<ProtectionMode>(protectionMode) ?: ProtectionMode.STANDARD,
        modeLabel = modeLabel,
        progressCurrent = progressCurrent,
        progressTotal = progressTotal,
        scheduleId = scheduleId?.let(::ScheduleId),
    )
}.getOrNull()

fun FocusSession.toEntity() = ActiveSessionEntity(
    slot = 0,
    sessionId = id.value,
    type = type.wire,
    title = title,
    task = task.text,
    plannedMinutes = plannedMinutes,
    missionId = missionLink?.missionId?.value,
    missionStepId = missionLink?.stepId?.value,
    startedAt = startedAt,
    lifecycle = lifecycle.name,
    accumulatedMs = timing.accumulatedMs,
    segmentStartWall = timing.segmentStartWall,
    segmentStartRealtimeMs = timing.segmentStartRealtimeMs,
    segmentBootCount = timing.segmentBootCount,
    checkpointElapsedMs = timing.checkpointElapsedMs,
    checkpointWall = timing.checkpointWall,
    protectionMode = protectionMode.name,
    modeLabel = modeLabel,
    progressCurrent = progressCurrent,
    progressTotal = progressTotal,
    scheduleId = scheduleId?.value,
)

fun SessionResultEntity.toModel(): FocusSessionResult? = runCatching {
    FocusSessionResult(
        sessionId = SessionId(sessionId),
        type = SessionType.fromWire(type) ?: return null,
        status = SessionStatus.fromWire(status) ?: return null,
        outcome = SessionOutcome.fromWire(outcome) ?: return null,
        endReason = SessionEndReason.fromWire(endReason) ?: SessionEndReason.IMPORTED,
        title = title,
        task = task,
        plannedMinutes = plannedMinutes,
        actualMinutes = actualMinutes,
        elapsedMs = elapsedMs,
        countsAsFocus = countsAsFocus,
        startedAt = startedAt,
        finishedAt = finishedAt,
        localDate = localDate,
        zoneId = zoneId,
        missionLink = if (missionId != null && missionStepId != null) MissionLink(MissionId(missionId), MissionStepId(missionStepId)) else null,
        protectionMode = enumOrNull<ProtectionMode>(protectionMode) ?: ProtectionMode.STANDARD,
        endNote = enumOrNull<EndNote>(endNote),
        rating = rating,
        origin = enumOrNull<DataOrigin>(origin) ?: DataOrigin.LOCAL,
        verification = enumOrNull<Verification>(verification) ?: Verification.LOCAL_UNVERIFIED,
    )
}.getOrNull()

fun FocusSessionResult.toEntity(savedAt: Instant) = SessionResultEntity(
    sessionId = sessionId.value, type = type.wire, status = status.wire, outcome = outcome.wire, endReason = endReason.wire,
    title = title, task = task, plannedMinutes = plannedMinutes, actualMinutes = actualMinutes, elapsedMs = elapsedMs,
    countsAsFocus = countsAsFocus, startedAt = startedAt, finishedAt = finishedAt, localDate = localDate, zoneId = zoneId,
    missionId = missionLink?.missionId?.value, missionStepId = missionLink?.stepId?.value, protectionMode = protectionMode.name,
    rating = rating, origin = origin.name, verification = verification.name, savedAt = savedAt, endNote = endNote?.name,
)

fun ActivityEntity.toModel(): ActivityRecord? =
    enumOrNull<ActivityType>(type)?.let { ActivityRecord(id, it, refId, occurredAt, localDate) }

fun ReminderEntity.toModel(): Reminder? {
    val type = enumOrNull<ReminderType>(type) ?: return null
    val precision = enumOrNull<ReminderPrecision>(precision) ?: return null
    return Reminder(key, type, sourceId, triggerAt, title, body, precision, deepLink, deliveredAt)
}

fun Reminder.toEntity() = ReminderEntity(key, type.name, sourceId, triggerAt, title, body, precision.name, deepLink, deliveredAt)

fun SelectedAppEntity.toModel() = SelectedDistractingApp(packageName, labelSnapshot, selectedAt, iconCacheRef)

fun weekdaysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
fun maskToWeekdays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

fun ScheduleWithApps.toModel(): Schedule? = runCatching {
    Schedule(
        id = ScheduleId(schedule.id),
        label = schedule.label,
        weekdays = maskToWeekdays(schedule.weekdaysMask),
        start = LocalTime.ofSecondOfDay(schedule.startMinute * 60L),
        end = LocalTime.ofSecondOfDay(schedule.endMinute * 60L),
        zone = schedule.zoneId?.let { ScheduleZone.Fixed(it) } ?: ScheduleZone.FollowDevice,
        protectionMode = enumOrNull<ProtectionMode>(schedule.protectionMode) ?: ProtectionMode.STANDARD,
        appPackages = apps.map { it.packageName }.toSet(),
        leadTimeMinutes = schedule.leadTimeMinutes,
        skipDate = schedule.skipDate,
        enabled = schedule.enabled,
        createdAt = schedule.createdAt,
        updatedAt = schedule.updatedAt,
    )
}.getOrNull()

fun Schedule.toEntity() = ScheduleEntity(
    id = id.value, label = label, weekdaysMask = weekdaysToMask(weekdays),
    startMinute = start.toSecondOfDay() / 60, endMinute = end.toSecondOfDay() / 60,
    zoneId = (zone as? ScheduleZone.Fixed)?.zoneId, protectionMode = protectionMode.name,
    leadTimeMinutes = leadTimeMinutes, skipDate = skipDate, enabled = enabled, createdAt = createdAt, updatedAt = updatedAt,
)
