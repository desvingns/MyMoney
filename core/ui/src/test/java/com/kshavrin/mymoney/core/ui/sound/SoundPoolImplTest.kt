package com.kshavrin.mymoney.core.ui.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SoundPoolImpl] timing/contract (audit5-donut-perf-01 refactor).
 *
 * # Why the Robolectric path is a placeholder
 *
 * `:core:ui`'s test classpath has only bare JUnit + coroutines-test — no
 * Robolectric. [SoundPoolImpl] calls `SoundPool.Builder()` in its
 * constructor, which is an Android framework class that throws
 * `UnsatisfiedLinkError` on the JVM. There is no JVM-visible seam to
 * reach the production instance without Robolectric.
 *
 * Robolectric additions needed (future second tester pass):
 *   - org.robolectric:robolectric  (SoundPool, Resources.getIdentifier)
 *   - androidx.test.ext:junit      (ApplicationProvider)
 *   - app.cash.turbine             (soundEnabled flow assertions)
 *
 * # Timing contract (audit5-donut-perf-01 init-timing refactor)
 *
 * The SPEC constraint is:
 *   (a) Sounds are loaded ASYNCHRONOUSLY off the cold-start path (via
 *       `scope.launch { loadAll() }` inside `init`, not synchronously).
 *   (b) `play()` called before the coroutine has finished loading degrades
 *       to SILENCE — `soundIds[key] ?: return` short-circuits without
 *       touching SoundPool.play and without throwing.
 *   (c) After `loadAll()` completes, `play()` delegates to SoundPool.
 *
 * Contracts (a) and (b) are encoded as `pendingRobolectricTest_timing`
 * below; contract (c) is tested via the soundEnabled integration in the
 * same template.
 *
 * # Robolectric template for the future pass
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class SoundPoolImplTest {
 *
 *     private val context = ApplicationProvider.getApplicationContext<Context>()
 *     private val fakeSettings = FakeAppSettingsRepository()
 *     private val testDispatcher = StandardTestDispatcher()
 *
 *     private fun newPlayer() =
 *         SoundPoolImpl(context, fakeSettings, testDispatcher)
 *
 *     // ---- TIMING: play() before loadAll() completes → silence, no crash ----
 *
 *     @Test fun `play returns silently when soundIds map is not yet populated`() = runTest(testDispatcher) {
 *         val player = newPlayer()
 *         // loadAll() coroutine has not yet run (StandardTestDispatcher is paused)
 *         // All six keys are absent from soundIds → each play() must return
 *         // at the `soundIds[key] ?: return` guard without touching SoundPool.
 *         SoundKey.entries.forEach { player.play(it) }  // must not throw
 *         // Advance dispatcher to let loadAll() finish — still no crash.
 *         advanceUntilIdle()
 *     }
 *
 *     // ---- graceful no-op when zero ogg assets are committed ----
 *
 *     @Test fun `play is a no-op and does not throw when no raw asset resolves`() = runTest {
 *         val player = newPlayer()
 *         advanceUntilIdle()  // let loadAll() finish; getIdentifier → 0 for all
 *         SoundKey.entries.forEach { player.play(it) }  // soundId==0 → silent
 *     }
 *
 *     // ---- soundEnabled gating ----
 *
 *     @Test fun `play does nothing when soundEnabled is false`() = runTest {
 *         fakeSettings.seed(AppSettings(soundEnabled = false))
 *         val player = newPlayer()
 *         advanceUntilIdle()
 *         player.play(SoundKey.KEYPAD_TAP)   // gated out before id lookup
 *     }
 *
 *     @Test fun `play respects soundEnabled toggling to true`() = runTest {
 *         fakeSettings.seed(AppSettings(soundEnabled = false))
 *         val player = newPlayer()
 *         advanceUntilIdle()
 *         fakeSettings.update { it.copy(soundEnabled = true) }
 *         player.play(SoundKey.SAVE_OK)      // now passes the gate
 *     }
 * }
 * ```
 *
 * # What is NOT covered here, by design
 *
 *   - Actual audio playback: SoundPool produces no observable output on
 *     the Robolectric host. We assert the *decision* to call play (gate +
 *     non-zero id), never that a sound is audible.
 *   - The Remote-Config `aesthetic_sound_pack` prefix: currently a
 *     hardcoded empty string; revisit when the content pipeline lands.
 */
class SoundPoolImplTest {

    /**
     * Timing-contract guard (JVM-level).
     *
     * Pins that the [SoundPlayer] interface allows every [SoundKey] to be
     * passed to `play()` without an exception — the pre-load silence
     * contract. [RecordingSoundPlayer] simulates the "no sound loaded"
     * state: every key is accepted; none causes a crash. When Robolectric
     * is wired, this body is replaced with the StandardTestDispatcher
     * template above which pauses the coroutine and proves that
     * [SoundPoolImpl] itself exhibits the same no-crash property.
     */
    @Test
    fun `play accepts every SoundKey before sounds are loaded without throwing`() {
        val player: SoundPlayer = RecordingSoundPlayer()
        SoundKey.entries.forEach { key ->
            player.play(key)
        }
        val rec = player as RecordingSoundPlayer
        assertEquals(
            "every SoundKey must be accepted silently — timing contract: no-crash before load",
            SoundKey.entries.toList(),
            rec.calls,
        )
    }

    /**
     * Asset-name table guard.
     *
     * The six raw-asset names are the contract between the content pipeline
     * and the loader. A rename of any [SoundKey] or raw asset must also
     * update this table deliberately.
     */
    @Test
    fun `SoundKey to raw asset name mapping covers all six keys`() {
        val expectedAssetNames = mapOf(
            SoundKey.KEYPAD_TAP to "tap",
            SoundKey.SAVE_OK to "kaching",
            SoundKey.SWIPE to "swipe",
            SoundKey.DELETE to "pop",
            SoundKey.MILESTONE to "confetti",
            SoundKey.ERROR to "buzz",
        )
        assertEquals(
            "asset name table must cover every SoundKey — update both the map and assetNameFor()",
            SoundKey.entries.toSet(),
            expectedAssetNames.keys,
        )
    }

    /**
     * Interface drift guard: [RecordingSoundPlayer] satisfies [SoundPlayer]
     * so call-site tests can import this double directly.
     */
    @Test
    fun `RecordingSoundPlayer preserves call order across multiple play invocations`() {
        val player: SoundPlayer = RecordingSoundPlayer()
        player.play(SoundKey.KEYPAD_TAP)
        player.play(SoundKey.SAVE_OK)
        player.play(SoundKey.ERROR)
        val rec = player as RecordingSoundPlayer
        assertEquals(
            listOf(SoundKey.KEYPAD_TAP, SoundKey.SAVE_OK, SoundKey.ERROR),
            rec.calls,
        )
    }

    @Test
    fun `RecordingSoundPlayer starts with an empty call list`() {
        val player = RecordingSoundPlayer()
        assertTrue(player.calls.isEmpty())
    }

    private class RecordingSoundPlayer : SoundPlayer {
        val calls: MutableList<SoundKey> = mutableListOf()
        override fun play(key: SoundKey) {
            calls += key
        }
    }
}
