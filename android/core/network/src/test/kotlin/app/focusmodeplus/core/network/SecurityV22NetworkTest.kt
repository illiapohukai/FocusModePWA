package app.focusmodeplus.core.network

import app.focusmodeplus.core.domain.ai.GenerationConfidence
import app.focusmodeplus.core.domain.ai.GenerationResult
import app.focusmodeplus.core.domain.ai.MissionGenerationRequest
import app.focusmodeplus.core.model.MissionType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * v22 network-boundary tests of the one Worker client (local MockWebServer only; the real Worker
 * and Gemini are never contacted). HTTPS is enforced by the factory and the network security
 * config; here the transport policy is checked over loopback with `requireHttps = false`.
 */
class SecurityV22NetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: WorkerMissionGenerationRepository
    private val request = MissionGenerationRequest("Mathe-Klausur über lineare Funktionen", MissionType.EXAM, GenerationConfidence.PARTIAL)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        repo = WorkerMissionGenerationRepository(WorkerClientFactory.create(server.url("/").toString(), requireHttps = false), WorkerTimeouts(2_000, 2_000))
    }

    @After fun tearDown() = server.close()

    @Test fun onlyAnHttpsWorkerBaseUrlIsAccepted() {
        for (bad in listOf("http://focusmodedemoai.example.workers.dev/", "ftp://example.invalid/", "https://example.invalid", "file:///data/")) {
            try {
                WorkerClientFactory.create(bad)
                fail("accepted $bad")
            } catch (_: IllegalArgumentException) { }
        }
        WorkerClientFactory.create("https://focusmodedemoai.example.workers.dev/")
    }

    @Test fun redirectsAreNeverFollowedNotEvenToCleartext() = runBlocking {
        for (code in listOf(301, 302, 307, 308)) {
            server.enqueue(MockResponse.Builder().code(code).addHeader("Location", "http://127.0.0.1:1/steal").body("{}").build())
            val r = repo.generate(request)
            assertTrue("$code: $r", r is GenerationResult.Failure)
        }
        // Exactly one request per call: the POST body never travelled to the redirect target.
        assertEquals(4, server.requestCount)
    }

    @Test fun aFailedGenerationIsNotRetriedAutomatically() = runBlocking {
        for (code in listOf(500, 502, 503, 429)) {
            server.enqueue(MockResponse.Builder().code(code).body("""{"ok":false,"error":{"code":"upstream"}}""").build())
            assertTrue(repo.generate(request) is GenerationResult.Failure)
        }
        // One paid AI request per user action; a retry is always a new, explicit user action.
        assertEquals(4, server.requestCount)
    }

    @Test fun requestsCarryNoCredentialsCookiesOrQueryParameters() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Set-Cookie", "session=attacker").body("""{"ok":false}""").build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":false}""").build())
        repo.generate(request)
        repo.generate(request)
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("POST", second.method)
        assertEquals("/create-mission", second.url.encodedPath)
        assertNull(second.url.query)
        assertNull(second.headers["Authorization"])
        assertNull(second.headers["Cookie"]) // no cookie jar: a Set-Cookie is never replayed
        assertNull(second.headers["X-Goog-Api-Key"])
        val body = second.body!!.utf8()
        // Only the Worker contract: no account, device, History or app inventory data.
        for (forbidden in listOf("uid", "email", "ownerUid", "packageName", "installId", "history", "token")) {
            assertFalse("$forbidden in request", body.contains("name=\"$forbidden\""))
        }
        assertTrue(body.contains("name=\"goalText\""))
    }
}
