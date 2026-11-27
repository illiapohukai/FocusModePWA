package app.focusmodeplus.core.protection

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.focusmodeplus.core.domain.platform.DndApplyResult
import app.focusmodeplus.core.domain.platform.UsageAccessGateway
import app.focusmodeplus.core.domain.repository.DndOverride
import app.focusmodeplus.core.domain.repository.DndOverrideStore
import app.focusmodeplus.core.domain.repository.DistractionLog
import app.focusmodeplus.core.model.ManagedDeviceStatus
import app.focusmodeplus.core.model.SessionId
import app.focusmodeplus.core.testing.FakeClock
import app.focusmodeplus.core.testing.FakePlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ProtectionGatewaysTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = FakeClock()

    private class MemoryDndStore : DndOverrideStore {
        var value: DndOverride? = null
        override suspend fun get() = value
        override suspend fun put(override: DndOverride) { value = override }
        override suspend fun clear() { value = null }
    }

    @Test fun usageAccessFollowsAppOpsAndIsNeverAssumed() {
        val gateway = AndroidUsageAccessGateway(context)
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED)
        assertFalse(gateway.hasAccess())
        assertTrue(gateway.foregroundEvents(0, 1_000, setOf("com.example.video")).isEmpty())
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        assertTrue(gateway.hasAccess())
    }

    @Test fun dndRestoresOnlyWhatFocusModeChanged() = runTest {
        val nm = context.getSystemService(NotificationManager::class.java)
        val store = MemoryDndStore()
        val dnd = AndroidDndController(context, store, clock)
        shadowOf(nm).setNotificationPolicyAccessGranted(false)
        assertEquals(DndApplyResult.NoAccess, dnd.applyForSession(SessionId("s1")))

        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertEquals(DndApplyResult.Applied, dnd.applyForSession(SessionId("s1")))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        assertTrue(store.value != null) // recorded before changing (crash-safe)
        assertTrue(dnd.restoreIfChanged())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertNull(store.value)

        // The user already had DND on: never touched, never "restored".
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        assertEquals(DndApplyResult.AlreadyActive, dnd.applyForSession(SessionId("s2")))
        assertFalse(dnd.restoreIfChanged())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)

        // The user changed DND during the Session: Focus Mode+ does not fight it.
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        dnd.applyForSession(SessionId("s3"))
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        assertFalse(dnd.restoreIfChanged())
        assertEquals(NotificationManager.INTERRUPTION_FILTER_NONE, nm.currentInterruptionFilter)
    }

    @Test fun consumerDeviceIsNeverManaged() {
        assertEquals(ManagedDeviceStatus.NOT_MANAGED, ConsumerManagedDeviceGateway(context).status())
        assertFalse(NoOpManagedFocusEnforcer().startEnforcement(setOf("com.example.video")))
    }

    @Test fun catalogListsLaunchableAppsAndResolvesUninstalled() = runTest {
        val pm = shadowOf(context.packageManager)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        fun addApp(pkg: String, label: String) {
            val appInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; enabled = true }
            val resolve = android.content.pm.ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = appInfo }
            }
            pm.addResolveInfoForIntent(launcher, resolve)
            pm.installPackage(android.content.pm.PackageInfo().apply { packageName = pkg; applicationInfo = appInfo })
        }
        addApp("com.example.video", "Video")
        addApp("com.example.chat", "Chat")
        addApp("com.example.video", "Video") // duplicate launcher entry collapses
        val catalog = AndroidInstalledAppCatalog(context)
        val apps = catalog.launchableApps()
        assertEquals(listOf("com.example.chat", "com.example.video"), apps.map { it.packageName })
        assertEquals("Video", catalog.resolve("com.example.video")!!.label)
        assertNull(catalog.resolve("com.example.uninstalled"))
    }

    @Test fun distractionMonitorRecordsHintsRateLimitedAndStopsOnRevocation() = runTest {
        var accessChecks = 0
        val usage = object : UsageAccessGateway {
            override fun isSupported() = true
            override fun hasAccess() = ++accessChecks <= 3 // revoked during the Session after three polls
            override fun foregroundEvents(fromMs: Long, toMs: Long, watched: Set<String>) =
                listOf("com.example.video" to 1L, "com.example.notwatched" to 2L).filter { it.first in watched }
        }
        val recorded = mutableListOf<String>()
        val log = object : DistractionLog {
            override suspend fun record(sessionId: SessionId, packageName: String, at: Instant) { recorded += packageName }
            override fun observeCount(sessionId: SessionId): Flow<Int> = flowOf(recorded.size)
            override suspend fun purgeOlderThan(cutoff: Instant) = 0
            override suspend fun purgeAll() = 0
        }
        val platform = FakePlatform()
        var interactive = true
        val monitor = DistractionMonitor(usage, log, platform, clock, object : ScreenState { override fun isInteractive() = interactive })
        assertEquals(DistractionMonitor.Stop.NothingToWatch, monitor.run(SessionId("s"), emptySet()) { 10 })

        val stop = monitor.run(SessionId("s"), setOf("com.example.video")) { 10 }
        assertEquals(DistractionMonitor.Stop.AccessRevoked, stop)
        assertEquals(listOf("com.example.video", "com.example.video", "com.example.video"), recorded)
        assertEquals(1, platform.hints) // rate-limited to one hint per three minutes
    }
}
