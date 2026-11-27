package app.focusmodeplus.feature.settings

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.designsystem.theme.FocusModeTheme
import app.focusmodeplus.core.model.AccountDataDecision
import app.focusmodeplus.core.model.AccountState
import app.focusmodeplus.core.model.AccountSwitchChoice
import app.focusmodeplus.core.model.MigrationPreview
import app.focusmodeplus.core.model.MigrationStatus
import app.focusmodeplus.core.model.SyncFailure
import app.focusmodeplus.core.model.SyncPhase
import app.focusmodeplus.core.model.SyncState
import app.focusmodeplus.core.testing.FakeUsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * v21 account section: every state renders honest German copy, decisions need an explicit tap,
 * and the layout holds at 320 dp with 160 % font, in landscape and on tablets. Screenshots go to
 * build/screenshots (fake test identities only).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xhdpi")
class AccountScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots").apply { mkdirs() }.let { File(it, "$name.png") }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(state: AccountUiState, actions: AccountScreenActions = AccountScreenActions()) {
        compose.setContent { FocusModeTheme { AccountScreen(state, actions) } }
    }

    /** Brings the whole node on screen, also inside a card taller than the viewport. */
    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("account").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }

    private fun pill(text: String) = hasText(text, ignoreCase = true)

    private val preview = MigrationPreview(
        missions = 2, missionSteps = 7, sessions = 18, historyFrom = LocalDate.parse("2026-10-02"), historyTo = LocalDate.parse("2026-11-25"),
        focusMinutes = 512, awaitingReview = 1, remoteMissions = 0, remoteSessions = 0,
    )
    private fun signedIn(phase: SyncPhase = SyncPhase.IDLE, pending: Int = 0, failure: SyncFailure? = null, user: app.focusmodeplus.core.model.AccountUser = FakeUsers.ALICE) = AccountUiState(
        loading = false, account = AccountState.SignedIn(user),
        sync = SyncState(phase = phase, pendingChanges = pending, lastSuccessAt = Instant.parse("2026-11-26T08:41:00Z"), failure = failure, migration = MigrationStatus.COMPLETE),
        lastSyncLabel = "26. Nov. 2026, 09:41", localMissions = 2, localSessions = 18,
    )

    @Test fun unconfiguredBuildStaysAFullGuestMode() {
        show(AccountUiState(loading = false, account = AccountState.FirebaseUnconfigured, sync = SyncState(phase = SyncPhase.UNAVAILABLE)))
        compose.onAllNodesWithText(AccountCopy.UNCONFIGURED).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        compose.onNodeWithTag("account_sync_pill").assert(pill("Nicht eingerichtet"))
        compose.onAllNodesWithText("Mit Google anmelden").fetchSemanticsNodes().let { assertTrue("no sign-in in an unconfigured build", it.isEmpty()) }
        scrollTo("account_backup")
        compose.onNodeWithTag("account_backup").assertHasClickAction()
        shot("acc01_unconfigured")
    }

    @Test fun guestSeesBenefitsWithoutPressure() {
        var signIns = 0
        var guest = 0
        show(
            AccountUiState(loading = false, account = AccountState.SignInAvailable, sync = SyncState(phase = SyncPhase.SIGNED_OUT)),
            AccountScreenActions(onSignIn = { signIns++ }, onContinueAsGuest = { guest++ }),
        )
        compose.onNodeWithTag("account_name").assert(hasText(AccountCopy.GUEST_TITLE))
        compose.onNodeWithText("Vorbereitung auf zukünftige Liga-Funktionen").assertIsDisplayed()
        shot("acc02_guest_benefits")
        scrollTo("account_sign_in")
        compose.onNodeWithTag("account_sign_in").performClick()
        compose.onNodeWithTag("account_guest").performClick()
        assertEquals(1, signIns)
        assertEquals(1, guest)
        // Guest mode is not presented as unsafe or defective.
        listOf("unsicher", "verlierst", "verloren", "erforderlich").forEach { word ->
            assertTrue(compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun signedInIdleShowsStatusAndActions() {
        var synced = 0
        show(signedIn(), AccountScreenActions(onSyncNow = { synced++ }))
        compose.onNodeWithTag("account_name").assert(hasText("Test Nutzerin A"))
        compose.onNodeWithTag("account_email").assert(hasText("nutzerin.a@example.test"))
        compose.onNodeWithTag("account_sync_pill").assert(pill("Synchronisiert"))
        scrollTo("account_sync_now")
        compose.onNodeWithTag("sync_last").assert(hasText("26. Nov. 2026, 09:41", substring = true))
        compose.onNodeWithTag("sync_migration").assert(hasText("Abgeschlossen", substring = true))
        compose.onNodeWithTag("account_sync_now").performClick()
        assertEquals(1, synced)
        shot("acc03_signed_in_idle")
        // The uid is never displayed.
        assertTrue(compose.onAllNodes(hasText(FakeUsers.ALICE.uid, substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test fun pendingAndFailedSyncStatesExplainThemselves() {
        show(signedIn(pending = 3))
        compose.onNodeWithTag("account_sync_pill").assert(pill("Änderungen ausstehend"))
        scrollTo("sync_pending")
        compose.onNodeWithTag("sync_pending").assert(hasText("3 Änderungen", substring = true))
        shot("acc04_sync_pending")
    }

    @Test fun failedSyncShowsTheReasonAndRetry() {
        show(signedIn(phase = SyncPhase.FAILED, pending = 1, failure = SyncFailure.SERVER).let { it.copy(sync = it.sync.copy(parkedChanges = 1)) })
        compose.onNodeWithTag("account_sync_pill").assert(pill("Fehler"))
        scrollTo("sync_failure")
        compose.onNodeWithTag("sync_failure").assert(hasText("Ein neuer Versuch folgt automatisch.", substring = true))
        compose.onNodeWithTag("sync_retry_parked").assertHasClickAction()
        shot("acc05_sync_error")
    }

    @Test fun migrationPreviewNeedsAnExplicitChoice() {
        var linked = 0
        var later = 0
        var cloud = 0
        val state = AccountUiState(
            loading = false, account = AccountState.SignedIn(FakeUsers.ALICE), sync = SyncState(phase = SyncPhase.DECISION_REQUIRED, migration = MigrationStatus.PENDING_DECISION),
            preview = preview, localMissions = 2, localSessions = 18,
        )
        show(state, AccountScreenActions(onLinkLocalData = { linked++ }, onPostpone = { later++ }, onUseCloudData = { cloud++ }))
        compose.onNodeWithText(AccountCopy.LINK_HEADLINE).assertIsDisplayed()
        compose.onNodeWithTag("preview_missions").assert(hasText("2 (7 Schritte)", substring = true))
        compose.onNodeWithTag("preview_sessions").assert(hasText("18", substring = true))
        compose.onNodeWithTag("preview_minutes").assert(hasText("512 Min.", substring = true))
        compose.onNodeWithTag("preview_range").assert(hasText("2. Okt. 2026", substring = true))
        shot("acc06_migration_preview")
        scrollTo("account_link_confirm")
        compose.onNodeWithTag("preview_no_league").assert(hasText(AccountCopy.NO_LEAGUE_POINTS))
        // No cloud data yet: "Cloud-Daten verwenden" is not offered as an option.
        compose.onNodeWithTag("account_use_cloud").assertIsNotEnabled()
        assertEquals(0, linked)
        compose.onNodeWithTag("account_link_confirm").performClick()
        assertEquals(1, linked)
        scrollTo("account_link_later_action")
        compose.onNodeWithTag("account_link_later_action").performClick()
        assertEquals(1, later)
        assertEquals(0, cloud)
    }

    @Test fun usingCloudDataWarnsAboutUnsynchronizedLocalData() {
        var cloud = 0
        show(
            AccountUiState(loading = false, account = AccountState.SignedIn(FakeUsers.ALICE), sync = SyncState(phase = SyncPhase.DECISION_REQUIRED), preview = preview.copy(remoteMissions = 3, remoteSessions = 40), localMissions = 2, localSessions = 18),
            AccountScreenActions(onUseCloudData = { cloud++ }),
        )
        scrollTo("account_use_cloud")
        compose.onNodeWithTag("account_use_cloud").performClick()
        compose.onNodeWithTag("account_cloud_dialog").assertIsDisplayed()
        compose.onNodeWithText("gehen dabei verloren", substring = true).assertIsDisplayed()
        shot("acc07_use_cloud_warning")
        assertEquals(0, cloud)
        compose.onNodeWithTag("account_cloud_confirm").performClick()
        assertEquals(1, cloud)
    }

    @Test fun migrationSuccessIsAnnounced() {
        show(signedIn().copy(feedback = DataFeedback(true, "Lokale Daten verbunden", "Dein lokaler Fortschritt ist jetzt mit diesem Konto verbunden. Deine Daten auf diesem Gerät bleiben erhalten.")))
        compose.onNodeWithTag("account_feedback").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite),
        )
        compose.onNodeWithText("Lokale Daten verbunden").assertIsDisplayed()
        shot("acc08_migration_success")
    }

    @Test fun signOutConfirmationShowsPendingChangesAndKeepsDataByDefault() {
        var removed: Boolean? = null
        show(signedIn(pending = 2), AccountScreenActions(onSignOut = { removed = it }))
        scrollTo("account_sign_out")
        compose.onNodeWithTag("account_sign_out").performClick()
        compose.onNodeWithText("Dein Konto und deine Cloud-Daten bleiben erhalten.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 Änderungen noch nicht synchronisiert", substring = true).assertIsDisplayed()
        shot("acc09_sign_out_confirm")
        compose.onNodeWithTag("account_sign_out_confirm").performClick()
        assertEquals(false, removed)
    }

    @Test fun accountDeletionNeedsConfirmationAndOffersABackup() {
        var deleted: Boolean? = null
        var backups = 0
        show(signedIn(), AccountScreenActions(onDeleteAccount = { deleted = it }, onExportBackup = { backups++ }))
        scrollTo("account_delete")
        compose.onNodeWithTag("account_delete").performClick()
        compose.onNodeWithText("Konto endgültig löschen?").assertIsDisplayed()
        compose.onNodeWithText("Dein Google-Konto selbst bleibt bestehen.", substring = true).assertIsDisplayed()
        shot("acc10_delete_confirm")
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(null, deleted)
        compose.onNodeWithTag("account_delete").performClick()
        compose.onNodeWithTag("account_delete_local").performClick()
        compose.onNodeWithTag("account_delete_confirm").performClick()
        assertEquals(true, deleted)
        compose.onNodeWithTag("account_delete").performClick()
        compose.onNodeWithText("Vorher lokales Backup exportieren").performClick()
        assertEquals(1, backups)
    }

    @Test fun anotherAccountsDataIsNeverTakenOverSilently() {
        val choices = mutableListOf<AccountSwitchChoice>()
        show(
            AccountUiState(
                loading = false, account = AccountState.SignedIn(FakeUsers.BOB), sync = SyncState(phase = SyncPhase.ACCOUNT_MISMATCH),
                switch = AccountDataDecision.AccountSwitch(FakeUsers.BOB.uid, 2, 1, 4), localMissions = 1, localSessions = 4,
            ),
            AccountScreenActions(onResolveSwitch = { choices += it }),
        )
        compose.onNodeWithTag("account_switch").assertIsDisplayed()
        compose.onNodeWithText("2 Änderungen des anderen Kontos sind noch nicht synchronisiert.").assertIsDisplayed()
        shot("acc11_account_switch")
        scrollTo("switch_use_new")
        compose.onNodeWithTag("switch_use_new").performClick()
        compose.onNodeWithTag("account_switch_dialog").assertIsDisplayed()
        assertTrue(choices.isEmpty()) // only after the dialog
        compose.onNodeWithTag("account_switch_confirm").performClick()
        compose.onNodeWithTag("switch_keep").performClick()
        assertEquals(listOf(AccountSwitchChoice.USE_NEW_ACCOUNT_DATA, AccountSwitchChoice.KEEP_SEPARATE), choices)
    }

    @Test fun accountProblemsHaveTheirOwnWordsAndNextAction() {
        var current by androidx.compose.runtime.mutableStateOf(AccountUiState(loading = false, account = AccountState.SignInCanceled, sync = SyncState(phase = SyncPhase.SIGNED_OUT)))
        compose.setContent { FocusModeTheme { AccountScreen(current, AccountScreenActions()) } }
        listOf(
            AccountState.SignInCanceled to "Anmeldung abgebrochen",
            AccountState.CredentialUnavailable to "Kein Google-Konto gefunden",
            AccountState.NetworkUnavailable to "Keine Verbindung",
            AccountState.AccountDisabled to "Konto deaktiviert",
            AccountState.AccountDeletedRemotely to "Konto nicht mehr verfügbar",
            AccountState.Error(app.focusmodeplus.core.model.AccountErrorKind.INVALID_CREDENTIAL) to "Anmeldung nicht möglich",
        ).forEach { (state, title) ->
            current = AccountUiState(loading = false, account = state, sync = SyncState(phase = SyncPhase.SIGNED_OUT))
            compose.waitForIdle()
            compose.onNodeWithTag("account_notice").assert(hasText(title, substring = true))
            scrollTo("account_sign_in")
            compose.onNodeWithTag("account_sign_in").assertHasClickAction()
            compose.onNodeWithTag("account").performScrollToNode(hasTestTag("account_status"))
        }
        current = AccountUiState(loading = false, account = AccountState.ReauthenticationRequired(FakeUsers.ALICE), sync = SyncState(phase = SyncPhase.IDLE))
        compose.waitForIdle()
        compose.onNodeWithTag("account_notice").assert(hasText("Anmeldung abgelaufen", substring = true))
        shot("acc12_reauthentication")
        scrollTo("account_sign_in")
        compose.onNodeWithTag("account_sign_in").assert(hasText("Erneut mit Google anmelden"))
    }

    @Test fun privacyCategoriesSeparateAccountAndAi() {
        show(signedIn())
        scrollTo("account_data_categories")
        compose.onNodeWithText("Mit deinem Konto synchronisiert").assertIsDisplayed()
        compose.onNodeWithText("Nur auf diesem Gerät").assertIsDisplayed()
        compose.onNodeWithText("An die AI – nur auf Anfrage").assertIsDisplayed()
        compose.onNodeWithText("Installierte und ausgewählte Apps", substring = true).assertIsDisplayed()
        shot("acc13_privacy_categories")
    }

    @Test fun headingsAndActionsAreExposedToTalkBack() {
        show(signedIn())
        assertTrue(compose.onAllNodes(isHeading()).fetchSemanticsNodes().size >= 3)
        // The decorative avatar exposes nothing; the name heading carries the meaning.
        compose.onNodeWithTag("account_name").assert(isHeading())
        assertTrue(compose.onAllNodes(hasText("TA")).fetchSemanticsNodes().isEmpty())
        scrollTo("account_sync_now")
        compose.onNodeWithTag("account_sync_now").assertHasClickAction()
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun longNameAndEmailFitAt320dpWith160PercentFont() {
        show(signedIn(user = FakeUsers.LONG, pending = 12))
        compose.onNodeWithTag("account_name").assertIsDisplayed()
        compose.onNodeWithTag("account_email").assertIsDisplayed()
        shot("acc14_long_identity_320dp_160")
        scrollTo("account_sync_now")
        compose.onNodeWithTag("account_sync_now").assertIsDisplayed()
        shot("acc15_sync_320dp_160")
    }

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi", fontScale = 1.6f)
    fun migrationPreviewFitsAt320dpWith160PercentFont() {
        show(AccountUiState(loading = false, account = AccountState.SignedIn(FakeUsers.ALICE), sync = SyncState(phase = SyncPhase.DECISION_REQUIRED), preview = preview))
        scrollTo("account_link_confirm")
        compose.onNodeWithTag("account_link_confirm").assertIsDisplayed()
        shot("acc16_preview_320dp_160")
    }

    @Test @Config(qualifiers = "w800dp-h360dp-land-xhdpi")
    fun landscape() {
        show(AccountUiState(loading = false, account = AccountState.SignInAvailable, sync = SyncState(phase = SyncPhase.SIGNED_OUT)))
        compose.onNodeWithTag("account_status").assertIsDisplayed()
        shot("acc17_guest_landscape")
    }

    @Test @Config(qualifiers = "w840dp-h1100dp-xhdpi")
    fun tabletKeepsAReadableWidth() {
        show(signedIn())
        compose.onNodeWithTag("account_status").assertIsDisplayed()
        shot("acc18_signed_in_tablet")
    }
}
