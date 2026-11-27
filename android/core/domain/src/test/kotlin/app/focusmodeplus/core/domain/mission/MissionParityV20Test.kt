package app.focusmodeplus.core.domain.mission

import app.focusmodeplus.core.domain.ai.AdaptedStep
import app.focusmodeplus.core.domain.ai.GeneratedMissionPlan
import app.focusmodeplus.core.domain.ai.GeneratedStep
import app.focusmodeplus.core.domain.ai.SourceAnalysis
import app.focusmodeplus.core.model.MissionType
import app.focusmodeplus.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MissionParityV20Test {
    private val at = Instant.parse("2026-11-26T09:00:00Z")

    @Test fun renameChangesOnlyTheTitleAndKeepsIdentity() {
        val m = Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3, completed = 1)
        val renamed = MissionRules.rename(m, "  Bio-Klausur\nKapitel 4  ", at)!!
        assertEquals("Bio-Klausur Kapitel 4", renamed.title)
        assertEquals(m.id, renamed.id)
        assertEquals(m.steps, renamed.steps)
        assertEquals(m.currentStepId, renamed.currentStepId)
        assertEquals(at, renamed.updatedAt)
        // The identity colour derives from the id only, so it cannot change with the title.
        assertEquals(MissionThemes.resolve(listOf(m)), MissionThemes.resolve(listOf(renamed)))
        assertSame(m, MissionRules.rename(m, "Biologie-Klausur", at))
    }

    @Test fun renameRejectsEmptyAndBoundsLength() {
        val m = Fixtures.mission()
        assertNull(MissionRules.rename(m, "   ", at))
        assertNull(MissionRules.cleanTitle("\u0007\n"))
        assertEquals(MissionRules.TITLE_MAX, MissionRules.cleanTitle("x".repeat(200))!!.length)
    }

    @Test fun toolsAndAppsTravelFromPlanAndAdaptationIntoSteps() {
        val plan = GeneratedMissionPlan(
            title = "Mathe", subject = "Mathematik", type = MissionType.EXAM, deadlineText = "Nicht angegeben",
            steps = listOf(GeneratedStep("a1", "Grundlagen", "Formeln", 25, "", null, listOf("Taschenrechner"), listOf("GeoGebra"))),
            estimateTotalMinutes = 25, scheduleWarning = null,
        )
        val draft = MissionDraft.new(at).copy(plan = plan)
        val mission = MissionRules.missionFromPlan(draft, plan, at)
        assertEquals(listOf("Taschenrechner"), mission.steps.single().requiredTools)
        assertEquals(listOf("GeoGebra"), mission.steps.single().recommendedApps)
        val adapted = MissionRules.replaceRemainingSteps(mission, listOf(AdaptedStep("Neu", "Üben", 20, "", listOf("Heft"), emptyList())), at)!!
        assertEquals(listOf("Heft"), adapted.steps.last().requiredTools)
    }

    @Test fun sourceAnalysisLabelsFollowThePwa() {
        assertEquals(85, SourceAnalysis.percentOf(0.85))
        assertEquals(85, SourceAnalysis.percentOf(85.0))
        assertNull(SourceAnalysis.percentOf(0.0))
        assertNull(SourceAnalysis.percentOf(Double.NaN))
        assertEquals("hoch", SourceAnalysis(confidencePercent = 80).confidenceLabel)
        assertEquals("mittel", SourceAnalysis(confidencePercent = 50).confidenceLabel)
        assertEquals("niedrig", SourceAnalysis(confidencePercent = 20).confidenceLabel)
        val s = SourceAnalysis(detectedTasks = "Aufgaben 1–5", detectedPages = "S. 40", facts = listOf("Thema: Ableitungen"))
        assertEquals(listOf("Aufgaben: Aufgaben 1–5", "Seiten: S. 40", "Thema: Ableitungen"), s.recognized)
        assertTrue(SourceAnalysis().isEmpty)
    }
}
