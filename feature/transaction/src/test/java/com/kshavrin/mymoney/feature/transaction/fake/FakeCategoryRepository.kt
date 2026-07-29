package com.kshavrin.mymoney.feature.transaction.fake

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCategoryRepository : CategoryRepository {
    private val state = MutableStateFlow<List<Category>>(emptyList())

    fun seed(vararg categories: Category) {
        state.value = (state.value + categories).distinctBy { it.id }
    }

    override fun observeByKind(kind: CategoryKind): Flow<List<Category>> = state.asStateFlow()

    override fun observeAll(): Flow<List<Category>> = state.asStateFlow()

    override suspend fun findById(id: Long): Category? = state.value.firstOrNull { it.id == id }

    override suspend fun upsert(category: Category): Long {
        val id = if (category.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
        state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
        return id
    }

    override suspend fun upsertAll(categories: List<Category>) {
        categories.forEach { upsert(it) }
    }

    override suspend fun uuidForId(id: Long): String? = null

    override suspend fun idForUuid(uuid: String): Long? = null

    override suspend fun applySharedUpsert(
        category: Category,
        uuid: String,
        deviceId: String,
    ) = Unit

    override suspend fun applySharedArchive(uuid: String) = Unit

    override suspend fun archive(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }
}
