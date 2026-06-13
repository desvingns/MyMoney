package com.kshavrin.mymoney.feature.settings.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsState())
        val state: StateFlow<SettingsState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                appSettingsRepository.settings.collect { settings ->
                    _state.value =
                        SettingsState(
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
            }
        }
    }

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
)

sealed interface SettingsEvent {
    data class SoundToggled(
        val enabled: Boolean,
    ) : SettingsEvent

    data class HapticToggled(
        val enabled: Boolean,
    ) : SettingsEvent
}
