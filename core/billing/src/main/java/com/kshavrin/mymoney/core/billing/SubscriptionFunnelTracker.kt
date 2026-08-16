package com.kshavrin.mymoney.core.billing

import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.usecase.EntitlementStateMachine
import java.time.Instant

/**
 * Emits the subscription-funnel analytics events that are only knowable from a server-confirmed
 * entitlement snapshot (the Supabase RTDN projection), keyed off the state transition between the
 * previously-resolved entitlement and the freshly-resolved one. Cancellation is therefore a server
 * fact, never an in-app tap. A trial that ends without converting is a cancellation too: Google Play
 * auto-charges at trial end unless the user actively cancels, so TRIAL -> non-entitled means churn.
 */
internal class SubscriptionFunnelTracker(
    private val analytics: AnalyticsGateway,
) {
    fun onServerConfirmed(
        previous: UserEntitlement,
        snapshot: EntitlementSnapshot?,
        now: Instant,
    ) {
        val current = EntitlementStateMachine.resolve(snapshot, now)
        val previousState = previous.stateOrNone()

        current.asSubscriptionPlus()?.let { currentSubscription ->
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

        previous.asSubscriptionPlus()?.let { previousSubscription ->
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
