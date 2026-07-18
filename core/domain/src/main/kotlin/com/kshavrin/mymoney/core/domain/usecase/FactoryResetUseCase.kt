package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.reset.FactoryResetGateway
import javax.inject.Inject

class FactoryResetUseCase
    @Inject
    constructor(
        private val gateway: FactoryResetGateway,
    ) {
        suspend operator fun invoke(): Result<Unit> {
            val failures = mutableListOf<Throwable>()
            runCatching { gateway.detachCloudSync() }.exceptionOrNull()?.let(failures::add)
            runCatching { gateway.wipeLocalData() }.exceptionOrNull()?.let(failures::add)
            runCatching { gateway.resetSettings() }.exceptionOrNull()?.let(failures::add)
            runCatching { gateway.clearSecrets() }.exceptionOrNull()?.let(failures::add)
            return if (failures.isEmpty()) {
                Result.success(Unit)
            } else {
                val primary = failures.first()
                failures.drop(1).forEach(primary::addSuppressed)
                Result.failure(primary)
            }
        }
    }
