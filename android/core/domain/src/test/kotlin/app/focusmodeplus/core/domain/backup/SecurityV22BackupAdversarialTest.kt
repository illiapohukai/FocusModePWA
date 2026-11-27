package app.focusmodeplus.core.domain.backup

import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.Verification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * v22 adversarial backup tests: every hostile input is generated at test time (no large fixtures
 * are committed). The importer must never throw, never overflow the stack, reject or skip with a
 * short German message, and never trust streaks, totals, League values, app inventories or usage data.
 */
class SecurityV22BackupAdversarialTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-26T09:00:00Z")
    private val importer = PwaBackupImporter()

    private fun parse(text: String): BackupParseResult = importer.parse(text.toByteArray(), zone, now)
    private fun parse(bytes: ByteArray): BackupParseResult = importer.parse(bytes, zone, now)
    private fun rejected(text: String): String = (parse(text) as? BackupParseResult.Rejected)?.message ?: error("expected a rejection for ${text.take(120)}")
    private fun ok(text: String): ImportPlan = (parse(text) as? BackupParseResult.Ok)?.plan ?: error("expected a plan, got ${parse(text)}")

    private fun session(id: String, started: String = "1790000000000", finished: String = "1790000600000", extra: String = "") =
        """{"id":"$id","type":"quick","status":"completed","title":"Q","task":"T","plannedMinutes":25,"actualMinutes":10,"startedAt":$started,"finishedAt":$finished$extra}"""
    private fun mission(id: String, title: String = "Mathe", extra: String = "") =
        """{"id":"$id","title":"$title","status":"active","steps":[{"id":"${id}_s1","title":"A","durationMinutes":25,"status":"active"}]$extra}"""
    private fun bundle(missions: String = "", sessions: String = "", extra: String = "") =
        """{"format":"focusmode-backup","exportVersion":1$extra,"data":{"missions":{"missions":[$missions]},"sessionHistory":{"sessions":[$sessions]}}}"""

    /** A German sentence, never a stack trace, exception class or raw input echo. */
    private fun assertConciseGerman(message: String) {
        assertTrue(message, message.length in 10..220)
        assertFalse(message, message.contains("Exception") || message.contains("at app.") || message.contains("kotlinx"))
    }

    // ------------------------------------------------------------------ structure and size

    @Test fun emptyInvalidAndNonUtf8FilesAreRejected() {
        listOf("", " ", "not json", "{", "}", "[]", "null", "\"text\"", "{\"format\":\"focusmode-backup\"").forEach { input ->
            val r = parse(input)
            assertTrue("input=$input", r is BackupParseResult.Rejected)
            assertConciseGerman((r as BackupParseResult.Rejected).message)
        }
        assertTrue(parse(byteArrayOf(0xC3.toByte(), 0x28, 0x7B)) is BackupParseResult.Rejected)
    }

    @Test fun deeplyNestedJsonNeverOverflowsTheStack() {
        for (depth in listOf(21, 1_000, 200_000)) {
            assertConciseGerman(rejected("[".repeat(depth) + "]".repeat(depth)))
            assertConciseGerman(rejected("{\"a\":".repeat(depth) + "1" + "}".repeat(depth)))
        }
        // Brackets inside strings do not count as nesting.
        assertTrue(parse(bundle(missions = mission("mission_brackets", title = "[[[[[[[[[[[[[[[[[[[[[[[[[[["))) is BackupParseResult.Ok)
    }

    @Test fun excessiveCountsAndOversizedValuesAreBounded() {
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":1,"data":{"x":[${(1..10_001).joinToString(",")}]}}"""))
        assertConciseGerman(rejected(bundle(missions = mission("mission_long", title = "x".repeat(60_000)))))
        val wideObject = (1..201).joinToString(",") { "\"k$it\":1" }
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":1,"data":{$wideObject}}"""))
        // 300 Missions: 250 are read, the rest is reported; at most two stay active.
        val plan = ok(bundle(missions = (1..300).joinToString(",") { mission("mission_$it") }))
        assertEquals(250, plan.missions.size)
        assertTrue(plan.report.skipped.any { it.startsWith("Zu viele Missionen") })
        assertEquals(2, plan.missions.count { it.status == app.focusmodeplus.core.model.MissionStatus.ACTIVE })
        // 6 000 Sessions in a web backup: capped, never more than the accepted maximum.
        val many = ok(bundle(sessions = (1..6_000).joinToString(",") { session("s_$it") }))
        assertTrue(many.results.size <= ImportLimits().keepSessions)
        assertTrue(many.report.skipped.any { it.contains("Sitzungen") })
        // Oversized titles that pass the structure guard are truncated, never stored in full.
        assertEquals(300, ok(bundle(missions = mission("mission_trunc", title = "y".repeat(5_000)))).missions.single().title.length)
    }

    @Test fun aMegabyteNumberLiteralIsRejectedBeforeTheIntegrityCheck() {
        // V22-F05: exact-decimal canonicalization of a huge literal took minutes (quadratic BigDecimal).
        val digest = "0".repeat(64)
        val text = """{"format":"focusmode-backup","exportVersion":1,"integrity":{"algorithm":"SHA-256","canonicalization":"focusmode-canonical-json-v1","digest":"$digest"},"data":{"n":${"9".repeat(200_000)},"missions":[]}}"""
        assertEquals("Die Datei enthält eine ungültige Zahl.", rejected(text))
        // Ordinary JSON.stringify numbers are untouched.
        assertTrue(parse(bundle(sessions = session("s_num", extra = ",\"elapsedMs\":-1.7976931348623157e+308"))) is BackupParseResult.Ok)
    }

    // ------------------------------------------------------------------ schema and integrity

    @Test fun unknownFutureAndDowngradedSchemasAreRejected() {
        assertConciseGerman(rejected("""{"format":"evil-backup","exportVersion":1,"data":{"missions":[]}}"""))
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":2,"data":{"missions":[]}}"""))
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":0,"data":{"missions":[]}}"""))
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":"1","data":{"missions":[]}}"""))
        assertConciseGerman(rejected("""{"format":"focusmode-backup","exportVersion":1,"schemaVersions":{"sessions":99},"data":{"missions":[]}}"""))
    }

    @Test fun checksumMismatchMalformedAndMissingChecksums() {
        val valid = buildSignedBundle()
        assertTrue(parse(valid) is BackupParseResult.Ok)
        assertEquals(IntegrityStatus.VERIFIED, (parse(valid) as BackupParseResult.Ok).plan.report.integrity)
        // One changed byte in the data after signing.
        assertTrue(rejected(valid.replace("Biologie", "Biologia")).startsWith("Datei beschädigt"))
        // A malformed digest and a wrong algorithm.
        assertTrue(rejected(valid.replace(Regex("\"digest\":\"[0-9a-f]{64}\""), "\"digest\":\"zz\"")).startsWith("Datei beschädigt"))
        assertTrue(rejected(valid.replace("\"SHA-256\"", "\"MD5\"")).startsWith("Datei beschädigt"))
        // Without a checksum the file is still a valid (older) backup, marked as unchecked.
        assertEquals(IntegrityStatus.ABSENT, ok(bundle(missions = mission("mission_plain"))).report.integrity)
    }

    private fun buildSignedBundle(sessions: String = ""): String {
        val unsigned = Json.parseToJsonElement(bundle(missions = mission("mission_bio", title = "Biologie"), sessions = sessions)).jsonObject
        val digest = BackupIntegrity.digest(unsigned)
        val signed = buildJsonObject {
            unsigned.forEach { (k, v) -> put(k, v) }
            put("integrity", buildJsonObject { put("algorithm", "SHA-256"); put("canonicalization", "focusmode-canonical-json-v1"); put("digest", digest) })
        }
        return signed.toString()
    }

    // ------------------------------------------------------------------ ids and text

    @Test fun duplicateConflictingAndPathLikeIdsAreSkipped() {
        val plan = ok(
            bundle(
                missions = listOf(mission("mission_dup", title = "Erste"), mission("mission_dup", title = "Zweite"), mission("../../databases/focus"), mission("mission/../x"), mission("file:///data"), mission("a")).joinToString(","),
                sessions = listOf(session("s_dup"), session("s_dup", started = "1790000100000", finished = "1790000700000"), session("..%2F..%2Fetc"), session("s\\u0000nul")).joinToString(","),
            ),
        )
        assertEquals(listOf("mission_dup"), plan.missions.map { it.id.value })
        assertEquals("Erste", plan.missions.single().title) // the first record wins; the conflict never overwrites
        assertEquals(listOf("s_dup"), plan.results.map { it.sessionId.value })
        assertTrue(plan.report.skipped.any { it.contains("ID") })
    }

    @Test fun htmlScriptAndJavascriptTextStaysInertPlainText() {
        val title = "<script>alert(1)</script><img src=x onerror=alert(2)>"
        val plan = ok(bundle(missions = mission("mission_html", title = title), sessions = session("s_js", extra = ",\"result\":{\"feedback\":\"javascript:alert(3)\"}")))
        // Text is data: kept as typed (Compose renders it as text, there is no WebView), never executed or turned into links.
        assertEquals(title, plan.missions.single().title)
        assertTrue(plan.results.single().title == "Q")
        // Control characters are removed from free text fields.
        val ctl = ok(bundle(missions = mission("mission_ctl", extra = ",\"estimate\":{\"explanation\":\"a\\u0007b\\u001bc\"}"))).missions.single()
        assertFalse(ctl.summary.any { it.code < 0x20 })
    }

    // ------------------------------------------------------------------ time and duration

    @Test fun corruptedImpossibleAndForgedTimesNeverCrashAndAreNotImported() {
        val cases = mapOf(
            "not_a_date" to """"gestern"""",
            "negative" to "-5",
            "pre_2000" to "946684799999", // V22-F03: valid locally once, never synchronizable
            "far_future" to "4102444700000", // V22-F03: 2099, would top History and inflate the streak
            "nan_like" to "1e400",
            "huge_iso_year" to """"+1000000000-01-01T00:00:00Z"""", // V22-F04: epoch-ms overflow crashed the preview
            "min_iso_year" to """"-1000000000-01-01T00:00:00Z"""",
        )
        cases.forEach { (name, value) ->
            val r = parse(bundle(sessions = session("s_$name", started = value, finished = value)))
            val plan = (r as? BackupParseResult.Ok)?.plan
            if (plan != null) {
                assertTrue("$name must not import", plan.results.isEmpty())
                assertTrue(name, plan.report.skipped.any { it.contains("Zeit") })
            }
        }
        // A Mission with a forged future creation time is imported with the import time instead.
        val m = ok(bundle(missions = mission("mission_future", extra = ",\"createdAt\":\"2099-01-01T00:00:00Z\",\"completedAt\":\"+999999999-12-31T23:59:59Z\""))).missions.single()
        assertEquals(now, m.createdAt)
        assertEquals(null, m.completedAt)
        // Up to one day of clock skew is still accepted.
        val skew = now.plusSeconds(3_600).toEpochMilli()
        assertEquals(1, ok(bundle(sessions = session("s_skew", started = (skew - 600_000).toString(), finished = skew.toString()))).results.size)
    }

    /** V22-F04: epoch-millisecond overflow of an extreme ISO year used to escape parse() and crash the preview. */
    @Test fun anExtremeIsoYearNeverCrashesThePreview() {
        for (t in listOf("+1000000000-01-01T00:00:00Z", "-1000000000-01-01T00:00:00Z", "+999999999-12-31T23:59:59Z")) {
            val q = "\"$t\""
            val plan = ok(bundle(missions = mission("mission_y", extra = ",\"completedAt\":$q,\"updatedAt\":$q"), sessions = session("s_y", started = q, finished = q)))
            assertTrue(t, plan.results.isEmpty())
            assertEquals(null, plan.missions.single().completedAt)
        }
    }

    @Test fun impossibleAndNegativeDurationsAreSkipped() {
        val sessions = listOf(
            """{"id":"d_neg","type":"quick","status":"completed","title":"Q","plannedMinutes":-25,"actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000}""",
            """{"id":"d_huge","type":"quick","status":"completed","title":"Q","plannedMinutes":100000,"actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000}""",
            """{"id":"d_actneg","type":"quick","status":"completed","title":"Q","plannedMinutes":25,"actualMinutes":-1,"startedAt":1790000000000,"finishedAt":1790000600000}""",
            """{"id":"d_reverse","type":"quick","status":"completed","title":"Q","plannedMinutes":25,"actualMinutes":10,"startedAt":1790000600000,"finishedAt":1790000000000}""",
            """{"id":"d_str","type":"quick","status":"completed","title":"Q","plannedMinutes":"25","actualMinutes":10,"startedAt":1790000000000,"finishedAt":1790000600000}""",
            """{"id":"d_elapsed","type":"quick","status":"completed","title":"Q","plannedMinutes":25,"actualMinutes":20,"elapsedMs":999999999999,"startedAt":1790000000000,"finishedAt":1790000600000}""",
        ).joinToString(",")
        val plan = ok(bundle(sessions = sessions))
        assertEquals(listOf("d_elapsed"), plan.results.map { it.sessionId.value })
        // An impossible elapsed time is replaced by the bounded minutes, never stored as given.
        assertEquals(20 * 60_000L, plan.results.single().elapsedMs)
    }

    // ------------------------------------------------------------------ trust boundaries

    @Test fun forgedStreaksTotalsLeagueAppInventoryAndUsageDataAreNeverImported() {
        val extra = ""","streak":{"current":999,"best":999},"totalMinutes":999999,"leaguePoints":123456,"verifiedFocusMinutes":99999,
            "entitlement":"pro","adminRole":"owner","selectedApps":["com.whatsapp","com.instagram.android"],
            "usageStats":[{"packageName":"com.whatsapp","totalTimeInForeground":86400000}]"""
        val text = """{"format":"focusmode-backup","exportVersion":1,"data":{"missions":{"missions":[${mission("mission_trust")}]},"sessionHistory":{"sessions":[${session("s_trust", extra = ",\"verification\":\"server_verified\",\"leaguePoints\":5")}]}$extra}}"""
        val plan = ok(text)
        assertTrue(plan.results.all { it.verification == Verification.LOCAL_UNVERIFIED && it.origin == DataOrigin.IMPORTED_PWA })
        assertTrue(plan.missions.all { it.origin == DataOrigin.IMPORTED_PWA })
        val dump = plan.toString()
        listOf("com.whatsapp", "com.instagram", "86400000", "123456", "999999", "server_verified", "owner").forEach { assertFalse("$it leaked into the plan", dump.contains(it)) }
        assertTrue(plan.report.notImported.any { it.contains("Serie") || it.contains("Liga") })
    }

    @Test fun anAndroidSessionNeverImportsAsStrictManagedOrPendingReview() {
        val plan = ok(bundle(sessions = session("s_android", extra = ",\"android\":{\"protectionMode\":\"STRICT_MANAGED\"},\"result\":{\"reason\":\"awaiting_user_review\"}")))
        val r = plan.results.single()
        assertEquals(app.focusmodeplus.core.model.ProtectionMode.STANDARD, r.protectionMode)
        assertTrue(r.endReason != app.focusmodeplus.core.model.SessionEndReason.AWAITING_USER_REVIEW)
    }

    // ------------------------------------------------------------------ bounded generated cases

    /** 400 seeded mutations of a valid backup: the importer only ever returns a result, never throws. */
    @Test fun seededMutationsNeverThrow() {
        val base = buildSignedBundle(sessions = session("s_fuzz"))
        val alphabet = "{}[]\",:0123456789-+.eE truefalsnul\\u00ZÄ€\u0000"
        val random = Random(22)
        repeat(400) { i ->
            val chars = base.toCharArray()
            repeat(1 + random.nextInt(8)) {
                val at = random.nextInt(chars.size)
                chars[at] = alphabet[random.nextInt(alphabet.length)]
            }
            val text = String(chars).let { if (i % 5 == 0) it.substring(0, random.nextInt(it.length)) else it }
            val r = importer.parse(text.toByteArray(), zone, now)
            if (r is BackupParseResult.Rejected) assertConciseGerman(r.message)
        }
    }

    @Test fun typeConfusedFieldsAreSkippedNotCoerced() {
        val weird = listOf("null", "true", "[]", "{}", "\"\"", "1.5", "-0", "1e999")
        weird.forEach { v ->
            val text = """{"format":"focusmode-backup","exportVersion":1,"data":{"missions":{"missions":[{"id":$v,"title":$v,"status":$v,"steps":$v}]},"sessionHistory":{"sessions":[{"id":$v,"type":$v,"status":$v,"title":$v,"plannedMinutes":$v,"actualMinutes":$v,"startedAt":$v,"finishedAt":$v}]}}}"""
            when (val r = parse(text)) {
                is BackupParseResult.Ok -> { assertTrue(v, r.plan.missions.isEmpty()); assertTrue(v, r.plan.results.isEmpty()) }
                is BackupParseResult.Rejected -> assertConciseGerman(r.message)
            }
        }
        assertTrue(JsonPrimitive("x").isString)
        assertTrue((Json.parseToJsonElement(bundle()) as JsonObject).containsKey("data"))
    }
}
