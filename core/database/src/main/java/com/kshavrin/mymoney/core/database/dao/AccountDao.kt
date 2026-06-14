package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM account WHERE is_archived = 0 ORDER BY sort_order")
    abstract fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT * FROM account WHERE is_default = 1 LIMIT 1")
    abstract suspend fun findDefault(): AccountEntity?

    @Query(
        """
        SELECT a.initial_balance
             + COALESCE((SELECT SUM(amount) FROM `transaction` WHERE account_id = :id AND kind = 'income'   AND is_deleted = 0), 0)
             - COALESCE((SELECT SUM(amount) FROM `transaction` WHERE account_id = :id AND kind = 'expense'  AND is_deleted = 0), 0)
             - COALESCE((SELECT SUM(amount) FROM `transaction` WHERE account_id = :id AND kind = 'transfer' AND is_deleted = 0), 0)
             + COALESCE((SELECT SUM(to_amount) FROM `transaction` WHERE to_account_id = :id AND kind = 'transfer' AND is_deleted = 0), 0)
        FROM account a WHERE a.id = :id
    """,
    )
    abstract suspend fun computeBalance(id: Long): Double

    @Upsert
    abstract suspend fun upsert(account: AccountEntity): Long

    @Query("UPDATE account SET is_archived = 1 WHERE id = :id")
    abstract suspend fun archive(id: Long)

    @Transaction
    open suspend fun setDefault(id: Long) {
        clearDefaults()
        markDefault(id)
    }

    @Query("UPDATE account SET is_default = 0")
    protected abstract suspend fun clearDefaults()

    @Query("UPDATE account SET is_default = 1 WHERE id = :id")
    protected abstract suspend fun markDefault(id: Long)

    @Query("SELECT COUNT(*) FROM account WHERE currency_id = :id AND is_archived = 0")
    abstract suspend fun countByCurrency(id: Long): Int

    @Query("DELETE FROM account")
    abstract suspend fun deleteAll()
}
