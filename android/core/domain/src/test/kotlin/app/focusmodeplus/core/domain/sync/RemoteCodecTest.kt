package app.focusmodeplus.core.domain.sync

import app.focusmodeplus.core.domain.repository.MissionProgressEntry
import app.focusmodeplus.core.model.DataOrigin
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.FocusSessionResult
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionLink
import app.focusmodeplus.core.model.MissionSchedule
import app.focusmodeplus.core.model.MissionStatus
import app.focusmodeplus.core.model.MissionStep
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.MissionStepStatus
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.model.MotionPreference
import app.focusmodeplus.core.model.NotificationDetailLevel
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.model.ScheduleStartMode
import app.focusmodeplus.core.model.SessionEndReason
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.model.SessionOutcome
import app.focusmodeplus.core.model.SessionStatus
import app.focusmodeplus.core.model.SessionType
import app.focusmodeplus.core.model.SyncEntityType
import app.focusmodeplus.core.model.Verification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * The codec writes exactly the documents `firestore.rules` accepts and reads server documents
 * strictly. The golden file firebase/test/fixtures/client-payloads.json is generated from here and
 * replayed by the Rules tests against the Firestore emulator: the two sides cannot drift apart.
 * Regenerate with `UPDATE_PAYLOAD_FIXTURES=true ./gradlew :core:domain:test`.
 */
class RemoteCodecTest {
    private val uid = "aliceUid0001"
    private val codec = RemoteCodec(uid)
    private val t0 = Instant.parse("2026-11-20T09:00:00Z")
    private val mutation = "0123456789abcdef0123456789abcdef"

    private val mission = run {
        val id = MissionId("mission_bio")
        Mission(
            id = id, title = "Biologie-Klausur", subject = "Biologie", type = MissionType.EXAM, deadlineText = "2026-12-10",
            deadlineDate = LocalDate.parse("2026-12-10"), preparationStartDate = LocalDate.parse("2026-11-26"), status = MissionStatus.ACTIVE,
            createdAt = t0, updatedAt = t0.plusSeconds(3600), currentStepId = MissionStepId("step_b"),
            steps = listOf(
                MissionStep(MissionStepId("step_a"), id, 0, "Zellbiologie", "Kapitel 1 lesen", 25, MissionStepStatus.COMPLETED, "Grundlagen zuerst", null, t0.plusSeconds(1800), listOf("Lehrbuch S. 40–52"), emptyList()),
                MissionStep(MissionStepId("step_b"), id, 1, "Genetik", "Aufgaben 1–5", 30, MissionStepStatus.ACTIVE, "", LocalDate.parse("2026-11-28"), null, emptyList(), listOf("Karteikarten-App")),
            ),
            origin = DataOrigin.LOCAL,
            schedule = MissionSchedule(ScheduleStartMode.TWO_WEEKS_BEFORE, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), 30),
            summary = "Erst Grundlagen, dann Übung.", estimateTotalMinutes = 55,
        )
    }
    private val progress = listOf(MissionProgressEntry(SessionId("session_m1"), mission.id, MissionStepId("step_a"), SessionOutcome.COMPLETED, 4, 25, 25, t0.plusSeconds(1800)))
    private val session = FocusSessionResult(
        sessionId = SessionId("session_m1"), type = SessionType.MISSION, status = SessionStatus.COMPLETED, outcome = SessionOutcome.COMPLETED,
        endReason = SessionEndReason.USER_REVIEWED, title = "Zellbiologie", task = "Kapitel 1 lesen", plannedMinutes = 25, actualMinutes = 25,
        elapsedMs = 1_500_000, countsAsFocus = true, startedAt = t0.plusSeconds(300), finishedAt = t0.plusSeconds(1800), localDate = LocalDate.parse("2026-11-20"),
        zoneId = "Europe/Berlin", missionLink = MissionLink(mission.id, MissionStepId("step_a")), protectionMode = ProtectionMode.PROTECTED,
        endNote = EndNote.TASK_DONE, rating = 4, origin = DataOrigin.LOCAL, verification = Verification.LOCAL_UNVERIFIED, reviewNote = "Gut verstanden",
    )

    private fun resolved(fields: Map<String, Any?>, serverMs: Long = 1_800_000_000_000L) = fields.mapValues { (_, v) -> if (v === ServerTimestamp) serverMs else v }
    private fun missionDoc() = RemoteDocument(RemotePath.Doc(RemoteCollection.MISSIONS, "mission_bio"), resolved(codec.mission(MissionSnapshot(mission, progress), 3, mutation)!!))
    private fun stepDocs() = mission.steps.map { RemoteDocument(RemotePath.Doc(RemoteCollection.STEPS, it.id.value, "mission_bio"), resolved(codec.step(it, 3, mutation, mission.updatedAt))) }

    @Test fun missionAndStepsRoundTrip() {
        val decoded = codec.decodeMission(missionDoc(), stepDocs())!!
        assertEquals(mission, decoded.snapshot.mission)
        assertEquals(progress, decoded.snapshot.progress)
        assertEquals(3L, decoded.syncVersion)
        assertEquals(listOf("step_a", "step_b"), decoded.stepIds)
    }

    @Test fun sessionRoundTripStaysUnverified() {
        val doc = codec.session(session, mutation, t0)!!
        assertFalse("verification" in doc)
        val decoded = codec.decodeSession(RemoteDocument(RemotePath.Doc(RemoteCollection.SESSIONS, "session_m1"), resolved(doc)))!!
        assertEquals(session, decoded.result)
        // Even a "verified" record arriving from somewhere is read as ordinary, unverified History.
        assertEquals(Verification.LOCAL_UNVERIFIED, decoded.result.verification)
    }

    @Test fun settingsAndTombstoneRoundTrip() {
        val s = SyncedSettings(45, MotionPreference.REDUCED, NotificationDetailLevel.GENERIC)
        val decoded = codec.decodeSettings(RemoteDocument(RemotePath.Doc(RemoteCollection.SETTINGS, "profile"), resolved(codec.settings(s, mutation, t0))))!!
        assertEquals(s, SyncedSettings(decoded.defaultQuickFocusMinutes, decoded.motion, decoded.notificationDetail))
        val t = codec.decodeTombstone(RemoteDocument(RemotePath.Doc(RemoteCollection.TOMBSTONES, "mission_mission_bio"), resolved(codec.tombstone(SyncEntityType.MISSION, "mission_bio", t0, 4, mutation))))!!
        assertEquals("mission_bio", t.entityId)
        assertEquals(4L, t.syncVersion)
    }

    @Test fun clientPayloadsNeverCarryReservedOrVerifiedFields() {
        val all = listOf(
            codec.profile(t0, t0), codec.mission(MissionSnapshot(mission, progress), 1, mutation)!!, codec.step(mission.steps[0], 1, mutation, t0),
            codec.session(session, mutation, t0)!!, codec.settings(SyncedSettings(25, MotionPreference.SYSTEM, NotificationDetailLevel.FULL_TASK), mutation, t0),
            codec.tombstone(SyncEntityType.MISSION, "mission_bio", t0, 2, mutation), codec.migration("migration0001", true, 1, 1, t0, t0, t0),
        )
        all.forEach { doc ->
            RemoteSchema.RESERVED_SERVER_FIELDS.forEach { assertFalse("$it must never be written", it in doc) }
            assertFalse(doc.keys.any { it.contains("verified", ignoreCase = true) || it.contains("league", ignoreCase = true) || it.contains("streak", ignoreCase = true) })
            assertEquals(uid, doc["ownerUid"])
        }
    }

    @Test fun serverDocumentsAreReadStrictly() {
        val good = missionDoc()
        fun with(change: (MutableMap<String, Any?>) -> Unit) = good.copy(fields = good.fields.toMutableMap().also(change))
        assertNull("foreign owner", codec.decodeMission(with { it["ownerUid"] = "bobUid000002" }, stepDocs()))
        assertNull("unknown field", codec.decodeMission(with { it["extra"] = true }, stepDocs()))
        assertNull("reserved field", codec.decodeMission(with { it["leaguePoints"] = 999L }, stepDocs()))
        assertNull("oversized title", codec.decodeMission(with { it["title"] = "x".repeat(RemoteSchema.TITLE_MAX + 1) }, stepDocs()))
        assertNull("unknown status", codec.decodeMission(with { it["status"] = "verified" }, stepDocs()))
        assertNull("wrong schema", codec.decodeMission(with { it["schemaVersion"] = 99L }, stepDocs()))
        assertNull("incomplete step set", codec.decodeMission(good, stepDocs().take(1)))
        assertNull("id mismatch", codec.decodeMission(good.copy(path = RemotePath.Doc(RemoteCollection.MISSIONS, "mission_other")), stepDocs()))
        val s = codec.session(session, mutation, t0)!!
        fun sessionWith(change: (MutableMap<String, Any?>) -> Unit) = RemoteDocument(RemotePath.Doc(RemoteCollection.SESSIONS, "session_m1"), resolved(s).toMutableMap().also(change))
        assertNull("negative duration", codec.decodeSession(sessionWith { it["actualMinutes"] = -5L }))
        assertNull("awaiting review never syncs", codec.decodeSession(sessionWith { it["endReason"] = "awaiting_user_review" }))
        assertNull("verified field", codec.decodeSession(sessionWith { it["verifiedSession"] = true }))
        assertNotNull(codec.decodeSession(sessionWith { }))
    }

    @Test fun invalidLocalDataIsNeverEncoded() {
        assertNull(codec.mission(MissionSnapshot(mission.copy(title = "x".repeat(RemoteSchema.TITLE_MAX + 1)), emptyList()), 1, mutation))
        assertNull(codec.mission(MissionSnapshot(mission, progress), 1, "short"))
        assertNull(codec.session(session.copy(endReason = SessionEndReason.AWAITING_USER_REVIEW), mutation, t0))
        assertNull(codec.session(session.copy(plannedMinutes = 0), mutation, t0))
    }

    @Test fun documentIdsFollowTheSafeIdPolicyAndFirestoreLimits() {
        assertTrue(RemoteSchema.isDocumentId("mission_" + "a".repeat(20)))
        assertTrue(RemoteSchema.isDocumentId("session-1:2.3"))
        assertFalse(RemoteSchema.isDocumentId(".."))
        assertFalse(RemoteSchema.isDocumentId("__reserved__"))
        assertFalse(RemoteSchema.isDocumentId("a/b"))
        assertFalse(RemoteSchema.isDocumentId("Biologie Klausur")) // user text is never an id
        assertFalse(RemoteSchema.isUid("../alice"))
    }

    // ------------------------------------------------------------------------------ golden payloads

    private fun json(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        ServerTimestamp -> JsonObject(mapOf("__serverTimestamp" to JsonPrimitive(true)))
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Long -> JsonPrimitive(v)
        is Int -> JsonPrimitive(v.toLong())
        is List<*> -> JsonArray(v.map(::json))
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k as String to json(x) })
        else -> error("unsupported ${v::class}")
    }

    @Test fun goldenClientPayloadsMatchTheRulesFixture() {
        val m1 = MissionSnapshot(mission, progress)
        val payloads = linkedMapOf<String, Any?>(
            "uid" to uid,
            "profile" to codec.profile(t0, t0),
            "missionCreate" to codec.mission(m1, 1, mutation),
            "missionUpdate" to codec.mission(m1.copy(mission = mission.copy(title = "Bio-Klausur", updatedAt = t0.plusSeconds(7200))), 2, "fedcba9876543210fedcba9876543210"),
            "steps" to mission.steps.map { codec.step(it, 1, mutation, mission.updatedAt) },
            "session" to codec.session(session, mutation, t0),
            "settings" to codec.settings(SyncedSettings(45, MotionPreference.REDUCED, NotificationDetailLevel.GENERIC), mutation, t0),
            "tombstone" to codec.tombstone(SyncEntityType.MISSION, "mission_gone", t0, 2, mutation),
            "migration" to codec.migration("migration0001", false, 1, 1, t0, null, t0),
            "migrationComplete" to codec.migration("migration0001", true, 1, 1, t0, t0.plusSeconds(60), t0.plusSeconds(60)),
            "reservedFields" to RemoteSchema.RESERVED_SERVER_FIELDS,
            "limits" to mapOf(
                "TITLE_MAX" to RemoteSchema.TITLE_MAX, "TEXT_MAX" to RemoteSchema.TEXT_MAX, "NOTE_MAX" to RemoteSchema.NOTE_MAX,
                "MINUTES_MAX" to RemoteSchema.MINUTES_MAX, "STEPS_MAX" to RemoteSchema.STEPS_MAX, "PROGRESS_MAX" to RemoteSchema.PROGRESS_MAX,
            ),
        )
        val text = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), json(payloads)) + "\n"
        val file = File("../../../firebase/test/fixtures/client-payloads.json")
        if (System.getenv("UPDATE_PAYLOAD_FIXTURES") == "true") {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        assertTrue("missing ${file.absolutePath}; run with UPDATE_PAYLOAD_FIXTURES=true", file.isFile)
        assertEquals("client payloads changed: update firestore.rules and regenerate the fixture", file.readText(), text)
    }
}
