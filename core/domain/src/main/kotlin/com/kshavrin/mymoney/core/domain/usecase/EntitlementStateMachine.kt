package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import java.time.Duration
import java.time.Instant

private val subscriptionSources =
    setOf(
        EntitlementSource.SUBSCRIPTION_MONTHLY,
        EntitlementSource.SUBSCRIPTION_YEARLY,
    )
private val subscriptionGraceDuration = Duration.ofDays(7)
private val trialEndingThreshold = Duration.ofDays(3)
private val expiryImminentThreshold = Duration.ofDays(1)

object EntitlementStateMachine {
    fun resolve(
        snapshot: EntitlementSnapshot?,
        now: Instant,
    ): UserEntitlement {
        if (snapshot == null || snapshot.revokedAt != null) return UserEntitlement.Free

        val expiresAt = snapshot.expiresAt
        if (
            (expiresAt == null && snapshot.source != EntitlementSource.WHITELIST) ||
            (expiresAt != null && !expiresAt.isAfter(snapshot.startsAt))
        ) {
            return UserEntitlement.Free
        }

        val graceEndsAt = expiresAt?.plus(graceDuration(snapshot.source))
        val state =
            when {
                expiresAt == null -> EntitlementState.ACTIVE
                now.isBefore(expiresAt) && snapshot.inTrial -> EntitlementState.TRIAL
                now.isBefore(expiresAt) -> EntitlementState.ACTIVE
                graceEndsAt != null && now.isBefore(graceEndsAt) -> EntitlementState.GRACE
                else -> EntitlementState.EXPIRED
            }

        return UserEntitlement.Plus(
            source = snapshot.source,
            state = state,
            startsAt = snapshot.startsAt,
            expiresAt = expiresAt,
            graceEndsAt = graceEndsAt,
        )
    }

    fun warnings(
        previous: EntitlementState?,
        current: UserEntitlement,
        now: Instant,
    ): Set<EntitlementWarning> {
        val entitlement = current as? UserEntitlement.Plus ?: return emptySet()

        return buildSet {
            if (
                entitlement.state == EntitlementState.TRIAL &&
                entitlement.expiresAt?.isWithin(now, trialEndingThreshold) == true
            ) {
                add(EntitlementWarning.TRIAL_ENDING_3D)
            }
            if (
                entitlement.state == EntitlementState.GRACE &&
                previous != null &&
                previous != EntitlementState.GRACE
            ) {
                add(EntitlementWarning.GRACE_ENTERED)
            }
            if (
                entitlement.state == EntitlementState.GRACE &&
                entitlement.graceEndsAt?.isWithin(now, expiryImminentThreshold) == true
            ) {
                add(EntitlementWarning.EXPIRY_IMMINENT_1D)
            }
        }
    }

    private fun graceDuration(source: EntitlementSource): Duration =
        if (source in subscriptionSources) subscriptionGraceDuration else Duration.ZERO

    private fun Instant.isWithin(
        now: Instant,
        threshold: Duration,
    ): Boolean {
        val remaining = Duration.between(now, this)
        return remaining > Duration.ZERO && remaining <= threshold
    }
}
