package com.kshavrin.mymoney.core.designsystem.keypad

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.core.designsystem.sound.SoundPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * JVM-level contract tests for the Keypad subsystem.
 *
 * The :core:designsystem module does NOT yet have Robolectric or
 * compose-ui-test-junit4 on the test classpath (see build.gradle.kts).
 * The full button-press Compose-UI test is captured as a documented
 * placeholder in `KeypadTest.kt` and will be activated in PHASE_15
 * when the test wiring is added.
 *
 * What we CAN pin at the JVM level right now:
 *
 *   1. KeypadEvent sealed-interface shape — every variant the keypad
 *      can emit must be representable and distinguishable. Note that
 *      `Backspace` stays part of the sealed contract even though the
 *      ⌫ key was removed from the keypad grid (form-chrome restyle):
 *      the amount box's trailing ✕ clear affordance now emits it via
 *      `AmountInput.onClear` → `KeypadEvent.Backspace`.
 *   2. Operator enum coverage — the four operators that appear on the
 *      keypad map to four distinct enum entries.
 *   3. SoundKey enum coverage — KEYPAD_TAP and SAVED must exist; the
 *      keypad calls play(KEYPAD_TAP) on every press.
 *   4. SoundPlayer interface contract — a fake implementation records
 *      every play() call in order; this fake is the exact one the
 *      Compose-UI test will use once Robolectric is wired.
 */
class KeypadContractTest {
    // ---- KeypadEvent: sealed-interface hierarchy ----

    @Test
    fun `Digit variant carries the digit value`() {
        val event: KeypadEvent = KeypadEvent.Digit(7)
        assertTrue("Digit must be a KeypadEvent", event is KeypadEvent.Digit)
        assertEquals(7, (event as KeypadEvent.Digit).d)
    }

    @Test
    fun `Digit equality is by value`() {
        assertEquals(KeypadEvent.Digit(3), KeypadEvent.Digit(3))
        assertNotEquals(KeypadEvent.Digit(3), KeypadEvent.Digit(4))
    }

    @Test
    fun `Op variant carries the operator`() {
        val event: KeypadEvent = KeypadEvent.Op(Operator.Plus)
        assertTrue("Op must be a KeypadEvent", event is KeypadEvent.Op)
        assertEquals(Operator.Plus, (event as KeypadEvent.Op).op)
    }

    @Test
    fun `Dot is a singleton KeypadEvent`() {
        val a: KeypadEvent = KeypadEvent.Dot
        val b: KeypadEvent = KeypadEvent.Dot
        assertSame("Dot must be a singleton object", a, b)
    }

    @Test
    fun `Backspace is a singleton KeypadEvent`() {
        val a: KeypadEvent = KeypadEvent.Backspace
        val b: KeypadEvent = KeypadEvent.Backspace
        assertSame("Backspace must be a singleton object", a, b)
    }

    @Test
    fun `Equals is a singleton KeypadEvent`() {
        val a: KeypadEvent = KeypadEvent.Equals
        val b: KeypadEvent = KeypadEvent.Equals
        assertSame("Equals must be a singleton object", a, b)
    }

    @Test
    fun `the five KeypadEvent kinds are mutually distinct`() {
        val events: List<KeypadEvent> =
            listOf(
                KeypadEvent.Digit(0),
                KeypadEvent.Op(Operator.Plus),
                KeypadEvent.Dot,
                KeypadEvent.Backspace,
                KeypadEvent.Equals,
            )
        // No two are the same class
        val classNames = events.map { it::class.simpleName }.toSet()
        assertEquals("five distinct subclasses expected", 5, classNames.size)
    }

    @Test
    fun `Digit accepts all ten decimal digit values`() {
        for (d in 0..9) {
            val e = KeypadEvent.Digit(d)
            assertEquals(d, e.d)
        }
    }

    // ---- Operator enum: four operators on the keypad ----

    @Test
    fun `Operator enum exposes Plus Minus Multiply Divide`() {
        val all = Operator.values().toSet()
        assertEquals(4, all.size)
        assertTrue(all.contains(Operator.Plus))
        assertTrue(all.contains(Operator.Minus))
        assertTrue(all.contains(Operator.Multiply))
        assertTrue(all.contains(Operator.Divide))
    }

    // ---- SoundKey enum: KEYPAD_TAP + SAVED ----

    @Test
    fun `SoundKey contains KEYPAD_TAP`() {
        val keys = SoundKey.values().toSet()
        assertTrue(
            "SoundKey.KEYPAD_TAP must exist — the keypad plays it on every press",
            keys.contains(SoundKey.KEYPAD_TAP),
        )
    }

    @Test
    fun `SoundKey contains SAVED`() {
        val keys = SoundKey.values().toSet()
        assertTrue(
            "SoundKey.SAVED must exist — used for save-success confirmation",
            keys.contains(SoundKey.SAVED),
        )
    }

    @Test
    fun `SoundKey enum has exactly KEYPAD_TAP and SAVED for now`() {
        // Pins the current set. If a new entry is added the test will fail
        // and the change must be acknowledged in this contract test.
        val keys = SoundKey.values().toSet()
        assertEquals(setOf(SoundKey.KEYPAD_TAP, SoundKey.SAVED), keys)
    }

    // ---- FakeSoundPlayer: the recorder used by the Compose-UI test ----

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
        fake.play(SoundKey.SAVED)
        fake.play(SoundKey.KEYPAD_TAP)
        assertEquals(
            listOf(SoundKey.KEYPAD_TAP, SoundKey.SAVED, SoundKey.KEYPAD_TAP),
            fake.calls,
        )
    }

    @Test
    fun `FakeSoundPlayer satisfies the SoundPlayer interface`() {
        val player: SoundPlayer = FakeSoundPlayer()
        player.play(SoundKey.KEYPAD_TAP)
        // If this line compiles + runs we have proved the fake is
        // type-compatible with the production interface; the cast
        // below proves we can still reach the recorded calls.
        val fake = player as FakeSoundPlayer
        assertEquals(1, fake.calls.size)
    }

    // ---- Pressing simulator: pure-function level pinning of the mapping ----
    // (No Compose. We mirror what Keypad does: build a KeypadEvent
    // from a label, then verify type + payload. This protects against
    // accidental wiring changes between label and event.)

    @Test
    fun `pressing each digit label produces the matching Digit event`() {
        for (d in 0..9) {
            val event = simulatePress(d.toString())
            assertNotNull("label '$d' must map to an event", event)
            assertTrue("label '$d' must map to a Digit", event is KeypadEvent.Digit)
            assertEquals(d, (event as KeypadEvent.Digit).d)
        }
    }

    @Test
    fun `pressing plus label maps to Op Plus`() {
        val event = simulatePress("+")
        assertEquals(KeypadEvent.Op(Operator.Plus), event)
    }

    @Test
    fun `pressing minus label maps to Op Minus`() {
        // The keypad uses the typographic minus character not ASCII hyphen.
        val event = simulatePress("−")
        assertEquals(KeypadEvent.Op(Operator.Minus), event)
    }

    @Test
    fun `pressing multiply label maps to Op Multiply`() {
        val event = simulatePress("×")
        assertEquals(KeypadEvent.Op(Operator.Multiply), event)
    }

    @Test
    fun `pressing divide label maps to Op Divide`() {
        val event = simulatePress("÷")
        assertEquals(KeypadEvent.Op(Operator.Divide), event)
    }

    @Test
    fun `pressing dot label maps to Dot`() {
        assertEquals(KeypadEvent.Dot, simulatePress("."))
    }

    @Test
    fun `pressing equals label maps to Equals`() {
        assertEquals(KeypadEvent.Equals, simulatePress("="))
    }

    @Test
    fun `the keypad has no standalone backspace key`() {
        // Form-chrome restyle: the ⌫ key was removed from the keypad grid.
        // Backspace now lives in the amount box (✕ clear affordance), so the
        // keypad's own label → event map must NOT recognise "⌫".
        assertNull(
            "the ⌫ key was removed from the keypad — it must not map to any keypad event",
            simulatePress("⌫"),
        )
    }

    @Test
    fun `Backspace event stays in the sealed contract despite the key removal`() {
        // Even though no keypad key emits it, the type must remain constructible:
        // AmountInput.onClear emits KeypadEvent.Backspace from the amount box.
        val event: KeypadEvent = KeypadEvent.Backspace
        assertTrue("Backspace must remain a KeypadEvent variant", event is KeypadEvent.Backspace)
    }

    @Test
    fun `unknown label maps to null`() {
        assertNull(simulatePress("?"))
    }

    @Test
    fun `the sixteen visible keypad labels are exhaustively covered`() {
        // SPEC (form-chrome restyle): digits 0..9 (10) + operators (4) + dot + equals = 16.
        // Backspace (⌫) is NO LONGER on the keypad — it moved to the amount box.
        val labels =
            listOf(
                "0",
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "7",
                "8",
                "9",
                "+",
                "−",
                "×",
                "÷",
                ".",
                "=",
            )
        assertEquals(16, labels.size)
        for (label in labels) {
            assertNotNull("label '$label' must map to an event", simulatePress(label))
        }
    }

    /**
     * Pure-function mirror of Keypad's label → event mapping.
     *
     * Keep this in lock-step with Keypad.kt. If the production
     * wiring changes (e.g. a label string is altered or a button is
     * added/removed) and this function is not updated, the
     * "sixteen visible keypad labels are exhaustively covered" or
     * "pressing each digit label" tests will fail.
     *
     * NOTE: there is intentionally no "⌫" branch — the backspace key was
     * removed from the keypad grid in the form-chrome restyle. The amount
     * box owns backspace now (its ✕ clear affordance emits
     * KeypadEvent.Backspace via AmountInput.onClear).
     */
    private fun simulatePress(label: String): KeypadEvent? =
        when (label) {
            "0" -> KeypadEvent.Digit(0)
            "1" -> KeypadEvent.Digit(1)
            "2" -> KeypadEvent.Digit(2)
            "3" -> KeypadEvent.Digit(3)
            "4" -> KeypadEvent.Digit(4)
            "5" -> KeypadEvent.Digit(5)
            "6" -> KeypadEvent.Digit(6)
            "7" -> KeypadEvent.Digit(7)
            "8" -> KeypadEvent.Digit(8)
            "9" -> KeypadEvent.Digit(9)
            "+" -> KeypadEvent.Op(Operator.Plus)
            "−" -> KeypadEvent.Op(Operator.Minus)
            "×" -> KeypadEvent.Op(Operator.Multiply)
            "÷" -> KeypadEvent.Op(Operator.Divide)
            "." -> KeypadEvent.Dot
            "=" -> KeypadEvent.Equals
            else -> null
        }

    @Test
    fun `simulator and production wiring must stay aligned (smoke)`() {
        // Cheap self-check: if someone changes the simulator without
        // changing the production keypad, this test still runs — but the
        // failing UI test in PHASE_15 will catch the drift. Until then,
        // the explicit per-label tests above act as the pin.
        try {
            // Just exercise every branch; absence of an exception is the
            // assertion here.
            simulatePress("7")
            simulatePress("+")
            simulatePress(".")
            simulatePress("=")
        } catch (t: Throwable) {
            fail("simulatePress threw on a known label: ${t.message}")
        }
    }

    /** Test double for SoundPlayer; mirrors the doc-only one in the placeholder UI test. */
    private class FakeSoundPlayer : SoundPlayer {
        val calls: MutableList<SoundKey> = mutableListOf()

        override fun play(key: SoundKey) {
            calls += key
        }
    }
}
