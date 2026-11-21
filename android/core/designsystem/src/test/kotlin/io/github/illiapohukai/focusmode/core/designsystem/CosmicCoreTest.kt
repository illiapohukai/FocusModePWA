package io.github.illiapohukai.focusmode.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicCore
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicQuality
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicQualityGovernor
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicQualityPolicy
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.CosmicSphere
import io.github.illiapohukai.focusmode.core.designsystem.cosmic.cosmicFrameLoopRuns
import io.github.illiapohukai.focusmode.core.designsystem.theme.FocusModeTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v18.1 Cosmic Core performance contract: adaptive tiers, reduced motion, no frame loop when hidden. */
@RunWith(AndroidJUnit4::class)
class CosmicCoreTest {
    @get:Rule val rule = createComposeRule()

    @After fun resetProcessFloor() { CosmicQualityGovernor.processFloor = CosmicQuality.HIGH }

    @Test fun tiersScaleDecorationDownButKeepTheCoreIdentity() {
        val high = CosmicQuality.HIGH.particleCount()
        val balanced = CosmicQuality.BALANCED.particleCount()
        val low = CosmicQuality.LOW.particleCount()
        assertTrue("HIGH $high > BALANCED $balanced > LOW $low", high > balanced && balanced > low)
        assertTrue(balanced <= high * 0.7)
        assertEquals(1, CosmicQuality.LOW.orbitLines) // minimum orbit detail: the primary ring
        assertEquals(4, CosmicQuality.BALANCED.orbitLines)
        assertTrue(CosmicQuality.LOW.frameIntervalMs in 25L..50L) // restrained cadence
        assertEquals(0L, CosmicQuality.HIGH.frameIntervalMs)
        assertFalse(CosmicQuality.LOW.glow)
    }

    @Test fun initialTierFollowsTheDeviceClass() {
        assertEquals(CosmicQuality.HIGH, CosmicQualityPolicy.initial(lowRamDevice = false, memoryClassMb = 256, powerSave = false))
        assertEquals(CosmicQuality.BALANCED, CosmicQualityPolicy.initial(lowRamDevice = false, memoryClassMb = 128, powerSave = false))
        assertEquals(CosmicQuality.LOW, CosmicQualityPolicy.initial(lowRamDevice = true, memoryClassMb = 512, powerSave = false))
        assertEquals(CosmicQuality.LOW, CosmicQualityPolicy.initial(lowRamDevice = false, memoryClassMb = 512, powerSave = true))
    }

    @Test fun governorStepsDownOnSustainedJankOnlyAndNeverUp() {
        val governor = CosmicQualityGovernor(CosmicQuality.HIGH)
        repeat(240) { assertNull(governor.onFrame(16_600_000L)) } // smooth 60 fps
        assertEquals(CosmicQuality.HIGH, governor.quality)
        // A few dropped frames are tolerated.
        repeat(120) { i -> governor.onFrame(if (i % 10 == 0) 40_000_000L else 16_600_000L) }
        assertEquals(CosmicQuality.HIGH, governor.quality)
        // Sustained 25 fps steps down one tier per window.
        var stepped: CosmicQuality? = null
        repeat(120) { stepped = governor.onFrame(40_000_000L) ?: stepped }
        assertEquals(CosmicQuality.BALANCED, stepped)
        repeat(120) { governor.onFrame(40_000_000L) }
        assertEquals(CosmicQuality.LOW, governor.quality)
        repeat(600) { governor.onFrame(80_000_000L) }
        assertEquals("LOW is the floor", CosmicQuality.LOW, governor.quality)
        repeat(600) { governor.onFrame(8_000_000L) }
        assertEquals("never steps back up within a visit", CosmicQuality.LOW, governor.quality)
        assertEquals("remembered for the process", CosmicQuality.LOW, CosmicQualityGovernor.processFloor)
    }

    @Test fun pausesAreNotCountedAsJank() {
        val governor = CosmicQualityGovernor(CosmicQuality.HIGH)
        repeat(300) { governor.onFrame(5_000_000_000L) } // screen off / background gaps
        assertEquals(CosmicQuality.HIGH, governor.quality)
    }

    @Test fun lowTierCadenceIsNotMistakenForJank() {
        val governor = CosmicQualityGovernor(CosmicQuality.LOW)
        repeat(600) { assertNull(governor.onFrame(50_000_000L)) }
        assertEquals(CosmicQuality.LOW, governor.quality)
    }

    @Test fun frameLoopRunsOnlyWhenVisibleAllowedAndNotReduced() {
        assertTrue(cosmicFrameLoopRuns(animate = true, visible = true, reducedMotion = false))
        assertFalse(cosmicFrameLoopRuns(animate = true, visible = false, reducedMotion = false))
        assertFalse(cosmicFrameLoopRuns(animate = true, visible = true, reducedMotion = true))
        assertFalse(cosmicFrameLoopRuns(animate = false, visible = true, reducedMotion = false))
    }

    @Test fun bakedSphereIsDeterministicLitFromTheUpperLeftAndTransparentOutside() {
        val q = 128
        val a = CosmicSphere.bake(q)
        assertArrayEquals(a, CosmicSphere.bake(q))
        fun alpha(x: Int, y: Int) = a[y * q + x] ushr 24
        fun luma(x: Int, y: Int): Int { val p = a[y * q + x]; return ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF) }
        assertEquals(0, alpha(0, 0))
        assertEquals(0, alpha(q - 1, q - 1))
        assertEquals(255, alpha(q / 2, q / 2))
        // Rim light: the upper-left limb is brighter than the lower-right limb.
        val d = (q * 0.42).toInt()
        assertTrue(luma(q / 2 - d * 7 / 10, q / 2 - d * 7 / 10) > luma(q / 2 + d * 7 / 10, q / 2 + d * 7 / 10))
        assertEquals(128, CosmicSphere.spriteSize(40f))
        assertEquals(288, CosmicSphere.spriteSize(900f))
        assertEquals(0, CosmicSphere.spriteSize(0f))
    }

    @Test fun reducedMotionComposesAStaticCoreThatIdles() {
        rule.setContent {
            FocusModeTheme {
                Box {
                    CosmicCore(Modifier.size(280.dp).testTag("core"), animate = true, reducedMotion = true, quality = CosmicQuality.HIGH)
                    Text("12:00")
                }
            }
        }
        rule.waitForIdle() // never idles if a frame loop ran
        rule.onNodeWithText("12:00").assertExists()
        rule.onNodeWithTag("core", useUnmergedTree = true).assertExists()
    }

    @Test fun switchingTierKeepsTheSurroundingContentAndNeverRestartsIt() {
        val tier = mutableStateOf(CosmicQuality.HIGH)
        var composedTimer = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FocusModeTheme {
                Box {
                    CosmicCore(Modifier.size(280.dp), animate = true, reducedMotion = false, quality = tier.value)
                    composedTimer++
                    Text("24:57")
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val before = composedTimer
        tier.value = CosmicQuality.LOW
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("24:57").assertExists()
        assertTrue("the timer text is not recomposed by animation frames", composedTimer - before <= 1)
    }
}
