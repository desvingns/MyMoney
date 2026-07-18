package com.kshavrin.mymoney.feature.settings.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.usecase.FactoryResetUseCase
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
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
class SettingsViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val factoryResetUseCase: FactoryResetUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsState())
        val state: StateFlow<SettingsState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<SettingsAction>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<SettingsAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                appSettingsRepository.settings.collect { settings ->
                    _state.value =
                        _state.value.copy(
                            themeMode = ThemeMode.fromStored(settings.themeMode),
                            language = AppLanguage.fromStored(settings.language),
                            soundEnabled = settings.soundEnabled,
                            hapticEnabled = settings.hapticEnabled,
                        )
                }
            }
        }

        fun onEvent(event: SettingsEvent) {
            when (event) {
                is SettingsEvent.SoundToggled ->
                    viewModelScope.launch {
                        appSettingsRepository.update { it.copy(soundEnabled = event.enabled) }
                    }
                is SettingsEvent.HapticToggled ->
                    viewModelScope.launch {
                        appSettingsRepository.update { it.copy(hapticEnabled = event.enabled) }
                    }
                SettingsEvent.FactoryResetRequested ->
                    _state.value =
                        _state.value.copy(
                            factoryResetStep = FactoryResetStep.Confirm,
                            factoryResetConfirmText = "",
                        )
                SettingsEvent.FactoryResetContinued ->
                    if (_state.value.factoryResetStep == FactoryResetStep.Confirm) {
                        _state.value = _state.value.copy(factoryResetStep = FactoryResetStep.TypeWord)
                    }
                is SettingsEvent.FactoryResetConfirmTextChanged ->
                    _state.value = _state.value.copy(factoryResetConfirmText = event.text)
                SettingsEvent.FactoryResetDismissed ->
                    _state.value =
                        _state.value.copy(
                            factoryResetStep = FactoryResetStep.Idle,
                            factoryResetConfirmText = "",
                        )
                SettingsEvent.FactoryResetConfirmed -> confirmFactoryReset()
            }
        }

        private fun confirmFactoryReset() {
            if (_state.value.factoryResetStep != FactoryResetStep.TypeWord) return
            if (_state.value.factoryResetConfirmText != FACTORY_RESET_CONFIRM_WORD) return
            if (_state.value.factoryResetInProgress) return
            viewModelScope.launch {
                _state.value = _state.value.copy(factoryResetInProgress = true)
                val result = factoryResetUseCase()
                result.exceptionOrNull()?.reportToSentry()
                _state.value =
                    _state.value.copy(
                        factoryResetInProgress = false,
                        factoryResetStep = FactoryResetStep.Idle,
                        factoryResetConfirmText = "",
                    )
                _actions.emit(SettingsAction.RestartToOnboardingAfterReset(hadFailures = result.isFailure))
            }
        }
    }

const val FACTORY_RESET_CONFIRM_WORD = "RESET"

enum class FactoryResetStep { Idle, Confirm, TypeWord }

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val factoryResetStep: FactoryResetStep = FactoryResetStep.Idle,
    val factoryResetConfirmText: String = "",
    val factoryResetInProgress: Boolean = false,
)

sealed interface SettingsEvent {
    data class SoundToggled(
        val enabled: Boolean,
    ) : SettingsEvent

    data class HapticToggled(
        val enabled: Boolean,
    ) : SettingsEvent

    data object FactoryResetRequested : SettingsEvent

    data object FactoryResetContinued : SettingsEvent

    data class FactoryResetConfirmTextChanged(
        val text: String,
    ) : SettingsEvent

    data object FactoryResetConfirmed : SettingsEvent

    data object FactoryResetDismissed : SettingsEvent
}

sealed interface SettingsAction {
    data class RestartToOnboardingAfterReset(
        val hadFailures: Boolean,
    ) : SettingsAction
}
