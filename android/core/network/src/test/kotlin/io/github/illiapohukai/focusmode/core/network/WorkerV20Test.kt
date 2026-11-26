package io.github.illiapohukai.focusmode.core.network

import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationHistoryEntry
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationRequest
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationResult
import io.github.illiapohukai.focusmode.core.domain.ai.AdaptationStepInput
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationConfidence
import io.github.illiapohukai.focusmode.core.domain.ai.GenerationResult
import io.github.illiapohukai.focusmode.core.domain.ai.MissionGenerationRequest
import io.github.illiapohukai.focusmode.core.model.MissionType
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v20: the verified Worker fields the PWA keeps (docs/API_CONTRACTS.md §2.3, §3.3) —
 * `plan.sourceAnalysis` and per-step `requiredTools` / `recommendedApps`. MockWebServer only.
 */
class WorkerV20Test {
    private lateinit var server: MockWebServer
    private lateinit var generation: WorkerMissionGenerationRepository
    private lateinit var adaptation: WorkerMissionAdaptationRepository
    private val request = MissionGenerationRequest("Mathe-Klausur Ableitungen", MissionType.EXAM, GenerationConfidence.PARTIAL)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = WorkerClientFactory.create(server.url("/").toString(), requireHttps = false)
        generation = WorkerMissionGenerationRepository(api, WorkerTimeouts(textMs = 1_500, imageMs = 1_500))
        adaptation = WorkerMissionAdaptationRepository(api, timeoutMs = 1_500)
    }

    @After fun tearDown() = server.close()

    private fun respond(body: String) = server.enqueue(MockResponse.Builder().code(200).body(body).build())

    @Test fun sourceAnalysisAndStepToolsArePreservedWithinPwaBounds() = runBlocking {
        val manyTools = (1..15).joinToString(",") { "\"Werkzeug $it\"" }
        respond(
            """{"ok":true,"plan":{"goal":{"title":"Ableitungen","subject":"Mathematik","type":"exam"},
            "sourceAnalysis":{"detectedTasks":"Aufgaben 1–6","detectedPages":"S. 40–42","confidence":0.82,
              "missingInformation":["Termin der Klausur"],"facts":["Thema: Kettenregel","<script>x</script>"],"estimates":["6 Aufgaben à 10 Min."]},
            "sessions":[{"id":"s1","title":"Grundlagen","task":"Regeln wiederholen","durationMinutes":25,
              "requiredTools":[$manyTools],"recommendedApps":["GeoGebra",{"name":"x"},"GeoGebra"]}]}}""",
        )
        val plan = (generation.generate(request) as GenerationResult.Success).plan
        val source = plan.source
        assertEquals("Aufgaben 1–6", source.detectedTasks)
        assertEquals("S. 40–42", source.detectedPages)
        assertEquals(82, source.confidencePercent)
        assertEquals("hoch", source.confidenceLabel)
        assertEquals(listOf("Termin der Klausur"), source.missingInformation)
        assertEquals("an unsafe fact is dropped, the plan is kept", listOf("Thema: Kettenregel"), source.facts)
        assertEquals(listOf("6 Aufgaben à 10 Min."), source.estimates)
        val step = plan.steps.single()
        assertEquals(12, step.requiredTools.size)
        assertEquals(listOf("GeoGebra"), step.recommendedApps)
    }

    @Test fun malformedOptionalFieldsNeverRejectAValidPlan() = runBlocking {
        respond(
            """{"ok":true,"plan":{"goal":{"title":"Ableitungen"},"sourceAnalysis":"kaputt",
            "sessions":[{"title":"Grundlagen","task":"Regeln","durationMinutes":25,"requiredTools":"Stift","recommendedApps":null}]}}""",
        )
        val plan = (generation.generate(request) as GenerationResult.Success).plan
        assertTrue(plan.source.isEmpty)
        assertTrue(plan.steps.single().requiredTools.isEmpty())
        assertNull(plan.source.confidenceLabel)
    }

    @Test fun adaptedStepsKeepTheirTools() = runBlocking {
        respond(
            """{"ok":true,"adaptation":{"message":"Angepasst","summary":"Kürzere Schritte",
            "updatedSessions":[{"title":"Üben","task":"Drei Aufgaben","durationMinutes":20,"reason":"kürzer","requiredTools":["Heft"],"recommendedApps":["Anki"]}]}}""",
        )
        val step = AdaptationStepInput("s1", "Grundlagen", "Regeln", 25, "active")
        val req = AdaptationRequest("mission_a", 1L, "Mathe", "Mathematik", "Nicht angegeben", step, 25, 10, "partial", 3, "", listOf(step), listOf(AdaptationHistoryEntry("session_1", "partial", 3, 10)))
        val result = adaptation.adapt(req) as AdaptationResult.Success
        assertEquals(listOf("Heft"), result.adaptation.steps.single().requiredTools)
        assertEquals(listOf("Anki"), result.adaptation.steps.single().recommendedApps)
    }
}
