package io.github.illiapohukai.focusmode.core.domain.sync

import io.github.illiapohukai.focusmode.core.domain.repository.MissionProgressEntry
import io.github.illiapohukai.focusmode.core.domain.session.QuickFocusDuration
import io.github.illiapohukai.focusmode.core.model.AppPreferences
import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EndNote
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionSchedule
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStep
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionType
import io.github.illiapohukai.focusmode.core.model.MotionPreference
import io.github.illiapohukai.focusmode.core.model.NotificationDetailLevel
import io.github.illiapohukai.focusmode.core.model.ProtectionMode
import io.github.illiapohukai.focusmode.core.model.ScheduleStartMode
import io.github.illiapohukai.focusmode.core.model.SessionEndReason
import io.github.illiapohukai.focusmode.core.model.SessionId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.model.SyncEntityType
import io.github.illiapohukai.focusmode.core.model.Verification
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The versioned per-user Firestore model (v21). `firestore.rules` mirrors every name and bound
 * here; `firebase/test/fixtures/client-payloads.json` is generated from [RemoteCodec] and the
 * Rules tests prove those exact payloads are accepted. Change both sides together.
 */
object RemoteSchema {
    const val SCHEMA_VERSION = 1L
    /** Firestore allows 500 writes per batch; the margin keeps one Mission with all its steps in one batch. */
    const val MAX_BATCH_WRITES = 450

    const val TITLE_MAX = 300
    const val TEXT_MAX = 2000
    const val NOTE_MAX = 1000
    const val WARNING_MAX = 500
    const val LIST_ITEMS_MAX = 20
    const val LIST_ITEM_MAX = 500
    const val STEPS_MAX = 100
    const val PROGRESS_MAX = 500
    const val MINUTES_MAX = 1440
    const val ESTIMATE_MAX = 200_000
    const val ELAPSED_MAX_MS = 172_800_000L
    const val EPOCH_MIN_MS = 946_684_800_000L // 2000-01-01
    const val EPOCH_MAX_MS = 4_102_444_800_000L // 2100-01-01
    const val ZONE_MAX = 64

    const val SETTINGS_DOC = "profile"
    /** One document per migration, so a second device linking its own local data never collides. */
    fun migrationDocId(migrationId: String): String = "migration_$migrationId"

    /**
     * Future server-authoritative fields (verified History, League, subscription, quota,
     * moderation, admin). The client never writes them, an import never fills them, and the
     * Rules reject any client write that carries one. No producer exists in v21.
     */
    val RESERVED_SERVER_FIELDS: List<String> = listOf(
        "verifiedFocusMinutes", "verifiedSessionCount", "verifiedStreak", "verifiedSession",
        "leaguePoints", "leagueRank", "leagueTier", "leagueSeason",
        "subscriptionTier", "entitlement", "aiQuota", "moderationStatus", "adminRole", "serverTrust",
    )

    object Fields {
        val PROFILE = listOf("ownerUid", "schemaVersion", "createdAt", "updatedAt", "serverUpdatedAt")
        val MISSION = listOf(
            "id", "ownerUid", "schemaVersion", "title", "subject", "type", "deadlineText", "deadlineDate", "preparationStartDate",
            "status", "origin", "createdAt", "updatedAt", "completedAt", "archivedAt", "currentStepId", "archivedStepId",
            "startMode", "preferredWeekdays", "preferredSessionMinutes", "estimateMinutes", "summary", "scheduleWarning",
            "stepIds", "progress", "syncVersion", "deviceMutationId", "serverUpdatedAt",
        )
        val PROGRESS_ENTRY = listOf("sessionId", "stepId", "outcome", "rating", "actualMinutes", "plannedMinutes", "appliedAt")
        val STEP = listOf(
            "id", "ownerUid", "missionId", "schemaVersion", "position", "title", "task", "durationMinutes", "status", "reason",
            "scheduledDate", "completedAt", "requiredTools", "recommendedApps", "syncVersion", "deviceMutationId", "updatedAt", "serverUpdatedAt",
        )
        val SESSION = listOf(
            "id", "ownerUid", "schemaVersion", "type", "status", "outcome", "endReason", "title", "task", "plannedMinutes",
            "actualMinutes", "elapsedMs", "countsAsFocus", "startedAt", "finishedAt", "localDate", "zoneId", "missionId",
            "missionStepId", "protectionMode", "endNote", "rating", "origin", "reviewNote", "createdAt", "updatedAt",
            "syncVersion", "deviceMutationId", "serverUpdatedAt",
        )
        val SETTINGS = listOf("ownerUid", "schemaVersion", "defaultQuickFocusMinutes", "motion", "notificationDetail", "updatedAt", "deviceMutationId", "serverUpdatedAt")
        val TOMBSTONE = listOf("id", "ownerUid", "schemaVersion", "entityType", "entityId", "deletedAt", "syncVersion", "deviceMutationId", "serverUpdatedAt")
        val MIGRATION = listOf(
            "ownerUid", "schemaVersion", "migrationId", "status", "missionCount", "sessionCount", "startedAt", "completedAt",
            "updatedAt", "serverUpdatedAt",
        )
    }

    private val MUTATION_ID = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val UID = Regex("^[A-Za-z0-9_-]{1,128}$")

    /** Safe-id policy plus Firestore document-id rules (`..` and `__…__` are reserved). Ids are never user text. */
    fun isDocumentId(id: String?): Boolean = id != null && EntityIds.isSafe(id) && id != ".." && !(id.startsWith("__") && id.endsWith("__"))
    fun isMutationId(id: String?): Boolean = id != null && MUTATION_ID.matches(id)
    fun isUid(uid: String?): Boolean = uid != null && UID.matches(uid)
    fun tombstoneId(type: SyncEntityType, entityId: String): String = "${type.wire}_$entityId"
}

/** Device-independent settings that synchronize (v21). Permissions and device state never do. */
data class SyncedSettings(
    val defaultQuickFocusMinutes: Int,
    val motion: MotionPreference,
    val notificationDetail: NotificationDetailLevel,
) {
    /** Stable comparison key; contains no personal data. */
    val fingerprint: String get() = "v1|$defaultQuickFocusMinutes|${motion.name}|${notificationDetail.name}"

    companion object {
        fun of(p: AppPreferences) = SyncedSettings(p.defaultQuickFocusMinutes, p.motion, p.notificationDetail)
    }
}

/**
 * Encodes local entities into exactly the documents the Rules accept, and decodes server
 * documents strictly: unknown values, foreign owners, out-of-bounds text or ids, and reserved
 * fields make a document invalid (it is skipped, never applied). Numbers are written as Long,
 * client times as epoch milliseconds, dates as ISO `yyyy-MM-dd`.
 */
class RemoteCodec(private val uid: String) {
    init { require(RemoteSchema.isUid(uid)) { "invalid_uid" } }

    // ------------------------------------------------------------------------------ encode

    fun profile(createdAt: Instant, now: Instant): Map<String, Any?> = linkedMapOf(
        "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
        "createdAt" to createdAt.toEpochMilli(), "updatedAt" to now.toEpochMilli(), "serverUpdatedAt" to ServerTimestamp,
    )

    fun migration(migrationId: String, complete: Boolean, missionCount: Int, sessionCount: Int, startedAt: Instant, completedAt: Instant?, now: Instant): Map<String, Any?> = linkedMapOf(
        "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION, "migrationId" to migrationId,
        "status" to if (complete) "complete" else "in_progress",
        "missionCount" to missionCount.toLong(), "sessionCount" to sessionCount.toLong(),
        "startedAt" to startedAt.toEpochMilli(), "completedAt" to completedAt?.toEpochMilli(),
        "updatedAt" to now.toEpochMilli(), "serverUpdatedAt" to ServerTimestamp,
    )

    /** The Mission document (steps are separate documents). Null when the local data cannot be stored remotely. */
    fun mission(snapshot: MissionSnapshot, syncVersion: Long, mutationId: String): Map<String, Any?>? {
        val m = snapshot.mission
        if (!validMission(m, snapshot.progress) || !RemoteSchema.isMutationId(mutationId) || syncVersion < 1) return null
        val ordered = m.steps.sortedBy { it.position }
        return linkedMapOf(
            "id" to m.id.value, "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
            "title" to m.title, "subject" to m.subject, "type" to m.type.wire, "deadlineText" to m.deadlineText,
            "deadlineDate" to m.deadlineDate?.toString(), "preparationStartDate" to m.preparationStartDate?.toString(),
            "status" to m.status.wire, "origin" to originWire(m.origin),
            "createdAt" to m.createdAt.toEpochMilli(), "updatedAt" to m.updatedAt.toEpochMilli(),
            "completedAt" to m.completedAt?.toEpochMilli(), "archivedAt" to m.archivedAt?.toEpochMilli(),
            "currentStepId" to m.currentStepId?.value, "archivedStepId" to m.archivedStepId?.value,
            "startMode" to m.schedule.startMode?.wire, "preferredWeekdays" to weekdayMask(m.schedule.preferredWeekdays).toLong(),
            "preferredSessionMinutes" to m.schedule.preferredSessionMinutes?.toLong(), "estimateMinutes" to m.estimateTotalMinutes?.toLong(),
            "summary" to m.summary, "scheduleWarning" to m.scheduleWarning,
            "stepIds" to ordered.map { it.id.value },
            "progress" to snapshot.progress.sortedWith(compareBy({ it.appliedAt }, { it.sessionId.value })).map { p ->
                linkedMapOf(
                    "sessionId" to p.sessionId.value, "stepId" to p.stepId.value, "outcome" to p.outcome.wire, "rating" to p.rating?.toLong(),
                    "actualMinutes" to p.actualMinutes.toLong(), "plannedMinutes" to p.plannedMinutes.toLong(), "appliedAt" to p.appliedAt.toEpochMilli(),
                )
            },
            "syncVersion" to syncVersion, "deviceMutationId" to mutationId, "serverUpdatedAt" to ServerTimestamp,
        )
    }

    fun step(step: MissionStep, syncVersion: Long, mutationId: String, updatedAt: Instant): Map<String, Any?> = linkedMapOf(
        "id" to step.id.value, "ownerUid" to uid, "missionId" to step.missionId.value, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
        "position" to step.position.toLong(), "title" to step.title, "task" to step.task, "durationMinutes" to step.durationMinutes.toLong(),
        "status" to step.status.wire, "reason" to step.reason, "scheduledDate" to step.scheduledDate?.toString(),
        "completedAt" to step.completedAt?.toEpochMilli(), "requiredTools" to step.requiredTools, "recommendedApps" to step.recommendedApps,
        "syncVersion" to syncVersion, "deviceMutationId" to mutationId, "updatedAt" to updatedAt.toEpochMilli(), "serverUpdatedAt" to ServerTimestamp,
    )

    /** A finalized Session. Never carries verification: synchronized History is ordinary, unverified History. */
    fun session(r: FocusSessionResult, mutationId: String, now: Instant): Map<String, Any?>? {
        if (!validSession(r) || !RemoteSchema.isMutationId(mutationId)) return null
        return linkedMapOf(
            "id" to r.sessionId.value, "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
            "type" to r.type.wire, "status" to r.status.wire, "outcome" to r.outcome.wire, "endReason" to r.endReason.wire,
            "title" to r.title, "task" to r.task, "plannedMinutes" to r.plannedMinutes.toLong(), "actualMinutes" to r.actualMinutes.toLong(),
            "elapsedMs" to r.elapsedMs, "countsAsFocus" to r.countsAsFocus,
            "startedAt" to r.startedAt.toEpochMilli(), "finishedAt" to r.finishedAt.toEpochMilli(),
            "localDate" to r.localDate.toString(), "zoneId" to r.zoneId,
            "missionId" to r.missionLink?.missionId?.value, "missionStepId" to r.missionLink?.stepId?.value,
            "protectionMode" to r.protectionMode.name.lowercase(), "endNote" to r.endNote?.name?.lowercase(),
            "rating" to r.rating?.toLong(), "origin" to originWire(r.origin), "reviewNote" to r.reviewNote,
            "createdAt" to r.finishedAt.toEpochMilli(), "updatedAt" to now.toEpochMilli(),
            "syncVersion" to 1L, "deviceMutationId" to mutationId, "serverUpdatedAt" to ServerTimestamp,
        )
    }

    fun settings(s: SyncedSettings, mutationId: String, now: Instant): Map<String, Any?> = linkedMapOf(
        "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
        "defaultQuickFocusMinutes" to s.defaultQuickFocusMinutes.toLong(), "motion" to s.motion.name.lowercase(),
        "notificationDetail" to s.notificationDetail.name.lowercase(),
        "updatedAt" to now.toEpochMilli(), "deviceMutationId" to mutationId, "serverUpdatedAt" to ServerTimestamp,
    )

    fun tombstone(type: SyncEntityType, entityId: String, deletedAt: Instant, syncVersion: Long, mutationId: String): Map<String, Any?> = linkedMapOf(
        "id" to RemoteSchema.tombstoneId(type, entityId), "ownerUid" to uid, "schemaVersion" to RemoteSchema.SCHEMA_VERSION,
        "entityType" to type.wire, "entityId" to entityId, "deletedAt" to deletedAt.toEpochMilli(),
        "syncVersion" to syncVersion, "deviceMutationId" to mutationId, "serverUpdatedAt" to ServerTimestamp,
    )

    // ------------------------------------------------------------------------------ decode

    fun decodeMission(doc: RemoteDocument, stepDocs: List<RemoteDocument>): RemoteMission? = runCatching {
        val f = doc.fields
        if (!header(f, RemoteSchema.Fields.MISSION, (doc.path as? RemotePath.Doc)?.id)) return null
        val id = MissionId(f.str("id")!!)
        val stepIds = (f["stepIds"] as? List<*>)?.map { it as? String ?: return null } ?: return null
        if (stepIds.size > RemoteSchema.STEPS_MAX || stepIds.toSet().size != stepIds.size || stepIds.any { !RemoteSchema.isDocumentId(it) }) return null
        val byId = stepDocs.mapNotNull { decodeStep(it, id) }.associateBy { it.id.value }
        val steps = stepIds.map { byId[it] ?: return null } // an incomplete step set is never applied
        if (steps.map { it.position }.toSet().size != steps.size) return null
        val progress = (f["progress"] as? List<*>)?.map { decodeProgress(it, id) ?: return null } ?: return null
        if (progress.size > RemoteSchema.PROGRESS_MAX) return null
        val current = f.optStr("currentStepId")?.let(::MissionStepId)
        if (current != null && current.value !in stepIds) return null
        val mission = Mission(
            id = id,
            title = f.text("title", RemoteSchema.TITLE_MAX, allowEmpty = false)!!,
            subject = f.text("subject", RemoteSchema.TITLE_MAX)!!,
            type = MissionType.entries.first { it.wire == f.str("type") },
            deadlineText = f.text("deadlineText", RemoteSchema.TITLE_MAX)!!,
            deadlineDate = f.optDate("deadlineDate"),
            preparationStartDate = f.optDate("preparationStartDate"),
            status = MissionStatus.fromWire(f.str("status"))!!,
            createdAt = f.instant("createdAt")!!,
            updatedAt = f.instant("updatedAt")!!,
            completedAt = f.optInstant("completedAt"),
            archivedAt = f.optInstant("archivedAt"),
            currentStepId = current,
            steps = steps,
            origin = origin(f.str("origin"))!!,
            schedule = MissionSchedule(
                startMode = f.optStr("startMode")?.let { ScheduleStartMode.fromWire(it) ?: return null },
                preferredWeekdays = weekdays(f.long("preferredWeekdays")?.toInt()?.takeIf { it in 0..127 } ?: return null),
                preferredSessionMinutes = f.optLong("preferredSessionMinutes")?.toInt()?.takeIf { it in 1..RemoteSchema.MINUTES_MAX }.also { if (f["preferredSessionMinutes"] != null && it == null) return null },
            ),
            summary = f.text("summary", RemoteSchema.TEXT_MAX)!!,
            estimateTotalMinutes = f.optLong("estimateMinutes")?.toInt()?.takeIf { it in 0..RemoteSchema.ESTIMATE_MAX }.also { if (f["estimateMinutes"] != null && it == null) return null },
            scheduleWarning = f.optText("scheduleWarning", RemoteSchema.WARNING_MAX),
            archivedStepId = f.optStr("archivedStepId")?.let(::MissionStepId)?.takeIf { it.value in stepIds },
        )
        RemoteMission(MissionSnapshot(mission, progress), stepIds, f.long("syncVersion")!!.takeIf { it >= 1 }!!, f.optStr("deviceMutationId"), f.optLong("serverUpdatedAt"))
    }.getOrNull()

    private fun decodeStep(doc: RemoteDocument, missionId: MissionId): MissionStep? = runCatching {
        val f = doc.fields
        if (!header(f, RemoteSchema.Fields.STEP, (doc.path as? RemotePath.Doc)?.id)) return null
        if (f.str("missionId") != missionId.value) return null
        MissionStep(
            id = MissionStepId(f.str("id")!!),
            missionId = missionId,
            position = f.long("position")!!.toInt().takeIf { it in 0 until 1000 }!!,
            title = f.text("title", RemoteSchema.TITLE_MAX, allowEmpty = false)!!,
            task = f.text("task", RemoteSchema.TEXT_MAX)!!,
            durationMinutes = f.long("durationMinutes")!!.toInt().takeIf { it in 1..RemoteSchema.MINUTES_MAX }!!,
            status = MissionStepStatus.fromWire(f.str("status"))!!,
            reason = f.text("reason", RemoteSchema.TEXT_MAX)!!,
            scheduledDate = f.optDate("scheduledDate"),
            completedAt = f.optInstant("completedAt"),
            requiredTools = f.textList("requiredTools")!!,
            recommendedApps = f.textList("recommendedApps")!!,
        )
    }.getOrNull()

    private fun decodeProgress(raw: Any?, missionId: MissionId): MissionProgressEntry? = runCatching {
        @Suppress("UNCHECKED_CAST") val f = raw as? Map<String, Any?> ?: return null
        if (f.keys != RemoteSchema.Fields.PROGRESS_ENTRY.toSet()) return null
        MissionProgressEntry(
            sessionId = SessionId(f.str("sessionId")!!.takeIf(RemoteSchema::isDocumentId)!!),
            missionId = missionId,
            stepId = MissionStepId(f.str("stepId")!!.takeIf(RemoteSchema::isDocumentId)!!),
            outcome = SessionOutcome.fromWire(f.str("outcome"))!!,
            rating = f.optLong("rating")?.toInt()?.takeIf { it in 1..5 }.also { if (f["rating"] != null && it == null) return null },
            actualMinutes = f.long("actualMinutes")!!.toInt().takeIf { it in 0..RemoteSchema.MINUTES_MAX }!!,
            plannedMinutes = f.long("plannedMinutes")!!.toInt().takeIf { it in 1..RemoteSchema.MINUTES_MAX }!!,
            appliedAt = f.instant("appliedAt")!!,
        )
    }.getOrNull()

    fun decodeSession(doc: RemoteDocument): RemoteSession? = runCatching {
        val f = doc.fields
        if (!header(f, RemoteSchema.Fields.SESSION, (doc.path as? RemotePath.Doc)?.id)) return null
        val missionId = f.optStr("missionId")
        val stepId = f.optStr("missionStepId")
        if ((missionId == null) != (stepId == null)) return null
        if (missionId != null && (!RemoteSchema.isDocumentId(missionId) || !RemoteSchema.isDocumentId(stepId))) return null
        val endReason = SessionEndReason.fromWire(f.str("endReason"))!!.takeIf { it != SessionEndReason.AWAITING_USER_REVIEW }!!
        val zone = f.text("zoneId", RemoteSchema.ZONE_MAX, allowEmpty = false)!!.also { ZoneId.of(it) }
        val result = FocusSessionResult(
            sessionId = SessionId(f.str("id")!!),
            type = SessionType.fromWire(f.str("type"))!!,
            status = SessionStatus.fromWire(f.str("status"))!!,
            outcome = SessionOutcome.fromWire(f.str("outcome"))!!,
            endReason = endReason,
            title = f.text("title", RemoteSchema.TITLE_MAX)!!,
            task = f.text("task", RemoteSchema.TEXT_MAX)!!,
            plannedMinutes = f.long("plannedMinutes")!!.toInt().takeIf { it in 1..RemoteSchema.MINUTES_MAX }!!,
            actualMinutes = f.long("actualMinutes")!!.toInt().takeIf { it in 0..RemoteSchema.MINUTES_MAX }!!,
            elapsedMs = f.long("elapsedMs")!!.takeIf { it in 0..RemoteSchema.ELAPSED_MAX_MS }!!,
            countsAsFocus = f["countsAsFocus"] as Boolean,
            startedAt = f.instant("startedAt")!!,
            finishedAt = f.instant("finishedAt")!!,
            localDate = LocalDate.parse(f.str("localDate")!!),
            zoneId = zone,
            missionLink = missionId?.let { MissionLink(MissionId(it), MissionStepId(stepId!!)) },
            protectionMode = ProtectionMode.entries.first { it.name.lowercase() == f.str("protectionMode") },
            endNote = f.optStr("endNote")?.let { w -> EndNote.entries.firstOrNull { it.name.lowercase() == w } ?: return null },
            rating = f.optLong("rating")?.toInt()?.takeIf { it in 1..5 }.also { if (f["rating"] != null && it == null) return null },
            origin = origin(f.str("origin"))!!,
            verification = Verification.LOCAL_UNVERIFIED,
            reviewNote = f.optText("reviewNote", RemoteSchema.NOTE_MAX),
        )
        if (result.finishedAt.isBefore(result.startedAt) || result.type == SessionType.MISSION && missionId == null) return null
        RemoteSession(result, f.optStr("deviceMutationId"), f.optLong("serverUpdatedAt"))
    }.getOrNull()

    fun decodeTombstone(doc: RemoteDocument): RemoteTombstone? = runCatching {
        val f = doc.fields
        if (!header(f, RemoteSchema.Fields.TOMBSTONE, (doc.path as? RemotePath.Doc)?.id)) return null
        val type = SyncEntityType.fromWire(f.str("entityType"))!!.takeIf { it == SyncEntityType.MISSION }!!
        val entityId = f.str("entityId")!!.takeIf(RemoteSchema::isDocumentId)!!
        if (f.str("id") != RemoteSchema.tombstoneId(type, entityId)) return null
        RemoteTombstone(type, entityId, f.instant("deletedAt")!!, f.long("syncVersion")!!.takeIf { it >= 1 }!!, f.optStr("deviceMutationId"), f.optLong("serverUpdatedAt"))
    }.getOrNull()

    fun decodeSettings(doc: RemoteDocument): RemoteSettings? = runCatching {
        val f = doc.fields
        if (!header(f, RemoteSchema.Fields.SETTINGS, null)) return null
        RemoteSettings(
            defaultQuickFocusMinutes = f.long("defaultQuickFocusMinutes")!!.toInt().takeIf(QuickFocusDuration::isValid)!!,
            motion = MotionPreference.entries.first { it.name.lowercase() == f.str("motion") },
            notificationDetail = NotificationDetailLevel.entries.first { it.name.lowercase() == f.str("notificationDetail") },
            serverUpdatedAtMs = f.optLong("serverUpdatedAt"),
        )
    }.getOrNull()

    // ------------------------------------------------------------------------------ validation

    private fun header(f: Map<String, Any?>, fields: List<String>, docId: String?): Boolean {
        if (f.keys != fields.toSet()) return false // exact field set: unknown and reserved fields are invalid
        if (f["ownerUid"] != uid) return false
        if (f.long("schemaVersion") != RemoteSchema.SCHEMA_VERSION) return false
        val id = f["id"]
        if ("id" in fields && (id !is String || !RemoteSchema.isDocumentId(id) || (docId != null && docId != id))) return false
        return true
    }

    private fun validMission(m: Mission, progress: List<MissionProgressEntry>): Boolean {
        if (!RemoteSchema.isDocumentId(m.id.value) || m.steps.size > RemoteSchema.STEPS_MAX || progress.size > RemoteSchema.PROGRESS_MAX) return false
        if (m.title.isBlank() || m.title.length > RemoteSchema.TITLE_MAX || m.subject.length > RemoteSchema.TITLE_MAX || m.deadlineText.length > RemoteSchema.TITLE_MAX) return false
        if (m.summary.length > RemoteSchema.TEXT_MAX || (m.scheduleWarning?.length ?: 0) > RemoteSchema.WARNING_MAX) return false
        if (listOfNotNull(m.createdAt, m.updatedAt, m.completedAt, m.archivedAt).any { !validTime(it) }) return false
        if (m.schedule.preferredSessionMinutes?.let { it !in 1..RemoteSchema.MINUTES_MAX } == true) return false
        if (m.estimateTotalMinutes?.let { it !in 0..RemoteSchema.ESTIMATE_MAX } == true) return false
        if (m.steps.map { it.id }.toSet().size != m.steps.size || m.steps.map { it.position }.toSet().size != m.steps.size) return false
        m.steps.forEach { s ->
            if (!RemoteSchema.isDocumentId(s.id.value) || s.missionId != m.id || s.position !in 0 until 1000) return false
            if (s.title.isBlank() || s.title.length > RemoteSchema.TITLE_MAX || s.task.length > RemoteSchema.TEXT_MAX || s.reason.length > RemoteSchema.TEXT_MAX) return false
            if (s.durationMinutes !in 1..RemoteSchema.MINUTES_MAX || s.completedAt?.let(::validTime) == false) return false
            if (!validList(s.requiredTools) || !validList(s.recommendedApps)) return false
        }
        if (listOfNotNull(m.currentStepId, m.archivedStepId).any { id -> m.steps.none { it.id == id } }) return false
        return progress.all { p ->
            RemoteSchema.isDocumentId(p.sessionId.value) && RemoteSchema.isDocumentId(p.stepId.value) && validTime(p.appliedAt) &&
                p.actualMinutes in 0..RemoteSchema.MINUTES_MAX && p.plannedMinutes in 1..RemoteSchema.MINUTES_MAX && (p.rating == null || p.rating in 1..5)
        }
    }

    private fun validSession(r: FocusSessionResult): Boolean =
        RemoteSchema.isDocumentId(r.sessionId.value) && r.endReason != SessionEndReason.AWAITING_USER_REVIEW &&
            r.title.length <= RemoteSchema.TITLE_MAX && r.task.length <= RemoteSchema.TEXT_MAX && (r.reviewNote?.length ?: 0) <= RemoteSchema.NOTE_MAX &&
            r.plannedMinutes in 1..RemoteSchema.MINUTES_MAX && r.actualMinutes in 0..RemoteSchema.MINUTES_MAX && r.elapsedMs in 0..RemoteSchema.ELAPSED_MAX_MS &&
            validTime(r.startedAt) && validTime(r.finishedAt) && !r.finishedAt.isBefore(r.startedAt) &&
            r.zoneId.isNotEmpty() && r.zoneId.length <= RemoteSchema.ZONE_MAX && (r.rating == null || r.rating in 1..5) &&
            (r.missionLink == null || (RemoteSchema.isDocumentId(r.missionLink!!.missionId.value) && RemoteSchema.isDocumentId(r.missionLink!!.stepId.value))) &&
            (r.type != SessionType.MISSION || r.missionLink != null)

    private fun validList(items: List<String>) = items.size <= RemoteSchema.LIST_ITEMS_MAX && items.all { it.isNotEmpty() && it.length <= RemoteSchema.LIST_ITEM_MAX }
    private fun validTime(i: Instant) = i.toEpochMilli() in RemoteSchema.EPOCH_MIN_MS..RemoteSchema.EPOCH_MAX_MS

    private fun originWire(o: DataOrigin) = o.name.lowercase()
    private fun origin(w: String?): DataOrigin? = DataOrigin.entries.firstOrNull { it.name.lowercase() == w }

    private fun Map<String, Any?>.str(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.optStr(k: String): String? = when (val v = this[k]) { null -> null; is String -> v; else -> throw IllegalArgumentException(k) }
    private fun Map<String, Any?>.long(k: String): Long? = when (val v = this[k]) { is Long -> v; is Int -> v.toLong(); else -> null }
    private fun Map<String, Any?>.optLong(k: String): Long? = when (val v = this[k]) { null -> null; is Long -> v; is Int -> v.toLong(); else -> throw IllegalArgumentException(k) }
    private fun Map<String, Any?>.instant(k: String): Instant? = long(k)?.takeIf { it in RemoteSchema.EPOCH_MIN_MS..RemoteSchema.EPOCH_MAX_MS }?.let(Instant::ofEpochMilli)
    private fun Map<String, Any?>.optInstant(k: String): Instant? = if (this[k] == null) null else instant(k) ?: throw IllegalArgumentException(k)
    private fun Map<String, Any?>.optDate(k: String): LocalDate? = optStr(k)?.let { s -> require(DATE.matches(s)); LocalDate.parse(s) }
    private fun Map<String, Any?>.text(k: String, max: Int, allowEmpty: Boolean = true): String? = str(k)?.takeIf { it.length <= max && (allowEmpty || it.isNotBlank()) }
    private fun Map<String, Any?>.optText(k: String, max: Int): String? = optStr(k)?.also { require(it.length <= max) }
    private fun Map<String, Any?>.textList(k: String): List<String>? = (this[k] as? List<*>)?.map { it as? String ?: return null }?.takeIf(::validList)

    private companion object {
        val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        fun weekdayMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
        fun weekdays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
    }
}
