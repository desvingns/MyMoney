package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SupabaseSupporterApi
import kotlinx.coroutines.CoroutineDispatcher
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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SupporterSync {
        override suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            withContext(dispatcher) {
                val session = auth.currentSession() ?: return@withContext Result.success(Unit)
                val accessToken = auth.accessToken().getOrElse { return@withContext Result.failure(it) }
                api
                    .postPurchase(
                        userId = session.user.id,
                        outcome = outcome,
                        accessToken = accessToken,
                    ).reportFailure()
            }

        override suspend fun restore(): Result<Unit> =
            withContext(dispatcher) {
                val session = auth.currentSession() ?: return@withContext Result.success(Unit)
                val accessToken = auth.accessToken().getOrElse { return@withContext Result.failure(it) }
                api
                    .getState(
                        userId = session.user.id,
                        accessToken = accessToken,
                    ).mapCatching { remote ->
                        supporterRepository
                            .mergeRemote(
                                remoteCount = remote.purchaseCount,
                                remoteBadge = remote.badgeEarned,
                            ).getOrThrow()
                    }.reportFailure()
            }
    }

private fun <T> Result<T>.reportFailure(): Result<T> =
    onFailure(Throwable::reportToSentry)
