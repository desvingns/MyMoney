package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.AccountDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountRepository {

    override fun observeActive(): Flow<List<Account>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Account? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun findDefault(): Account? = withContext(ioDispatcher) {
        dao.findDefault()?.toDomain()
    }

    override suspend fun computeBalance(accountId: Long): BigDecimal = withContext(ioDispatcher) {
        BigDecimal.valueOf(dao.computeBalance(accountId))
    }

    override suspend fun upsert(account: Account): Long = withContext(ioDispatcher) {
        require(account.name.isNotBlank() && account.name.length <= 32) { "name must be non-blank and <= 32 chars" }
        require(account.colorHex.matches(COLOR_HEX_REGEX)) { "colorHex must match ${COLOR_HEX_REGEX.pattern}; got: ${account.colorHex}" }
        dao.upsert(account.toEntity())
    }

    override suspend fun archive(id: Long) = withContext(ioDispatcher) {
        dao.archive(id)
    }

    override suspend fun setDefault(id: Long) = withContext(ioDispatcher) {
        dao.setDefault(id)
    }

    override suspend fun countByCurrency(currencyId: Long): Int = withContext(ioDispatcher) {
        dao.countByCurrency(currencyId)
    }

    private companion object {
        val COLOR_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
