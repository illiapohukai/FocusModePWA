package app.focusmodeplus.core.domain.backup

import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.model.Verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PwaBackupImporterTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-19T09:00:00Z")
    private val importer = PwaBackupImporter()
    private fun resource(name: String) = javaClass.getResourceAsStream("/backup/$name")!!.readBytes()
    private fun ok(bytes: ByteArray) = (importer.parse(bytes, zone, now) as BackupParseResult.Ok).plan
    private fun rejected(json: String) = importer.parse(json.toByteArray(), zone, now) as BackupParseResult.Rejected

    @Test fun importsTheRealPwaV1716Export() {
        val plan = ok(resource("export-v17.16-full.json"))
        assertEquals(4, plan.missions.size)
        assertEquals(listOf(MissionStatus.ACTIVE, MissionStatus.ACTIVE, MissionStatus.COMPLETED, MissionStatus.ARCHIVED), plan.missions.map { it.status })
        assertTrue(plan.missions.all { it.origin == DataOrigin.IMPORTED_PWA })
        assertEquals(12, plan.results.size)
        assertTrue(plan.results.all { it.verification == Verification.LOCAL_UNVERIFIED && it.origin == DataOrigin.IMPORTED_PWA })
        val cancelled = plan.results.single { it.status == SessionStatus.CANCELLED }
        assertEquals(SessionOutcome.CANCELED, cancelled.outcome)
        assertTrue(!cancelled.countsAsFocus)
        assertTrue(plan.results.filter { it.type == SessionType.QUICK }.all { it.missionLink == null && it.outcome == SessionOutcome.COMPLETED })
        assertEquals(plan.results.sortedByDescending { it.finishedAt }, plan.results)
        assertTrue(plan.report.notImported.any { it.contains("App-Auswahl") })
        assertTrue(plan.report.notImported.any { it.contains("Integrität") })
    }

    @Test fun importsTheLegacyRootArrayShape() {
        val plan = ok(resource("export-legacy-root-arrays.json"))
        assertEquals(1, plan.missions.size)
        assertEquals(2, plan.results.size)
    }

    @Test fun rejectsMalformedAndHostileFiles() {
        rejected("not json")
        rejected("""{"format":"other","exportVersion":1,"data":{}}""")
        rejected("""{"format":"focusmode-backup","exportVersion":2,"data":{}}""")
        rejected("""{"format":"focusmode-backup","exportVersion":1,"schemaVersions":{"missions":2},"data":{"missions":[]}}""")
        rejected("""{"format":"focusmode-backup","exportVersion":1,"data":{}}""")
        rejected("[".repeat(50) + "]".repeat(50))
        assertTrue(importer.parse(ByteArray(6 * 1024 * 1024) { ' '.code.toByte() }, zone, now) is BackupParseResult.Rejected)
        rejected("""{"exportVersion":1,"missions":[],"x":"${"a".repeat(60_000)}"}""")
    }

    @Test fun skipsInvalidRecordsAndNeverImportsStreakOrLeague() {
        val json = """
        {"format":"focusmode-backup","exportVersion":1,"data":{
          "streak":{"current":99},"leagueScore":12345,
          "missions":{"missions":[
            {"id":"bad id!","title":"X","status":"active","steps":[]},
            {"id":"mission_ok","title":"Mathe","status":"active","steps":[{"id":"s1x","title":"A","durationMinutes":25,"status":"active"},{"id":"s1x","title":"Dup","durationMinutes":25,"status":"pending"}]},
            {"id":"mission_2","title":"B","status":"active","steps":[]},
            {"id":"mission_3","title":"C","status":"active","steps":[]}
          ]},
          "sessionHistory":{"sessions":[
            {"id":"q1x","type":"quick","status":"completed","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000,"missionId":"m","missionStepId":"s"},
            {"id":"q2x","type":"quick","status":"completed","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000},
            {"id":"q3x","type":"quick","status":"running","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000},
            {"id":"q4x","type":"quick","status":"completed","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":10,"startedAt":1790000600000,"finishedAt":1790000000000}
          ]}}}
        """.trimIndent()
        val plan = ok(json.toByteArray())
        assertEquals(listOf("mission_ok", "mission_2", "mission_3"), plan.missions.map { it.id.value })
        assertEquals(1, plan.missions.first().steps.size)
        assertEquals(MissionStatus.ARCHIVED, plan.missions.last().status) // max two active
        assertEquals(listOf("q2x"), plan.results.map { it.sessionId.value })
        assertEquals(SessionOutcome.PARTIAL, plan.results.single().outcome)
        assertTrue(plan.report.notImported.any { it.contains("Serie") })
        assertTrue(plan.report.skipped.isNotEmpty())
    }
}
