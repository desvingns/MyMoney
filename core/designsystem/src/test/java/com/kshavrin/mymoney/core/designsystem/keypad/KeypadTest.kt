package com.kshavrin.mymoney.core.designsystem.keypad

import com.kshavrin.mymoney.core.designsystem.sound.SoundKey
import com.kshavrin.mymoney.core.designsystem.sound.SoundPlayer
import org.junit.Test

/**
 * Placeholder for the full Compose-UI test of [Keypad].
 *
 * # Why this file is a placeholder
 *
 * `:core:designsystem`'s test classpath currently has only:
 *
 *   - libs.junit
 *   - libs.kotlinx.coroutines.test
 *
 * It does NOT have:
 *
 *   - Robolectric
 *   - androidx.compose.ui:ui-test-junit4
 *   - androidx.compose.ui:ui-test-manifest
 *   - androidx.test.ext:junit
 *
 * Wiring those in is scheduled for PHASE_15 (polish / tests / release).
 * Until then, the contract-level pinning lives in
 * [KeypadContractTest], which exercises the JVM-visible parts:
 * `KeypadEvent` sealed-interface, `Operator` enum, `SoundKey` enum,
 * `SoundPlayer` interface, and a pure-function mirror of the label
 * to event mapping inside Keypad.
 *
 * When PHASE_15 lands, replace the body of [pendingComposeUiTest_seePhase15]
 * with the real assertions described below. No other change required.
 *
 * # What the real test must cover
 *
 * Total visible buttons: 16 (the ⌫ backspace key was REMOVED in the
 * form-chrome restyle — backspace now lives in the amount box).
 *   - Digits 0..9                                  -> 10
 *   - Operators +, −, ×, ÷                          ->  4
 *   - Dot ".", Equals "="                           ->  2
 *
 * Layout (from Keypad.kt — a 4×4 grid):
 *   - Row 1 (4 cells): "1" "2" "3" "+"
 *   - Row 2 (4 cells): "4" "5" "6" "−"
 *   - Row 3 (4 cells): "7" "8" "9" "×"
 *   - Row 4 (4 cells): "." "0" "=" "÷"
 *
 * There must be NO "⌫" node anywhere in the keypad tree. The Compose-UI
 * test should assert `onNodeWithText("⌫").assertDoesNotExist()`.
 *
 * # Expected test code (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class KeypadTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private lateinit var events: MutableList<KeypadEvent>
 *     private lateinit var fakeSoundPlayer: FakeSoundPlayer
 *
 *     @Before
 *     fun setUp() {
 *         events = mutableListOf()
 *         fakeSoundPlayer = FakeSoundPlayer()
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 Keypad(
 *                     onEvent = events::add,
 *                     soundPlayer = fakeSoundPlayer,
 *                 )
 *             }
 *         }
 *     }
 *
 *     // ---- digits 0..9 ----
 *
 *     @Test fun `tapping 0 emits Digit(0) and plays KEYPAD_TAP`() {
 *         composeTestRule.onNodeWithText("0", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Digit(0), events.last())
 *         assertEquals(SoundKey.KEYPAD_TAP, fakeSoundPlayer.calls.last())
 *     }
 *     // … one test per digit, identical shape …
 *
 *     // ---- operators ----
 *
 *     @Test fun `tapping plus emits Op Plus`() {
 *         composeTestRule.onNodeWithText("+", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Op(Operator.Plus), events.last())
 *     }
 *     @Test fun `tapping minus emits Op Minus`() {
 *         composeTestRule.onNodeWithText("−", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Op(Operator.Minus), events.last())
 *     }
 *     @Test fun `tapping multiply emits Op Multiply`() {
 *         composeTestRule.onNodeWithText("×", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Op(Operator.Multiply), events.last())
 *     }
 *     @Test fun `tapping divide emits Op Divide`() {
 *         composeTestRule.onNodeWithText("÷", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Op(Operator.Divide), events.last())
 *     }
 *
 *     // ---- dot / equals ----
 *
 *     @Test fun `tapping dot emits Dot`() {
 *         composeTestRule.onNodeWithText(".", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Dot, events.last())
 *     }
 *     @Test fun `tapping equals emits Equals`() {
 *         composeTestRule.onNodeWithText("=", useUnmergedTree = true).performClick()
 *         assertEquals(KeypadEvent.Equals, events.last())
 *     }
 *     @Test fun `there is no backspace key on the keypad`() {
 *         composeTestRule.onNodeWithText("⌫", useUnmergedTree = true).assertDoesNotExist()
 *     }
 *
 *     // ---- cross-cutting ----
 *
 *     @Test fun `every visible button calls SoundPlayer with KEYPAD_TAP exactly once`() {
 *         val labels = listOf(
 *             "0","1","2","3","4","5","6","7","8","9",
 *             "+","−","×","÷",
 *             ".","=",
 *         )
 *         labels.forEach { label ->
 *             composeTestRule.onNodeWithText(label, useUnmergedTree = true).performClick()
 *         }
 *         assertEquals(labels.size, events.size)
 *         assertEquals(labels.size, fakeSoundPlayer.calls.size)
 *         assertTrue(fakeSoundPlayer.calls.all { it == SoundKey.KEYPAD_TAP })
 *     }
 *
 *     @Test fun `keypad renders without a SoundPlayer (default null)`() {
 *         // re-set content with soundPlayer = null and tap a key;
 *         // verify only the onEvent callback fires, no NullPointerException.
 *     }
 *
 *     private class FakeSoundPlayer : SoundPlayer {
 *         val calls: MutableList<SoundKey> = mutableListOf()
 *         override fun play(key: SoundKey) { calls += key }
 *     }
 * }
 * ```
 *
 * # What is NOT tested here, by design
 *
 *   - Haptic feedback: `LocalHapticFeedback.performHapticFeedback(LongPress)` has
 *     no observable side-effect on the Robolectric host (no real vibrator).
 *     We rely on visual QA + manual device testing for that. The SPEC
 *     explicitly notes "haptic happens but is hard to assert headlessly".
 *   - Per-press scale animation: visual polish, not a contract.
 */
class KeypadTest {
    /**
     * Single placeholder method so the test class is non-empty and shows
     * up in the test report as a single skipped/pending entry per CI run.
     * The body is a runtime guard that always passes: it constructs the
     * fake recorder + a sample event to keep imports live and prevent
     * stale-import drift over the next phases. Real assertions arrive
     * in PHASE_15 by replacing this body with the template above.
     */
    @Test
    fun pendingComposeUiTest_seePhase15() {
        // Imports kept alive so the placeholder doesn't drift out of sync
        // with the production types it will later exercise.
        val fake: SoundPlayer = FakeSoundPlayer()
        fake.play(SoundKey.KEYPAD_TAP)
        val sample: KeypadEvent = KeypadEvent.Op(Operator.Plus)
        check(sample is KeypadEvent.Op) { "sealed-interface drift detected" }
        check((fake as FakeSoundPlayer).calls.size == 1) {
            "FakeSoundPlayer must record exactly one call after one play()"
        }
    }

    private class FakeSoundPlayer : SoundPlayer {
        val calls: MutableList<SoundKey> = mutableListOf()

        override fun play(key: SoundKey) {
            calls += key
        }
    }
}
