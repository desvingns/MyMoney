package com.kshavrin.mymoney.feature.transaction.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryPickerViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: CategoryKind = savedStateHandle
        .get<String>(KEY_KIND)
        ?.let { runCatching { CategoryKind.fromString(it) }.getOrNull() }
        ?: CategoryKind.Expense

    private val amountPreview: String? = savedStateHandle.get<String>(KEY_AMOUNT_PREVIEW)

    private val _state = MutableStateFlow(
        CategoryPickerState(kind = kind, amountPreview = amountPreview),
    )
    val state: StateFlow<CategoryPickerState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<CategoryPickerAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<CategoryPickerAction> = _actions.asSharedFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { all ->
                _state.value = _state.value.copy(
                    categories = all
                        .filter { it.kind == kind && !it.isArchived }
                        .sortedBy { it.sortOrder },
                )
            }
        }
    }

    fun onEvent(event: CategoryPickerEvent) {
        when (event) {
            is CategoryPickerEvent.CategoryClicked ->
                viewModelScope.launch { _actions.emit(CategoryPickerAction.ReturnWithId(event.id)) }
            CategoryPickerEvent.AddCategoryClicked ->
                viewModelScope.launch { _actions.emit(CategoryPickerAction.AddCategory(kind)) }
            CategoryPickerEvent.BackClicked ->
                viewModelScope.launch { _actions.emit(CategoryPickerAction.NavigateBack) }
        }
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_AMOUNT_PREVIEW = "amountPreview"
        const val KEY_PICKED_CATEGORY_ID = "pickedCategoryId"
        const val KEY_CREATED_CATEGORY_ID = "createdCategoryId"
    }
}
