package com.kshavrin.mymoney.core.datastore.supporter

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.billing.COFFEE_LARGE_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.COFFEE_SMALL_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
                .onStart { backfillPurchaseSplitIfNeeded() }
                .map { settings ->
                    SupporterState(
                        badgeEarned = settings.supporterBadgeEarned,
                        purchaseCount = settings.supportPurchaseCount,
                        smallCoffeeCount = settings.supportPurchaseCountSmall,
                        largeCoffeeCount = settings.supportPurchaseCountLarge,
                    )
                }.distinctUntilChanged()

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            cancellationAwareResult {
                appSettingsRepository.update { settings ->
                    if (outcome.purchaseToken in settings.supporterPurchaseTokens) {
                        settings
                    } else {
                        val backfilledSettings = settings.withBackfilledPurchaseSplit()
                        backfilledSettings.copy(
                            supporterBadgeEarned = true,
                            supportPurchaseCount = backfilledSettings.supportPurchaseCount + 1,
                            supportPurchaseCountSmall =
                                backfilledSettings.supportPurchaseCountSmall +
                                    if (outcome.productId == COFFEE_SMALL_PRODUCT_ID) 1 else 0,
                            supportPurchaseCountLarge =
                                backfilledSettings.supportPurchaseCountLarge +
                                    if (outcome.productId == COFFEE_LARGE_PRODUCT_ID) 1 else 0,
                            supporterPurchaseTokens = backfilledSettings.supporterPurchaseTokens + outcome.purchaseToken,
                        )
                    }
                }
            }

        override suspend fun mergeRemote(
            remoteCount: Int,
            remoteBadge: Boolean,
        ): Result<Unit> =
            cancellationAwareResult {
                appSettingsRepository.update { settings ->
                    val backfilledSettings = settings.withBackfilledPurchaseSplit()
                    backfilledSettings
                        .copy(
                            supporterBadgeEarned = backfilledSettings.supporterBadgeEarned || remoteBadge,
                            supportPurchaseCount = maxOf(backfilledSettings.supportPurchaseCount, remoteCount),
                        )
                }
            }

        private suspend fun backfillPurchaseSplitIfNeeded() {
            if (!appSettingsRepository.settings.first().supportPurchaseSplitBackfilled) {
                appSettingsRepository.update { settings -> settings.withBackfilledPurchaseSplit() }
            }
        }

        private fun AppSettings.withBackfilledPurchaseSplit(): AppSettings =
            if (supportPurchaseSplitBackfilled) {
                this
            } else {
                copy(
                    supportPurchaseCountSmall = maxOf(supportPurchaseCountSmall, supportPurchaseCount),
                    supportPurchaseSplitBackfilled = true,
                )
            }

        private suspend fun <T> cancellationAwareResult(block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
    }
