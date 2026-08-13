package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class EntitlementRefreshWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val entitlementRepository: EntitlementRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            runCatching { entitlementRepository.refresh().getOrThrow() }.fold(
                onSuccess = { Result.success() },
                onFailure = {
                    if (it is CancellationException) throw it
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                },
            )

        companion object {
            const val MAX_RETRIES = 3
            const val UNIQUE_PERIODIC = "entitlement_refresh"
        }
    }
