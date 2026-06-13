package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CategoryEditViewModel
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
        private val transactionRepository: TransactionRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val categoryId: Long = savedStateHandle.get<Long>("id") ?: -1L
        private val fromPicker: Boolean = savedStateHandle.get<Boolean>("fromPicker") ?: false
        private val initialKind: CategoryKind? =
            savedStateHandle
                .get<String>("kind")
                ?.let { runCatching { CategoryKind.fromString(it) }.getOrNull() }

        private val _state =
            MutableStateFlow(
                CategoryEditState(
                    isCreateMode = categoryId == -1L,
                    kind = if (categoryId == -1L && initialKind != null) initialKind else CategoryKind.Expense,
                ),
            )
        val state: StateFlow<CategoryEditState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<CategoryEditAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<CategoryEditAction> = _actions.asSharedFlow()

        init {
            if (categoryId != -1L) {
                viewModelScope.launch {
                    categoryRepository.findById(categoryId)?.let { existing ->
                        _state.value =
                            _state.value.copy(
                                name = existing.name,
                                kind = existing.kind,
                                iconKey = existing.iconKey,
                                colorHex = existing.colorHex,
                                sortOrder = existing.sortOrder,
                                isDefault = existing.isDefault,
                                isCreateMode = false,
                            )
                    }
                }
            }
        }

        fun onEvent(event: CategoryEditEvent) {
            when (event) {
                is CategoryEditEvent.NameChanged ->
                    _state.value = _state.value.copy(name = event.value, errorMessage = null)
                is CategoryEditEvent.KindChanged ->
                    _state.value = _state.value.copy(kind = event.value)
                is CategoryEditEvent.IconChanged ->
                    _state.value = _state.value.copy(iconKey = event.value)
                is CategoryEditEvent.ColorChanged ->
                    _state.value = _state.value.copy(colorHex = event.value)
                CategoryEditEvent.SaveClicked -> save()
                CategoryEditEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(CategoryEditAction.NavigateBack) }
                CategoryEditEvent.DeleteClicked -> attemptDelete()
                CategoryEditEvent.BlockedDeleteDismissed ->
                    _state.value = _state.value.copy(blockedDeleteCount = null)
            }
        }

        private fun save() {
            if (_state.value.isSaving) return
            val s = _state.value
            if (s.name.isBlank() || s.name.length > 32) {
                _state.value = s.copy(errorMessage = "name_required_or_too_long")
                return
            }
            _state.value = s.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val newId =
                        categoryRepository.upsert(
                            Category(
                                id = if (categoryId == -1L) 0L else categoryId,
                                name = s.name,
                                kind = s.kind,
                                iconKey = s.iconKey,
                                colorHex = s.colorHex,
                                sortOrder = s.sortOrder,
                                isDefault = s.isDefault,
                                isArchived = false,
                                createdAt = Instant.now(),
                            ),
                        )
                    _state.value = _state.value.copy(isSaving = false)
                    if (fromPicker && categoryId == -1L) {
                        _actions.emit(CategoryEditAction.NavigateBackToPickerWithId(newId))
                    } else {
                        _actions.emit(CategoryEditAction.NavigateBack)
                    }
                } catch (e: IllegalArgumentException) {
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "save_failed",
                        )
                } finally {
                    _state.value = _state.value.copy(isSaving = false)
                }
            }
        }

        private fun attemptDelete() {
            if (categoryId == -1L) return
            viewModelScope.launch {
                val count = transactionRepository.countByCategory(categoryId)
                if (count > 0) {
                    _state.value = _state.value.copy(blockedDeleteCount = count)
                } else {
                    categoryRepository.archive(categoryId)
                    _actions.emit(CategoryEditAction.NavigateBack)
                }
            }
        }
    }

data class CategoryEditState(
    val isCreateMode: Boolean = true,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.Expense,
    val iconKey: String = "ic_cat_food",
    val colorHex: String = "#7A9685",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val blockedDeleteCount: Int? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

sealed interface CategoryEditEvent {
    data class NameChanged(
        val value: String,
    ) : CategoryEditEvent

    data class KindChanged(
        val value: CategoryKind,
    ) : CategoryEditEvent

    data class IconChanged(
        val value: String,
    ) : CategoryEditEvent

    data class ColorChanged(
        val value: String,
    ) : CategoryEditEvent

    data object SaveClicked : CategoryEditEvent

    data object BackClicked : CategoryEditEvent

    data object DeleteClicked : CategoryEditEvent

    data object BlockedDeleteDismissed : CategoryEditEvent
}

sealed interface CategoryEditAction {
    data object NavigateBack : CategoryEditAction

    data class NavigateBackToPickerWithId(
        val id: Long,
    ) : CategoryEditAction
}
