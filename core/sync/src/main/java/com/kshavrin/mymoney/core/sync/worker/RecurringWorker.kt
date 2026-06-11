package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.domain.usecase.GenerateDueRecurringUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.Instant

@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generateDueRecurring: GenerateDueRecurringUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching { generateDueRecurring(Instant.now()) }.fold(
        onSuccess = { Result.success() },
        onFailure = {
            if (it is CancellationException) throw it
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        },
    )

    companion object {
        const val MAX_RETRIES = 3
        const val UNIQUE_PERIODIC = "recurring"
    }
}
