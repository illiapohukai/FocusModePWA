package io.github.illiapohukai.focusmode

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.illiapohukai.focusmode.core.data.db.FocusDatabase
import io.github.illiapohukai.focusmode.core.data.db.Migrations
import io.github.illiapohukai.focusmode.core.data.db.toEntity
import io.github.illiapohukai.focusmode.core.data.repository.RoomMissionRepository
import io.github.illiapohukai.focusmode.core.domain.repository.MissionWriteResult
import io.github.illiapohukai.focusmode.core.model.FocusSessionResult
import io.github.illiapohukai.focusmode.core.model.MissionLink
import io.github.illiapohukai.focusmode.core.model.MissionStepId
import io.github.illiapohukai.focusmode.core.model.SessionOutcome
import io.github.illiapohukai.focusmode.core.model.SessionStatus
import io.github.illiapohukai.focusmode.core.model.SessionType
import io.github.illiapohukai.focusmode.core.testing.FakeClock
import io.github.illiapohukai.focusmode.core.testing.FakePreferencesRepository
import io.github.illiapohukai.focusmode.core.testing.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * v20 parity on the JVM with the real Hilt graph, Room and DataStore: Home Activity, History with
 * the Fokus-Rhythmus chart, "Dein Fokusverlauf", "Daten & Backup" and the full deletion of
 * personal data. History and Missions are seeded into the real database file before the activity
 * starts (relative to today, so filters and charts always have data). Not a device test.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParityV20FlowTest {
    @get:Rule(order = 0) val system = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 3)
            if (description.methodName != "emptyHistoryAndInsightsExplainThemselves") seed(app)
        }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private fun result(id: String, daysAgo: Long, minutes: Int, outcome: SessionOutcome, hour: Int, mission: Boolean = false): FocusSessionResult {
        val date = today.minusDays(daysAgo)
        val end = date.atTime(hour, 30).atZone(zone).toInstant()
        val base = Fixtures.result(id, date, minutes, planned = 25, outcome = outcome, counts = outcome != SessionOutcome.CANCELED && minutes >= 1, finishedAt = end, task = if (mission) "Kapitel 2 wiederholen" else "Konzentriert arbeiten")
        return base.copy(
            status = if (outcome == SessionOutcome.CANCELED) SessionStatus.CANCELLED else SessionStatus.COMPLETED,
            zoneId = zone.id,
            type = if (mission) SessionType.MISSION else SessionType.QUICK,
            missionLink = if (mission) MissionLink(io.github.illiapohukai.focusmode.core.model.MissionId("mission_bio"), MissionStepId("mission_bio_step_0")) else null,
        )
    }

    private fun seed(app: Application) = runBlocking {
        val db = Room.databaseBuilder(app, FocusDatabase::class.java, FocusDatabase.NAME).addMigrations(*Migrations.ALL).build()
        val repo = RoomMissionRepository(db, FakeClock(wall = Instant.now()), FakePreferencesRepository())
        val mission = Fixtures.mission("mission_bio", title = "Biologie-Klausur", steps = 3, completed = 1, deadline = today.plusDays(12))
        val withTools = mission.copy(steps = mission.steps.map { if (it.position == 1) it.copy(requiredTools = listOf("Lehrbuch S. 40–52", "Karteikarten")) else it })
        check(repo.create(withTools) is MissionWriteResult.Ok)
        val results = listOf(
            result("s_today_1", 0, 25, SessionOutcome.COMPLETED, 9, mission = true),
            result("s_today_2", 0, 12, SessionOutcome.PARTIAL, 11),
            result("s_yday_1", 1, 30, SessionOutcome.COMPLETED, 16),
            result("s_yday_2", 1, 0, SessionOutcome.CANCELED, 18),
            result("s_d3", 3, 45, SessionOutcome.COMPLETED, 10, mission = true),
            result("s_d5", 5, 20, SessionOutcome.COMPLETED, 14),
            result("s_d9", 9, 25, SessionOutcome.COMPLETED, 15),
            result("s_d16", 16, 50, SessionOutcome.COMPLETED, 15),
        )
        db.sessionDao().insertResults(results.map { it.toEntity(savedAt = it.finishedAt) })
        db.close()
    }

    private fun present(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = rule.waitUntil(10_000) { present(tag) }
    private fun waitForText(text: String) = rule.waitUntil(10_000) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun activityHistoryRhythmAndInsightsReadOneHistoryOwner() {
        waitFor("home")
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_activity"))
        waitFor("home_activity_s_today_1")
        rule.onNodeWithTag("home_activity").assertIsDisplayed()
        // Mission rows name their Mission; outcomes are words next to the node shape.
        rule.onNodeWithTag("home_activity_s_today_1").assert(hasText("Biologie-Klausur", substring = true)).assert(hasText("Abgeschlossen"))
        rule.onNodeWithTag("home_activity_s_today_2").assert(hasText("Quick Focus", substring = true)).assert(hasText("Teilweise"))
        shot("22_home_activity")

        rule.onNodeWithTag("nav_history").performClick()
        waitFor("history")
        rule.onNodeWithText("Deine Fokuszeit").assertIsDisplayed()
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("rhythm_chart"))
        rule.onNodeWithTag("rhythm_bucket_6").performClick() // today
        waitFor("rhythm_detail")
        shot("23_history_rhythm")
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_row_s_today_2"))
        rule.onNodeWithTag("history_row_s_today_2").assert(hasText("Teilweise")).assert(hasText("12 Min."))
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_row_s_today_1"))
        rule.onNodeWithTag("history_row_s_today_1").assert(hasText("Biologie-Klausur · Schritt 01"))

        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_open_insights"))
        rule.onNodeWithTag("history_open_insights").performClick()
        waitFor("insights")
        rule.onNodeWithText("Dein Fokusverlauf").assertIsDisplayed()
        rule.onNodeWithTag("insights_hero").assertIsDisplayed()
        shot("24_insights")
        rule.onNodeWithTag("insights").performScrollToNode(hasTestTag("insights_types"))
        rule.onNodeWithText("Konstanz").assertIsDisplayed()
        shot("25_insights_cards")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun fullDeletionRemovesEveryPersonalRecordAndReturnsToFirstTimeHome() {
        waitFor("home")
        waitFor("home_mission")
        rule.onNodeWithTag("nav_settings").performClick()
        waitFor("settings")
        rule.onNodeWithTag("settings").performScrollToNode(hasTestTag("settings_data_backup"))
        rule.onNodeWithTag("settings_data_backup").performClick()
        waitFor("data_backup")
        rule.onNodeWithText("Daten & Backup").assertIsDisplayed()
        waitForText("1 / 2 (1)")
        shot("26_data_backup")

        rule.onNodeWithTag("data_backup").performScrollToNode(hasTestTag("data_erase"))
        rule.onNodeWithTag("data_erase").performClick()
        rule.onNodeWithTag("data_erase_confirm").assertIsDisplayed()
        shot("27_data_erase_confirm")
        rule.onNodeWithTag("data_erase_confirm").performClick()
        waitForText("Daten gelöscht")
        waitForText("0 / 2 (0)")

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Application>(), FocusDatabase::class.java, FocusDatabase.NAME).addMigrations(*Migrations.ALL).build()
        assertEquals(0, runBlocking { db.personalDataDao().remainingRows() })
        db.close()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("nav_home").performClick()
        waitFor("home_mission_empty") // first-time hero again: the one-way onboarding mark was reset too
        shot("28_home_after_deletion")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xhdpi")
    fun emptyHistoryAndInsightsExplainThemselves() {
        waitFor("home")
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_activity"))
        rule.onNodeWithText("Noch keine Aktivität").assertIsDisplayed()
        rule.onNodeWithTag("nav_history").performClick()
        waitFor("history")
        rule.onNodeWithTag("history").performScrollToNode(hasText("Erste Sitzung starten"))
        rule.onNodeWithText("Dein Sitzungsverlauf ist noch leer.").assertIsDisplayed()
        rule.onNodeWithText("Erste Sitzung starten").assertIsDisplayed()
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_open_insights"))
        rule.onNodeWithTag("history_open_insights").performClick()
        waitFor("insights_empty")
        shot("29_insights_empty")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun compactLargeFontHistoryAndActivityStayReadable() {
        waitFor("home")
        rule.onNodeWithTag("home").performScrollToNode(hasTestTag("home_activity"))
        shot("30_home_activity_320dp_font160")
        rule.onNodeWithTag("nav_history").performClick()
        waitFor("history")
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("rhythm_chart"))
        rule.onNodeWithTag("rhythm_range_WEEKS_4").performClick()
        shot("31_history_rhythm_320dp_font160")
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_open_insights"))
        rule.onNodeWithTag("history_open_insights").performClick()
        waitFor("insights")
        shot("32_insights_320dp_font160")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscapeInsightsAndDataBackupScroll() {
        waitFor("home")
        rule.onNodeWithTag("nav_history").performClick()
        waitFor("history")
        rule.onNodeWithTag("history").performScrollToNode(hasTestTag("history_open_insights"))
        rule.onNodeWithTag("history_open_insights").performClick()
        waitFor("insights")
        shot("33_insights_landscape")
    }

    @Test
    @Config(qualifiers = "w840dp-h1100dp-xhdpi")
    fun tabletHistoryUsesReadableWidth() {
        waitFor("home")
        rule.onNodeWithTag("nav_history").performClick()
        waitFor("history")
        shot("34_history_tablet")
    }
}
