package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Account
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>

    suspend fun findById(id: Long): Account?

    suspend fun findDefault(): Account?

    suspend fun computeBalance(accountId: Long): BigDecimal

    suspend fun upsert(account: Account): Long

    /** Stable cross-device uuid for a local row, for publishing it to a Shared workspace. */
    suspend fun uuidForId(id: Long): String?

    /** Resolve a local Room id from a cross-device uuid when applying Shared foreign-key references. */
    suspend fun idForUuid(uuid: String): Long?

    /**
     * Apply a Shared-workspace upsert keyed by [uuid]: update the matching local row in place
     * (preserving its own local Room id), or insert a new row carrying [uuid]. Writes NO
     * private-cloud journal entry.
     */
    suspend fun applySharedUpsert(
        account: Account,
        uuid: String,
        deviceId: String,
    )

    /** Apply a Shared-workspace archive keyed by [uuid] without a private-cloud journal entry. */
    suspend fun applySharedArchive(uuid: String)

    suspend fun archive(id: Long)

    suspend fun setDefault(id: Long)

    suspend fun countByCurrency(currencyId: Long): Int
}
