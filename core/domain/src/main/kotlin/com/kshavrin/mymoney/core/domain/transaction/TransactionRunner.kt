package com.kshavrin.mymoney.core.domain.transaction

interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
