package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.sync.SnapshotSyncRepository
import com.kshavrin.mymoney.core.sync.SyncTarget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: SnapshotSyncRepository,
    private val settings: AppSettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val target = inputData.getString(KEY_TARGET)
        val result = if (target != null) {
            orchestrator.push(SyncTarget.valueOf(target)).map { }
        } else {
            if (!settings.settings.first().autoSyncEnabled) return Result.success()
            orchestrator.autoSyncConnected()
        }
        return result.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() },
        )
    }

    companion object {
        const val KEY_TARGET = "target"
        const val MAX_RETRIES = 3
        const val UNIQUE_PERIODIC = "auto_sync"
        const val UNIQUE_MANUAL = "manual_sync"
    }
}
