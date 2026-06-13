package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
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
class GoalsListViewModel
    @Inject
    constructor(
        private val goalRepository: GoalRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(GoalsListState())
        val state: StateFlow<GoalsListState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<GoalsListAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<GoalsListAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                goalRepository.observeActive().collect { goals ->
                    _state.value = _state.value.copy(rows = goals)
                }
            }
        }

        fun onEvent(event: GoalsListEvent) {
            when (event) {
                GoalsListEvent.AddClicked ->
                    viewModelScope.launch { _actions.emit(GoalsListAction.NavigateAdd) }
                is GoalsListEvent.ItemClicked ->
                    viewModelScope.launch { _actions.emit(GoalsListAction.NavigateEdit(event.id)) }
                GoalsListEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(GoalsListAction.NavigateBack) }
            }
        }
    }

data class GoalsListState(
    val rows: List<Goal> = emptyList(),
)

sealed interface GoalsListEvent {
    data object AddClicked : GoalsListEvent

    data class ItemClicked(
        val id: Long,
    ) : GoalsListEvent

    data object BackClicked : GoalsListEvent
}

sealed interface GoalsListAction {
    data object NavigateAdd : GoalsListAction

    data class NavigateEdit(
        val id: Long,
    ) : GoalsListAction

    data object NavigateBack : GoalsListAction
}
