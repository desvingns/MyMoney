package com.kshavrin.mymoney.core.ui.haptic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM tests for [HapticEffectSelector] — the pure API-selection
 * seam extracted from [HapticPlayerImpl].
 *
 * The Android-bound part of [HapticPlayerImpl] (turning a [HapticSelection]
 * into a real `VibrationEffect` and dispatching it to the platform `Vibrator`)
 * still needs a connected device; that is owned by the separate Android 10/11
 * legacy device order. Everything the SPEC calls the "haptic compatibility
 * contract" — enable/disable gating, no-vibrator no-op, the API 29/30 legacy
 * fallback, the API 31/32 composition path and the API 33+ celebratory branch —
 * is realised in this pure seam and pinned here without any mocking framework.
 */
class HapticPlayerImplTest {
    private fun select(
        sdk: Int,
        enabled: Boolean = true,
        hasVibrator: Boolean = true,
        kind: HapticKind = HapticKind.SOFT,
    ): HapticSelection = HapticEffectSelector.select(sdk, enabled, hasVibrator, kind)

    private val everyBand = listOf(29, 30, 31, 32, 33, 34)

    @Test
    fun `disabled haptics select None on every band and kind`() {
        for (sdk in everyBand) {
            for (kind in HapticKind.entries) {
                assertEquals(
                    "sdk=$sdk kind=$kind",
                    HapticSelection.None,
                    select(sdk, enabled = false, kind = kind),
                )
            }
        }
    }

    @Test
    fun `missing vibrator selects None on every band and kind`() {
        for (sdk in everyBand) {
            for (kind in HapticKind.entries) {
                assertEquals(
                    "sdk=$sdk kind=$kind",
                    HapticSelection.None,
                    select(sdk, hasVibrator = false, kind = kind),
                )
            }
        }
    }

    @Test
    fun `API 29 and 30 never select a composition or celebratory path`() {
        for (sdk in listOf(29, 30)) {
            for (kind in HapticKind.entries) {
                val selection = select(sdk, kind = kind)
                assertTrue(
                    "sdk=$sdk kind=$kind selected $selection",
                    selection is HapticSelection.LegacyOneShot ||
                        selection is HapticSelection.LegacyWaveform,
                )
            }
        }
    }

    @Test
    fun `API 30 SOFT is a low-amplitude one-shot`() {
        assertEquals(
            HapticSelection.LegacyOneShot(durationMillis = 20L, amplitude = 128),
            select(30, kind = HapticKind.SOFT),
        )
    }

    @Test
    fun `API 29 WARNING is a two-pulse legacy waveform`() {
        val selection = select(29, kind = HapticKind.WARNING) as HapticSelection.LegacyWaveform
        assertEquals(selection.timings.size, selection.amplitudes.size)
        assertEquals(2, selection.amplitudes.count { it > 0 })
    }

    @Test
    fun `API 30 SUCCESS_SHIMMER is a three-pulse legacy waveform`() {
        val selection = select(30, kind = HapticKind.SUCCESS_SHIMMER) as HapticSelection.LegacyWaveform
        assertEquals(selection.timings.size, selection.amplitudes.size)
        assertEquals(3, selection.amplitudes.count { it > 0 })
    }

    @Test
    fun `API 31 and 32 SOFT composes a single CLICK at half scale`() {
        for (sdk in listOf(31, 32)) {
            assertEquals(
                "sdk=$sdk",
                HapticSelection.Composition(listOf(HapticStep(HapticPrimitive.CLICK, 0.5f))),
                select(sdk, kind = HapticKind.SOFT),
            )
        }
    }

    @Test
    fun `API 31 and 32 HEAVY composes a single THUD at full scale`() {
        for (sdk in listOf(31, 32)) {
            assertEquals(
                "sdk=$sdk",
                HapticSelection.Composition(listOf(HapticStep(HapticPrimitive.THUD, 1.0f))),
                select(sdk, kind = HapticKind.HEAVY),
            )
        }
    }

    @Test
    fun `API 31 and 32 SUCCESS_SHIMMER falls back to three TICK primitives`() {
        for (sdk in listOf(31, 32)) {
            val selection = select(sdk, kind = HapticKind.SUCCESS_SHIMMER) as HapticSelection.Composition
            assertEquals("sdk=$sdk", 3, selection.steps.size)
            assertTrue("sdk=$sdk", selection.steps.all { it.primitive == HapticPrimitive.TICK })
        }
    }

    @Test
    fun `API 31 and 32 never select the celebratory SPIN`() {
        for (sdk in listOf(31, 32)) {
            assertTrue(
                "sdk=$sdk",
                select(sdk, kind = HapticKind.SUCCESS_SHIMMER) !is HapticSelection.Celebratory,
            )
        }
    }

    @Test
    fun `API 33 and above SUCCESS_SHIMMER selects the celebratory SPIN`() {
        for (sdk in listOf(33, 34)) {
            assertEquals(
                "sdk=$sdk",
                HapticSelection.Celebratory(HapticStep(HapticPrimitive.SPIN, 1.0f)),
                select(sdk, kind = HapticKind.SUCCESS_SHIMMER),
            )
        }
    }

    @Test
    fun `API 33 non-shimmer kinds compose exactly like API 31`() {
        for (kind in HapticKind.entries.filter { it != HapticKind.SUCCESS_SHIMMER }) {
            assertEquals("kind=$kind", select(31, kind = kind), select(33, kind = kind))
        }
    }
}
