package com.kshavrin.mymoney.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category WHERE kind = :kind AND is_archived = 0 ORDER BY sort_order")
    fun observeByKind(kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE uuid = :uuid LIMIT 1")
    suspend fun findByUuid(uuid: String): CategoryEntity?

    @Query("UPDATE category SET is_archived = 1 WHERE uuid = :uuid")
    suspend fun archiveByUuid(uuid: String)

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE category SET is_archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE category SET name = :name WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
    )

    // App categories carry no currency link; ReplaceAll (O2) clears them wholesale, currencies and
    // AppSettings live in separate stores and are preserved. SPEC 03 reuses this for its category
    // strategies.
    @Query("DELETE FROM category")
    suspend fun deleteAll()

    // ReplaceCurrent (D5): deletes only the existing categories the wizard did not elect to keep.
    @Query("DELETE FROM category WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
