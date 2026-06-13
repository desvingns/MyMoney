package com.kshavrin.mymoney.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kshavrin.mymoney.core.sync.worker.PruneDeletedWorker
import com.kshavrin.mymoney.core.sync.worker.RecurringWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : WorkScheduler {
        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override fun scheduleDailyJobs() {
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
        }

        private companion object {
            const val PERIOD_HOURS = 24L
        }
    }
