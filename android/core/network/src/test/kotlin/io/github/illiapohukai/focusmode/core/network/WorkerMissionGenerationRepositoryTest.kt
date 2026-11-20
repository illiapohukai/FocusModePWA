package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationError
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationResult
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationSchedule
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.MissionMaterial
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class WorkerMissionGenerationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: WorkerMissionGenerationRepository

    private val request = MissionGenerationRequest("Mathe-Klausur über lineare Funktionen", MissionType.EXAM, GenerationConfidence.PARTIAL)
    private val validPlan = """
        {"ok":true,"plan":{"goal":{"title":"Mathe-Klausur","subject":"Mathematik","type":"EXAM","deadlineText":"Nicht angegeben"},
         "sessions":[{"id":"s1","title":"Grundlagen","task":"Steigung wiederholen","durationMinutes":25,"reason":"Basis"},
                     {"id":"bad id!","title":"Übung","task":"10 Aufgaben","durationMinutes":30.4}],
         "estimate":{"totalMinutes":55,"difficulty":"medium"}}}
    """.trimIndent()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = WorkerClientFactory.create(server.url("/").toString(), requireHttps = false)
        repo = WorkerMissionGenerationRepository(api, WorkerTimeouts(textMs = 1_500, imageMs = 1_500))
    }

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun successIsValidatedAndNormalized() = runBlocking {
        respond(200, validPlan)
        val plan = (repo.generate(request) as GenerationResult.Success).plan
        assertEquals("Mathe-Klausur", plan.title)
        assertEquals(MissionType.EXAM, plan.type)
        assertEquals(listOf(25, 30), plan.steps.map { it.durationMinutes })
        assertEquals("s1", plan.steps[0].id)
        assertTrue(plan.steps[1].id.startsWith("step_")) // unsafe AI id replaced
        val recorded = server.takeRequest()
        assertEquals("/create-mission", recorded.url.encodedPath)
        assertTrue(recorded.headers["Content-Type"]!!.startsWith("multipart/form-data"))
    }

    @Test fun requestCarriesOnlyWorkerContractFields() = runBlocking {
        respond(200, validPlan)
        val schedule = GenerationSchedule(LocalDate.parse("2026-11-19"), LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-20"), "custom", "Europe/Berlin", listOf(1, 3, 3, 9), 45)
        repo.generate(request.copy(schedule = schedule, material = MissionMaterial(byteArrayOf(1, 2, 3), "image/png")))
        val body = server.takeRequest().body!!.utf8()
        val fields = Regex("(?<!file)name=\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("goalText", "missionType", "confidence", "schedule", "image"), fields)
        assertTrue(body.contains("filename=\"material.png\""))
        assertTrue(body.contains("\"preferredWeekdays\":[1,3]"))
        listOf("package", "com.", "usage", "streak", "history").forEach { assertFalse("leaked '$it'", body.contains(it, ignoreCase = true)) }
    }

    @Test fun statusCodesMapToSafeErrors() = runBlocking {
        val cases = listOf(
            402 to GenerationError.QuotaRequired,
            429 to GenerationError.RateLimited,
            503 to GenerationError.Unavailable,
            400 to GenerationError.InvalidRequest,
            413 to GenerationError.FileTooLarge,
        )
        cases.forEach { (code, expected) ->
            respond(code, """{"ok":false,"error":{"code":"X","message":"Stacktrace at line 4","requestId":"req_ABC123"}}""")
            val failure = repo.generate(request) as GenerationResult.Failure
            assertEquals(expected, failure.error)
            assertEquals("req_ABC123", failure.serverRequestId)
            assertFalse(failure.error.message.contains("Stacktrace"))
        }
    }

    @Test fun malformedOrIncompleteResponsesAreRejected() = runBlocking {
        respond(200, "<html>oops</html>")
        assertEquals(GenerationError.InvalidResponse, (repo.generate(request) as GenerationResult.Failure).error)
        respond(200, """{"ok":true,"plan":{"goal":{"title":"X"},"sessions":[{"title":"A","task":"B","durationMinutes":500}]}}""")
        assertEquals(GenerationError.InvalidResponse, (repo.generate(request) as GenerationResult.Failure).error)
        respond(200, """{"ok":true,"plan":{"goal":{"title":""},"sessions":[]}}""")
        assertEquals(GenerationError.InvalidResponse, (repo.generate(request) as GenerationResult.Failure).error)
        respond(200, """{"ok":true,"plan":{"goal":{"title":7}}}""")
        assertEquals(GenerationError.InvalidResponse, (repo.generate(request) as GenerationResult.Failure).error)
    }

    @Test fun slowWorkerTimesOut() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(validPlan).headersDelay(5, TimeUnit.SECONDS).build())
        assertEquals(GenerationError.Timeout, (repo.generate(request) as GenerationResult.Failure).error)
    }

    @Test fun secondRequestWhileInFlightIsRejected() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(validPlan).headersDelay(700, TimeUnit.MILLISECONDS).build())
        val first = async { repo.generate(request) }
        delay(150)
        assertEquals(GenerationError.AlreadyInFlight, (repo.generate(request) as GenerationResult.Failure).error)
        assertTrue(first.await() is GenerationResult.Success)
    }

    @Test fun cancellationPropagatesAndReleasesTheLock() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(validPlan).headersDelay(5, TimeUnit.SECONDS).build())
        val job = async { repo.generate(request) }
        delay(200)
        job.cancel()
        try {
            job.await()
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
        respond(200, validPlan)
        assertTrue(repo.generate(request) is GenerationResult.Success)
    }

    @Test fun inputValidationHappensBeforeAnyNetworkCall() = runBlocking {
        assertEquals(GenerationError.EmptyInput, (repo.generate(request.copy(goalText = "   ")) as GenerationResult.Failure).error)
        assertEquals(GenerationError.UnsupportedFile, (repo.generate(request.copy(material = MissionMaterial(byteArrayOf(1), "image/gif"))) as GenerationResult.Failure).error)
        assertEquals(GenerationError.FileTooLarge, (repo.generate(request.copy(material = MissionMaterial(ByteArray(1_600_000), "image/jpeg"))) as GenerationResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test fun offlineIsReportedAsOffline() = runBlocking {
        server.close()
        assertEquals(GenerationError.Offline, (repo.generate(request) as GenerationResult.Failure).error)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleartextBaseUrlIsRefusedInProduction() {
        WorkerClientFactory.create("http://example.org/", requireHttps = true)
    }
}
