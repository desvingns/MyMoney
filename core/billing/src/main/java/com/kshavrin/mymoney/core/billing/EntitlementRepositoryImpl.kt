package com.kshavrin.mymoney.core.billing

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.EntitlementCache
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.usecase.EntitlementStateMachine
import com.kshavrin.mymoney.core.network.shared.AuthSessionLifecycle
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SupabaseEntitlementApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRepositoryImpl
    @Inject
    constructor(
        private val api: SupabaseEntitlementApi,
        private val cache: EntitlementCache,
        private val auth: SharedAuth,
        private val authSessionLifecycle: AuthSessionLifecycle,
        private val clock: Clock,
        analytics: AnalyticsGateway,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : EntitlementRepository {
        private val funnelTracker = SubscriptionFunnelTracker(analytics)
        private val authSessionGeneration = MutableStateFlow(0L)
        private val activeUserId = MutableStateFlow(currentUserId())

        init {
            authSessionLifecycle.addInvalidationListener {
                authSessionGeneration.update { generation -> generation + 1L }
                activeUserId.value = currentUserId()
                applicationScope.launch(ioDispatcher) { cache.clear() }
            }
        }

        override val entitlement: StateFlow<UserEntitlement> =
            combine(cache.cachedEntitlement, activeUserId, authSessionGeneration) {
                    cached,
                    activeUserId,
                    currentSessionGeneration,
                ->
                if (
                    cached.ownerUserId == activeUserId &&
                    activeUserId != null &&
                    cached.ownerSessionGeneration == currentSessionGeneration
                ) {
                    EntitlementStateMachine.resolve(cached.snapshot, clock.instant())
                } else {
                    UserEntitlement.Free
                }
            }
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UserEntitlement.Free,
                )

        override suspend fun bindGooglePlayPurchase(purchaseToken: String): Result<Unit> =
            withContext(ioDispatcher) {
                api.bindGooglePlayPurchase(purchaseToken)
            }

        override suspend fun refresh(): Result<Unit> =
            withContext(ioDispatcher) {
                val ownerUserId = currentUserId()
                    ?: return@withContext Result.failure(SyncException(SyncError.Auth))
                val refreshGeneration = authSessionGeneration.value
                val previous = entitlement.value
                api
                    .getMyEntitlement()
                    .mapCatching { snapshot ->
                        if (
                            authSessionGeneration.value != refreshGeneration ||
                            currentUserId() != ownerUserId
                        ) {
                            throw SyncException(SyncError.Auth)
                        }
                        val now = clock.instant()
                        cache.update(
                            snapshot = snapshot,
                            lastValidatedAt = now,
                            ownerUserId = ownerUserId,
                            ownerSessionGeneration = refreshGeneration,
                        )
                        funnelTracker.onServerConfirmed(previous = previous, snapshot = snapshot, now = now)
                    }
            }

        private fun currentUserId(): String? =
            auth.currentSession()?.user?.id?.takeIf(String::isNotBlank)
    }
