package com.kshavrin.mymoney.feature.settings.language

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
class LanguageViewModel
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val localeController: AppLocaleController,
    ) : ViewModel() {
        private val _state = MutableStateFlow(LanguageState())
        val state: StateFlow<LanguageState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                appSettingsRepository.settings.collect { settings ->
                    _state.value = LanguageState(selected = AppLanguage.fromStored(settings.language))
                }
            }
        }

        fun onEvent(event: LanguageEvent) {
            when (event) {
                is LanguageEvent.LanguageSelected -> {
                    localeController.apply(event.language.localeTag)
                    viewModelScope.launch {
                        appSettingsRepository.update { it.copy(language = event.language.stored) }
                    }
                }
            }
        }
    }

enum class AppLanguage(
    val stored: String,
    val localeTag: String?,
) {
    System("system", null),
    English("en", "en"),
    Russian("ru", "ru"),
    ;

    companion object {
        fun fromStored(value: String): AppLanguage =
            entries.firstOrNull { it.stored == value } ?: System
    }
}

data class LanguageState(
    val selected: AppLanguage = AppLanguage.System,
)

sealed interface LanguageEvent {
    data class LanguageSelected(
        val language: AppLanguage,
    ) : LanguageEvent
}
