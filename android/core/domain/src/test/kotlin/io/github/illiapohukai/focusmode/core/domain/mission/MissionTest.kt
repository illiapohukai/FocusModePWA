package io.github.illiapohukai.focusmode.core.domain.mission

import io.github.illiapohukai.focusmode.core.model.MissionId
import io.github.illiapohukai.focusmode.core.model.MissionStatus
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.MissionStepStatus
import io.github.illiapohukai.focusmode.core.model.MissionTheme
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MissionTest {
    /** Expected values computed with the PWA's own `missionThemeHashV177` in Node. */
    @Test fun themeHashMatchesPwa() {
        assertEquals(0, MissionThemes.hashIndex("mission_11111111-1111-4111-8111-111111111111"))
        assertEquals(2, MissionThemes.hashIndex("mission_22222222-2222-4222-8222-222222222222"))
        assertEquals(2, MissionThemes.hashIndex("mission_test_1"))
        assertEquals(0, MissionThemes.hashIndex("mission_äöü"))
        assertEquals(3, MissionThemes.hashIndex("mission_😀x"))
    }

    @Test fun concurrentMissionsGetDistinctTones() {
        val a = Fixtures.mission("mission_test_1", createdAt = Instant.parse("2026-11-01T00:00:00Z"))
        val b = Fixtures.mission("ab", createdAt = Instant.parse("2026-11-02T00:00:00Z")) // also hashes to EMBER
        val themes = MissionThemes.resolve(listOf(b, a))
        assertEquals(MissionTheme.EMBER, themes[MissionId("mission_test_1")])
        assertNotEquals(themes[MissionId("mission_test_1")], themes[MissionId("ab")])
    }

    @Test fun completingStepsAdvancesAndCompletesMission() {
        var m = Fixtures.mission(steps = 2)
        val at = Instant.parse("2026-11-19T10:00:00Z")
        assertTrue(MissionRules.canStartStep(m, m.steps[0].id))
        assertFalse(MissionRules.canStartStep(m, m.steps[1].id))
        m = (MissionRules.completeStep(m, m.steps[0].id, at) as StepCompletion.Completed).mission
        assertEquals(m.steps[1].id, m.currentStepId)
        assertEquals(MissionStepStatus.ACTIVE, m.steps[1].status)
        assertTrue(MissionRules.completeStep(m, m.steps[0].id, at) is StepCompletion.AlreadyCompleted)
        m = (MissionRules.completeStep(m, m.steps[1].id, at) as StepCompletion.Completed).mission
        assertEquals(MissionStatus.COMPLETED, m.status)
        assertEquals(100, m.progressPercent)
        assertTrue(MissionRules.completeStep(m, MissionStepId("nope"), at) is StepCompletion.StepNotFound)
    }
}
