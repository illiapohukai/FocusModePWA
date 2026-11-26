package io.github.illiapohukai.focusmode.core.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.github.illiapohukai.focusmode.core.model.SyncFailure
import io.github.illiapohukai.focusmode.core.model.SyncResult
import io.github.illiapohukai.focusmode.core.testing.FakeAccountRepository
import io.github.illiapohukai.focusmode.core.testing.FakeSyncRepository
import io.github.illiapohukai.focusmode.core.testing.FakeUsers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** One unique, account-aware, network-constrained sync job with bounded retries. */
@RunWith(AndroidJUnit4::class)
class SyncWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val accounts = FakeAccountRepository()
    private val sync = FakeSyncRepository()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    private fun infos(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(AndroidSyncScheduler.WORK_NAME).get()

    @Test fun manyChangesQueueOneUniqueNetworkJob() {
        val scheduler = AndroidSyncScheduler(context)
        repeat(25) { scheduler.schedule(FakeUsers.ALICE.uid, delayMs = 3_000) }
        val all = infos()
        assertEquals("no job per record", 1, all.size)
        assertEquals(WorkInfo.State.ENQUEUED, all.single().state)
        assertEquals(NetworkType.CONNECTED, all.single().constraints.requiredNetworkType)
        scheduler.cancel()
        assertEquals(WorkInfo.State.CANCELLED, infos().single().state)
    }

    private fun worker(uid: String, attempt: Int = 0) = TestListenableWorkerBuilder<SyncWorker>(context)
        .setInputData(workDataOf(SyncWorker.KEY_UID to uid))
        .setRunAttemptCount(attempt)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                SyncWorker(appContext, workerParameters, sync, accounts)
        })
        .build()

    @Test fun aJobForAnotherOrSignedOutAccountNeverSyncs() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(FakeUsers.ALICE.uid).doWork()) // signed out
        accounts.signInAs(FakeUsers.BOB)
        assertEquals(ListenableWorker.Result.success(), worker(FakeUsers.ALICE.uid).doWork()) // another account
        assertTrue(sync.calls.isEmpty())
    }

    @Test fun retriesAreBoundedAndOnlyForRetryableFailures() = runBlocking {
        accounts.signInAs(FakeUsers.ALICE)
        assertEquals(ListenableWorker.Result.success(), worker(FakeUsers.ALICE.uid).doWork())
        sync.nextResult = SyncResult.Failed(SyncFailure.OFFLINE, retryable = true)
        assertEquals(ListenableWorker.Result.retry(), worker(FakeUsers.ALICE.uid).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(FakeUsers.ALICE.uid, attempt = SyncWorker.MAX_RUN_ATTEMPTS).doWork())
        sync.nextResult = SyncResult.Failed(SyncFailure.PERMISSION_DENIED, retryable = false)
        assertEquals(ListenableWorker.Result.success(), worker(FakeUsers.ALICE.uid).doWork())
        assertEquals(4, sync.calls.count { it == "sync" })
    }
}
