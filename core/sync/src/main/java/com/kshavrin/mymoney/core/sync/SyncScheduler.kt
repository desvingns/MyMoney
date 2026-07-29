package com.kshavrin.mymoney.core.sync

interface SyncScheduler {
    fun enablePeriodicSync()

    fun disablePeriodicSync()

    fun cancelAllSync() {
        disablePeriodicSync()
    }

    fun syncNow(target: SyncTarget? = null)
}
