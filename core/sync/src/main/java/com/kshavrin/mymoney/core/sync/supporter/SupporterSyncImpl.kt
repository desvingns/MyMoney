package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.supporter.SupporterPurchaseStore
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SupabaseSupporterApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupporterSyncImpl
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val api: SupabaseSupporterApi,
        private val supporterRepository: SupporterRepository,
        private val supporterPurchaseStore: SupporterPurchaseStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SupporterSync {
        private val deliveryMutex = Mutex()

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            withContext(dispatcher) {
                deliveryMutex.withLock {
                    recordPurchaseLocked(outcome).reportFailure()
                }
            }

        override suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            withContext(dispatcher) {
                deliveryMutex.withLock {
                    recordPurchaseLocked(outcome)
                        .getOrElse { return@withLock Result.failure<Unit>(it).reportFailure() }
                    val session = auth.currentSession()
                    if (session == null) {
                        Result.success(Unit)
                    } else {
                        deliverPendingPurchases()
                    }.reportFailure()
                }
            }

        override suspend fun restore(): Result<Unit> =
            withContext(dispatcher) {
                deliveryMutex.withLock {
                    val session = auth.currentSession() ?: return@withLock Result.success(Unit)
                    val accessToken =
                        auth.accessToken().getOrElse { throwable ->
                            if (throwable is CancellationException) throw throwable
                            return@withLock Result.failure(throwable)
                        }
                    val deliveryResult =
                        deliverPendingPurchases(
                            userId = session.user.id,
                            accessToken = accessToken,
                        )
                    if (deliveryResult.isFailure) {
                        return@withLock deliveryResult.reportFailure()
                    }
                    cancellationAwareResult {
                        val remote =
                            api
                                .getState(
                                    userId = session.user.id,
                                    accessToken = accessToken,
                                ).getOrThrow()
                        supporterRepository
                            .mergeRemote(
                                remoteCount = remote.purchaseCount,
                                remoteBadge = remote.badgeEarned,
                            ).getOrThrow()
                    }.reportFailure()
                }
            }

        private suspend fun recordPurchaseLocked(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            supporterPurchaseStore.recordPurchase(
                outcome = outcome,
                ownerUserId = auth.currentSession()?.user?.id,
            )

        private suspend fun deliverPendingPurchases(): Result<Unit> {
            val session = auth.currentSession() ?: return Result.success(Unit)
            val accessToken = auth.accessToken().getOrElse { return Result.failure(it) }
            return deliverPendingPurchases(
                userId = session.user.id,
                accessToken = accessToken,
            )
        }

        private suspend fun deliverPendingPurchases(
            userId: String,
            accessToken: String,
        ): Result<Unit> =
            cancellationAwareResult {
                val pendingPurchases = supporterPurchaseStore.pendingPurchases(userId).getOrThrow()
                pendingPurchases.forEach { outcome ->
                    api.postPurchase(userId, outcome, accessToken).getOrThrow()
                    supporterPurchaseStore.remove(userId, outcome.purchaseToken).getOrThrow()
                }
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

private fun <T> Result<T>.reportFailure(): Result<T> =
    onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
        throwable.reportToSentry()
    }
