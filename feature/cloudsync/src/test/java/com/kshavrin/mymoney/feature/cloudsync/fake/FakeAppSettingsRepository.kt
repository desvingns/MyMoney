package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings = _settings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
    }

    fun seed(settings: AppSettings) {
        _settings.value = settings
    }
}
