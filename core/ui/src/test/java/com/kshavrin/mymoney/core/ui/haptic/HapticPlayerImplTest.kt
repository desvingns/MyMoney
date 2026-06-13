package com.kshavrin.mymoney.core.ui.haptic

import org.junit.Test

/**
 * Placeholder for the Robolectric-backed test of [HapticPlayerImpl].
 *
 * # Why this file is a placeholder
 *
 * `:core:ui`'s test classpath currently has NO test runner wired — the
 * module's `build.gradle.kts` declares only production + debug
 * dependencies, no `testImplementation`. To exercise [HapticPlayerImpl]
 * we need:
 *
 *   - org.robolectric:robolectric            (for android.os.Vibrator,
 *                                              android.os.VibratorManager,
 *                                              android.os.VibrationEffect,
 *                                              and per-SDK @Config control)
 *   - androidx.test.ext:junit                (ApplicationProvider)
 *   - libs.junit + libs.kotlinx.coroutines.test
 *
 * Those are NOT present yet. The kind → VibrationEffect.Composition
 * selection inside HapticPlayerImpl (including the SUCCESS_SHIMMER API
 * split) is `private` and is expressed entirely in terms of Android types
 * (Vibrator, VibrationEffect, Build.VERSION.SDK_INT), so there is no
 * JVM-visible seam to pin without Robolectric — hence this placeholder,
 * mirroring the `MonefyKeypadTest` convention in :core:designsystem.
 *
 * # What the real test must cover (SPEC §6.9 + CONSTRAINTS)
 *
 * The load-bearing CONSTRAINT is the per-API SUCCESS_SHIMMER behaviour:
 * there is no platform PRIMITIVE_SHIMMER, so the impl uses PRIMITIVE_SPIN
 * on API 33+ and falls back to TICK×3 on API 31–32. Robolectric's @Config
 * sdk lets us drive both branches headlessly via a shadow Vibrator that
 * records the composed VibrationEffect.
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(application = android.app.Application::class)
 * class HapticPlayerImplTest {
 *
 *     private val fakeSettings = FakeAppSettingsRepository()
 *
 *     private fun newPlayer(context: Context) =
 *         HapticPlayerImpl(context, fakeSettings, UnconfinedTestDispatcher())
 *
 *     // ---- hapticEnabled gating ----
 *
 *     @Test fun `fire does nothing when hapticEnabled is false`() {
 *         fakeSettings.seed(AppSettings(hapticEnabled = false))
 *         val player = newPlayer(ApplicationProvider.getApplicationContext())
 *         player.fire(HapticKind.SOFT)
 *         // ShadowVibrator records no vibration when gated out.
 *         assertNull(shadowOf(vibrator).mostRecentVibration)
 *     }
 *
 *     @Test fun `fire is a no-op and does not throw when device has no vibrator`() {
 *         // ShadowVibrator.setHasVibrator(false) → fire() short-circuits.
 *     }
 *
 *     // ---- SUCCESS_SHIMMER: API split (the headline constraint) ----
 *
 *     @Test @Config(sdk = [33])
 *     fun `SUCCESS_SHIMMER uses a single PRIMITIVE_SPIN on API 33`() {
 *         // Inspect the composed VibrationEffect: one primitive == SPIN.
 *     }
 *
 *     @Test @Config(sdk = [32])
 *     fun `SUCCESS_SHIMMER falls back to three TICK primitives on API 32`() {
 *         // Inspect the composed VibrationEffect: three primitives == TICK,
 *         // each at FULL_SCALE, gapped by GAP_MILLIS.
 *     }
 *
 *     @Test @Config(sdk = [31])
 *     fun `SUCCESS_SHIMMER falls back to three TICK primitives on API 31`() {
 *         // Lower bound of the fallback window (minSdk == 31).
 *     }
 *
 *     // ---- other kinds → primitive selection ----
 *
 *     @Test fun `SOFT composes a single CLICK at half scale`() { ... }
 *     @Test fun `MEDIUM composes a single CLICK at full scale`() { ... }
 *     @Test fun `HEAVY composes a single THUD at full scale`() { ... }
 *     @Test fun `WARNING composes two gapped TICK primitives`() { ... }
 * }
 * ```
 *
 * # What is NOT covered here, by design
 *
 *   - Physical vibration intensity / timing on real hardware: device-only.
 *   - The fact that `fire()` is wrapped behind `hasVibrator()`: covered by
 *     the no-vibrator gating test above.
 */
class HapticPlayerImplTest {
    /**
     * Single live-import guard so the placeholder cannot silently drift
     * away from the production contract over the coming phases. Constructs
     * the recorder + a sample kind; the assertions below always pass. Real
     * Robolectric assertions replace this body once the test classpath is
     * wired (see template above).
     */
    @Test
    fun pendingRobolectricTest_seeHapticPlayerImplWiring() {
        val player: HapticPlayer = RecordingHapticPlayer()
        player.fire(HapticKind.SOFT)
        player.fire(HapticKind.SUCCESS_SHIMMER)
        val rec = player as RecordingHapticPlayer
        check(rec.calls == listOf(HapticKind.SOFT, HapticKind.SUCCESS_SHIMMER)) {
            "HapticPlayer interface drift detected"
        }
        // The shimmer split is the headline constraint; assert the entry
        // still exists so a rename forces a visible update to this
        // placeholder (and to the Robolectric template above).
        check(HapticKind.entries.contains(HapticKind.SUCCESS_SHIMMER)) {
            "SUCCESS_SHIMMER must remain — it carries the API 33+ / 31-32 split"
        }
    }

    private class RecordingHapticPlayer : HapticPlayer {
        val calls: MutableList<HapticKind> = mutableListOf()

        override fun fire(kind: HapticKind) {
            calls += kind
        }
    }
}
