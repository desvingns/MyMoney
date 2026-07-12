package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    private val mutableSettings = MutableStateFlow(initial)
    override val settings = mutableSettings.asStateFlow()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = mutableSettings.value
        val next = transform(current)
        check(!current.firstPositiveSeen || next.firstPositiveSeen) {
            "firstPositiveSeen is monotonic - cannot flip true to false"
        }
        mutableSettings.value = next
    }

    override suspend fun reset() {
        mutableSettings.value = AppSettings()
    }

    fun seed(settings: AppSettings) {
        mutableSettings.value = settings
    }
}
