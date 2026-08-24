package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.domain.supporter.SupporterStateSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSupporterStateUseCase
    @Inject
    constructor(
        private val supporterStateSource: SupporterStateSource,
    ) {
        operator fun invoke(): Flow<SupporterState> = supporterStateSource.state()
    }
