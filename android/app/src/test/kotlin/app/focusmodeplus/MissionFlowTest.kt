package app.focusmodeplus

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.data.db.FocusDatabase
import app.focusmodeplus.core.data.db.Migrations
import app.focusmodeplus.core.data.repository.RoomMissionRepository
import app.focusmodeplus.core.model.Mission
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.LocalDate

/**
 * The Mission vertical slice on the JVM with the real Hilt graph, Room, DataStore and the Session
 * owner. Missions are seeded into the real database file before the activity starts. The AI
 * Worker is never called here: generation is covered by owner, ViewModel and MockWebServer tests.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MissionFlowTest {
    private val seeds = mapOf(
        "missionStepRunsAsARealSessionAndItsResultMovesProgressOnce" to listOf(
            Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 2, deadline = LocalDate.parse("2026-12-10")),
        ),
        "twoActiveMissionsUseTheCarouselAndBlockAThird" to listOf(
            Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3, completed = 1),
            Fixtures.mission("mission_ref", title = "Referat Weimarer Republik", steps = 4),
        ),
    )

    @get:Rule(order = 0) val system = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 3)
            seeds[description.methodName]?.let { seed(app, it) }
        }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun seed(app: Application, missions: List<Mission>) = runBlocking {
        val db = Room.databaseBuilder(app, FocusDatabase::class.java, FocusDatabase.NAME).addMigrations(*Migrations.ALL).build()
        val repo = RoomMissionRepository(db, FakeClock(), FakePreferencesRepository())
        missions.forEach { check(repo.create(it) is app.focusmodeplus.core.domain.repository.MissionWriteResult.Ok) }
        db.close()
    }

    private fun present(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun pumpUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        if (rule.mainClock.autoAdvance) { rule.waitUntil(timeoutMs, condition); return }
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "condition not met within $timeoutMs ms" }
            rule.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
    }

    private fun waitFor(tag: String) = pumpUntil { present(tag) }

    private fun settle(frames: Int = 60) {
        if (rule.mainClock.autoAdvance) rule.waitForIdle() else repeat(frames) { rule.mainClock.advanceTimeByFrame() }
    }

    private fun shot(name: String) {
        settle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Ends the running Mission Session after [minutes] through the ordinary end sheet. */
    private fun focusAndEnd(minutes: Long) {
        waitFor("focus_timer")
        ShadowSystemClock.advanceBy(Duration.ofMinutes(minutes))
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("focus_end").performClick()
        waitFor("end_sheet")
        settle()
        rule.onNodeWithTag("end_confirm").performClick()
        settle()
        waitFor("mission_review")
    }

    private fun reviewCompleted() {
        rule.onNodeWithTag("review_completed").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("review_submit").performSemanticsAction(SemanticsActions.OnClick) // manual clock: no scroll animation
        settle()
        waitFor("result_mission_progress")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun missionStepRunsAsARealSessionAndItsResultMovesProgressOnce() {
        waitFor("home_mission")
        rule.onNodeWithText("Biologie-Klausur").assertIsDisplayed()
        shot("13_home_one_mission")

        // Home → Mission Plan → step start (one real Session through the one Session owner).
        rule.onNodeWithTag("home_mission").performClick()
        waitFor("plan_hero")
        shot("14_mission_plan")
        rule.onNodeWithTag("mission_detail").performScrollToNode(hasTestTag("mission_start_step"))
        rule.mainClock.autoAdvance = false // the Cosmic Core frame loop never idles
        rule.onNodeWithTag("mission_start_step").performClick()
        waitFor("focus_timer")
        check(rule.onAllNodes(hasText("Schritt 1 von 2")).fetchSemanticsNodes().isNotEmpty()) // the Mission relationship is visible
        shot("15_mission_focus")

        // Recreation keeps the Mission linkage (never converted into Quick Focus).
        rule.activityRule.scenario.recreate()
        waitFor("focus_timer")
        check(rule.onAllNodes(hasText("Schritt 1 von 2")).fetchSemanticsNodes().isNotEmpty())

        focusAndEnd(12)
        shot("16_mission_review")
        reviewCompleted()
        pumpUntil { rule.onAllNodes(hasText("1 von 2 Schritten erledigt")).fetchSemanticsNodes().isNotEmpty() } // next Room emission
        shot("17_mission_result")

        // Home now shows the next step and 50 % (the same progress value as Plan and Result).
        rule.onNodeWithTag("result_done").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        waitFor("home_mission")
        pumpUntil { rule.onAllNodes(hasText("50%")).fetchSemanticsNodes().isNotEmpty() }

        // Final step: the Mission completes and Home falls back to the returning state, not onboarding.
        rule.onNodeWithTag("home_start_step").performClick()
        focusAndEnd(12)
        reviewCompleted()
        // The Session state is in memory; the completed Mission arrives with Room's next Flow emission.
        pumpUntil { rule.onAllNodes(hasText("Mission abgeschlossen", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("result_done").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        waitFor("home_mission_return")
        rule.onNodeWithTag("home_mission_empty").assertDoesNotExist()
        shot("18_home_returning_empty")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun twoActiveMissionsUseTheCarouselAndBlockAThird() {
        waitFor("home_mission_carousel")
        shot("19_home_two_missions")
        rule.onNodeWithTag("nav_missions").performClick()
        waitFor("missions_limit")
        rule.onNodeWithTag("nav_plus").performClick()
        settle()
        rule.onNodeWithText("Neue Mission").performClick()
        waitFor("create_limit")
        rule.onNodeWithTag("create_plan").assertIsNotEnabled()
        shot("20_create_limit")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun firstMissionFormSurvivesRecreationWithoutAnyRequest() {
        waitFor("home_mission_empty")
        rule.onNodeWithTag("home_create_mission").performScrollTo().performClick()
        waitFor("mission_create")
        rule.onNodeWithTag("create_goal").performTextInput("Mathe-Klausur über lineare Funktionen")
        rule.onNodeWithTag("type_exam").performClick()
        rule.onNodeWithTag("confidence_partial").performClick()
        settle()
        shot("21_create_form")
        rule.activityRule.scenario.recreate()
        waitFor("mission_create")
        pumpUntil { rule.onAllNodes(hasText("Mathe-Klausur über lineare Funktionen")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("create_plan").assertIsDisplayed() // planning stays an explicit tap; nothing was sent
    }
}
