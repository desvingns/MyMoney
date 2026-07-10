package com.kshavrin.mymoney

import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.haptic.HapticPlayer
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.sound.SoundPlayer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the laziness contract of [LazySoundPlayer] and [LazyHapticPlayer]:
 *
 * - construction must NOT touch the underlying dagger.Lazy (no early init)
 * - the first call to play/fire must resolve the lazy exactly once and forward the argument
 * - subsequent calls must each resolve the lazy exactly once per call — no double-resolution
 *   per invocation (dagger.Lazy already caches the instance; the wrapper must not add its own
 *   extra get() call on top of that)
 */
class LazyPlayerDelegatesTest {
    // ---- test doubles ----

    /**
     * Counts how many times [get] is called; returns the same [instance] every time.
     *
     * The explicit type parameter T must be declared at the call site as the interface type
     * (e.g. CountingLazy<SoundPlayer>(…)) because dagger.Lazy is a Java interface and therefore
     * invariant in Kotlin — CountingLazy<RecordingSoundPlayer> would not satisfy dagger.Lazy<SoundPlayer>.
     */
    private class CountingLazy<T>(
        private val instance: T,
    ) : dagger.Lazy<T> {
        var getCallCount = 0
            private set

        override fun get(): T {
            getCallCount++
            return instance
        }
    }

    private class RecordingSoundPlayer : SoundPlayer {
        val played = mutableListOf<SoundKey>()

        override fun play(key: SoundKey) {
            played += key
        }
    }

    private class RecordingHapticPlayer : HapticPlayer {
        val fired = mutableListOf<HapticKind>()

        override fun fire(kind: HapticKind) {
            fired += kind
        }
    }

    // ---- LazySoundPlayer ----

    @Test
    fun `LazySoundPlayer does not resolve lazy delegate during construction`() {
        val fakeLazy = CountingLazy<SoundPlayer>(RecordingSoundPlayer())

        LazySoundPlayer(fakeLazy)

        assertEquals(0, fakeLazy.getCallCount)
    }

    @Test
    fun `LazySoundPlayer resolves delegate exactly once on first play`() {
        val fakeLazy = CountingLazy<SoundPlayer>(RecordingSoundPlayer())
        val player = LazySoundPlayer(fakeLazy)

        player.play(SoundKey.KEYPAD_TAP)

        assertEquals(1, fakeLazy.getCallCount)
    }

    @Test
    fun `LazySoundPlayer forwards the sound key to the underlying player unchanged`() {
        val underlying = RecordingSoundPlayer()
        val player = LazySoundPlayer(CountingLazy<SoundPlayer>(underlying))

        player.play(SoundKey.SAVE_OK)

        assertEquals(listOf(SoundKey.SAVE_OK), underlying.played)
    }

    @Test
    fun `LazySoundPlayer resolves delegate exactly once per call with no double-resolution`() {
        val fakeLazy = CountingLazy<SoundPlayer>(RecordingSoundPlayer())
        val player = LazySoundPlayer(fakeLazy)

        player.play(SoundKey.KEYPAD_TAP)
        player.play(SoundKey.DELETE)

        // one get() call per play() call — the wrapper must not call get() twice per invocation
        assertEquals(2, fakeLazy.getCallCount)
    }

    @Test
    fun `LazySoundPlayer forwards all subsequent keys to underlying player in order`() {
        val underlying = RecordingSoundPlayer()
        val player = LazySoundPlayer(CountingLazy<SoundPlayer>(underlying))

        player.play(SoundKey.KEYPAD_TAP)
        player.play(SoundKey.MILESTONE)
        player.play(SoundKey.ERROR)

        assertEquals(
            listOf(SoundKey.KEYPAD_TAP, SoundKey.MILESTONE, SoundKey.ERROR),
            underlying.played,
        )
    }

    // ---- LazyHapticPlayer ----

    @Test
    fun `LazyHapticPlayer does not resolve lazy delegate during construction`() {
        val fakeLazy = CountingLazy<HapticPlayer>(RecordingHapticPlayer())

        LazyHapticPlayer(fakeLazy)

        assertEquals(0, fakeLazy.getCallCount)
    }

    @Test
    fun `LazyHapticPlayer resolves delegate exactly once on first fire`() {
        val fakeLazy = CountingLazy<HapticPlayer>(RecordingHapticPlayer())
        val player = LazyHapticPlayer(fakeLazy)

        player.fire(HapticKind.SOFT)

        assertEquals(1, fakeLazy.getCallCount)
    }

    @Test
    fun `LazyHapticPlayer forwards the haptic kind to the underlying player unchanged`() {
        val underlying = RecordingHapticPlayer()
        val player = LazyHapticPlayer(CountingLazy<HapticPlayer>(underlying))

        player.fire(HapticKind.HEAVY)

        assertEquals(listOf(HapticKind.HEAVY), underlying.fired)
    }

    @Test
    fun `LazyHapticPlayer resolves delegate exactly once per call with no double-resolution`() {
        val fakeLazy = CountingLazy<HapticPlayer>(RecordingHapticPlayer())
        val player = LazyHapticPlayer(fakeLazy)

        player.fire(HapticKind.SOFT)
        player.fire(HapticKind.WARNING)

        // one get() call per fire() call — the wrapper must not call get() twice per invocation
        assertEquals(2, fakeLazy.getCallCount)
    }

    @Test
    fun `LazyHapticPlayer forwards all subsequent kinds to underlying player in order`() {
        val underlying = RecordingHapticPlayer()
        val player = LazyHapticPlayer(CountingLazy<HapticPlayer>(underlying))

        player.fire(HapticKind.SOFT)
        player.fire(HapticKind.WARNING)
        player.fire(HapticKind.SUCCESS_SHIMMER)

        assertEquals(
            listOf(HapticKind.SOFT, HapticKind.WARNING, HapticKind.SUCCESS_SHIMMER),
            underlying.fired,
        )
    }
}
