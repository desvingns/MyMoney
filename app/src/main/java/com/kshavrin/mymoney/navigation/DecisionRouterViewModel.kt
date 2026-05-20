package com.kshavrin.mymoney.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DecisionRouterViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DecisionDestination.Pending)
    val state: StateFlow<DecisionDestination> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = appSettingsRepository.settings.first()
            _state.value = if (settings.onboardingCompletedAt == null) {
                DecisionDestination.Splash
            } else {
                DecisionDestination.Dashboard
            }
        }
    }
}

enum class DecisionDestination { Pending, Splash, Dashboard }
