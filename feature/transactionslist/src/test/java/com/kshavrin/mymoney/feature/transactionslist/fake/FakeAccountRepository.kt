package com.kshavrin.mymoney.feature.transactionslist.fake

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

class FakeAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())

    fun seed(vararg items: Account) {
        accounts.value = (accounts.value + items).distinctBy { it.id }
    }

    override fun observeActive(): Flow<List<Account>> = accounts.asStateFlow()

    override suspend fun findById(id: Long): Account? = accounts.value.firstOrNull { it.id == id }

    override suspend fun findDefault(): Account? = accounts.value.firstOrNull { it.isDefault }

    override suspend fun computeBalance(accountId: Long): BigDecimal = BigDecimal.ZERO

    override suspend fun upsert(account: Account): Long = account.id

    override suspend fun archive(id: Long) = Unit

    override suspend fun setDefault(id: Long) = Unit

    override suspend fun countByCurrency(currencyId: Long): Int =
        accounts.value.count { it.currencyId == currencyId }
}
