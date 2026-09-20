package com.kshavrin.mymoney.core.ui.haptic

enum class HapticPrimitive {
    CLICK,
    TICK,
    THUD,
    SPIN,
}

data class HapticStep(
    val primitive: HapticPrimitive,
    val scale: Float,
    val delayMillis: Int = 0,
)

sealed interface HapticSelection {
    data object None : HapticSelection

    data class LegacyOneShot(
        val durationMillis: Long,
        val amplitude: Int,
    ) : HapticSelection

    data class LegacyWaveform(
        val timings: List<Long>,
        val amplitudes: List<Int>,
    ) : HapticSelection

    data class Composition(
        val steps: List<HapticStep>,
    ) : HapticSelection

    data class Celebratory(
        val step: HapticStep,
    ) : HapticSelection
}

/**
 * Pure, Android-free decision seam for the haptic subsystem. It maps
 * (SDK band, enable flag, vibrator presence, [HapticKind]) to a [HapticSelection]
 * so the per-API behaviour is unit-testable on the JVM without Robolectric.
 *
 * Band split (deliberate, see TDD §6.9): API 29/30 use the legacy one-shot /
 * waveform primitives only — the API 31+ `VibrationEffect.Composition` path is
 * never chosen there, so no API 31 class is reachable on Android 10/11.
 */
object HapticEffectSelector {
    private const val API_S = 31
    private const val API_TIRAMISU = 33

    private const val SOFT_SCALE = 0.5f
    private const val FULL_SCALE = 1.0f
    private const val GAP_MILLIS = 40

    private const val LEGACY_SOFT_MILLIS = 20L
    private const val LEGACY_MEDIUM_MILLIS = 30L
    private const val LEGACY_HEAVY_MILLIS = 45L
    private const val LEGACY_PULSE_MILLIS = 25L
    private const val LEGACY_GAP_MILLIS = 40L
    private const val LEGACY_SOFT_AMPLITUDE = 128
    private const val LEGACY_FULL_AMPLITUDE = 255

    fun select(
        sdkInt: Int,
        hapticsEnabled: Boolean,
        hasVibrator: Boolean,
        kind: HapticKind,
    ): HapticSelection {
        if (!hapticsEnabled || !hasVibrator) return HapticSelection.None
        return when {
            sdkInt >= API_TIRAMISU -> celebratoryOrComposition(kind)
            sdkInt >= API_S -> composition(kind)
            else -> legacy(kind)
        }
    }

    private fun celebratoryOrComposition(kind: HapticKind): HapticSelection =
        when (kind) {
            HapticKind.SUCCESS_SHIMMER -> HapticSelection.Celebratory(HapticStep(HapticPrimitive.SPIN, FULL_SCALE))
            else -> composition(kind)
        }

    private fun composition(kind: HapticKind): HapticSelection =
        when (kind) {
            HapticKind.SOFT ->
                HapticSelection.Composition(listOf(HapticStep(HapticPrimitive.CLICK, SOFT_SCALE)))
            HapticKind.MEDIUM ->
                HapticSelection.Composition(listOf(HapticStep(HapticPrimitive.CLICK, FULL_SCALE)))
            HapticKind.HEAVY ->
                HapticSelection.Composition(listOf(HapticStep(HapticPrimitive.THUD, FULL_SCALE)))
            HapticKind.WARNING ->
                HapticSelection.Composition(
                    listOf(
                        HapticStep(HapticPrimitive.TICK, FULL_SCALE),
                        HapticStep(HapticPrimitive.TICK, FULL_SCALE, GAP_MILLIS),
                    ),
                )
            HapticKind.SUCCESS_SHIMMER ->
                HapticSelection.Composition(
                    listOf(
                        HapticStep(HapticPrimitive.TICK, FULL_SCALE),
                        HapticStep(HapticPrimitive.TICK, FULL_SCALE, GAP_MILLIS),
                        HapticStep(HapticPrimitive.TICK, FULL_SCALE, GAP_MILLIS),
                    ),
                )
        }

    private fun legacy(kind: HapticKind): HapticSelection =
        when (kind) {
            HapticKind.SOFT ->
                HapticSelection.LegacyOneShot(LEGACY_SOFT_MILLIS, LEGACY_SOFT_AMPLITUDE)
            HapticKind.MEDIUM ->
                HapticSelection.LegacyOneShot(LEGACY_MEDIUM_MILLIS, LEGACY_FULL_AMPLITUDE)
            HapticKind.HEAVY ->
                HapticSelection.LegacyOneShot(LEGACY_HEAVY_MILLIS, LEGACY_FULL_AMPLITUDE)
            HapticKind.WARNING ->
                HapticSelection.LegacyWaveform(
                    timings = listOf(0L, LEGACY_PULSE_MILLIS, LEGACY_GAP_MILLIS, LEGACY_PULSE_MILLIS),
                    amplitudes = listOf(0, LEGACY_FULL_AMPLITUDE, 0, LEGACY_FULL_AMPLITUDE),
                )
            HapticKind.SUCCESS_SHIMMER ->
                HapticSelection.LegacyWaveform(
                    timings =
                        listOf(
                            0L,
                            LEGACY_PULSE_MILLIS,
                            LEGACY_GAP_MILLIS,
                            LEGACY_PULSE_MILLIS,
                            LEGACY_GAP_MILLIS,
                            LEGACY_PULSE_MILLIS,
                        ),
                    amplitudes =
                        listOf(
                            0,
                            LEGACY_FULL_AMPLITUDE,
                            0,
                            LEGACY_FULL_AMPLITUDE,
                            0,
                            LEGACY_FULL_AMPLITUDE,
                        ),
                )
        }
}
