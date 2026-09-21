package com.kshavrin.mymoney.core.datastore.usecase

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class CompleteOnboardingTourUseCaseTest {

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
