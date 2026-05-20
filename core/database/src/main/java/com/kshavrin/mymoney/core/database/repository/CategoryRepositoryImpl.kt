package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.CategoryDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CategoryRepository {

    override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
        dao.observeByKind(kind.name.lowercase()).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Category? = withContext(ioDispatcher) {
        dao.findById(id)?.toDomain()
    }

    override suspend fun upsert(category: Category): Long = withContext(ioDispatcher) {
        require(category.name.isNotBlank() && category.name.length <= 32) { "name must be non-blank and <= 32 chars" }
        dao.upsert(category.toEntity())
    }

    override suspend fun upsertAll(categories: List<Category>) = withContext(ioDispatcher) {
        dao.upsertAll(categories.map { it.toEntity() })
    }

    override suspend fun archive(id: Long) = withContext(ioDispatcher) {
        dao.archive(id)
    }
}
