package com.kshavrin.mymoney.feature.dictionaries.goals.fake

import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGoalRepository : GoalRepository {

    private val state = MutableStateFlow<List<Goal>>(emptyList())

    fun seed(items: List<Goal>) {
        state.value = items
    }

    override fun observeActive(): Flow<List<Goal>> =
        state.map { list -> list.filter { !it.isArchived } }

    override suspend fun findById(id: Long): Goal? =
        state.value.firstOrNull { it.id == id }

    override suspend fun upsert(goal: Goal): Long {
        val id = if (goal.id == 0L) {
            (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        } else {
            goal.id
        }
        state.value = state.value.filterNot { it.id == id } + goal.copy(id = id)
        return id
    }

    override suspend fun archive(id: Long) {
        state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
    }
}
