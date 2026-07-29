package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.SyncExecutionGate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val journalSync: JournalSync,
        private val settings: AppSettingsRepository,
        private val executionGate: SyncExecutionGate,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            executionGate.withExclusive {
                val manual = inputData.getString(KEY_TARGET) != null
                if (!manual && !settings.settings.first().autoSyncEnabled) {
                    Result.success()
                } else {
                    runCatching { journalSync.syncNow() }.fold(
                        onSuccess = { Result.success() },
                        onFailure = {
                            if (it is CancellationException) throw it
                            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                        },
                    )
                }
            }

        companion object {
            const val KEY_TARGET = "target"
            const val MAX_RETRIES = 3
            const val UNIQUE_PERIODIC = "auto_sync"
            const val UNIQUE_MANUAL = "manual_sync"
        }
    }
