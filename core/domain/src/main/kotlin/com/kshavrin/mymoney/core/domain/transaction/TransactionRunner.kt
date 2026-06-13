package com.kshavrin.mymoney.core.domain.transaction

/**
 * Runs a block of repository operations inside a single database transaction so that a failure
 * mid-way leaves no partially-committed state. Defined in the domain layer; the database layer
 * provides a Room-backed implementation.
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
