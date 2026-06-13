package com.kshavrin.mymoney.feature.settings.fake

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings = _settings.asStateFlow()
    var resetCalls: Int = 0
        private set
    private var resetFailure: Throwable? = null

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    override suspend fun reset() {
        resetCalls += 1
        resetFailure?.let { throw it }
        _settings.value = AppSettings()
    }

    fun seed(settings: AppSettings) {
        _settings.value = settings
    }

    fun simulateResetFailure(throwable: Throwable = RuntimeException("settings reset failed")) {
        resetFailure = throwable
    }
}
