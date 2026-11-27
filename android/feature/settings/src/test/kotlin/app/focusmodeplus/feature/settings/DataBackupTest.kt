package app.focusmodeplus.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.domain.backup.BackupContent
import app.focusmodeplus.core.domain.backup.BackupExportOwner
import app.focusmodeplus.core.domain.backup.BackupParseResult
import app.focusmodeplus.core.domain.backup.BackupScope
import app.focusmodeplus.core.domain.backup.BackupSnapshotSource
import app.focusmodeplus.core.domain.backup.IntegrityStatus
import app.focusmodeplus.core.domain.backup.PersonalDataEraser
import app.focusmodeplus.core.domain.backup.PersonalDataStore
import app.focusmodeplus.core.domain.backup.PwaBackupImporter
import app.focusmodeplus.core.domain.mission.MissionAdaptationOwner
import app.focusmodeplus.core.domain.mission.MissionGenerationOwner
import app.focusmodeplus.core.domain.platform.ReminderScheduler
import app.focusmodeplus.core.domain.platform.ReminderSyncReason
import app.focusmodeplus.core.domain.session.ElapsedReading
import app.focusmodeplus.core.domain.session.FinishResult
import app.focusmodeplus.core.domain.session.FocusSessionController
import app.focusmodeplus.core.domain.session.QuickFocusRequest
import app.focusmodeplus.core.domain.session.RestoreTrigger
import app.focusmodeplus.core.domain.session.SessionState
import app.focusmodeplus.core.domain.session.StartResult
import app.focusmodeplus.core.model.EndNote
import app.focusmodeplus.core.model.MissionId
import app.focusmodeplus.core.model.MissionReview
import app.focusmodeplus.core.model.MissionStepId
import app.focusmodeplus.core.model.ProtectionMode
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakeMaterialStore
import app.focusmodeplus.core.testing.FakeMissionAdapter
import app.focusmodeplus.core.testing.FakeMissionGenerator
import app.focusmodeplus.core.testing.FakeMissionRepository
import app.focusmodeplus.core.testing.FakeNetworkStatus
import app.focusmodeplus.core.testing.FakePlatform
import app.focusmodeplus.core.testing.FakePreferencesRepository
import app.focusmodeplus.core.testing.FakeSessionStore
import app.focusmodeplus.core.testing.Fixtures
import app.focusmodeplus.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** PWA "Daten & Backup" on Android: export through a document Uri, explicit full deletion. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class DataBackupTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val main = MainDispatcherRule()

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deletionNeedsExplicitConfirmation() {
        var erased = 0
        val exports = mutableListOf<BackupScope>()
        compose.setContent {
            FocusModeTheme {
                DataBackupScreen(DataBackupUiState(loading = false, activeMissions = 1, missions = 2, sessions = 14, lastBackup = "20. Nov. 2026, 18:04"), {}, {}, { exports += it }, { erased++ })
            }
        }
        compose.onNodeWithText("1 / 2 (2)").assertIsDisplayed()
        compose.onNodeWithTag("data_export_full").performClick()
        compose.onNodeWithTag("data_export_missions").performClick()
        assertEquals(listOf(BackupScope.FULL, BackupScope.MISSIONS), exports)
        shot("s01_data_backup")
        compose.onNodeWithTag("data_backup").performScrollToNode(hasTestTag("data_erase"))
        compose.onNodeWithTag("data_erase").performClick()
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(0, erased)
        compose.onNodeWithTag("data_erase").performClick()
        compose.onNodeWithText("Alle persönlichen und nutzergenerierten Daten löschen?").assertIsDisplayed()
        shot("s02_data_erase_dialog")
        compose.onNodeWithTag("data_erase_confirm").performClick()
        assertEquals(1, erased)
    }

    /** A minimal Session owner: nothing is running. The real one is covered in :core:session. */
    private val idle = object : FocusSessionController {
        override val state = MutableStateFlow<SessionState>(SessionState.Idle())
        override suspend fun restore(trigger: RestoreTrigger) = Unit
        override suspend fun startQuick(request: QuickFocusRequest): StartResult = error("unused")
        override suspend fun startMissionStep(missionId: MissionId, stepId: MissionStepId, mode: ProtectionMode): StartResult = error("unused")
        override suspend fun endByUser(note: EndNote?) = FinishResult.NoActiveSession
        override suspend fun cancel(note: EndNote?) = FinishResult.NoActiveSession
        override suspend fun emergencyExit() = FinishResult.NoActiveSession
        override suspend fun resumeRecovered() = false
        override suspend fun saveRecovered() = FinishResult.NoActiveSession
        override suspend fun discardRecovered() = false
        override suspend fun submitMissionReview(review: MissionReview) = false
        override suspend fun abandonForDataDeletion() = Unit
        override suspend fun onRuntimeTick() = Unit
        override fun read(): ElapsedReading? = null
    }

    private fun TestScope.vm(content: BackupContent, prefs: FakePreferencesRepository, clock: FakeClock): DataBackupViewModel {
        val missions = FakeMissionRepository(clock)
        val store = FakeSessionStore(clock)
        val source = object : BackupSnapshotSource { override suspend fun snapshot() = content }
        val records = object : PersonalDataStore {
            override suspend fun wipeRecords() = Unit
            override suspend fun clearReminderRows() = Unit
            override suspend fun remainingRows() = 0
        }
        val scheduler = object : ReminderScheduler {
            override suspend fun sync(reason: ReminderSyncReason) = Unit
            override suspend fun deliver(key: String) = Unit
        }
        val material = FakeMaterialStore()
        val eraser = PersonalDataEraser(
            idle,
            MissionGenerationOwner(missions, FakeMissionGenerator(), material, prefs, FakeNetworkStatus(), clock, backgroundScope),
            MissionAdaptationOwner(missions, store, FakeMissionAdapter(), FakeNetworkStatus(), backgroundScope),
            records, prefs, material, scheduler, FakePlatform(),
        )
        return DataBackupViewModel(missions, store, prefs, BackupExportOwner(source, prefs, clock, "20.0.0"), eraser, clock).also { it.uiState.launchIn(backgroundScope) }
    }

    @Test fun exportWritesAVerifiedFileAndRecordsTheLastBackup() = runTest {
        val clock = FakeClock(wall = Instant.parse("2026-11-26T09:00:00Z"))
        val prefs = FakePreferencesRepository()
        val content = BackupContent(listOf(Fixtures.mission()), emptyList(), listOf(Fixtures.result("s1", LocalDate.parse("2026-11-25"), 25)), prefs.current())
        val vm = vm(content, prefs, clock)
        assertEquals("focus-mode-backup-2026-11-26.json", vm.fileName(BackupScope.FULL))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "export-test.json").apply { delete() }
        vm.export(BackupScope.FULL, context.contentResolver, Uri.fromFile(file))
        val state = vm.uiState.first { it.feedback != null }
        assertTrue(state.feedback!!.success)
        assertTrue(state.feedback!!.body.contains("1 Missionen, 1 Sitzungen"))
        assertNotNull(prefs.current().lastExportAt)
        val plan = (PwaBackupImporter().parse(file.readBytes(), clock.zone(), clock.now()) as BackupParseResult.Ok).plan
        assertEquals(IntegrityStatus.VERIFIED, plan.report.integrity)
        assertEquals(1, plan.results.size)
    }

    @Test fun eraseReportsCompletion() = runTest {
        val clock = FakeClock()
        val prefs = FakePreferencesRepository()
        val vm = vm(BackupContent(emptyList(), emptyList(), emptyList(), prefs.current()), prefs, clock)
        vm.eraseAll()
        val state = vm.uiState.first { it.feedback != null }
        assertEquals("Daten gelöscht", state.feedback!!.title)
    }
}
