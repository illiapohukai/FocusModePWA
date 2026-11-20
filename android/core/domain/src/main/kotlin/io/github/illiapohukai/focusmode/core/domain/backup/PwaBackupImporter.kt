package io.github.illiapohukai.focusmode.core.domain.backup

import io.github.illiapohukai.focusmode.core.model.DataOrigin
import io.github.illiapohukai.focusmode.core.model.EntityIds
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.Mission
import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionLink
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
import io.github.illiapohukai.focusmode.core.model.Verification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Import limits of the v17.17 security baseline (docs/security/security-baseline.md §3). */
data class ImportLimits(
    val maxBytes: Int = 5 * 1024 * 1024,
    val maxDepth: Int = 20,
    val maxNodes: Int = 250_000,
    val maxKeysPerObject: Int = 200,
    val maxArrayLength: Int = 10_000,
    val maxMissions: Int = 250,
    val maxStepsPerMission: Int = 100,
    val maxSessionsAccepted: Int = 5_000,
    val keepSessions: Int = 500,
    val titleMax: Int = 300,
    val textMax: Int = 20_000,
    val anyStringMax: Int = 50_000,
    val maxMinutes: Int = 1_440,
    val maxElapsedMs: Long = 48L * 3_600_000L,
)

data class ImportReport(
    val sourceAppVersion: String?,
    val exportedAt: String?,
    val missionsFound: Int,
    val stepsAccepted: Int,
    val sessionsFound: Int,
    val skipped: List<String>,
    val notImported: List<String>,
)

data class ImportPlan(val missions: List<Mission>, val results: List<FocusSessionResult>, val report: ImportReport)

sealed interface BackupParseResult {
    data class Ok(val plan: ImportPlan) : BackupParseResult
    data class Rejected(val message: String) : BackupParseResult
}

/**
 * Parses a Focus Mode+ PWA backup (`format: "focusmode-backup"`, exportVersion 1, or the legacy
 * root-array shape) into an import plan. Pure and side-effect free: persistence happens in one
 * Room transaction in :core:data. Imported Sessions stay LOCAL_UNVERIFIED; imported streaks,
 * scores or League values are never accepted.
 */
class PwaBackupImporter(private val limits: ImportLimits = ImportLimits()) {

    private val json = Json { isLenient = false }

    fun parse(bytes: ByteArray, zone: ZoneId, now: Instant): BackupParseResult {
        if (bytes.isEmpty()) return reject("Die Datei ist leer.")
        if (bytes.size > limits.maxBytes) return reject("Die Datei ist größer als 5 MB.")
        val text = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { return reject("Die Datei ist nicht UTF-8-kodiert.") }
        if (!depthWithin(text, limits.maxDepth)) return reject("Die Datei ist zu tief verschachtelt.")
        val root = runCatching { json.parseToJsonElement(text) }.getOrElse { return reject("Die Datei ist kein gültiges JSON.") }
        structureError(root)?.let { return reject(it) }
        if (root !is JsonObject) return reject("Unbekanntes Backup-Format.")

        val format = root.str("format")
        if (format != null && format != "focusmode-backup") return reject("Unbekanntes Backup-Format.")
        if (root.num("exportVersion")?.toInt() != 1) return reject("Diese Backup-Version wird nicht unterstützt.")
        (root["schemaVersions"] as? JsonObject)?.let { versions ->
            val tooNew = versions.entries.any { (domain, v) -> domain in KNOWN_DOMAINS && ((v as? JsonPrimitive)?.doubleOrNull ?: 0.0) > 1.0 }
            if (tooNew) return reject("Dieses Backup stammt aus einer neueren Version von Focus Mode+.")
        }
        val data = root["data"] as? JsonObject

        val skipped = mutableListOf<String>()
        val rawMissions = missionArray(data, root)
        val rawSessions = sessionArray(data, root)
        if (rawMissions == null && rawSessions == null) return reject("Das Backup enthält keine Missionen und keinen Verlauf.")

        val missions = mapMissions(rawMissions.orEmpty(), now, skipped)
        val results = mapSessions(rawSessions.orEmpty(), zone, skipped)

        val report = ImportReport(
            sourceAppVersion = root.str("appVersion") ?: root.str("applicationVersion"),
            exportedAt = root.str("exportedAt"),
            missionsFound = rawMissions?.size ?: 0,
            stepsAccepted = missions.sumOf { it.steps.size },
            sessionsFound = rawSessions?.size ?: 0,
            skipped = skipped.groupingBy { it }.eachCount().map { (msg, n) -> if (n > 1) "$msg ($n×)" else msg },
            notImported = notImported(root, data),
        )
        return BackupParseResult.Ok(ImportPlan(missions, results, report))
    }

    private fun reject(message: String) = BackupParseResult.Rejected(message)

    // ---------------------------------------------------------------- structure guards

    /** Bracket depth scan before parsing, so hostile nesting cannot exhaust the parser stack. */
    private fun depthWithin(text: String, max: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> { depth++; if (depth > max) return false }
                '}', ']' -> depth--
            }
        }
        return true
    }

    private fun structureError(root: JsonElement): String? {
        var nodes = 0
        val stack = ArrayDeque<JsonElement>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val e = stack.removeLast()
            if (++nodes > limits.maxNodes) return "Die Datei enthält zu viele Einträge."
            when (e) {
                is JsonObject -> {
                    if (e.size > limits.maxKeysPerObject) return "Die Datei enthält ein zu großes Objekt."
                    e.values.forEach(stack::addLast)
                }
                is JsonArray -> {
                    if (e.size > limits.maxArrayLength) return "Die Datei enthält eine zu lange Liste."
                    e.forEach(stack::addLast)
                }
                is JsonPrimitive -> if (e.isString && e.content.length > limits.anyStringMax) return "Die Datei enthält einen zu langen Text."
            }
        }
        return null
    }

    // ---------------------------------------------------------------- sections

    private fun missionArray(data: JsonObject?, root: JsonObject): JsonArray? =
        (data?.get("missions") as? JsonObject)?.get("missions") as? JsonArray
            ?: data?.get("missions") as? JsonArray
            ?: root["missions"] as? JsonArray

    private fun sessionArray(data: JsonObject?, root: JsonObject): JsonArray? =
        (data?.get("sessionHistory") as? JsonObject)?.get("sessions") as? JsonArray
            ?: data?.get("sessionHistory") as? JsonArray
            ?: (root["sessionHistory"] as? JsonObject)?.get("sessions") as? JsonArray
            ?: root["sessionHistory"] as? JsonArray

    private fun notImported(root: JsonObject, data: JsonObject?): List<String> = buildList {
        val d = data ?: root
        if (d["activeSession"].present() || d["lastSession"].present() || (d["recovery"] as? JsonObject)?.get("hasRecoverableSession")?.let { (it as? JsonPrimitive)?.booleanOrNull } == true)
            add("Aktive oder letzte Sitzung (wird nicht übernommen; nur abgeschlossener Verlauf zählt)")
        if (d["lastAiPlan"].present()) add("Letzter AI-Entwurf (nicht bestätigte Pläne werden nicht übernommen)")
        if (d["preferences"].present()) add("Sprache und Web-Einstellungen (Android nutzt die Systemsprache)")
        if (root["integrity"].present()) add("Integritätsangaben (Backup bleibt „lokal, nicht verifiziert“)")
        if (d.keys.any { it.contains("league", true) || it.contains("streak", true) || it.contains("score", true) })
            add("Serie, Liga oder Punkte (werden lokal neu berechnet und nie als verifiziert übernommen)")
        add("App-Auswahl der Web-App (enthält keine Android-Paketnamen)")
    }

    // ---------------------------------------------------------------- missions

    private fun mapMissions(raw: List<JsonElement>, now: Instant, skipped: MutableList<String>): List<Mission> {
        val seen = HashSet<String>()
        var active = 0
        return raw.take(limits.maxMissions).also { if (raw.size > limits.maxMissions) skipped += "Zu viele Missionen" }
            .mapNotNull { el ->
                val m = el as? JsonObject ?: return@mapNotNull null.also { skipped += "Ungültige Mission" }
                val id = m.str("id")
                if (!EntityIds.isSafe(id) || !seen.add(id!!)) return@mapNotNull null.also { skipped += "Mission mit ungültiger oder doppelter ID" }
                val title = m.str("title")?.trim()?.take(limits.titleMax)
                if (title.isNullOrEmpty()) return@mapNotNull null.also { skipped += "Mission ohne Titel" }
                var status = MissionStatus.fromWire(m.str("status")) ?: return@mapNotNull null.also { skipped += "Mission mit unbekanntem Status" }
                val missionId = MissionId(id)
                val steps = mapSteps(missionId, m["steps"] as? JsonArray ?: m["sessions"] as? JsonArray, skipped)
                val createdAt = instant(m["createdAt"]) ?: now
                var archivedAt = instant(m["archivedAt"])
                if (status == MissionStatus.ACTIVE && ++active > Mission.MAX_ACTIVE) {
                    status = MissionStatus.ARCHIVED; archivedAt = now
                    skipped += "Mehr als zwei aktive Missionen – weitere wurden archiviert"
                }
                val deadlineText = m.str("deadlineText")?.trim()?.take(limits.titleMax).orEmpty().ifEmpty { "Nicht angegeben" }
                val current = m.str("currentStepId")?.let(::MissionStepId)
                    ?.takeIf { cid -> steps.any { it.id == cid && it.status != MissionStepStatus.COMPLETED } }
                    ?: steps.firstOrNull { it.status != MissionStepStatus.COMPLETED }?.id
                Mission(
                    id = missionId,
                    title = title,
                    subject = m.str("subject")?.trim()?.take(limits.titleMax).orEmpty().ifEmpty { "Allgemein" },
                    type = MissionType.fromWire(m.str("type")),
                    deadlineText = deadlineText,
                    deadlineDate = isoDate(deadlineText),
                    preparationStartDate = null,
                    status = status,
                    createdAt = createdAt,
                    updatedAt = instant(m["updatedAt"]) ?: createdAt,
                    completedAt = instant(m["completedAt"]),
                    archivedAt = archivedAt,
                    currentStepId = if (status == MissionStatus.ACTIVE) current else null,
                    steps = steps,
                    origin = DataOrigin.IMPORTED_PWA,
                )
            }
    }

    private fun mapSteps(missionId: MissionId, raw: JsonArray?, skipped: MutableList<String>): List<MissionStep> {
        if (raw == null) return emptyList()
        val seen = HashSet<String>()
        return raw.take(limits.maxStepsPerMission).mapIndexedNotNull { index, el ->
            val s = el as? JsonObject ?: return@mapIndexedNotNull null
            val id = s.str("id")
            if (!EntityIds.isSafe(id) || !seen.add(id!!)) return@mapIndexedNotNull null.also { skipped += "Schritt mit ungültiger oder doppelter ID" }
            val title = s.str("title")?.trim()?.take(limits.titleMax)
            if (title.isNullOrEmpty()) return@mapIndexedNotNull null.also { skipped += "Schritt ohne Titel" }
            val minutes = s.num("durationMinutes")?.toInt()?.takeIf { it in 1..limits.maxMinutes }
                ?: return@mapIndexedNotNull null.also { skipped += "Schritt mit ungültiger Dauer" }
            MissionStep(
                id = MissionStepId(id),
                missionId = missionId,
                position = index,
                title = title,
                task = s.str("task")?.trim()?.take(2_000).orEmpty(),
                durationMinutes = minutes.coerceAtMost(240),
                status = MissionStepStatus.fromWire(s.str("status")) ?: MissionStepStatus.PENDING,
                reason = s.str("reason")?.trim()?.take(2_000).orEmpty(),
                scheduledDate = isoDate(s.str("scheduledDate")),
                completedAt = instant(s["completedAt"]),
            )
        }
    }

    // ---------------------------------------------------------------- sessions

    private fun mapSessions(raw: List<JsonElement>, zone: ZoneId, skipped: MutableList<String>): List<FocusSessionResult> {
        val seen = HashSet<String>()
        if (raw.size > limits.maxSessionsAccepted) skipped += "Mehr als ${limits.maxSessionsAccepted} Sitzungen – Rest ignoriert"
        return raw.take(limits.maxSessionsAccepted).mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null.also { skipped += "Ungültige Sitzung" }
            mapSession(s, zone, seen, skipped)
        }.sortedByDescending { it.finishedAt }.take(limits.keepSessions)
    }

    private fun mapSession(s: JsonObject, zone: ZoneId, seen: MutableSet<String>, skipped: MutableList<String>): FocusSessionResult? {
        val id = s.str("id")
        if (!EntityIds.isSafe(id) || !seen.add(id!!)) return null.also { skipped += "Sitzung mit ungültiger oder doppelter ID" }
        val type = SessionType.fromWire(s.str("type")) ?: return null.also { skipped += "Sitzung mit unbekanntem Typ" }
        val status = SessionStatus.fromWire(s.str("status")) ?: return null.also { skipped += "Nicht abgeschlossene Sitzung" }
        val title = s.str("title")?.trim()?.take(180)
        if (title.isNullOrEmpty()) return null.also { skipped += "Sitzung ohne Titel" }
        val planned = s.num("plannedMinutes")?.toInt()?.takeIf { it in 1..limits.maxMinutes }
            ?: return null.also { skipped += "Sitzung mit ungültiger Dauer" }
        val actual = s.num("actualMinutes")?.toInt()?.takeIf { it in 0..limits.maxMinutes }
            ?: return null.also { skipped += "Sitzung mit ungültiger Dauer" }
        val started = instant(s["startedAt"]) ?: return null.also { skipped += "Sitzung mit ungültiger Zeit" }
        val finished = instant(s["finishedAt"]) ?: return null.also { skipped += "Sitzung mit ungültiger Zeit" }
        if (finished.isBefore(started)) return null.also { skipped += "Sitzung mit ungültiger Zeit" }
        val missionId = s.str("missionId")
        val stepId = s.str("missionStepId")
        val link = when (type) {
            SessionType.QUICK -> if (missionId != null || stepId != null) return null.also { skipped += "Quick Focus mit Missionsbezug" } else null
            SessionType.MISSION -> if (!EntityIds.isSafe(missionId) || !EntityIds.isSafe(stepId)) return null.also { skipped += "Missionssitzung ohne gültigen Bezug" }
            else MissionLink(MissionId(missionId!!), MissionStepId(stepId!!))
        }
        val result = s["result"] as? JsonObject
        val explicitCount = (result?.get("countAsFocus") as? JsonPrimitive)?.booleanOrNull
        // PWA `countedFocus`.
        val counts = actual >= 1 && when (explicitCount) { false -> false; true -> true; null -> status == SessionStatus.COMPLETED }
        val outcomeWire = result?.str("outcome")
        val outcome = when (status) {
            SessionStatus.CANCELLED -> SessionOutcome.CANCELED
            SessionStatus.INTERRUPTED -> if (counts) SessionOutcome.PARTIAL else SessionOutcome.CANCELED
            SessionStatus.COMPLETED -> when (outcomeWire) {
                "completed" -> SessionOutcome.COMPLETED
                "partial" -> SessionOutcome.PARTIAL
                "aborted" -> if (counts) SessionOutcome.PARTIAL else SessionOutcome.CANCELED
                else -> when { actual >= planned -> SessionOutcome.COMPLETED; counts -> SessionOutcome.PARTIAL; else -> SessionOutcome.CANCELED }
            }
        }
        val elapsed = s.num("elapsedMs")?.toLong()?.takeIf { it in 0..limits.maxElapsedMs } ?: actual * 60_000L
        return FocusSessionResult(
            sessionId = SessionId(id),
            type = type,
            status = status,
            outcome = outcome,
            endReason = SessionEndReason.fromWire(result?.str("reason")) ?: SessionEndReason.IMPORTED,
            title = title,
            task = s.str("task")?.trim()?.take(1_200).orEmpty().ifEmpty { title },
            plannedMinutes = planned,
            actualMinutes = actual.coerceAtMost(planned),
            elapsedMs = elapsed,
            countsAsFocus = counts,
            startedAt = started,
            finishedAt = finished,
            localDate = finished.atZone(zone).toLocalDate(),
            zoneId = zone.id,
            missionLink = link,
            protectionMode = ProtectionMode.STANDARD,
            rating = result?.num("rating")?.toInt()?.takeIf { it in 1..5 },
            origin = DataOrigin.IMPORTED_PWA,
            verification = Verification.LOCAL_UNVERIFIED,
        )
    }

    // ---------------------------------------------------------------- primitives

    private fun JsonElement?.present(): Boolean = this != null && this !is JsonNull
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    /** Epoch ms or ISO-8601 between 1970 and 2100 (baseline timestamp rule). */
    private fun instant(e: JsonElement?): Instant? {
        val p = e as? JsonPrimitive ?: return null
        val value = if (p.isString) runCatching { Instant.parse(p.content) }.getOrNull() else p.doubleOrNull?.takeIf { it.isFinite() }?.let { Instant.ofEpochMilli(it.toLong()) }
        return value?.takeIf { it.toEpochMilli() in 1L..MAX_EPOCH_MS }
    }

    private fun isoDate(v: String?): LocalDate? {
        if (v == null || !ISO_DATE.matches(v)) return null
        return try { LocalDate.parse(v) } catch (_: DateTimeParseException) { null }
    }

    private companion object {
        val KNOWN_DOMAINS = setOf("bundle", "missions", "missionSteps", "sessions", "sessionHistory", "preferences", "recovery")
        val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        const val MAX_EPOCH_MS = 4_102_444_800_000L // 2100-01-01
    }
}

data class ImportPreview(
    val plan: ImportPlan,
    val newMissions: Int,
    val duplicateMissions: Int,
    val newSessions: Int,
    val duplicateSessions: Int,
    /** Active Missions that will be archived on import to keep the "max two active" rule. */
    val missionsArchivedByLimit: Int,
)

sealed interface ImportPreviewResult {
    data class Ready(val preview: ImportPreview) : ImportPreviewResult
    data class Rejected(val message: String) : ImportPreviewResult
}

data class ImportApplyResult(val missionsImported: Int, val sessionsImported: Int)

/** PWA backup migration path: preview first, then one all-or-nothing transaction. */
interface BackupImportGateway {
    suspend fun preview(bytes: ByteArray): ImportPreviewResult
    suspend fun apply(plan: ImportPlan): ImportApplyResult
}
