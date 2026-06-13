package com.kshavrin.mymoney.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DecisionRouterViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val initialDataSeeder: InitialDataSeeder,
        @ShowOnboarding private val showOnboarding: Boolean,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DecisionDestination.Pending)
        val state: StateFlow<DecisionDestination> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val settings = appSettingsRepository.settings.first()
                _state.value =
                    when {
                        settings.onboardingCompletedAt != null -> DecisionDestination.Dashboard
                        showOnboarding -> DecisionDestination.Splash
                        else -> {
                            skipOnboarding()
                            DecisionDestination.Dashboard
                        }
                    }
            }
        }

        private suspend fun skipOnboarding() {
            try {
                initialDataSeeder.seedIfNeeded(Instant.now())
                appSettingsRepository.update { it.copy(onboardingCompletedAt = System.currentTimeMillis()) }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                throwable.reportToSentry()
            }
        }
    }

enum class DecisionDestination { Pending, Splash, Dashboard }
