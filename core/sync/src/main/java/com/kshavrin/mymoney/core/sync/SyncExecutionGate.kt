package com.kshavrin.mymoney.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncExecutionGate
    @Inject
    constructor() {
        private val mutex = Mutex()

        suspend fun <T> withExclusive(block: suspend () -> T): T = mutex.withLock { block() }
    }
