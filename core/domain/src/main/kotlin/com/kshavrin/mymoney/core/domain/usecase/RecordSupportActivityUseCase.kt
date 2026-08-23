package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import javax.inject.Inject

class RecordSupportActivityUseCase
    @Inject
    constructor(
        private val supporterRepository: SupporterRepository,
    ) {
        suspend operator fun invoke(): Result<Unit> = supporterRepository.recordSupportActivity()
    }
