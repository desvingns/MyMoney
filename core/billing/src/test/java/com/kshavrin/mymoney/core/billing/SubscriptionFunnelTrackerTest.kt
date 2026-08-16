package com.kshavrin.mymoney.core.billing

import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.testing.fake.FakeAnalyticsGateway
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Pins the server-fact subscription funnel events produced by [EntitlementRepositoryImpl] via its
 * [SubscriptionFunnelTracker] delegate. The tracker is the unit that owns the transition logic; the
 * repository only captures `previous`, refreshes the cache, and forwards the resolved snapshot.
 */
class SubscriptionFunnelTrackerTest {
    private val now: Instant = Instant.parse("2026-08-16T00:00:00Z")
    private val analytics = FakeAnalyticsGateway()
    private val tracker = SubscriptionFunnelTracker(analytics)

    @Test
    fun `NONE to TRIAL fires TrialStarted with the subscription product id`() {
        tracker.onServerConfirmed(
            previous = UserEntitlement.Free,
            snapshot = monthlySnapshot(expiresAt = now.plusDays(13), inTrial = true),
            now = now,
        )

        assertEquals(listOf(AnalyticsEvent.TrialStarted(PLUS_MONTHLY)), analytics.events)
    }

    @Test
    fun `yearly trial reports the yearly product id`() {
        tracker.onServerConfirmed(
            previous = UserEntitlement.Free,
            snapshot =
                EntitlementSnapshot(
                    source = EntitlementSource.SUBSCRIPTION_YEARLY,
                    startsAt = now.minusDays(1),
                    expiresAt = now.plusDays(6),
                    inTrial = true,
                    revokedAt = null,
                ),
            now = now,
        )

        assertEquals(listOf(AnalyticsEvent.TrialStarted(PLUS_YEARLY)), analytics.events)
    }

    @Test
    fun `TRIAL to ACTIVE fires SubscriptionPurchased marked as a trial conversion`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.TRIAL),
            snapshot = monthlySnapshot(expiresAt = now.plusDays(30), inTrial = false),
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionPurchased(PLUS_MONTHLY, isTrialConversion = true)),
            analytics.events,
        )
    }

    @Test
    fun `NONE to ACTIVE fires SubscriptionPurchased that is not a trial conversion`() {
        tracker.onServerConfirmed(
            previous = UserEntitlement.Free,
            snapshot = monthlySnapshot(expiresAt = now.plusDays(30), inTrial = false),
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionPurchased(PLUS_MONTHLY, isTrialConversion = false)),
            analytics.events,
        )
    }

    @Test
    fun `ACTIVE to EXPIRED fires SubscriptionCancelled with reason expired`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.ACTIVE),
            snapshot = monthlySnapshot(expiresAt = now.minusDays(8), inTrial = false),
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionCancelled(PLUS_MONTHLY, reason = "expired")),
            analytics.events,
        )
    }

    @Test
    fun `ACTIVE to revoked fires SubscriptionCancelled with reason revoked`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.ACTIVE),
            snapshot =
                monthlySnapshot(expiresAt = now.plusDays(30), inTrial = false)
                    .copy(revokedAt = now),
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionCancelled(PLUS_MONTHLY, reason = "revoked")),
            analytics.events,
        )
    }

    @Test
    fun `TRIAL to non-entitled is a cancellation, never a spurious trial or purchase`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.TRIAL),
            snapshot = null,
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionCancelled(PLUS_MONTHLY, reason = "expired")),
            analytics.events,
        )
    }

    @Test
    fun `GRACE to non-entitled fires only SubscriptionCancelled`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.GRACE),
            snapshot = null,
            now = now,
        )

        assertEquals(
            listOf(AnalyticsEvent.SubscriptionCancelled(PLUS_MONTHLY, reason = "expired")),
            analytics.events,
        )
    }

    @Test
    fun `ACTIVE to GRACE emits nothing because GRACE is an entitled state`() {
        // Grace period: expiresAt is 1 day in the past; graceEndsAt = expiresAt + 7 days is still
        // in the future. The tracker must NOT fire SubscriptionCancelled — GRACE is in ENTITLED_STATES.
        // GraceEntered is the responsibility of EntitlementWarningWorker, not this tracker.
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.ACTIVE),
            snapshot = monthlySnapshot(expiresAt = now.minusDays(1), inTrial = false),
            now = now,
        )

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun `steady active subscription emits nothing`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.ACTIVE),
            snapshot = monthlySnapshot(expiresAt = now.plusDays(30), inTrial = false),
            now = now,
        )

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun `steady trial subscription emits nothing`() {
        tracker.onServerConfirmed(
            previous = plus(EntitlementState.TRIAL),
            snapshot = monthlySnapshot(expiresAt = now.plusDays(13), inTrial = true),
            now = now,
        )

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun `ad reward entitlement produces no subscription funnel events`() {
        tracker.onServerConfirmed(
            previous = UserEntitlement.Free,
            snapshot =
                EntitlementSnapshot(
                    source = EntitlementSource.AD_REWARD,
                    startsAt = now.minusDays(1),
                    expiresAt = now.plusDays(1),
                    inTrial = false,
                    revokedAt = null,
                ),
            now = now,
        )

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    private fun plus(state: EntitlementState): UserEntitlement.Plus =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = state,
            startsAt = now.minusDays(40),
            expiresAt = now.plusDays(30),
            graceEndsAt = now.plusDays(37),
        )

    private fun monthlySnapshot(
        expiresAt: Instant,
        inTrial: Boolean,
    ): EntitlementSnapshot =
        EntitlementSnapshot(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            startsAt = now.minusDays(40),
            expiresAt = expiresAt,
            inTrial = inTrial,
            revokedAt = null,
        )

    private companion object {
        const val PLUS_MONTHLY = "plus_monthly"
        const val PLUS_YEARLY = "plus_yearly"
    }
}

private fun Instant.plusDays(days: Long): Instant = plus(java.time.Duration.ofDays(days))

private fun Instant.minusDays(days: Long): Instant = minus(java.time.Duration.ofDays(days))
