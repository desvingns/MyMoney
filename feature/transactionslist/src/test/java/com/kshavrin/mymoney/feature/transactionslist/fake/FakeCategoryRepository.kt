package com.kshavrin.mymoney.feature.transactionslist.fake

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeCategoryRepository : CategoryRepository {
    private val categories = MutableStateFlow<List<Category>>(emptyList())

    fun seed(vararg items: Category) {
        categories.value = (categories.value + items).distinctBy { it.id }
    }

    override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
        categories.map { list -> list.filter { it.kind == kind } }

    override fun observeAll(): Flow<List<Category>> = categories.asStateFlow()

    override suspend fun findById(id: Long): Category? = categories.value.firstOrNull { it.id == id }

    override suspend fun upsert(category: Category): Long = category.id

    override suspend fun upsertAll(categories: List<Category>) = Unit

    override suspend fun archive(id: Long) = Unit
}
