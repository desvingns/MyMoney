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

    suspend fun archive(id: Long)
}
