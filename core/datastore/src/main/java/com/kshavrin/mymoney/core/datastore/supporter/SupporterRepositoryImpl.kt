package com.kshavrin.mymoney.core.datastore.supporter

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupporterRepositoryImpl
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
    ) : SupporterRepository {
        override fun state(): Flow<SupporterState> =
            appSettingsRepository.settings
                .map { settings ->
                    SupporterState(
                        badgeEarned = settings.supporterBadgeEarned,
                        purchaseCount = settings.supportPurchaseCount,
                    )
                }.distinctUntilChanged()

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            runCatching {
                appSettingsRepository.update { settings ->
                    if (outcome.purchaseToken in settings.supporterPurchaseTokens) {
                        settings
                    } else {
                        settings.copy(
                            supporterBadgeEarned = true,
                            supportPurchaseCount = settings.supportPurchaseCount + 1,
                            supporterPurchaseTokens = settings.supporterPurchaseTokens + outcome.purchaseToken,
                        )
                    }
                }
            }

        override suspend fun mergeRemote(
            remoteCount: Int,
            remoteBadge: Boolean,
        ): Result<Unit> =
            runCatching {
                appSettingsRepository.update { settings ->
                    settings.copy(
                        supporterBadgeEarned = settings.supporterBadgeEarned || remoteBadge,
                        supportPurchaseCount = maxOf(settings.supportPurchaseCount, remoteCount),
                    )
                }
            }
    }
