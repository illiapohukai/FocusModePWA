package app.focusmodeplus.core.data.db

import app.focusmodeplus.core.domain.ai.GeneratedMissionPlan
import app.focusmodeplus.core.domain.ai.GeneratedStep
import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.SourceAnalysis
import app.focusmodeplus.core.domain.mission.DraftAttachment
import app.focusmodeplus.core.domain.mission.MissionDraft
import app.focusmodeplus.core.domain.mission.MissionDraftPhase
import app.focusmodeplus.core.domain.mission.ScheduleDraft
import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.ActivityRecord
import app.focusmodeplus.core.model.ActivityType
import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.FocusSession
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.FocusTask
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionSchedule
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStep
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.Reminder
import app.focusmodeplus.core.model.ReminderPrecision
import app.focusmodeplus.core.model.ReminderType
import app.focusmodeplus.core.model.Schedule
import app.focusmodeplus.core.model.ScheduleId
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.model.ScheduleZone
import app.focusmodeplus.core.model.SelectedDistractingApp
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionLifecycle
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionTiming
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.model.Verification
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
        schedule = MissionSchedule(
            startMode = ScheduleStartMode.fromWire(mission.startMode),
            preferredWeekdays = maskToWeekdays(mission.preferredWeekdays),
            preferredSessionMinutes = mission.preferredSessionMinutes?.takeIf { it in MissionSchedule.SESSION_MINUTES },
        ),
        summary = mission.summary,
        estimateTotalMinutes = mission.estimateMinutes?.takeIf { it > 0 },
        scheduleWarning = mission.scheduleWarning,
        archivedStepId = mission.archivedStepId?.let(::MissionStepId),
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
    requiredTools = linesOf(requiredTools),
    recommendedApps = linesOf(recommendedApps),
)

/** One list item per line. Items are control-stripped by their producers, so they never contain a line break. */
internal fun linesOf(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split('\n').map(String::trim).filter(String::isNotEmpty)
internal fun lines(items: List<String>): String = items.map { it.replace('\n', ' ').replace('\r', ' ').trim() }.filter(String::isNotEmpty).joinToString("\n")

fun Mission.toEntity() = MissionEntity(
    id = id.value, title = title, subject = subject, type = type.wire, deadlineText = deadlineText,
    deadlineDate = deadlineDate, preparationStartDate = preparationStartDate, status = status.wire,
    createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt, archivedAt = archivedAt,
    currentStepId = currentStepId?.value, origin = origin.name,
    startMode = schedule.startMode?.wire, preferredWeekdays = weekdaysToMask(schedule.preferredWeekdays),
    preferredSessionMinutes = schedule.preferredSessionMinutes, estimateMinutes = estimateTotalMinutes, summary = summary,
    scheduleWarning = scheduleWarning, archivedStepId = archivedStepId?.value,
)

fun MissionStep.toEntity() = MissionStepEntity(
    id = id.value, missionId = missionId.value, position = position, title = title, task = task,
    durationMinutes = durationMinutes, status = status.wire, reason = reason, scheduledDate = scheduledDate,
    completedAt = completedAt, requiredTools = lines(requiredTools), recommendedApps = lines(recommendedApps),
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
        reviewNote = reviewNote,
    )
}.getOrNull()

fun FocusSessionResult.toEntity(savedAt: Instant) = SessionResultEntity(
    sessionId = sessionId.value, type = type.wire, status = status.wire, outcome = outcome.wire, endReason = endReason.wire,
    title = title, task = task, plannedMinutes = plannedMinutes, actualMinutes = actualMinutes, elapsedMs = elapsedMs,
    countsAsFocus = countsAsFocus, startedAt = startedAt, finishedAt = finishedAt, localDate = localDate, zoneId = zoneId,
    missionId = missionLink?.missionId?.value, missionStepId = missionLink?.stepId?.value, protectionMode = protectionMode.name,
    rating = rating, origin = origin.name, verification = verification.name, savedAt = savedAt, endNote = endNote?.name,
    reviewNote = reviewNote,
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

fun MissionDraftWithSteps.toModel(): MissionDraft? = runCatching {
    val d = draft
    val plan = if (d.planTitle != null && steps.isNotEmpty()) {
        GeneratedMissionPlan(
            title = d.planTitle,
            subject = d.planSubject.orEmpty(),
            type = MissionType.fromWire(d.planType),
            deadlineText = d.planDeadlineText.orEmpty(),
            steps = steps.sortedBy { it.position }.map {
                GeneratedStep(it.stepId, it.title, it.task, it.durationMinutes, it.reason, it.scheduledDate, linesOf(it.requiredTools), linesOf(it.recommendedApps))
            },
            estimateTotalMinutes = d.planEstimateMinutes ?: steps.sumOf { it.durationMinutes },
            scheduleWarning = d.planWarning,
            difficulty = d.planDifficulty,
            explanation = d.planExplanation.orEmpty(),
            source = SourceAnalysis(
                detectedTasks = d.sourceDetectedTasks,
                detectedPages = d.sourceDetectedPages,
                confidencePercent = d.sourceConfidence?.takeIf { it in 1..100 },
                missingInformation = linesOf(d.sourceMissing),
                facts = linesOf(d.sourceFacts),
                estimates = linesOf(d.sourceEstimates),
            ),
        )
    } else null
    val phase = enumOrNull<MissionDraftPhase>(d.phase) ?: MissionDraftPhase.EDITING
    MissionDraft(
        draftId = d.draftId,
        missionId = MissionId(d.missionId),
        goalText = d.goalText,
        type = d.missionType?.let { t -> MissionType.entries.firstOrNull { it.wire == t } },
        confidence = d.confidence?.let { c -> GenerationConfidence.entries.firstOrNull { it.wire == c } },
        schedule = ScheduleDraft(
            deadline = d.deadlineDate,
            preparationStart = d.preparationStartDate,
            startMode = ScheduleStartMode.fromWire(d.startMode) ?: ScheduleStartMode.IMMEDIATE,
            startModeChosen = d.startModeChosen,
            preferredWeekdays = maskToWeekdays(d.preferredWeekdays),
            preferredSessionMinutes = d.preferredSessionMinutes?.takeIf { it in MissionSchedule.SESSION_MINUTES },
            startClamped = d.startClamped,
        ),
        attachment = if (d.attachmentBytes != null && d.attachmentMime != null) DraftAttachment(d.attachmentBytes, d.attachmentMime, d.attachmentWidth ?: 0, d.attachmentHeight ?: 0) else null,
        // A BRIEFING without its plan rows is not a Briefing; it falls back to editing.
        phase = if (phase == MissionDraftPhase.BRIEFING && plan == null) MissionDraftPhase.EDITING else phase,
        error = GenerationError.fromCode(d.errorCode),
        errorRequestId = d.errorRequestId,
        plan = plan,
        createdAt = d.createdAt,
        updatedAt = d.updatedAt,
    )
}.getOrNull()

fun MissionDraft.toEntity() = MissionDraftEntity(
    slot = 0,
    draftId = draftId,
    missionId = missionId.value,
    goalText = goalText,
    missionType = type?.wire,
    confidence = confidence?.wire,
    deadlineDate = schedule.deadline,
    preparationStartDate = schedule.preparationStart,
    startMode = schedule.startMode.wire,
    startModeChosen = schedule.startModeChosen,
    startClamped = schedule.startClamped,
    preferredWeekdays = weekdaysToMask(schedule.preferredWeekdays),
    preferredSessionMinutes = schedule.preferredSessionMinutes,
    attachmentBytes = attachment?.byteCount,
    attachmentMime = attachment?.mime,
    attachmentWidth = attachment?.width,
    attachmentHeight = attachment?.height,
    phase = phase.name,
    errorCode = error?.code,
    errorRequestId = errorRequestId,
    planTitle = plan?.title,
    planSubject = plan?.subject,
    planType = plan?.type?.wire,
    planDeadlineText = plan?.deadlineText,
    planEstimateMinutes = plan?.estimateTotalMinutes,
    planDifficulty = plan?.difficulty,
    planExplanation = plan?.explanation,
    planWarning = plan?.scheduleWarning,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sourceDetectedTasks = plan?.source?.detectedTasks.orEmpty(),
    sourceDetectedPages = plan?.source?.detectedPages.orEmpty(),
    sourceConfidence = plan?.source?.confidencePercent,
    sourceMissing = lines(plan?.source?.missingInformation.orEmpty()),
    sourceFacts = lines(plan?.source?.facts.orEmpty()),
    sourceEstimates = lines(plan?.source?.estimates.orEmpty()),
)

fun MissionDraft.stepEntities(): List<MissionDraftStepEntity> =
    plan?.steps.orEmpty().mapIndexed { i, s ->
        MissionDraftStepEntity(0, i, s.id, s.title, s.task, s.durationMinutes, s.reason, s.scheduledDate, lines(s.requiredTools), lines(s.recommendedApps))
    }

fun MissionProgressEntity.toModel(): MissionProgressEntry? = runCatching {
    MissionProgressEntry(
        sessionId = SessionId(sessionId),
        missionId = MissionId(missionId),
        stepId = MissionStepId(stepId),
        outcome = SessionOutcome.fromWire(outcome) ?: return null,
        rating = rating,
        actualMinutes = actualMinutes,
        plannedMinutes = plannedMinutes,
        appliedAt = appliedAt,
    )
}.getOrNull()
