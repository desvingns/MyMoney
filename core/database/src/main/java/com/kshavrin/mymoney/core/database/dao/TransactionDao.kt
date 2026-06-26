package com.kshavrin.mymoney.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.projection.CategoryGroupRow
import com.kshavrin.mymoney.core.database.projection.CategorySummaryRow
import com.kshavrin.mymoney.core.database.projection.TransactionDedupRow
import com.kshavrin.mymoney.core.database.projection.TransferRow
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT * FROM `transaction`
        WHERE account_id = :accountId
          AND occurred_at BETWEEN :from AND :to
          AND is_deleted = 0
        ORDER BY occurred_at DESC, created_at DESC
    """,
    )
    fun pagedByAccount(
        accountId: Long,
        from: Long,
        to: Long,
    ): PagingSource<Int, TransactionEntity>

    @Query(
        """
        SELECT * FROM `transaction`
        WHERE account_id = :accountId
          AND occurred_at BETWEEN :from AND :to
          AND is_deleted = 0
          AND (:categoryId IS NULL OR category_id = :categoryId)
        ORDER BY occurred_at DESC, created_at DESC
    """,
    )
    fun pagedByPeriod(
        accountId: Long,
        categoryId: Long?,
        from: Long,
        to: Long,
    ): PagingSource<Int, TransactionEntity>

    @Query(
        """
        SELECT * FROM `transaction`
        WHERE is_deleted = 0
        ORDER BY occurred_at DESC, created_at DESC
        LIMIT :limit
    """,
    )
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM `transaction`
        WHERE is_deleted = 0
        ORDER BY occurred_at DESC, created_at DESC
    """,
    )
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, c.color_hex AS colorHex,
               c.text_color AS textColorHex,
               c.icon_key AS iconKey, SUM(t.amount) AS total
        FROM `transaction` t
        INNER JOIN category c ON c.id = t.category_id
        WHERE t.account_id = :accountId
          AND t.occurred_at BETWEEN :from AND :to
          AND t.kind = :kind
          AND t.is_deleted = 0
        GROUP BY c.id
        ORDER BY total DESC
    """,
    )
    suspend fun getCategorySummary(
        accountId: Long,
        from: Long,
        to: Long,
        kind: String,
    ): List<CategorySummaryRow>

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS name, c.icon_key AS iconKey,
               c.color_hex AS colorHex, c.text_color AS textColorHex, c.kind AS kind,
               SUM(t.amount) AS total, COUNT(t.id) AS txCount
        FROM `transaction` t
        INNER JOIN category c ON c.id = t.category_id
        WHERE t.account_id = :accountId
          AND t.occurred_at BETWEEN :from AND :to
          AND t.kind IN ('expense', 'income')
          AND t.is_deleted = 0
        GROUP BY c.id
        ORDER BY total DESC
    """,
    )
    suspend fun getCategoryGroups(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<CategoryGroupRow>

    @Query(
        """
        SELECT * FROM `transaction`
        WHERE account_id = :accountId
          AND occurred_at BETWEEN :from AND :to
          AND is_deleted = 0
        ORDER BY occurred_at DESC, created_at DESC
    """,
    )
    suspend fun listByPeriod(
        accountId: Long,
        from: Long,
        to: Long,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT t.id AS id,
               af.name AS fromAccountName,
               at.name AS toAccountName,
               t.amount AS amount,
               t.to_amount AS toAmount,
               t.currency_id AS currencyId,
               t.occurred_at AS occurredAt,
               t.note AS note
        FROM `transaction` t
        INNER JOIN account af ON af.id = t.account_id
        INNER JOIN account at ON at.id = t.to_account_id
        WHERE t.kind = 'transfer'
          AND t.occurred_at BETWEEN :from AND :to
          AND t.is_deleted = 0
          AND (:accountId IS NULL OR t.account_id = :accountId OR t.to_account_id = :accountId)
        ORDER BY t.occurred_at DESC, t.created_at DESC
    """,
    )
    suspend fun getTransfers(
        accountId: Long?,
        from: Long,
        to: Long,
    ): List<TransferRow>

    @Query(
        """
        SELECT * FROM `transaction`
        WHERE is_deleted = 0
          AND (note LIKE '%' || :q || '%' COLLATE NOCASE
               OR category_id IN (SELECT id FROM category WHERE name LIKE '%' || :q || '%' COLLATE NOCASE))
        ORDER BY occurred_at DESC
        LIMIT :limit
    """,
    )
    suspend fun searchByNote(
        q: String,
        limit: Int = 200,
    ): List<TransactionEntity>

    @Query("SELECT * FROM `transaction` WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM `transaction` WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): TransactionEntity?

    @Query("UPDATE `transaction` SET is_deleted = 1, updated_at = :now WHERE uuid = :uuid")
    suspend fun softDeleteByUuid(
        uuid: String,
        now: Long,
    )

    @Query("SELECT * FROM `transaction`")
    suspend fun listForTimezoneNormalization(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity): Long

    @Query("UPDATE `transaction` SET occurred_at = :occurredAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOccurredAt(
        id: Long,
        occurredAt: Long,
        updatedAt: Long,
    )

    @Query("UPDATE `transaction` SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(
        id: Long,
        now: Long,
    )

    @Query("UPDATE `transaction` SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(
        id: Long,
        now: Long,
    )

    @Query("DELETE FROM `transaction` WHERE is_deleted = 1 AND updated_at < :before")
    suspend fun pruneDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM `transaction` WHERE account_id = :id AND is_deleted = 0")
    suspend fun countByAccount(id: Long): Int

    @Query("SELECT COUNT(*) FROM `transaction` WHERE category_id = :id AND is_deleted = 0")
    suspend fun countByCategory(id: Long): Int

    @Query("SELECT COUNT(*) FROM `transaction` WHERE currency_id = :id AND is_deleted = 0")
    suspend fun countByCurrency(id: Long): Int

    @Query(
        """
        SELECT a.name AS accountName,
               c.name AS categoryName,
               t.kind AS kind,
               t.amount AS amount,
               t.occurred_at AS occurredAt,
               t.note AS note
        FROM `transaction` t
        INNER JOIN account a ON a.id = t.account_id
        LEFT JOIN category c ON c.id = t.category_id
        WHERE t.is_deleted = 0
    """,
    )
    suspend fun listDedupRows(): List<TransactionDedupRow>

    @Query("DELETE FROM `transaction`")
    suspend fun deleteAll()

    // AppendManualMerge (D6): redirect every row of an import category onto the target category id so
    // records merge under one categoryId without spawning a duplicate category.
    @Query("UPDATE `transaction` SET category_id = :newId WHERE category_id = :oldId")
    suspend fun reassignCategory(
        oldId: Long,
        newId: Long,
    )

    // ReplaceCurrent + OrphanDecision.DeleteTransactions (D5): hard-delete a category's rows before
    // the category itself is removed.
    @Query("DELETE FROM `transaction` WHERE category_id = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)
}
