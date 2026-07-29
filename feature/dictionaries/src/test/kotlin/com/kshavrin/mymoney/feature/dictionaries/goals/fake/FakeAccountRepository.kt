package com.kshavrin.mymoney.feature.dictionaries.goals.fake

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

class FakeAccountRepository : AccountRepository {
    private val state = MutableStateFlow<List<Account>>(emptyList())
    private val balances = mutableMapOf<Long, BigDecimal>()

    fun seed(vararg accounts: Account) {
        state.value = (state.value + accounts).distinctBy { it.id }
    }

    fun setBalance(
        accountId: Long,
        balance: BigDecimal,
    ) {
        balances[accountId] = balance
    }

    override fun observeActive(): Flow<List<Account>> = state.asStateFlow()

    override suspend fun listAllIncludingArchived(): List<Account> = state.value

    override suspend fun findById(id: Long): Account? =
        state.value.firstOrNull { it.id == id }

    override suspend fun findDefault(): Account? =
        state.value.firstOrNull { it.isDefault }

    override suspend fun computeBalance(accountId: Long): BigDecimal =
        balances[accountId] ?: (state.value.firstOrNull { it.id == accountId }?.initialBalance ?: BigDecimal.ZERO)

    override suspend fun upsert(account: Account): Long {
        val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
        state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
        return id
    }

    override suspend fun uuidForId(id: Long): String? = null

    override suspend fun idForUuid(uuid: String): Long? = null

    override suspend fun applySharedUpsert(
        account: Account,
        uuid: String,
        deviceId: String,
    ) = Unit

    override suspend fun applySharedArchive(uuid: String) = Unit

    override suspend fun archive(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }

    override suspend fun setDefault(id: Long) {
        state.value = state.value.map { it.copy(isDefault = it.id == id) }
    }

    override suspend fun countByCurrency(currencyId: Long): Int =
        state.value.count { it.currencyId == currencyId && !it.isArchived }
}
