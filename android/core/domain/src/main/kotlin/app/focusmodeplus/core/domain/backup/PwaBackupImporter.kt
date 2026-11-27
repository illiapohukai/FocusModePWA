package app.focusmodeplus.core.domain.backup

import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.MissionReview
import app.focusmodeplus.core.model.MissionSchedule
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.model.EntityIds
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
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
import app.focusmodeplus.core.model.Verification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.DayOfWeek
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
    /** Characters of one number literal (v22): `-1.7976931348623157e+308` has 24. */
    val maxNumberLiteral: Int = 40,
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
    /** PWA `verifyBundleIntegrity`; a mismatch or malformed digest never reaches a plan. */
    val integrity: IntegrityStatus = IntegrityStatus.ABSENT,
    /** "android" for a backup written by this app, null for the web app. */
    val platform: String? = null,
    val scope: String? = null,
)

data class ImportPlan(
    val missions: List<Mission>,
    val results: List<FocusSessionResult>,
    val report: ImportReport,
    /** Applied Mission Session results (`progress.history`), keyed by Session id. */
    val progress: List<MissionProgressEntry> = emptyList(),
)

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
        // PWA v17.17: a changed or truncated file is rejected before anything is read from it.
        val integrity = BackupIntegrity.verify(root)
        if (integrity == IntegrityStatus.MISMATCH) return reject("Datei beschädigt: Die Prüfsumme stimmt nicht. Die Datei wurde nach dem Export verändert oder unvollständig kopiert.")
        if (integrity == IntegrityStatus.INVALID) return reject("Datei beschädigt: Die Prüfsumme ist ungültig.")
        val platform = root.str("platform")?.takeIf { it == "android" }
        val data = root["data"] as? JsonObject

        val skipped = mutableListOf<String>()
        val rawMissions = missionArray(data, root)
        val rawSessions = sessionArray(data, root)
        if (rawMissions == null && rawSessions == null) return reject("Das Backup enthält keine Missionen und keinen Verlauf.")

        val missions = mapMissions(rawMissions.orEmpty(), now, skipped)
        // The web app keeps at most 500 Sessions; an Android backup may hold up to the accepted maximum.
        val keep = if (platform != null) limits.maxSessionsAccepted else limits.keepSessions
        val results = mapSessions(rawSessions.orEmpty(), zone, now, skipped, keep)
        val progress = mapProgress(rawMissions.orEmpty(), missions, now)

        val report = ImportReport(
            sourceAppVersion = root.str("appVersion") ?: root.str("applicationVersion"),
            exportedAt = root.str("exportedAt"),
            missionsFound = rawMissions?.size ?: 0,
            stepsAccepted = missions.sumOf { it.steps.size },
            sessionsFound = rawSessions?.size ?: 0,
            skipped = skipped.groupingBy { it }.eachCount().map { (msg, n) -> if (n > 1) "$msg ($n×)" else msg },
            notImported = notImported(root, data),
            integrity = integrity,
            platform = platform,
            scope = root.str("scope"),
        )
        return BackupParseResult.Ok(ImportPlan(missions, results, report, progress))
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
                is JsonPrimitive -> {
                    if (e.isString && e.content.length > limits.anyStringMax) return "Die Datei enthält einen zu langen Text."
                    // v22 (V22-F05): a number literal is bounded too. JSON.stringify never writes more
                    // than ~24 characters; a megabyte-long literal made the exact-decimal integrity
                    // check run for minutes (quadratic BigDecimal parsing).
                    if (!e.isString && e.content.length > limits.maxNumberLiteral) return "Die Datei enthält eine ungültige Zahl."
                }
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
        if (d["preferences"].present()) add("Einstellungen (dieses Gerät behält seine eigenen; die Sprache bleibt Deutsch)")
        if (root["integrity"].present()) add("Integritätsangaben (die Prüfsumme erkennt nur Beschädigung; das Backup bleibt „lokal, nicht verifiziert“)")
        if (d.keys.any { it.contains("league", true) || it.contains("streak", true) || it.contains("score", true) })
            add("Serie, Liga oder Punkte (werden lokal neu berechnet und nie als verifiziert übernommen)")
        add(if (root.str("platform") == "android") "App-Auswahl und Geplanter Fokus (sind nicht im Backup enthalten)" else "App-Auswahl der Web-App (enthält keine Android-Paketnamen)")
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
                val steps = mapSteps(missionId, m["steps"] as? JsonArray ?: m["sessions"] as? JsonArray, now, skipped)
                val createdAt = instant(m["createdAt"], now) ?: now
                var archivedAt = instant(m["archivedAt"], now)
                if (status == MissionStatus.ACTIVE && ++active > Mission.MAX_ACTIVE) {
                    status = MissionStatus.ARCHIVED; archivedAt = now
                    skipped += "Mehr als zwei aktive Missionen – weitere wurden archiviert"
                }
                val deadlineText = m.str("deadlineText")?.trim()?.take(limits.titleMax).orEmpty().ifEmpty { "Nicht angegeben" }
                val current = m.str("currentStepId")?.let(::MissionStepId)
                    ?.takeIf { cid -> steps.any { it.id == cid && it.status != MissionStepStatus.COMPLETED } }
                    ?: steps.firstOrNull { it.status != MissionStepStatus.COMPLETED }?.id
                val estimate = m["estimate"] as? JsonObject
                val android = m["android"] as? JsonObject
                val archivedStep = m.str("archivedCurrentStepId")?.let(::MissionStepId)?.takeIf { aid -> steps.any { it.id == aid } }
                Mission(
                    id = missionId,
                    title = title,
                    subject = m.str("subject")?.trim()?.take(limits.titleMax).orEmpty().ifEmpty { "Allgemein" },
                    type = MissionType.fromWire(m.str("type")),
                    deadlineText = deadlineText,
                    deadlineDate = isoDate(deadlineText) ?: isoDate(android?.str("deadlineDate")),
                    preparationStartDate = isoDate(android?.str("preparationStartDate")),
                    status = status,
                    createdAt = createdAt,
                    updatedAt = instant(m["updatedAt"], now) ?: createdAt,
                    completedAt = instant(m["completedAt"], now),
                    archivedAt = archivedAt,
                    currentStepId = if (status == MissionStatus.ACTIVE) current else null,
                    steps = steps,
                    origin = DataOrigin.IMPORTED_PWA,
                    schedule = schedule(android),
                    summary = estimate?.str("explanation")?.let(::cleanText)?.take(2_000).orEmpty(),
                    estimateTotalMinutes = estimate?.num("totalMinutes")?.toInt()?.takeIf { it in 1..100_000 },
                    scheduleWarning = estimate?.str("scheduleWarning")?.let(::cleanText)?.take(500)?.ifEmpty { null },
                    archivedStepId = if (status == MissionStatus.ARCHIVED) archivedStep else null,
                )
            }
    }

    private fun mapSteps(missionId: MissionId, raw: JsonArray?, now: Instant, skipped: MutableList<String>): List<MissionStep> {
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
                completedAt = instant(s["completedAt"], now),
                requiredTools = textList(s["requiredTools"]),
                recommendedApps = textList(s["recommendedApps"]),
            )
        }
    }

    // ---------------------------------------------------------------- sessions

    private fun mapSessions(raw: List<JsonElement>, zone: ZoneId, now: Instant, skipped: MutableList<String>, keep: Int): List<FocusSessionResult> {
        val seen = HashSet<String>()
        if (raw.size > limits.maxSessionsAccepted) skipped += "Mehr als ${limits.maxSessionsAccepted} Sitzungen – Rest ignoriert"
        return raw.take(limits.maxSessionsAccepted).mapNotNull { el ->
            val s = el as? JsonObject ?: return@mapNotNull null.also { skipped += "Ungültige Sitzung" }
            mapSession(s, zone, now, seen, skipped)
        }.sortedByDescending { it.finishedAt }.take(keep)
    }

    private fun mapSession(s: JsonObject, zone: ZoneId, now: Instant, seen: MutableSet<String>, skipped: MutableList<String>): FocusSessionResult? {
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
        val started = instant(s["startedAt"], now) ?: return null.also { skipped += "Sitzung mit ungültiger Zeit" }
        val finished = instant(s["finishedAt"], now) ?: return null.also { skipped += "Sitzung mit ungültiger Zeit" }
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
        val android = s["android"] as? JsonObject
        // The local day stored with an Android backup is kept when it is consistent with the end instant.
        val storedDay = isoDate(android?.str("localDate"))?.takeIf { d -> kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(finished.atZone(java.time.ZoneOffset.UTC).toLocalDate(), d)) <= 1 }
        val storedZone = android?.str("zoneId")?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        return FocusSessionResult(
            sessionId = SessionId(id),
            type = type,
            status = status,
            outcome = outcome,
            // A review that was still open on the source device is not reopened here: the record
            // counts like any imported Session and never becomes a pending review.
            endReason = SessionEndReason.fromWire(result?.str("reason"))?.takeIf { it != SessionEndReason.AWAITING_USER_REVIEW } ?: SessionEndReason.IMPORTED,
            title = title,
            task = s.str("task")?.trim()?.take(1_200).orEmpty().ifEmpty { title },
            plannedMinutes = planned,
            actualMinutes = actual.coerceAtMost(planned),
            elapsedMs = elapsed,
            countsAsFocus = counts,
            startedAt = started,
            finishedAt = finished,
            localDate = storedDay ?: finished.atZone(zone).toLocalDate(),
            zoneId = if (storedDay != null && storedZone != null) storedZone.id else zone.id,
            missionLink = link,
            protectionMode = android?.str("protectionMode")?.let { m -> ProtectionMode.entries.firstOrNull { it.name == m && it != ProtectionMode.STRICT_MANAGED } } ?: ProtectionMode.STANDARD,
            endNote = android?.str("endNote")?.let { n -> EndNote.entries.firstOrNull { it.name == n } },
            reviewNote = if (type == SessionType.MISSION) MissionReview.cleanNote(result?.str("feedback")) else null,
            rating = result?.num("rating")?.toInt()?.takeIf { it in 1..5 },
            origin = DataOrigin.IMPORTED_PWA,
            verification = Verification.LOCAL_UNVERIFIED,
        )
    }

    // ---------------------------------------------------------------- Mission extras

    /** Android Zeitplan extension; unknown or invalid values fall back to "not set". */
    private fun schedule(android: JsonObject?): MissionSchedule {
        android ?: return MissionSchedule()
        val days = (android["preferredWeekdays"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull?.toInt()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet()
        return MissionSchedule(
            startMode = ScheduleStartMode.fromWire(android.str("startMode")),
            preferredWeekdays = days,
            preferredSessionMinutes = android.num("preferredSessionMinutes")?.toInt()?.takeIf { it in MissionSchedule.SESSION_MINUTES },
        )
    }

    /** PWA `aiList` bounds for step tools and apps: 12 items of 120 characters. */
    private fun textList(e: JsonElement?): List<String> = (e as? JsonArray).orEmpty().take(12)
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.let(::cleanText)?.take(120)?.ifEmpty { null } }.distinct()

    private fun cleanText(v: String): String = v.replace(CONTROL, " ").trim()

    /**
     * `progress.history` of the imported Missions: only entries whose Session id is safe and whose
     * step belongs to that Mission. They let an adaptation after a restore see the same history.
     */
    private fun mapProgress(raw: List<JsonElement>, missions: List<Mission>, now: Instant): List<MissionProgressEntry> {
        val byId = missions.associateBy { it.id.value }
        val seen = HashSet<String>()
        return raw.take(limits.maxMissions).flatMap { el ->
            val m = el as? JsonObject ?: return@flatMap emptyList()
            val mission = byId[m.str("id")] ?: return@flatMap emptyList()
            val history = ((m["progress"] as? JsonObject)?.get("history") as? JsonArray).orEmpty().take(limits.maxArrayLength)
            history.mapNotNull { h ->
                val e = h as? JsonObject ?: return@mapNotNull null
                val sessionId = e.str("sessionId")?.takeIf { EntityIds.isSafe(it) && seen.add(it) } ?: return@mapNotNull null
                val stepId = e.str("stepId")?.let(::MissionStepId)?.takeIf { sid -> mission.steps.any { it.id == sid } } ?: return@mapNotNull null
                MissionProgressEntry(
                    sessionId = SessionId(sessionId),
                    missionId = mission.id,
                    stepId = stepId,
                    outcome = SessionOutcome.fromWire(e.str("outcome")) ?: return@mapNotNull null,
                    rating = e.num("rating")?.toInt()?.takeIf { it in 1..5 },
                    actualMinutes = e.num("actualMinutes")?.toInt()?.takeIf { it in 0..limits.maxMinutes } ?: return@mapNotNull null,
                    plannedMinutes = e.num("plannedMinutes")?.toInt()?.takeIf { it in 1..limits.maxMinutes } ?: return@mapNotNull null,
                    appliedAt = instant(e["appliedAt"], now) ?: return@mapNotNull null,
                )
            }
        }
    }

    // ---------------------------------------------------------------- primitives

    private fun JsonElement?.present(): Boolean = this != null && this !is JsonNull
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

    /**
     * Epoch ms or ISO-8601. v22 (V22-F03): only 2000-01-01 up to one day after the import (clock
     * skew), the same lower bound as the synchronized schema. Earlier or future times are corrupt or
     * forged (a 2099 Session would sit on top of History and inflate the streak) and are not imported.
     */
    private fun instant(e: JsonElement?, now: Instant): Instant? {
        val p = e as? JsonPrimitive ?: return null
        val value = if (p.isString) runCatching { Instant.parse(p.content) }.getOrNull() else p.doubleOrNull?.takeIf { it.isFinite() }?.let { Instant.ofEpochMilli(it.toLong()) }
        // Compared as Instants: ISO text may carry years up to 1 000 000 000, whose epoch
        // milliseconds overflow a Long (V22-F04, that overflow crashed the import preview).
        val latest = minOf(MAX_INSTANT, now.plusMillis(FUTURE_SKEW_MS))
        return value?.takeIf { !it.isBefore(MIN_INSTANT) && !it.isAfter(latest) }
    }

    private fun isoDate(v: String?): LocalDate? {
        if (v == null || !ISO_DATE.matches(v)) return null
        return try { LocalDate.parse(v) } catch (_: DateTimeParseException) { null }
    }

    private companion object {
        val CONTROL = Regex("[\\u0000-\\u001F\\u007F]")
        val KNOWN_DOMAINS = setOf("bundle", "missions", "missionSteps", "sessions", "sessionHistory", "preferences", "recovery")
        val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        val MIN_INSTANT: Instant = Instant.ofEpochMilli(946_684_800_000L) // 2000-01-01 (RemoteSchema.EPOCH_MIN_MS)
        val MAX_INSTANT: Instant = Instant.ofEpochMilli(4_102_444_800_000L) // 2100-01-01
        const val FUTURE_SKEW_MS = 24L * 3_600_000L
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
