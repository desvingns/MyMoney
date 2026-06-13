package com.kshavrin.mymoney

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppThemeViewModel
    @Inject
    constructor(
        appSettingsRepository: AppSettingsRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            appSettingsRepository.settings
                .map { ThemeMode.fromStored(it.themeMode) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = ThemeMode.System,
                )
    }
