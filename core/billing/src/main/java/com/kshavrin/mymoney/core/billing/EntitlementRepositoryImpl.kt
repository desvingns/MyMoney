package com.kshavrin.mymoney.core.billing

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.EntitlementCache
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.usecase.EntitlementStateMachine
import com.kshavrin.mymoney.core.network.shared.SupabaseEntitlementApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
        private val clock: Clock,
        private val analytics: AnalyticsGateway,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : EntitlementRepository {
        override val entitlement: StateFlow<UserEntitlement> =
            cache.cachedEntitlement
                .map { cached -> EntitlementStateMachine.resolve(cached.snapshot, clock.instant()) }
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UserEntitlement.Free,
                )

        override suspend fun refresh(): Result<Unit> =
            withContext(ioDispatcher) {
                val previous = entitlement.value
                api
                    .getMyEntitlement()
                    .mapCatching { snapshot ->
                        val now = clock.instant()
                        cache.update(snapshot = snapshot, lastValidatedAt = now)
                        val current = EntitlementStateMachine.resolve(snapshot, now)
                        logSubscriptionTransitions(previous, current, snapshot)
                    }
            }

        private fun logSubscriptionTransitions(
            previous: UserEntitlement,
            current: UserEntitlement,
            snapshot: EntitlementSnapshot?,
        ) {
            val previousState = previous.stateOrNone()

            val currentSubscription = current.asSubscriptionPlus()
            if (currentSubscription != null) {
                val productId = currentSubscription.source.subscriptionProductId()
                when (currentSubscription.state) {
                    EntitlementState.TRIAL ->
                        if (previousState != EntitlementState.TRIAL) {
                            analytics.log(AnalyticsEvent.TrialStarted(productId))
                        }

                    EntitlementState.ACTIVE ->
                        if (previousState != EntitlementState.ACTIVE) {
                            analytics.log(
                                AnalyticsEvent.SubscriptionPurchased(
                                    productId = productId,
                                    isTrialConversion = previousState == EntitlementState.TRIAL,
                                ),
                            )
                        }

                    else -> Unit
                }
            }

            val previousSubscription = previous.asSubscriptionPlus()
            if (previousSubscription != null) {
                val wasEntitled = previousState in ENTITLED_STATES
                val isEntitled = current.stateOrNone() in ENTITLED_STATES
                if (wasEntitled && !isEntitled) {
                    val reason = if (snapshot?.revokedAt != null) REASON_REVOKED else REASON_EXPIRED
                    analytics.log(
                        AnalyticsEvent.SubscriptionCancelled(
                            productId = previousSubscription.source.subscriptionProductId(),
                            reason = reason,
                        ),
                    )
                }
            }
        }

        private companion object {
            const val REASON_EXPIRED = "expired"
            const val REASON_REVOKED = "revoked"

            val ENTITLED_STATES =
                setOf(
                    EntitlementState.TRIAL,
                    EntitlementState.ACTIVE,
                    EntitlementState.GRACE,
                )
        }
    }

private fun UserEntitlement.stateOrNone(): EntitlementState =
    when (this) {
        is UserEntitlement.Plus -> state
        UserEntitlement.Free -> EntitlementState.NONE
    }

private fun UserEntitlement.asSubscriptionPlus(): UserEntitlement.Plus? =
    (this as? UserEntitlement.Plus)?.takeIf { it.source.isSubscription() }

private fun EntitlementSource.isSubscription(): Boolean =
    this == EntitlementSource.SUBSCRIPTION_MONTHLY || this == EntitlementSource.SUBSCRIPTION_YEARLY

private fun EntitlementSource.subscriptionProductId(): String =
    when (this) {
        EntitlementSource.SUBSCRIPTION_MONTHLY -> "plus_monthly"
        EntitlementSource.SUBSCRIPTION_YEARLY -> "plus_yearly"
        EntitlementSource.AD_REWARD,
        EntitlementSource.WHITELIST,
        -> error("not a subscription source")
    }
