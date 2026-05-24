package com.kshavrin.mymoney.core.sync

interface SyncScheduler {
    fun enablePeriodicSync()
    fun disablePeriodicSync()
    fun syncNow(target: SyncTarget? = null)
}
