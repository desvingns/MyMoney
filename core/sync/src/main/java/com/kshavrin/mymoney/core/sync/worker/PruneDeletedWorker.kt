package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.temporal.ChronoUnit

@HiltWorker
class PruneDeletedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        transactionRepository.pruneDeleted(pruneCutoff(Instant.now()))
    }.fold(
        onSuccess = { Result.success() },
        onFailure = {
            if (it is CancellationException) throw it
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        },
    )

    companion object {
        const val MAX_RETRIES = 3
        const val UNIQUE_PERIODIC = "prune_deleted"
        const val RETENTION_DAYS = 30L

        internal fun pruneCutoff(now: Instant): Instant = now.minus(RETENTION_DAYS, ChronoUnit.DAYS)
    }
}
