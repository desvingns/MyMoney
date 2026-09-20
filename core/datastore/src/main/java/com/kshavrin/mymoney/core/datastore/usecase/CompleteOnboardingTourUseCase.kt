package com.kshavrin.mymoney.core.datastore.usecase

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import javax.inject.Inject

// Marks the first-launch spotlight tour as completed by stamping AppSettings.onboardingCompletedAt
// so the tour never reappears. Failure handling (Sentry) and cancellation are the caller's concern.
class CompleteOnboardingTourUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        suspend operator fun invoke() {
            appSettingsRepository.update { it.copy(onboardingCompletedAt = System.currentTimeMillis()) }
        }
    }
