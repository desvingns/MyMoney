package com.kshavrin.mymoney.feature.onboarding

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.feature.onboarding.util.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `completeOnboarding persists onboardingCompletedAt and marks state completed`() =
        runTest {
            val settingsRepository = FakeAppSettingsRepository()
            val viewModel = OnboardingViewModel(settingsRepository)

            viewModel.state.test {
                assertFalse(awaitItem().completed)

                viewModel.completeOnboarding()
                advanceUntilIdle()

                assertEquals(true, awaitItem().completed)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, settingsRepository.updateCalls)
            assertNotNull(settingsRepository.currentSettings.onboardingCompletedAt)
        }

    @Test
    fun `completeOnboarding ignores repeated completion after the first success`() =
        runTest {
            val settingsRepository = FakeAppSettingsRepository()
            val viewModel = OnboardingViewModel(settingsRepository)

            viewModel.completeOnboarding()
            advanceUntilIdle()
            val completedAt = settingsRepository.currentSettings.onboardingCompletedAt

            viewModel.completeOnboarding()
            advanceUntilIdle()

            assertEquals(1, settingsRepository.updateCalls)
            assertEquals(completedAt, settingsRepository.currentSettings.onboardingCompletedAt)
        }

    private class FakeAppSettingsRepository(
        initial: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        private val state = MutableStateFlow(initial)

        var updateCalls: Int = 0
            private set

        override val settings: StateFlow<AppSettings> = state.asStateFlow()

        val currentSettings: AppSettings
            get() = state.value

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            updateCalls += 1
            state.value = transform(state.value)
        }
    }
}
