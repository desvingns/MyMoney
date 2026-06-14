package com.kshavrin.mymoney.core.sync

interface WorkScheduler {
    suspend fun scheduleDailyJobs()
}
