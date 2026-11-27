package app.focusmodeplus.core.domain.backup

import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.AppPreferences
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionSchedule
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BackupExportAndIntegrityTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-26T09:00:00Z")
    private val importer = PwaBackupImporter()
    private fun resource(name: String) = javaClass.getResourceAsStream("/backup/$name")!!.readBytes()
    private fun parse(bytes: ByteArray) = importer.parse(bytes, zone, now)

    // ---------------------------------------------------------------- PWA digest compatibility

    @Test fun verifiesTheDigestWrittenByThePwaExporter() {
        // export-v17.17-digest.json was produced by the PWA's own exporter (Web Crypto SHA-256).
        val root = Json.parseToJsonElement(resource("export-v17.17-digest.json").decodeToString()).jsonObject
        assertEquals(IntegrityStatus.VERIFIED, BackupIntegrity.verify(root))
        val plan = (parse(resource("export-v17.17-digest.json")) as BackupParseResult.Ok).plan
        assertEquals(IntegrityStatus.VERIFIED, plan.report.integrity)
        assertEquals(4, plan.missions.size)
        assertEquals(12, plan.results.size)
        assertNull("web backups are not Android backups", plan.report.platform)
    }

    @Test fun olderBackupWithoutDigestIsAbsentNotVerified() {
        val plan = (parse(resource("export-v17.16-full.json")) as BackupParseResult.Ok).plan
        assertEquals(IntegrityStatus.ABSENT, plan.report.integrity)
    }

    @Test fun tamperedOrMalformedDigestIsRejectedLikeThePwa() {
        val text = resource("export-v17.17-digest.json").decodeToString()
        val tampered = text.replaceFirst("\"title\": \"", "\"title\": \"X")
        val r1 = parse(tampered.toByteArray()) as BackupParseResult.Rejected
        assertTrue(r1.message.startsWith("Datei beschädigt: Die Prüfsumme stimmt nicht"))
        val digest = Regex("\"digest\": \"([0-9a-f]{64})\"").find(text)!!.groupValues[1]
        val truncated = text.replace(digest, digest.take(20))
        assertEquals("Datei beschädigt: Die Prüfsumme ist ungültig.", (parse(truncated.toByteArray()) as BackupParseResult.Rejected).message)
        // Whitespace does not matter: the digest covers the canonical form.
        val compact = Json.parseToJsonElement(text).toString()
        assertEquals(IntegrityStatus.VERIFIED, (parse(compact.toByteArray()) as BackupParseResult.Ok).plan.report.integrity)
    }

    @Test fun canonicalJsonMatchesJsonStringify() {
        assertEquals("1795685023098", BackupIntegrity.number("1795685023098"))
        assertEquals("0.8", BackupIntegrity.number("0.80"))
        assertEquals("100", BackupIntegrity.number("1e2"))
        assertEquals("1e+21", BackupIntegrity.number("1e21"))
        assertEquals("1e-7", BackupIntegrity.number("0.0000001"))
        assertEquals("0.000001", BackupIntegrity.number("1e-6"))
        assertEquals("-2.5", BackupIntegrity.number("-2.50"))
        assertEquals("0", BackupIntegrity.number("-0"))
        val obj = Json.parseToJsonElement("""{"b":[1,"ä\n\"x\"",null,true],"a":{"z":1,"Z":2},"\u0001":"\u001f"}""")
        assertEquals("""{"\u0001":"\u001f","a":{"Z":2,"z":1},"b":[1,"ä\n\"x\"",null,true]}""", BackupIntegrity.canonical(obj))
    }

    // ---------------------------------------------------------------- Android export

    private fun content(): BackupContent {
        val base = Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3, completed = 1, deadline = LocalDate.parse("2026-12-10"))
        val mission = base.copy(
            preparationStartDate = LocalDate.parse("2026-11-20"),
            schedule = MissionSchedule(ScheduleStartMode.ONE_WEEK_BEFORE, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 30),
            summary = "Erst Grundlagen, dann Übungen.",
            estimateTotalMinutes = 75,
            steps = base.steps.map { if (it.position == 1) it.copy(requiredTools = listOf("Lehrbuch", "Karteikarten"), recommendedApps = listOf("Anki")) else it },
        )
        val archived = Fixtures.mission("mission_old", title = "Referat", steps = 2, status = MissionStatus.ARCHIVED).let { it.copy(archivedStepId = it.steps[0].id, currentStepId = null) }
        val quick = Fixtures.result("s_quick", LocalDate.parse("2026-11-25"), 25).copy(protectionMode = ProtectionMode.PROTECTED, endNote = EndNote.TASK_DONE)
        val missionSession = Fixtures.result("s_mission", LocalDate.parse("2026-11-24"), 25).copy(
            type = SessionType.MISSION, missionLink = MissionLink(mission.id, mission.steps[0].id), rating = 4, reviewNote = "Formeln fehlen noch", endReason = SessionEndReason.USER_REVIEWED,
        )
        val progress = MissionProgressEntry(missionSession.sessionId, mission.id, mission.steps[0].id, SessionOutcome.COMPLETED, 4, 25, 25, Instant.parse("2026-11-24T10:05:00Z"))
        return BackupContent(listOf(mission, archived), listOf(progress), listOf(quick, missionSession), AppPreferences(lastSelectedMissionId = "mission_bio"))
    }

    @Test fun androidExportRoundTripsEveryExportedField() {
        val c = content()
        val file = AndroidBackupExporter("20.0.0").export(c, BackupScope.FULL, now, zone)
        // Kept as a build output for the PWA-side compatibility check (tests/security harness); never committed.
        java.io.File("build/backup-samples").apply { mkdirs() }.resolve("android-export-full.json").writeText(file.json)
        assertEquals("focus-mode-backup-2026-11-26.json", file.fileName)
        assertEquals(2, file.missionCount)
        assertEquals(2, file.sessionCount)
        val plan = (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan
        assertEquals(IntegrityStatus.VERIFIED, plan.report.integrity)
        assertEquals("android", plan.report.platform)
        assertEquals("full", plan.report.scope)

        val m = plan.missions.single { it.id.value == "mission_bio" }
        val source = c.missions.first()
        assertEquals(source.title, m.title)
        assertEquals(source.deadlineDate, m.deadlineDate)
        assertEquals(source.preparationStartDate, m.preparationStartDate)
        assertEquals(source.schedule, m.schedule)
        assertEquals(source.summary, m.summary)
        assertEquals(source.estimateTotalMinutes, m.estimateTotalMinutes)
        assertEquals(source.currentStepId, m.currentStepId)
        assertEquals(source.steps.map { it.id to it.status }, m.steps.map { it.id to it.status })
        assertEquals(listOf("Lehrbuch", "Karteikarten"), m.steps[1].requiredTools)
        assertEquals(listOf("Anki"), m.steps[1].recommendedApps)
        val old = plan.missions.single { it.id.value == "mission_old" }
        assertEquals(MissionStatus.ARCHIVED, old.status)
        assertEquals(c.missions[1].archivedStepId, old.archivedStepId)

        val q = plan.results.single { it.sessionId.value == "s_quick" }
        assertEquals(ProtectionMode.PROTECTED, q.protectionMode)
        assertEquals(EndNote.TASK_DONE, q.endNote)
        assertEquals(LocalDate.parse("2026-11-25"), q.localDate)
        val ms = plan.results.single { it.sessionId.value == "s_mission" }
        assertEquals(4, ms.rating)
        assertEquals("Formeln fehlen noch", ms.reviewNote)
        assertEquals(SessionOutcome.COMPLETED, ms.outcome)
        assertEquals(c.results[1].missionLink, ms.missionLink)
        assertEquals(c.progress, plan.progress)
    }

    @Test fun exportUsesThePwaEnvelopeAndEpochMilliseconds() {
        val file = AndroidBackupExporter("20.0.0").export(content(), BackupScope.FULL, now, zone)
        val root = Json.parseToJsonElement(file.json).jsonObject
        assertEquals("focusmode-backup", root["format"]!!.jsonPrimitive.content)
        assertEquals(1, root["exportVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals("local_backup_unverified", root["trust"]!!.jsonPrimitive.content)
        val integrity = root["integrity"]!!.jsonObject
        assertEquals("SHA-256", integrity["algorithm"]!!.jsonPrimitive.content)
        assertEquals(file.digest, integrity["digest"]!!.jsonPrimitive.content)
        val data = root["data"]!!.jsonObject
        assertTrue(data["preferences"] is JsonObject)
        assertEquals("mission_bio", data["missions"]!!.jsonObject["primaryMissionId"]!!.jsonPrimitive.content)
        val session = data["sessionHistory"]!!.jsonObject["sessions"]!!.jsonArray.first().jsonObject
        assertTrue("PWA validator needs numeric timestamps", (session["startedAt"] as JsonPrimitive).let { !it.isString && it.long > 0 })
        assertEquals("completed", session["status"]!!.jsonPrimitive.content)
        assertFalse(file.json.contains("packageName"))
    }

    @Test fun missionsScopeLeavesHistoryOutAndUsesItsOwnFileName() {
        val file = AndroidBackupExporter("20.0.0").export(content(), BackupScope.MISSIONS, now, zone)
        assertEquals("focus-mode-backup-missionen-2026-11-26.json", file.fileName)
        assertEquals(0, file.sessionCount)
        val plan = (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan
        assertTrue(plan.results.isEmpty())
        assertEquals(2, plan.missions.size)
        assertEquals("missions", plan.report.scope)
        val sessions = Json.parseToJsonElement(file.json).jsonObject["data"]!!.jsonObject["sessionHistory"]!!.jsonObject["sessions"] as JsonArray
        assertTrue(sessions.isEmpty())
    }

    @Test fun fileNamesNeverContainUserContent() {
        assertEquals("focus-mode-backup-2026-01-05.json", AndroidBackupExporter.fileName(LocalDate.parse("2026-01-05"), BackupScope.FULL))
        assertTrue(Regex("^focus-mode-backup-(missionen-)?\\d{4}-\\d{2}-\\d{2}\\.json$").matches(AndroidBackupExporter.fileName(LocalDate.parse("2026-12-31"), BackupScope.MISSIONS)))
    }

    @Test fun exportedStartNeverFollowsTheEnd() {
        val r = Fixtures.result("s_clock", LocalDate.parse("2026-11-25"), 25)
        val skewed = r.copy(startedAt = r.finishedAt.plusSeconds(600)) // wall clock moved back during the Session
        val c = BackupContent(emptyList(), emptyList(), listOf(skewed), AppPreferences())
        val file = AndroidBackupExporter("20.0.0").export(c, BackupScope.FULL, now, zone)
        val plan = (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan
        assertEquals(1, plan.results.size)
    }

    @Test fun androidBackupsKeepMoreThanTheWebAppsFiveHundredSessions() {
        val many = (0 until 650).map { i -> Fixtures.result("s_$i", LocalDate.parse("2026-11-01"), 25, finishedAt = Instant.parse("2026-11-01T08:00:00Z").plusSeconds(i * 60L)) }
        val file = AndroidBackupExporter("20.0.0").export(BackupContent(emptyList(), emptyList(), many, AppPreferences()), BackupScope.FULL, now, zone)
        assertEquals(650, (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan.results.size)
    }

    @Test fun anOpenReviewIsNeverReopenedOnAnotherDevice() {
        val pending = Fixtures.result("s_pending", LocalDate.parse("2026-11-25"), 25).copy(endReason = SessionEndReason.AWAITING_USER_REVIEW)
        val file = AndroidBackupExporter("20.0.0").export(BackupContent(emptyList(), emptyList(), listOf(pending), AppPreferences()), BackupScope.FULL, now, zone)
        assertEquals(SessionEndReason.IMPORTED, (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan.results.single().endReason)
    }

    @Test fun progressEntriesForUnknownStepsAreDropped() {
        val c = content()
        val bad = c.progress.single().copy(sessionId = SessionId("s_other"), stepId = app.focusmodeplus.core.model.MissionStepId("not_a_step"))
        val file = AndroidBackupExporter("20.0.0").export(c.copy(progress = c.progress + bad), BackupScope.FULL, now, zone)
        assertEquals(c.progress, (parse(file.json.toByteArray()) as BackupParseResult.Ok).plan.progress)
    }
}
