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
        onInvalidSnapshot: (EntitlementSnapshot) -> Unit = {},
    ): UserEntitlement {
        if (snapshot == null) return UserEntitlement.Free

        if (snapshot.hasInvalidExpiry()) {
            onInvalidSnapshot(snapshot)
            return UserEntitlement.Free
        }
        if (snapshot.revokedAt != null) return UserEntitlement.Free

        val expiresAt = snapshot.expiresAt
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

    fun resolve(
        snapshots: Collection<EntitlementSnapshot>,
        now: Instant,
        onInvalidSnapshot: (EntitlementSnapshot) -> Unit = {},
    ): UserEntitlement {
        var selected: UserEntitlement.Plus? = null

        snapshots.forEach { snapshot ->
            val candidate =
                resolve(snapshot, now, onInvalidSnapshot) as? UserEntitlement.Plus ?: return@forEach
            val current = selected
            if (current == null || candidate.isPreferredOver(current)) {
                selected = candidate
            }
        }

        return selected ?: UserEntitlement.Free
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

    private fun EntitlementSnapshot.hasInvalidExpiry(): Boolean =
        when (source) {
            EntitlementSource.WHITELIST -> expiresAt != null
            EntitlementSource.SUBSCRIPTION_MONTHLY,
            EntitlementSource.SUBSCRIPTION_YEARLY,
            EntitlementSource.AD_REWARD,
            -> expiresAt == null || !expiresAt.isAfter(startsAt)
        }

    private fun UserEntitlement.Plus.isPreferredOver(other: UserEntitlement.Plus): Boolean {
        val candidateGraceEndsAt = graceEndsAt
        val selectedGraceEndsAt = other.graceEndsAt
        if (candidateGraceEndsAt == null || selectedGraceEndsAt == null) {
            if (candidateGraceEndsAt != selectedGraceEndsAt) return candidateGraceEndsAt == null
        } else {
            val graceComparison = candidateGraceEndsAt.compareTo(selectedGraceEndsAt)
            if (graceComparison != 0) return graceComparison > 0
        }

        val sourcePriorityComparison = sourcePriority(source).compareTo(sourcePriority(other.source))
        if (sourcePriorityComparison != 0) return sourcePriorityComparison > 0

        val sourceComparison = source.name.compareTo(other.source.name)
        if (sourceComparison != 0) return sourceComparison > 0

        val startsAtComparison = startsAt.compareTo(other.startsAt)
        if (startsAtComparison != 0) return startsAtComparison > 0

        return state.compareTo(other.state) > 0
    }

    private fun sourcePriority(source: EntitlementSource): Int =
        if (source in subscriptionSources) 1 else 0

    private fun Instant.isWithin(
        now: Instant,
        threshold: Duration,
    ): Boolean {
        val remaining = Duration.between(now, this)
        return remaining > Duration.ZERO && remaining <= threshold
    }
}
