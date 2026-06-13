package com.kshavrin.mymoney.core.ui.sound

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-level contract tests for the :core:ui sound subsystem.
 *
 * The production [SoundKey] enum drives the TDD §6.8 sound table and the
 * call sites wired across the keypad, dashboard, transaction and
 * swipe-to-delete screens. This test pins the *full membership* of the
 * enum so an accidental addition or removal fails loudly — same style as
 * the legacy `MonefyKeypadContractTest` in :core:designsystem.
 *
 * NOTE — this is a SEPARATE type from the legacy
 * `com.kshavrin.mymoney.core.designsystem.sound.SoundKey` ({KEYPAD_TAP, SAVED}).
 * That legacy enum still exists and is still correct; do not conflate them.
 *
 * The Android-bound behaviour of `SoundPoolImpl` (SoundPool, Resources
 * identifier lookup, soundEnabled gating, no-op when soundId == 0) is NOT
 * exercised here — those require Robolectric, which :core:ui's test
 * classpath does not have. That behaviour is captured as a documented
 * placeholder in [SoundPoolImplTest].
 */
class SoundPlayerContractTest {
    // ---- SoundKey: exact membership ----

    @Test
    fun `SoundKey has exactly the six aesthetic keys`() {
        // SPEC §6.8: KEYPAD_TAP, SAVE_OK, SWIPE, DELETE, MILESTONE, ERROR.
        // Pins the current set. Adding or removing an entry must update
        // this assertion deliberately.
        val keys = SoundKey.entries.toSet()
        assertEquals(
            setOf(
                SoundKey.KEYPAD_TAP,
                SoundKey.SAVE_OK,
                SoundKey.SWIPE,
                SoundKey.DELETE,
                SoundKey.MILESTONE,
                SoundKey.ERROR,
            ),
            keys,
        )
    }

    @Test
    fun `SoundKey count is six`() {
        assertEquals(6, SoundKey.entries.size)
    }

    @Test
    fun `SoundKey contains KEYPAD_TAP`() {
        assertTrue(SoundKey.entries.contains(SoundKey.KEYPAD_TAP))
    }

    @Test
    fun `SoundKey contains SAVE_OK`() {
        assertTrue(SoundKey.entries.contains(SoundKey.SAVE_OK))
    }

    @Test
    fun `SoundKey contains SWIPE`() {
        assertTrue(SoundKey.entries.contains(SoundKey.SWIPE))
    }

    @Test
    fun `SoundKey contains DELETE`() {
        assertTrue(SoundKey.entries.contains(SoundKey.DELETE))
    }

    @Test
    fun `SoundKey contains MILESTONE`() {
        assertTrue(SoundKey.entries.contains(SoundKey.MILESTONE))
    }

    @Test
    fun `SoundKey contains ERROR`() {
        assertTrue(SoundKey.entries.contains(SoundKey.ERROR))
    }

    @Test
    fun `SoundKey valueOf round-trips every entry by name`() {
        for (key in SoundKey.entries) {
            assertSame(key, SoundKey.valueOf(key.name))
        }
    }

    // ---- SoundPlayer interface: the fake recorder used by call-site tests ----

    @Test
    fun `FakeSoundPlayer records nothing when not invoked`() {
        val fake = FakeSoundPlayer()
        assertTrue("no calls expected on a fresh fake", fake.calls.isEmpty())
    }

    @Test
    fun `FakeSoundPlayer records a single play call`() {
        val fake = FakeSoundPlayer()
        fake.play(SoundKey.KEYPAD_TAP)
        assertEquals(listOf(SoundKey.KEYPAD_TAP), fake.calls)
    }

    @Test
    fun `FakeSoundPlayer preserves the order of multiple calls`() {
        val fake = FakeSoundPlayer()
        fake.play(SoundKey.KEYPAD_TAP)
        fake.play(SoundKey.SAVE_OK)
        fake.play(SoundKey.DELETE)
        assertEquals(
            listOf(SoundKey.KEYPAD_TAP, SoundKey.SAVE_OK, SoundKey.DELETE),
            fake.calls,
        )
    }

    @Test
    fun `FakeSoundPlayer satisfies the SoundPlayer interface`() {
        val player: SoundPlayer = FakeSoundPlayer()
        player.play(SoundKey.MILESTONE)
        val fake = player as FakeSoundPlayer
        assertEquals(1, fake.calls.size)
        assertEquals(SoundKey.MILESTONE, fake.calls.single())
    }

    /**
     * Test double for [SoundPlayer]. This is the exact recorder the
     * Compose-UI / call-site tests will use to assert "screen X plays
     * SoundKey Y" once Robolectric is wired on this module's test
     * classpath (see [SoundPoolImplTest]).
     */
    private class FakeSoundPlayer : SoundPlayer {
        val calls: MutableList<SoundKey> = mutableListOf()

        override fun play(key: SoundKey) {
            calls += key
        }
    }
}
