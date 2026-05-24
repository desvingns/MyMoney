package com.kshavrin.mymoney.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kshavrin.mymoney.core.sync.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun enablePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun disablePeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC)
    }

    override fun syncNow(target: SyncTarget?) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_TARGET to target?.name))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_MANUAL,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val PERIOD_HOURS = 6L
    }
}
