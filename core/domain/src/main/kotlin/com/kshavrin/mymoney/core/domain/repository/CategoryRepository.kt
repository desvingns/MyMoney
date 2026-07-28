package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeByKind(kind: CategoryKind): Flow<List<Category>>

    fun observeAll(): Flow<List<Category>>

    suspend fun findById(id: Long): Category?

    suspend fun upsert(category: Category): Long

    suspend fun upsertAll(categories: List<Category>)

    /** Stable cross-device uuid for a local row, for publishing it to a Shared workspace. */
    suspend fun uuidForId(id: Long): String?

    /**
     * Apply a Shared-workspace upsert keyed by [uuid]: update the matching local row in place
     * (preserving its own local Room id), or insert a new row carrying [uuid]. Writes NO
     * private-cloud journal entry.
     */
    suspend fun applySharedUpsert(
        category: Category,
        uuid: String,
        deviceId: String,
    )

    /** Apply a Shared-workspace archive keyed by [uuid] without a private-cloud journal entry. */
    suspend fun applySharedArchive(uuid: String)

    suspend fun archive(id: Long)
}
