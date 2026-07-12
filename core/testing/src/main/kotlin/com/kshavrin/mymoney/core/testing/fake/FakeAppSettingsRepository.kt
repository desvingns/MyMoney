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
    var resetCalls: Int = 0
        private set
    private var resetFailure: Throwable? = null

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = mutableSettings.value
        val next = transform(current)
        check(!current.firstPositiveSeen || next.firstPositiveSeen) {
            "firstPositiveSeen is monotonic - cannot flip true to false"
        }
        mutableSettings.value = next
    }

    override suspend fun reset() {
        resetCalls += 1
        resetFailure?.let { throw it }
        mutableSettings.value = AppSettings()
    }

    fun seed(settings: AppSettings) {
        mutableSettings.value = settings
    }

    fun current(): AppSettings = mutableSettings.value

    fun simulateResetFailure(throwable: Throwable = RuntimeException("settings reset failed")) {
        resetFailure = throwable
    }
}
