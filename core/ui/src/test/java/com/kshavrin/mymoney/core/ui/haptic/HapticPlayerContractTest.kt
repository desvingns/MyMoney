package com.kshavrin.mymoney.core.ui.haptic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-level contract tests for the :core:ui haptic subsystem.
 *
 * Pins the full [HapticKind] membership (TDD §6.9) and exercises the pure
 * [HapticEffectSelector] seam — enable/disable gating, no-vibrator no-op,
 * the API 29/30 legacy fallback, API 31/32 composition, and API 33+
 * celebratory branch — without Robolectric or mocking. [HapticPlayerImplTest]
 * covers the same seam with finer-grained per-kind assertions.
 *
 * Only the platform Vibrator dispatch (createOneShot / startComposition /
 * vibrate) still requires a connected device.
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

    // ---- API compatibility contract (no mocking framework) ----

    private val supportedBands = listOf(29, 30, 31, 32, 33, 34)

    @Test
    fun `every kind stays silent when haptics are disabled on every band`() {
        for (sdk in supportedBands) {
            for (kind in HapticKind.entries) {
                assertEquals(
                    "sdk=$sdk kind=$kind",
                    HapticSelection.None,
                    HapticEffectSelector.select(sdk, hapticsEnabled = false, hasVibrator = true, kind = kind),
                )
            }
        }
    }

    @Test
    fun `every kind is a no-op without a vibrator on every band`() {
        for (sdk in supportedBands) {
            for (kind in HapticKind.entries) {
                assertEquals(
                    "sdk=$sdk kind=$kind",
                    HapticSelection.None,
                    HapticEffectSelector.select(sdk, hapticsEnabled = true, hasVibrator = false, kind = kind),
                )
            }
        }
    }

    @Test
    fun `Android 10 and 11 resolve every kind to the legacy path only`() {
        for (sdk in listOf(29, 30)) {
            for (kind in HapticKind.entries) {
                val selection = HapticEffectSelector.select(sdk, hapticsEnabled = true, hasVibrator = true, kind = kind)
                assertTrue(
                    "sdk=$sdk kind=$kind selected $selection must not touch composition",
                    selection is HapticSelection.LegacyOneShot || selection is HapticSelection.LegacyWaveform,
                )
            }
        }
    }

    @Test
    fun `legacy waveforms keep timing and amplitude arrays the same length`() {
        for (sdk in listOf(29, 30)) {
            for (kind in HapticKind.entries) {
                val selection = HapticEffectSelector.select(sdk, hapticsEnabled = true, hasVibrator = true, kind = kind)
                if (selection is HapticSelection.LegacyWaveform) {
                    assertEquals("sdk=$sdk kind=$kind", selection.timings.size, selection.amplitudes.size)
                }
            }
        }
    }

    @Test
    fun `celebratory SPIN is reserved for API 33 and above`() {
        for (sdk in supportedBands) {
            val selection =
                HapticEffectSelector.select(sdk, hapticsEnabled = true, hasVibrator = true, kind = HapticKind.SUCCESS_SHIMMER)
            val celebratory = selection is HapticSelection.Celebratory
            assertEquals("sdk=$sdk", sdk >= 33, celebratory)
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
