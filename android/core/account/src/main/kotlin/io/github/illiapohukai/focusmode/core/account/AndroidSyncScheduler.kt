package io.github.illiapohukai.focusmode.core.account

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.illiapohukai.focusmode.core.domain.account.AccountRepository
import io.github.illiapohukai.focusmode.core.domain.sync.SyncRepository
import io.github.illiapohukai.focusmode.core.domain.sync.SyncScheduler
import io.github.illiapohukai.focusmode.core.model.SyncResult
import io.github.illiapohukai.focusmode.core.model.activeUid
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background synchronization (v21): exactly one unique job ("focus-account-sync"), only with a
 * network connection, exponential backoff, coalesced (KEEP: a burst of changes queues one job).
 * The job carries the uid it was scheduled for; it does nothing for any other account. Sign-out,
 * account switch, deletion and local erase cancel it.
 */
@Singleton
class AndroidSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : SyncScheduler {
    override fun schedule(uid: String, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_UID to uid))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "focus-account-sync"
        const val TAG = "focus-account-sync"
        const val BACKOFF_SECONDS = 30L
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
    private val accounts: AccountRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID) ?: return Result.success()
        // Account-aware: a job scheduled for another (or a signed-out) account never runs a sync.
        if (accounts.accountState.value.activeUid != uid) return Result.success()
        return when (val r = sync.synchronize()) {
            is SyncResult.Success, is SyncResult.Skipped -> Result.success()
            is SyncResult.Failed -> if (r.retryable && runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_UID = "uid"
        /** Bounded: after this many attempts the job ends; the outbox keeps its own backoff and parking. */
        const val MAX_RUN_ATTEMPTS = 6
    }
}
