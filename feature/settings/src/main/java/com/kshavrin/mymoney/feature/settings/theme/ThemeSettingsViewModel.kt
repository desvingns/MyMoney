package com.kshavrin.mymoney.feature.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.core.ui.theme.stored
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeSettingsViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ThemeSettingsState())
        val state: StateFlow<ThemeSettingsState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                appSettingsRepository.settings.collect { settings ->
                    _state.value = ThemeSettingsState(selected = ThemeMode.fromStored(settings.themeMode))
                }
            }
        }

        fun onEvent(event: ThemeSettingsEvent) {
            when (event) {
                is ThemeSettingsEvent.ModeSelected ->
                    viewModelScope.launch {
                        appSettingsRepository.update { it.copy(themeMode = event.mode.stored) }
                    }
            }
        }
    }

data class ThemeSettingsState(
    val selected: ThemeMode = ThemeMode.System,
)

sealed interface ThemeSettingsEvent {
    data class ModeSelected(
        val mode: ThemeMode,
    ) : ThemeSettingsEvent
}
