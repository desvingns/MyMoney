package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Account
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>
    suspend fun findById(id: Long): Account?
    suspend fun findDefault(): Account?
    suspend fun computeBalance(accountId: Long): BigDecimal
    suspend fun upsert(account: Account): Long
    suspend fun archive(id: Long)
    suspend fun setDefault(id: Long)
    suspend fun countByCurrency(currencyId: Long): Int
}
