package com.kshavrin.mymoney.core.datastore.usecase

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// True when the first-launch spotlight tour is still pending, i.e. onboarding has never been
// completed (AppSettings.onboardingCompletedAt == null). A single first-read of the settings flow.
class ObserveOnboardingTourPendingUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) {
        suspend operator fun invoke(): Boolean = appSettingsRepository.settings.first().onboardingCompletedAt == null
    }
