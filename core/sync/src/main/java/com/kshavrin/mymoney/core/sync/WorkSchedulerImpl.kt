package com.kshavrin.mymoney.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.sync.worker.PruneDeletedWorker
import com.kshavrin.mymoney.core.sync.worker.RecurringWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appSettings: AppSettingsRepository,
        private val snapshotSync: SnapshotSync,
        private val syncScheduler: SyncScheduler,
    ) : WorkScheduler {
        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override suspend fun scheduleDailyJobs() {
            val recurring =
                PeriodicWorkRequestBuilder<RecurringWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(Constraints.NONE)
                    .build()
            workManager.enqueueUniquePeriodicWork(
                RecurringWorker.UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                recurring,
            )

            val prune =
                PeriodicWorkRequestBuilder<PruneDeletedWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    ).build()
            workManager.enqueueUniquePeriodicWork(
                PruneDeletedWorker.UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                prune,
            )

            val autoSyncEnabled = appSettings.settings.first().autoSyncEnabled
            if (autoSyncEnabled && snapshotSync.connectedTargets().isNotEmpty()) {
                syncScheduler.enablePeriodicSync()
            } else {
                syncScheduler.disablePeriodicSync()
            }
        }

        private companion object {
            const val PERIOD_HOURS = 24L
        }
    }
