package app.focusmodeplus.core.domain.backup

import app.focusmodeplus.core.domain.history.HistoryQueries
import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStep
import app.focusmodeplus.core.model.MissionStepStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** PWA `EXPORT_SCOPES`: everything, or "Nur Missionen & Einstellungen" (no Session History). */
enum class BackupScope(val wire: String, val label: String) {
    FULL("full", "Vollständiges Backup"),
    MISSIONS("missions", "Nur Missionen & Einstellungen"),
}

/** Everything a backup may contain, read in one consistent snapshot by the data layer. */
data class BackupContent(
    val missions: List<Mission>,
    val progress: List<MissionProgressEntry>,
    val results: List<FocusSessionResult>,
    val preferences: AppPreferences,
)

data class BackupFile(
    val fileName: String,
    val json: String,
    val scope: BackupScope,
    val missionCount: Int,
    val sessionCount: Int,
    val digest: String,
)

/**
 * Builds a Focus Mode+ backup from Android data in the PWA's versioned envelope
 * (`format: "focusmode-backup"`, exportVersion 1, schema versions 1, SHA-256 integrity over the
 * canonical payload). The PWA importer reads it as a regular backup (unknown fields are dropped
 * there); the Android importer reads it back completely, including the `android` extension
 * objects (Zeitplan, local day, protection mode). Pure and side-effect free.
 *
 * Deliberately not exported: a running Session (device-bound, like the PWA's missions-only
 * scope), selected apps (package names stay on this device), Geplanter Fokus, reminder state,
 * distraction counts, the AI draft and any streak or score (always recomputed from History).
 * The file is a local data export: it proves nothing about honest progress or streaks.
 */
class AndroidBackupExporter(private val appVersion: String) {

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(content: BackupContent, scope: BackupScope, now: Instant, zone: ZoneId): BackupFile {
        val missions = content.missions.sortedWith(compareBy<Mission> { it.createdAt }.thenBy { it.id.value })
        val sessions = if (scope == BackupScope.FULL) HistoryQueries.ordered(content.results) else emptyList()
        val progressByMission = content.progress.groupBy { it.missionId }
        val active = missions.filter { it.status == MissionStatus.ACTIVE }
        val selected = content.preferences.lastSelectedMissionId?.takeIf { id -> active.any { it.id.value == id } } ?: active.firstOrNull()?.id?.value

        val data = buildJsonObject {
            putJsonObject("missions") {
                put("schemaVersion", 1)
                put("stepSchemaVersion", 1)
                put("missions", JsonArray(missions.map { mission(it, progressByMission[it.id].orEmpty()) }))
                put("selectedMissionId", selected)
                put("primaryMissionId", selected)
                put("updatedAt", now.toEpochMilli())
            }
            put("selectedMissionId", selected)
            put("primaryMissionId", selected)
            putJsonObject("sessionHistory") {
                put("schemaVersion", 1)
                put("sessions", JsonArray(sessions.map(::session)))
            }
            put("lastSession", JsonNull)
            put("activeSession", JsonNull)
            putJsonObject("recovery") {
                put("schemaVersion", 1)
                put("hasRecoverableSession", false)
                put("activeSessionId", JsonNull)
                put("lifecycleState", JsonNull)
            }
            put("preferences", preferences(content.preferences))
            put("lastAiPlan", JsonNull)
        }
        val unsigned = buildJsonObject {
            put("exportVersion", 1)
            put("applicationVersion", APPLICATION_VERSION)
            put("exportedAt", now.toString())
            put("timestamp", now.toEpochMilli())
            put("reason", "user_export")
            putJsonObject("schemaVersions") { SCHEMA_DOMAINS.forEach { put(it, 1) } }
            put("data", data)
            put("format", FORMAT)
            put("appVersion", "Android $appVersion")
            put("platform", "android")
            put("scope", scope.wire)
            put("trust", "local_backup_unverified")
        }
        val digest = BackupIntegrity.digest(unsigned)
        val bundle = JsonObject(
            unsigned.toMutableMap().apply {
                put("integrity", buildJsonObject {
                    put("missionCount", missions.size)
                    put("activeMissionCount", active.size)
                    put("sessionCount", sessions.size)
                    put("hasActiveSession", false)
                    put("algorithm", BackupIntegrity.ALGORITHM)
                    put("canonicalization", BackupIntegrity.CANONICALIZATION)
                    putJsonArray("covers") { BackupIntegrity.COVERS.forEach { add(JsonPrimitive(it)) } }
                    put("digest", digest)
                    put("status", "present")
                })
            },
        )
        return BackupFile(
            fileName = fileName(now.atZone(zone).toLocalDate(), scope),
            json = pretty.encodeToString(JsonElement.serializer(), bundle) + "\n",
            scope = scope,
            missionCount = missions.size,
            sessionCount = sessions.size,
            digest = digest,
        )
    }

    private fun mission(m: Mission, progress: List<MissionProgressEntry>): JsonObject = buildJsonObject {
        put("id", m.id.value)
        put("missionSchemaVersion", 1)
        put("title", m.title)
        put("subject", m.subject)
        put("type", m.type.wire)
        put("deadlineText", m.deadlineText)
        put("status", m.status.wire)
        put("createdAt", m.createdAt.toEpochMilli())
        put("updatedAt", m.updatedAt.toEpochMilli())
        putMillis("completedAt", m.completedAt)
        putMillis("archivedAt", m.archivedAt)
        put("currentStepId", m.currentStepId?.value)
        put("archivedCurrentStepId", m.archivedStepId?.value)
        putJsonObject("estimate") {
            put("totalMinutes", m.estimateTotalMinutes)
            put("explanation", m.summary)
            put("scheduleWarning", m.scheduleWarning)
        }
        put("steps", JsonArray(m.steps.sortedBy { it.position }.map { step(it, m) }))
        putJsonObject("progress") {
            putJsonArray("completedStepIds") { m.steps.filter { it.status == MissionStepStatus.COMPLETED }.sortedBy { it.position }.forEach { add(JsonPrimitive(it.id.value)) } }
            put("history", JsonArray(progress.sortedWith(compareBy<MissionProgressEntry> { it.appliedAt }.thenBy { it.sessionId.value }).map(::progressEntry)))
            put("initialStepCount", m.steps.size)
        }
        putJsonObject("android") {
            put("deadlineDate", m.deadlineDate?.toString())
            put("preparationStartDate", m.preparationStartDate?.toString())
            put("startMode", m.schedule.startMode?.wire)
            putJsonArray("preferredWeekdays") { m.schedule.preferredWeekdays.map { it.value }.sorted().forEach { add(JsonPrimitive(it)) } }
            put("preferredSessionMinutes", m.schedule.preferredSessionMinutes)
            put("origin", m.origin.name)
        }
    }

    private fun step(s: MissionStep, m: Mission): JsonObject = buildJsonObject {
        put("id", s.id.value)
        put("missionId", m.id.value)
        put("stepSchemaVersion", 1)
        put("title", s.title)
        put("task", s.task)
        put("durationMinutes", s.durationMinutes)
        put("status", s.status.wire)
        put("reason", s.reason)
        putJsonArray("requiredTools") { s.requiredTools.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("recommendedApps") { s.recommendedApps.forEach { add(JsonPrimitive(it)) } }
        putMillis("completedAt", s.completedAt)
        put("scheduledDate", s.scheduledDate?.toString())
    }

    private fun progressEntry(p: MissionProgressEntry): JsonObject = buildJsonObject {
        put("sessionId", p.sessionId.value)
        put("stepId", p.stepId.value)
        put("outcome", p.outcome.wire)
        put("rating", p.rating)
        put("actualMinutes", p.actualMinutes)
        put("plannedMinutes", p.plannedMinutes)
        put("appliedAt", p.appliedAt.toEpochMilli())
    }

    /** PWA Session record (`session-model.js`), terminal status, epoch-millisecond timestamps. */
    private fun session(r: FocusSessionResult): JsonObject = buildJsonObject {
        put("id", r.sessionId.value)
        put("type", r.type.wire)
        put("status", r.status.wire)
        put("title", r.title)
        put("task", r.task)
        put("plannedMinutes", r.plannedMinutes)
        put("actualMinutes", r.actualMinutes)
        put("elapsedMs", r.elapsedMs)
        // A wall-clock change during a Session can put the start after the end; the PWA contract needs start ≤ end.
        put("startedAt", minOf(r.startedAt, r.finishedAt).toEpochMilli())
        put("finishedAt", r.finishedAt.toEpochMilli())
        put("missionId", r.missionLink?.missionId?.value)
        put("missionStepId", r.missionLink?.stepId?.value)
        putJsonObject("result") {
            put("outcome", r.outcome.wire)
            put("countAsFocus", r.countsAsFocus)
            put("reason", r.endReason.wire)
            put("rating", r.rating)
            put("feedback", r.reviewNote)
        }
        putJsonObject("android") {
            put("localDate", r.localDate.toString())
            put("zoneId", r.zoneId)
            put("protectionMode", r.protectionMode.name)
            put("endNote", r.endNote?.name)
            put("origin", r.origin.name)
        }
    }

    /** The PWA preferences section plus this device's Focus settings (informational on import). */
    private fun preferences(p: AppPreferences): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("language", "de")
        put("pwaInstallHelpDismissed", JsonNull)
        putJsonObject("android") {
            put("defaultQuickFocusMinutes", p.defaultQuickFocusMinutes)
            put("defaultProtectionMode", p.defaultProtectionMode.name)
            put("motion", p.motion.name)
            put("notificationsEnabled", p.notificationsEnabled)
            put("notificationDetail", p.notificationDetail.name)
            put("missionRemindersEnabled", p.missionRemindersEnabled)
            put("dailyPlanningEnabled", p.dailyPlanningEnabled)
            put("dailyPlanningTime", p.dailyPlanningTime.toString())
            put("usageMonitoringEnabled", p.usageMonitoringEnabled)
            put("doNotDisturbDuringFocus", p.doNotDisturbDuringFocus)
        }
    }

    private fun JsonObjectBuilder.putMillis(key: String, at: Instant?) {
        put(key, at?.toEpochMilli())
    }

    companion object {
        const val FORMAT = "focusmode-backup"
        const val APPLICATION_VERSION = "Focus Mode+ Android"
        private val SCHEMA_DOMAINS = listOf("bundle", "missions", "missionSteps", "sessions", "sessionHistory", "preferences", "recovery")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /** PWA `formatExportFilename`: a fixed prefix and the date, never user content. */
        fun fileName(date: LocalDate, scope: BackupScope): String =
            "focus-mode-backup-" + (if (scope == BackupScope.MISSIONS) "missionen-" else "") + DATE.format(date) + ".json"
    }
}
