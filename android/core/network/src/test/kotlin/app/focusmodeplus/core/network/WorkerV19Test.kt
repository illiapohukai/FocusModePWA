package app.focusmodeplus.core.network

import app.focusmodeplus.core.domain.ai.AdaptationHistoryEntry
import app.focusmodeplus.core.domain.ai.AdaptationRequest
import app.focusmodeplus.core.domain.ai.AdaptationResult
import app.focusmodeplus.core.domain.ai.AdaptationStepInput
import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.ai.GenerationError
import app.focusmodeplus.core.domain.ai.GenerationResult
import app.focusmodeplus.core.domain.ai.MissionGenerationRequest
import app.focusmodeplus.core.model.MissionType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** v19 Worker hardening: status mapping, stricter plan validation and the adaptation client. */
class WorkerV19Test {
    private lateinit var server: MockWebServer
    private lateinit var generation: WorkerMissionGenerationRepository
    private lateinit var adaptation: WorkerMissionAdaptationRepository
    private val request = MissionGenerationRequest("Biologie-Klausur Zellatmung", MissionType.EXAM, GenerationConfidence.UNCERTAIN)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = WorkerClientFactory.create(server.url("/").toString(), requireHttps = false)
        generation = WorkerMissionGenerationRepository(api, WorkerTimeouts(textMs = 1_500, imageMs = 1_500))
        adaptation = WorkerMissionAdaptationRepository(api, timeoutMs = 1_500)
    }

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun plan(sessions: String, extra: String = "") =
        """{"ok":true,"plan":{"goal":{"title":"Zellatmung","subject":"Biologie","type":"exam"},"sessions":[$sessions]$extra}}"""

    private fun step(i: Int, title: String = "Schritt $i", task: String = "Aufgabe $i", minutes: Number = 25, id: String = "\"s$i\"") =
        """{"id":$id,"title":"$title","task":"$task","durationMinutes":$minutes}"""

    @Test fun moreStatusCodesMapSafelyAndAreNotRetriedWhenFinal() = runBlocking {
        val cases = listOf(
            401 to GenerationError.Refused, 403 to GenerationError.Refused, 408 to GenerationError.Timeout,
            422 to GenerationError.InvalidRequest, 500 to GenerationError.Unavailable, 504 to GenerationError.Timeout, 418 to GenerationError.InvalidRequest,
        )
        cases.forEach { (code, expected) ->
            respond(code, """{"ok":false,"error":{"code":"WHATEVER","message":"internal detail"}}""")
            val f = generation.generate(request) as GenerationResult.Failure
            assertEquals("status $code", expected, f.error)
        }
        assertFalse(GenerationError.Refused.retryable)
        assertFalse(GenerationError.InvalidRequest.retryable)
        assertEquals(cases.size, server.requestCount) // one request per call, never an automatic retry
    }

    @Test fun textAndImageRequestsUseTheContract() = runBlocking {
        respond(200, plan(step(1)))
        assertTrue(generation.generate(request) is GenerationResult.Success)
        val text = server.takeRequest().body!!.utf8()
        assertFalse(text.contains("name=\"image\""))
        respond(200, plan(step(1)))
        assertTrue(generation.generate(request.copy(material = app.focusmodeplus.core.domain.ai.MissionMaterial(byteArrayOf(-1, -40, -1), "image/jpeg"))) is GenerationResult.Success)
        val image = server.takeRequest().body!!.utf8()
        assertTrue(image.contains("filename=\"material.jpg\""))
    }

    @Test fun excessiveStepsAreRejectedNotTruncated() = runBlocking {
        respond(200, plan((1..7).joinToString(",") { step(it) }))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
    }

    @Test fun duplicateStepsAreRejectedAndDuplicateOrUnsafeIdsAreReplaced() = runBlocking {
        respond(200, plan(step(1, title = "Lesen", task = "Kapitel 1") + "," + step(2, title = "lesen", task = "kapitel 1")))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
        respond(200, plan(step(1, id = "\"same\"") + "," + step(2, id = "\"same\"") + "," + step(3, id = "\"<b>\"")))
        val steps = (generation.generate(request) as GenerationResult.Success).plan.steps
        assertEquals(3, steps.map { it.id }.toSet().size)
        assertEquals("same", steps[0].id)
    }

    @Test fun negativeOrNonNumericDurationsAreRejected() = runBlocking {
        respond(200, plan(step(1, minutes = -25)))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
        respond(200, plan("""{"id":"a","title":"A","task":"B","durationMinutes":"zwanzig"}"""))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
    }

    @Test fun markupScriptAndUrlsInAnyFieldRejectThePlan() = runBlocking {
        listOf(
            step(1, title = "<script>alert(1)</script>"),
            step(1, task = "Öffne javascript:alert(1)"),
            step(1, task = "<img src=x onerror=alert(1)>"),
        ).forEach { s ->
            respond(200, plan(s))
            assertEquals(s, GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
        }
        respond(200, plan(step(1), extra = ""","estimate":{"explanation":"<a href='x'>mehr</a>"}"""))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
    }

    @Test fun explanationAndDifficultyAreKeptWhenSafe() = runBlocking {
        respond(200, plan(step(1), extra = ""","estimate":{"totalMinutes":25,"difficulty":"HARD","explanation":"Erst verstehen, dann üben.","unknownField":1},"unexpected":{"x":1}"""))
        val p = (generation.generate(request) as GenerationResult.Success).plan
        assertEquals("hard", p.difficulty)
        assertEquals("Erst verstehen, dann üben.", p.explanation)
    }

    @Test fun oversizedBodiesAreNotRead() = runBlocking {
        respond(200, plan(step(1), extra = ""","padding":"${"x".repeat(600 * 1024)}""""))
        assertEquals(GenerationError.InvalidResponse, (generation.generate(request) as GenerationResult.Failure).error)
    }

    // ---------------------------------------------------------------- adaptation

    private val adaptRequest = AdaptationRequest(
        missionId = "mission_a", missionUpdatedAt = 1_790_000_000_000, title = "Zellatmung", subject = "Biologie", deadlineText = "2026-12-10",
        currentStep = AdaptationStepInput("step_1", "Glykolyse", "Lesen", 25, "active"), plannedMinutes = 25, actualMinutes = 14,
        outcome = "partial", rating = 2, feedback = "Bilanz unklar",
        remainingSteps = listOf(AdaptationStepInput("step_1", "Glykolyse", "Lesen", 25, "active"), AdaptationStepInput("step_2", "Citratzyklus", "Üben", 30, "pending")),
        history = listOf(AdaptationHistoryEntry("session_1", "partial", 2, 14)),
    )

    @Test fun adaptationSendsOnlyThisMissionsContractFields() = runBlocking {
        respond(200, """{"ok":true,"adaptation":{"message":"Kürzere Blöcke","summary":"Bilanz zuerst","updatedSessions":[{"title":"Bilanz üben","task":"3 Aufgaben","durationMinutes":20},{"title":"Citratzyklus","task":"Lesen","durationMinutes":30}]}}""")
        val result = adaptation.adapt(adaptRequest) as AdaptationResult.Success
        assertEquals(listOf("Bilanz üben", "Citratzyklus"), result.adaptation.steps.map { it.title })
        val recorded = server.takeRequest()
        assertEquals("/adapt-mission", recorded.url.encodedPath)
        assertTrue(recorded.headers["Content-Type"]!!.startsWith("application/json"))
        val body = recorded.body!!.utf8()
        val keys = Regex("\"([A-Za-z]+)\":").findAll(body).map { it.groupValues[1] }.toSet()
        val allowed = setOf(
            "missionId", "missionUpdatedAt", "mission", "title", "subject", "deadlineText", "currentSession", "id", "task", "durationMinutes", "status",
            "plannedMinutes", "actualMinutes", "rating", "feedback", "result", "outcome", "remainingSessions", "history", "sessionId",
        )
        assertTrue("unexpected keys: ${keys - allowed}", allowed.containsAll(keys))
        listOf("package", "streak", "selected", "device").forEach { assertFalse(it, body.contains(it, ignoreCase = true)) }
    }

    @Test fun invalidAdaptationsAreRejectedAsAWhole() = runBlocking {
        listOf(
            """{"ok":true,"adaptation":{"updatedSessions":[]}}""",
            """{"ok":true,"adaptation":{"updatedSessions":[${(1..21).joinToString(",") { """{"title":"S$it","task":"T","durationMinutes":20}""" }}]}}""",
            """{"ok":true,"adaptation":{"updatedSessions":[{"title":"A","task":"B","durationMinutes":-5}]}}""",
            """{"ok":true,"adaptation":{"updatedSessions":[{"title":"","task":"B","durationMinutes":20}]}}""",
            """{"ok":true,"adaptation":{"updatedSessions":[{"title":"<b>A</b>","task":"B","durationMinutes":20}]}}""",
            """{"ok":true}""",
            "not json",
        ).forEach { body ->
            respond(200, body)
            val f = adaptation.adapt(adaptRequest) as AdaptationResult.Failure
            assertEquals(body, GenerationError.InvalidResponse, f.error)
        }
    }

    @Test fun adaptationErrorsAndTimeoutsAreSafe() = runBlocking {
        respond(429, """{"ok":false,"error":{"code":"AI_RATE_LIMITED","requestId":"req_ABCDEF"}}""")
        val limited = adaptation.adapt(adaptRequest) as AdaptationResult.Failure
        assertEquals(GenerationError.RateLimited, limited.error)
        assertEquals("req_ABCDEF", limited.serverRequestId)
        respond(503, "")
        assertEquals(GenerationError.Unavailable, (adaptation.adapt(adaptRequest) as AdaptationResult.Failure).error)
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(5, TimeUnit.SECONDS).build())
        assertEquals(GenerationError.Timeout, (adaptation.adapt(adaptRequest) as AdaptationResult.Failure).error)
    }
}
