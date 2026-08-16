package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveAdRewardStateUseCase
    @Inject
    constructor(
        private val adRewardRepository: AdRewardRepository,
    ) {
        val state: StateFlow<AdRewardState?>
            get() = adRewardRepository.state

        suspend fun refresh(): Result<AdRewardState> = adRewardRepository.refresh()

        suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome =
            adRewardRepository.awaitConfirmation(previous)
    }
