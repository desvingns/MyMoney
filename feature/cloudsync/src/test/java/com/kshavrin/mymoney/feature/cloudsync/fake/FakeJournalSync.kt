package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.sync.JournalSync

/**
 * Hand-written fake for [JournalSync]. Records calls and lets a test fail [syncNow] so the
 * ViewModel's error path can be asserted.
 */
class FakeJournalSync : JournalSync {
    var syncNowCalls = 0
    var pushCalls = 0
    var pullCalls = 0
    var syncNowError: Throwable? = null

    override suspend fun push() {
        pushCalls++
    }

    override suspend fun pull() {
        pullCalls++
    }

    override suspend fun syncNow() {
        syncNowCalls++
        syncNowError?.let { throw it }
    }
}
