package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget

class FakeSyncScheduler : SyncScheduler {

    var enableCount = 0
        private set
    var disableCount = 0
        private set
    val syncNowCalls = mutableListOf<SyncTarget?>()

    override fun enablePeriodicSync() {
        enableCount++
    }

    override fun disablePeriodicSync() {
        disableCount++
    }

    override fun syncNow(target: SyncTarget?) {
        syncNowCalls += target
    }
}
