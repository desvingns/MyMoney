package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesListViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesListState())
    val state: StateFlow<CategoriesListState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<CategoriesListAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<CategoriesListAction> = _actions.asSharedFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { all ->
                _state.value = _state.value.copy(
                    expense = all
                        .filter { it.kind == CategoryKind.Expense && !it.isArchived }
                        .sortedBy { it.sortOrder },
                    income = all
                        .filter { it.kind == CategoryKind.Income && !it.isArchived }
                        .sortedBy { it.sortOrder },
                )
            }
        }
    }

    fun onEvent(event: CategoriesListEvent) {
        when (event) {
            CategoriesListEvent.AddClicked ->
                viewModelScope.launch { _actions.emit(CategoriesListAction.NavigateAdd) }
            is CategoriesListEvent.ItemClicked ->
                viewModelScope.launch { _actions.emit(CategoriesListAction.NavigateEdit(event.id)) }
            CategoriesListEvent.BackClicked ->
                viewModelScope.launch { _actions.emit(CategoriesListAction.NavigateBack) }
            is CategoriesListEvent.Reordered -> persistReorder(event.kind, event.newOrder)
        }
    }

    private fun persistReorder(kind: CategoryKind, newOrder: List<Category>) {
        val reindexed = newOrder.mapIndexed { idx, cat -> cat.copy(sortOrder = idx) }
        _state.value = when (kind) {
            CategoryKind.Expense -> _state.value.copy(expense = reindexed)
            CategoryKind.Income -> _state.value.copy(income = reindexed)
        }
        viewModelScope.launch {
            categoryRepository.upsertAll(reindexed)
        }
    }
}

data class CategoriesListState(
    val expense: List<Category> = emptyList(),
    val income: List<Category> = emptyList(),
)

sealed interface CategoriesListEvent {
    data object AddClicked : CategoriesListEvent
    data class ItemClicked(val id: Long) : CategoriesListEvent
    data object BackClicked : CategoriesListEvent
    data class Reordered(val kind: CategoryKind, val newOrder: List<Category>) : CategoriesListEvent
}

sealed interface CategoriesListAction {
    data object NavigateAdd : CategoriesListAction
    data class NavigateEdit(val id: Long) : CategoriesListAction
    data object NavigateBack : CategoriesListAction
}
