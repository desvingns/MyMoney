package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class BackupRotationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val treeUri = inputData.getString(KEY_TREE_URI) ?: return Result.failure()
        return backupRepository.rotateBackups(treeUri).fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (it is CancellationException) throw it
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        const val KEY_TREE_URI = "tree_uri"
        const val MAX_RETRIES = 3
        const val UNIQUE_ROTATION = "backup_rotation"
    }
}
