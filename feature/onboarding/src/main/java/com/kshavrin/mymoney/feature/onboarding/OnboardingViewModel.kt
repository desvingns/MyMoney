package com.kshavrin.mymoney.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun completeOnboarding() {
        if (_state.value.completed) return
        viewModelScope.launch {
            appSettingsRepository.update { it.copy(onboardingCompletedAt = System.currentTimeMillis()) }
            _state.value = _state.value.copy(completed = true)
        }
    }
}

data class OnboardingState(
    val completed: Boolean = false,
)
