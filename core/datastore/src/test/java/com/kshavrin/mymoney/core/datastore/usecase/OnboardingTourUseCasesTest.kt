package com.kshavrin.mymoney.core.datastore.usecase

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTourUseCasesTest {

    @Test
    fun `pending is true when onboardingCompletedAt is null`() =
        runTest {
            val repository = FakeAppSettingsRepository(AppSettings(onboardingCompletedAt = null))
            val useCase = ObserveOnboardingTourPendingUseCase(repository)

            assertTrue(useCase())
        }

    @Test
    fun `pending is false when onboardingCompletedAt is set`() =
        runTest {
            val repository = FakeAppSettingsRepository(AppSettings(onboardingCompletedAt = 123L))
            val useCase = ObserveOnboardingTourPendingUseCase(repository)

            assertFalse(useCase())
        }

    @Test
    fun `complete stamps onboardingCompletedAt`() =
        runTest {
            val repository = FakeAppSettingsRepository(AppSettings(onboardingCompletedAt = null))
            val complete = CompleteOnboardingTourUseCase(repository)
            val pending = ObserveOnboardingTourPendingUseCase(repository)

            complete()

            assertNotNull(repository.current().onboardingCompletedAt)
            assertFalse("tour is no longer pending after completion", pending())
        }
}
