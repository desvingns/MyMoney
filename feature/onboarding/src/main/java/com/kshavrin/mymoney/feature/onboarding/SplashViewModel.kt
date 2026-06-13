package com.kshavrin.mymoney.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        private val initialDataSeeder: InitialDataSeeder,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SplashState())
        val state: StateFlow<SplashState> = _state.asStateFlow()

        fun initialise() {
            if (_state.value.destination != SplashDestination.Pending) return
            seed()
        }

        fun retry() {
            if (!_state.value.seedFailed) return
            _state.value = _state.value.copy(seedFailed = false)
            seed()
        }

        private fun seed() {
            viewModelScope.launch {
                runCatching { initialDataSeeder.seedIfNeeded(Instant.now()) }
                    .onSuccess {
                        _state.value = _state.value.copy(destination = SplashDestination.Onboarding)
                    }.onFailure { throwable ->
                        throwable.reportToSentry()
                        _state.value = _state.value.copy(seedFailed = true)
                    }
            }
        }
    }

data class SplashState(
    val destination: SplashDestination = SplashDestination.Pending,
    val seedFailed: Boolean = false,
)

enum class SplashDestination { Pending, Onboarding }
