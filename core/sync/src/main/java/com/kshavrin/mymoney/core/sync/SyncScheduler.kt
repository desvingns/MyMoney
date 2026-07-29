package com.kshavrin.mymoney.core.sync

interface SyncScheduler {
    fun enablePeriodicSync()

    fun disablePeriodicSync()

    suspend fun cancelAllSync(): Result<Unit> {
        disablePeriodicSync()
        return Result.success(Unit)
    }

    fun syncNow(target: SyncTarget? = null)
}
