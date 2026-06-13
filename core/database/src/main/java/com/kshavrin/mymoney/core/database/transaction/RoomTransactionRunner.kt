package com.kshavrin.mymoney.core.database.transaction

import androidx.room.withTransaction
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransactionRunner
    @Inject
    constructor(
        private val database: MoneyDatabase,
    ) : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = database.withTransaction(block)
    }
