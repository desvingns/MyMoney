package com.kshavrin.mymoney.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val initialDataSeeder: InitialDataSeeder,
) : ViewModel() {

    private val _state = MutableStateFlow(SplashState())
    val state: StateFlow<SplashState> = _state.asStateFlow()

    fun initialise() {
        if (_state.value.destination != SplashDestination.Pending) return
        viewModelScope.launch {
            initialDataSeeder.seedIfNeeded(Instant.now())
            _state.value = _state.value.copy(destination = SplashDestination.Onboarding)
        }
    }
}

data class SplashState(
    val destination: SplashDestination = SplashDestination.Pending,
)

enum class SplashDestination { Pending, Onboarding }
