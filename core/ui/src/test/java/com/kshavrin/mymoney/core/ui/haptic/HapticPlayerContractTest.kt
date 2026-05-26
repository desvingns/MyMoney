package com.kshavrin.mymoney.core.ui.haptic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-level contract tests for the :core:ui haptic subsystem.
 *
 * The production [HapticKind] enum drives the TDD §6.9 haptic table and the
 * call sites wired across the keypad, dashboard, transaction and
 * swipe-to-delete screens. This test pins the *full membership* of the
 * enum so an accidental addition or removal fails loudly — same style as
 * the legacy `MonefyKeypadContractTest` SoundKey pinning.
 *
 * The Android-bound behaviour of `HapticPlayerImpl` (Vibrator,
 * VibrationEffect.Composition, hapticEnabled gating, and the
 * SUCCESS_SHIMMER → PRIMITIVE_SPIN on API 33+ / TICK×3 fallback on
 * API 31–32) is NOT exercised here — those require Robolectric, which
 * :core:ui's test classpath does not have. That behaviour is captured as
 * a documented placeholder in [HapticPlayerImplTest].
 */
class HapticPlayerContractTest {

    // ---- HapticKind: exact membership ----

    @Test
    fun `HapticKind has exactly the five kinds`() {
        // SPEC §6.9: SOFT, MEDIUM, HEAVY, WARNING, SUCCESS_SHIMMER.
        // Pins the current set. Adding or removing an entry must update
        // this assertion deliberately.
        val kinds = HapticKind.entries.toSet()
        assertEquals(
            setOf(
                HapticKind.SOFT,
                HapticKind.MEDIUM,
                HapticKind.HEAVY,
                HapticKind.WARNING,
                HapticKind.SUCCESS_SHIMMER,
            ),
            kinds,
        )
    }

    @Test
    fun `HapticKind count is five`() {
        assertEquals(5, HapticKind.entries.size)
    }

    @Test
    fun `HapticKind contains SOFT`() {
        assertTrue(HapticKind.entries.contains(HapticKind.SOFT))
    }

    @Test
    fun `HapticKind contains MEDIUM`() {
        assertTrue(HapticKind.entries.contains(HapticKind.MEDIUM))
    }

    @Test
    fun `HapticKind contains HEAVY`() {
        assertTrue(HapticKind.entries.contains(HapticKind.HEAVY))
    }

    @Test
    fun `HapticKind contains WARNING`() {
        assertTrue(HapticKind.entries.contains(HapticKind.WARNING))
    }

    @Test
    fun `HapticKind contains SUCCESS_SHIMMER`() {
        // The shimmer is the celebratory primitive — there is no platform
        // PRIMITIVE_SHIMMER, so the impl maps it to PRIMITIVE_SPIN (API 33+)
        // or a TICK×3 fallback (API 31–32). The enum entry itself must exist
        // regardless of how it is realised on a given API level.
        assertTrue(HapticKind.entries.contains(HapticKind.SUCCESS_SHIMMER))
    }

    @Test
    fun `HapticKind valueOf round-trips every entry by name`() {
        for (kind in HapticKind.entries) {
            assertSame(kind, HapticKind.valueOf(kind.name))
        }
    }

    // ---- HapticPlayer interface: the fake recorder used by call-site tests ----

    @Test
    fun `FakeHapticPlayer records nothing when not invoked`() {
        val fake = FakeHapticPlayer()
        assertTrue("no calls expected on a fresh fake", fake.calls.isEmpty())
    }

    @Test
    fun `FakeHapticPlayer records a single fire call`() {
        val fake = FakeHapticPlayer()
        fake.fire(HapticKind.SOFT)
        assertEquals(listOf(HapticKind.SOFT), fake.calls)
    }

    @Test
    fun `FakeHapticPlayer preserves the order of multiple calls`() {
        val fake = FakeHapticPlayer()
        fake.fire(HapticKind.SOFT)
        fake.fire(HapticKind.SUCCESS_SHIMMER)
        fake.fire(HapticKind.WARNING)
        assertEquals(
            listOf(HapticKind.SOFT, HapticKind.SUCCESS_SHIMMER, HapticKind.WARNING),
            fake.calls,
        )
    }

    @Test
    fun `FakeHapticPlayer satisfies the HapticPlayer interface`() {
        val player: HapticPlayer = FakeHapticPlayer()
        player.fire(HapticKind.HEAVY)
        val fake = player as FakeHapticPlayer
        assertEquals(1, fake.calls.size)
        assertEquals(HapticKind.HEAVY, fake.calls.single())
    }

    /**
     * Test double for [HapticPlayer]. This is the exact recorder the
     * Compose-UI / call-site tests will use to assert "screen X fires
     * HapticKind Y" once Robolectric is wired on this module's test
     * classpath (see [HapticPlayerImplTest]).
     */
    private class FakeHapticPlayer : HapticPlayer {
        val calls: MutableList<HapticKind> = mutableListOf()
        override fun fire(kind: HapticKind) {
            calls += kind
        }
    }
}
