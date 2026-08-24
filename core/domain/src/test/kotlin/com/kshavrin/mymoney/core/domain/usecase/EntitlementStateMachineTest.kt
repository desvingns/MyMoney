package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class EntitlementStateMachineTest {
    @Test
    fun `worked example 1 resolves a missing snapshot to Free with NONE and no warnings`() {
        val now = instant("2026-08-12T00:00:00Z")
        val result = EntitlementStateMachine.resolve(snapshot = null, now = now)

        assertFreeWithNone(result)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, result, now))
    }

    @Test
    fun `worked example 2 resolves an active yearly subscription`() {
        val now = instant("2026-08-12T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_YEARLY,
                    startsAt = instant("2026-01-01T00:00:00Z"),
                    expiresAt = instant("2027-01-01T00:00:00Z"),
                ),
                now,
            )

        assertEquals(EntitlementState.ACTIVE, result.state)
        assertEquals(instant("2027-01-08T00:00:00Z"), result.graceEndsAt)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, result, now))
    }

    @Test
    fun `worked example 3 keeps a trial and warns exactly three days before expiry`() {
        val now = instant("2026-08-14T12:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_YEARLY,
                    startsAt = instant("2026-08-10T12:00:00Z"),
                    expiresAt = instant("2026-08-17T12:00:00Z"),
                    inTrial = true,
                ),
                now,
            )

        assertEquals(EntitlementState.TRIAL, result.state)
        assertEquals(
            setOf(EntitlementWarning.TRIAL_ENDING_3D),
            EntitlementStateMachine.warnings(null, result, now),
        )
    }

    @Test
    fun `worked example 4 does not warn three days and one second before trial expiry`() {
        val now = instant("2026-08-14T11:59:59Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_YEARLY,
                    startsAt = instant("2026-08-10T12:00:00Z"),
                    expiresAt = instant("2026-08-17T12:00:00Z"),
                    inTrial = true,
                ),
                now,
            )

        assertEquals(EntitlementState.TRIAL, result.state)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, result, now))
    }

    @Test
    fun `worked example 5 enters seven-day subscription grace and emits the entry warning`() {
        val now = instant("2026-08-15T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                    startsAt = instant("2026-07-12T00:00:00Z"),
                    expiresAt = instant("2026-08-12T00:00:00Z"),
                ),
                now,
            )

        assertEquals(EntitlementState.GRACE, result.state)
        assertEquals(instant("2026-08-19T00:00:00Z"), result.graceEndsAt)
        assertEquals(
            setOf(EntitlementWarning.GRACE_ENTERED),
            EntitlementStateMachine.warnings(EntitlementState.ACTIVE, result, now),
        )
    }

    @Test
    fun `worked example 6 warns exactly one day before grace ends`() {
        val now = instant("2026-08-18T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                    startsAt = instant("2026-07-12T00:00:00Z"),
                    expiresAt = instant("2026-08-12T00:00:00Z"),
                ),
                now,
            )

        assertEquals(EntitlementState.GRACE, result.state)
        assertEquals(
            setOf(EntitlementWarning.EXPIRY_IMMINENT_1D),
            EntitlementStateMachine.warnings(EntitlementState.GRACE, result, now),
        )
    }

    @Test
    fun `worked example 7 expires at the exact end of subscription grace`() {
        val now = instant("2026-08-19T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                    startsAt = instant("2026-07-12T00:00:00Z"),
                    expiresAt = instant("2026-08-12T00:00:00Z"),
                ),
                now,
            )

        assertEquals(EntitlementState.EXPIRED, result.state)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.GRACE, result, now))
    }

    @Test
    fun `worked example 8 expires an ad reward immediately without grace`() {
        val expiresAt = instant("2026-08-12T10:00:00Z")
        val now = instant("2026-08-12T10:00:01Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.AD_REWARD,
                    startsAt = instant("2026-08-11T10:00:00Z"),
                    expiresAt = expiresAt,
                ),
                now,
            )

        assertEquals(EntitlementState.EXPIRED, result.state)
        assertEquals(expiresAt, result.graceEndsAt)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, result, now))
    }

    @Test
    fun `worked example 9 keeps a whitelist active without an expiry`() {
        val now = instant("2030-01-01T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.WHITELIST,
                    startsAt = instant("2026-01-01T00:00:00Z"),
                    expiresAt = null,
                ),
                now,
            )

        assertEquals(EntitlementState.ACTIVE, result.state)
        assertEquals(null, result.expiresAt)
        assertEquals(null, result.graceEndsAt)
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, result, now))
    }

    @Test
    fun `revoked snapshot resolves to Free regardless of its otherwise valid entitlement`() {
        val now = instant("2026-08-12T00:00:00Z")
        val result =
            EntitlementStateMachine.resolve(
                snapshot(
                    source = EntitlementSource.WHITELIST,
                    startsAt = instant("2026-01-01T00:00:00Z"),
                    expiresAt = null,
                    revokedAt = instant("2026-08-01T00:00:00Z"),
                ),
                now,
            )

        assertFreeWithNone(result)
    }

    @Test
    fun `expiry equal to start resolves a broken record to Free`() {
        val startsAt = instant("2026-08-12T00:00:00Z")
        val invalidSnapshot =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                startsAt = startsAt,
                expiresAt = startsAt,
            )
        val invalidSnapshots = mutableListOf<EntitlementSnapshot>()
        val result =
            EntitlementStateMachine.resolve(
                snapshot = invalidSnapshot,
                now = startsAt,
                onInvalidSnapshot = { invalidSnapshots += it },
            )

        assertFreeWithNone(result)
        assertEquals(listOf(invalidSnapshot), invalidSnapshots)
    }

    @Test
    fun `expiry before start resolves a broken record to Free`() {
        val startsAt = instant("2026-08-12T00:00:00Z")
        val invalidSnapshot =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_YEARLY,
                startsAt = startsAt,
                expiresAt = startsAt.minusSeconds(1),
            )
        val invalidSnapshots = mutableListOf<EntitlementSnapshot>()
        val result =
            EntitlementStateMachine.resolve(
                snapshot = invalidSnapshot,
                now = startsAt,
                onInvalidSnapshot = { invalidSnapshots += it },
            )

        assertFreeWithNone(result)
        assertEquals(listOf(invalidSnapshot), invalidSnapshots)
    }

    @Test
    fun `non-whitelist entitlement without an expiry resolves to Free and reports its snapshot`() {
        val now = instant("2026-08-12T00:00:00Z")
        val invalidSnapshots =
            listOf(
                EntitlementSource.SUBSCRIPTION_MONTHLY,
                EntitlementSource.SUBSCRIPTION_YEARLY,
                EntitlementSource.AD_REWARD,
            ).map { source -> snapshot(source = source, expiresAt = null) }
        val reportedSnapshots = mutableListOf<EntitlementSnapshot>()

        invalidSnapshots.forEach { invalidSnapshot ->
            val result =
                EntitlementStateMachine.resolve(
                    snapshot = invalidSnapshot,
                    now = now,
                    onInvalidSnapshot = { reportedSnapshots += it },
                )

            assertFreeWithNone(result)
        }
        assertEquals(invalidSnapshots, reportedSnapshots)
    }

    @Test
    fun `finite whitelist expiry resolves to Free with NONE`() {
        val invalidSnapshot =
            snapshot(
                source = EntitlementSource.WHITELIST,
                expiresAt = instant("2026-08-20T00:00:00Z"),
            )
        val reportedSnapshots = mutableListOf<EntitlementSnapshot>()

        val result =
            EntitlementStateMachine.resolve(
                snapshot = invalidSnapshot,
                now = instant("2026-08-12T00:00:00Z"),
                onInvalidSnapshot = { reportedSnapshots += it },
            )

        assertFreeWithNone(result)
        assertEquals(listOf(invalidSnapshot), reportedSnapshots)
    }

    @Test
    fun `empty collection resolves to Free with NONE`() {
        val result =
            EntitlementStateMachine.resolve(
                snapshots = emptyList(),
                now = instant("2026-08-12T00:00:00Z"),
            )

        assertFreeWithNone(result)
    }

    @Test
    fun `all-invalid collection resolves to Free with NONE and reports every invalid snapshot`() {
        val now = instant("2026-08-12T00:00:00Z")
        val equalExpiry = instant("2026-08-12T00:00:00Z")
        val invalidSnapshots =
            listOf(
                snapshot(
                    source = EntitlementSource.WHITELIST,
                    expiresAt = instant("2026-08-20T00:00:00Z"),
                ),
                snapshot(
                    source = EntitlementSource.AD_REWARD,
                    expiresAt = null,
                ),
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                    startsAt = equalExpiry,
                    expiresAt = equalExpiry,
                ),
            )
        val reportedSnapshots = mutableListOf<EntitlementSnapshot>()

        val result =
            EntitlementStateMachine.resolve(
                snapshots = invalidSnapshots,
                now = now,
                onInvalidSnapshot = { reportedSnapshots += it },
            )

        assertFreeWithNone(result)
        assertEquals(invalidSnapshots, reportedSnapshots)
    }

    @Test
    fun `collection resolver selects the candidate with the maximum grace end`() {
        val now = instant("2026-08-01T00:00:00Z")
        val shorterGrace =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                expiresAt = instant("2026-08-10T00:00:00Z"),
            )
        val longerGrace =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_YEARLY,
                expiresAt = instant("2026-08-12T00:00:00Z"),
            )

        val result =
            resolvePlus(
                snapshots = listOf(shorterGrace, longerGrace),
                now = now,
            )

        assertEquals(EntitlementSource.SUBSCRIPTION_YEARLY, result.source)
        assertEquals(EntitlementState.ACTIVE, result.state)
        assertEquals(longerGrace.expiresAt, result.expiresAt)
        assertEquals(instant("2026-08-19T00:00:00Z"), result.graceEndsAt)
    }

    @Test
    fun `whitelist with null grace end outranks finite candidates`() {
        val now = instant("2026-08-01T00:00:00Z")
        val finiteCandidate =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_YEARLY,
                expiresAt = instant("2026-08-12T00:00:00Z"),
            )
        val whitelist =
            snapshot(
                source = EntitlementSource.WHITELIST,
                startsAt = instant("2026-01-01T00:00:00Z"),
                expiresAt = null,
            )

        val result =
            resolvePlus(
                snapshots = listOf(finiteCandidate, whitelist),
                now = now,
            )

        assertEquals(EntitlementSource.WHITELIST, result.source)
        assertEquals(EntitlementState.ACTIVE, result.state)
        assertEquals(null, result.expiresAt)
        assertEquals(null, result.graceEndsAt)
    }

    @Test
    fun `equal grace end prefers a subscription over an ad reward`() {
        val now = instant("2026-08-01T00:00:00Z")
        val adReward =
            snapshot(
                source = EntitlementSource.AD_REWARD,
                expiresAt = instant("2026-08-26T00:00:00Z"),
            )
        val subscription =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                expiresAt = instant("2026-08-19T00:00:00Z"),
            )

        val result =
            resolvePlus(
                snapshots = listOf(adReward, subscription),
                now = now,
            )

        assertEquals(EntitlementSource.SUBSCRIPTION_MONTHLY, result.source)
        assertEquals(instant("2026-08-26T00:00:00Z"), result.graceEndsAt)
    }

    @Test
    fun `subscription grace lasts seven days for monthly and yearly sources`() {
        val expiresAt = instant("2026-08-12T00:00:00Z")
        listOf(
            EntitlementSource.SUBSCRIPTION_MONTHLY,
            EntitlementSource.SUBSCRIPTION_YEARLY,
        ).forEach { source ->
            val result =
                resolvePlus(
                    snapshot(source = source, expiresAt = expiresAt),
                    expiresAt.plusSeconds(1),
                )

            assertEquals(EntitlementState.GRACE, result.state)
            assertEquals(expiresAt.plusDays(7), result.graceEndsAt)
        }
    }

    @Test
    fun `subscription enters grace at expiry and expires at the inclusive grace boundary`() {
        val expiresAt = instant("2026-08-12T00:00:00Z")
        val snapshot =
            snapshot(
                source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                expiresAt = expiresAt,
            )

        assertEquals(
            EntitlementState.GRACE,
            resolvePlus(snapshot, expiresAt).state,
        )
        assertEquals(
            EntitlementState.EXPIRED,
            resolvePlus(snapshot, expiresAt.plusDays(7)).state,
        )
    }

    @Test
    fun `ad reward has zero grace and expires at its expiry instant`() {
        val expiresAt = instant("2026-08-12T00:00:00Z")
        val snapshot = snapshot(source = EntitlementSource.AD_REWARD, expiresAt = expiresAt)

        val beforeExpiry = resolvePlus(snapshot, expiresAt.minusSeconds(1))
        val atExpiry = resolvePlus(snapshot, expiresAt)

        assertEquals(EntitlementState.ACTIVE, beforeExpiry.state)
        assertEquals(expiresAt, beforeExpiry.graceEndsAt)
        assertEquals(EntitlementState.EXPIRED, atExpiry.state)
        assertEquals(expiresAt, atExpiry.graceEndsAt)
    }

    @Test
    fun `state follows the formula even when now is before starts`() {
        val startsAt = instant("2026-08-12T00:00:00Z")
        val result =
            resolvePlus(
                snapshot(
                    source = EntitlementSource.SUBSCRIPTION_YEARLY,
                    startsAt = startsAt,
                    expiresAt = startsAt.plusDays(7),
                    inTrial = true,
                ),
                startsAt.minusSeconds(1),
            )

        assertEquals(EntitlementState.TRIAL, result.state)
    }

    @Test
    fun `grace entered warning requires a previous non-grace state`() {
        val now = instant("2026-08-15T00:00:00Z")
        val current =
            UserEntitlement.Plus(
                source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                state = EntitlementState.GRACE,
                startsAt = instant("2026-07-12T00:00:00Z"),
                expiresAt = instant("2026-08-12T00:00:00Z"),
                graceEndsAt = instant("2026-08-19T00:00:00Z"),
            )

        listOf(
            EntitlementState.TRIAL,
            EntitlementState.ACTIVE,
            EntitlementState.EXPIRED,
        ).forEach { previous ->
            assertEquals(
                setOf(EntitlementWarning.GRACE_ENTERED),
                EntitlementStateMachine.warnings(previous, current, now),
            )
        }
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, current, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.GRACE, current, now))
    }

    @Test
    fun `trial ending warning is inclusive at three days and exclusive at zero or above threshold`() {
        val now = instant("2026-08-12T00:00:00Z")
        val exactlyThreeDays = trial(expiresAt = now.plusDays(3))
        val aboveThreshold = trial(expiresAt = now.plusDays(3).plusSeconds(1))
        val atExpiry = trial(expiresAt = now)
        val afterExpiry = trial(expiresAt = now.minusSeconds(1))

        assertEquals(
            setOf(EntitlementWarning.TRIAL_ENDING_3D),
            EntitlementStateMachine.warnings(null, exactlyThreeDays, now),
        )
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, aboveThreshold, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, atExpiry, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, afterExpiry, now))
    }

    @Test
    fun `expiry imminent warning is inclusive at one day and exclusive at zero or above threshold`() {
        val now = instant("2026-08-12T00:00:00Z")
        val exactlyOneDay = grace(graceEndsAt = now.plusDays(1))
        val aboveThreshold = grace(graceEndsAt = now.plusDays(1).plusSeconds(1))
        val atGraceEnd = grace(graceEndsAt = now)
        val afterGraceEnd = grace(graceEndsAt = now.minusSeconds(1))

        assertEquals(
            setOf(EntitlementWarning.EXPIRY_IMMINENT_1D),
            EntitlementStateMachine.warnings(EntitlementState.GRACE, exactlyOneDay, now),
        )
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.GRACE, aboveThreshold, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.GRACE, atGraceEnd, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.GRACE, afterGraceEnd, now))
    }

    @Test
    fun `trial ending warning excludes an ad reward source even within the threshold`() {
        val now = instant("2026-08-12T00:00:00Z")
        val adTrial =
            UserEntitlement.Plus(
                source = EntitlementSource.AD_REWARD,
                state = EntitlementState.TRIAL,
                startsAt = Instant.EPOCH,
                expiresAt = now.plusDays(3),
                graceEndsAt = now.plusDays(3),
            )

        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(null, adTrial, now))
    }

    @Test
    fun `warnings are empty for Free and for non-warning Plus states`() {
        val now = instant("2026-08-12T00:00:00Z")
        val active =
            UserEntitlement.Plus(
                source = EntitlementSource.AD_REWARD,
                state = EntitlementState.ACTIVE,
                startsAt = instant("2026-08-01T00:00:00Z"),
                expiresAt = instant("2026-08-20T00:00:00Z"),
                graceEndsAt = instant("2026-08-20T00:00:00Z"),
            )

        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.ACTIVE, UserEntitlement.Free, now))
        assertEquals(emptySet<EntitlementWarning>(), EntitlementStateMachine.warnings(EntitlementState.ACTIVE, active, now))
    }

    private fun snapshot(
        source: EntitlementSource,
        startsAt: Instant = instant("2026-08-01T00:00:00Z"),
        expiresAt: Instant?,
        inTrial: Boolean = false,
        revokedAt: Instant? = null,
    ) = EntitlementSnapshot(
        source = source,
        startsAt = startsAt,
        expiresAt = expiresAt,
        inTrial = inTrial,
        revokedAt = revokedAt,
    )

    private fun trial(expiresAt: Instant) =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_YEARLY,
            state = EntitlementState.TRIAL,
            startsAt = Instant.EPOCH,
            expiresAt = expiresAt,
            graceEndsAt = expiresAt.plusDays(7),
        )

    private fun grace(graceEndsAt: Instant) =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = EntitlementState.GRACE,
            startsAt = Instant.EPOCH,
            expiresAt = graceEndsAt.minusDays(7),
            graceEndsAt = graceEndsAt,
        )

    private fun resolvePlus(
        snapshot: EntitlementSnapshot,
        now: Instant,
    ): UserEntitlement.Plus {
        val result = EntitlementStateMachine.resolve(snapshot, now)
        assertTrue("Expected Plus, got $result", result is UserEntitlement.Plus)
        return result as UserEntitlement.Plus
    }

    private fun resolvePlus(
        snapshots: Collection<EntitlementSnapshot>,
        now: Instant,
    ): UserEntitlement.Plus {
        val result = EntitlementStateMachine.resolve(snapshots, now)
        assertTrue("Expected Plus, got $result", result is UserEntitlement.Plus)
        return result as UserEntitlement.Plus
    }

    private fun assertFreeWithNone(entitlement: UserEntitlement) {
        assertTrue("Expected Free, got $entitlement", entitlement is UserEntitlement.Free)
        assertEquals(EntitlementState.NONE, stateOf(entitlement))
    }

    private fun stateOf(entitlement: UserEntitlement): EntitlementState =
        when (entitlement) {
            UserEntitlement.Free -> EntitlementState.NONE
            is UserEntitlement.Plus -> entitlement.state
        }

    private fun instant(value: String) = Instant.parse(value)
}

private fun Instant.plusDays(days: Long): Instant = plus(days, ChronoUnit.DAYS)

private fun Instant.minusDays(days: Long): Instant = minus(days, ChronoUnit.DAYS)
