package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the "consume once" contract for the [DashboardRoute] chart-settings auto-open behaviour.
 *
 * The production code (DashboardRoute) reads:
 *
 *   var chartSettingsAutoOpened by rememberSaveable { mutableStateOf(false) }
 *   LaunchedEffect(openChartSettings) {
 *       if (openChartSettings && !chartSettingsAutoOpened) {
 *           chartSettingsAutoOpened = true
 *           viewModel.onEvent(DashboardEvent.ChartSettingsClicked)
 *       }
 *   }
 *
 * [DashboardRoute] is Hilt-injected and cannot be rendered in a JVM compose test.
 * [ConsumeOnceOpener] below carries the identical [rememberSaveable] + [LaunchedEffect] key
 * pattern and is tested in isolation. A source-level contract check lives in DestinationsTest,
 * which asserts that the production file contains the guard tokens.
 *
 * The semantic reviewer flagged the gap: no test explicitly exercised "second recomposition
 * with openChartSettings=true on the SAME composable instance does not re-open the sheet"
 * (Gherkin: "Шторка авто-открывается один раз").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRouteOpenOnceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `openChartSettings false does not trigger auto-open`() {
        var openCount = 0
        composeTestRule.setContent {
            ConsumeOnceOpener(openChartSettings = false, onOpen = { openCount++ })
        }
        composeTestRule.waitForIdle()
        assertEquals(0, openCount)
    }

    @Test
    fun `openChartSettings true triggers auto-open exactly once`() {
        var openCount = 0
        composeTestRule.setContent {
            ConsumeOnceOpener(openChartSettings = true, onOpen = { openCount++ })
        }
        composeTestRule.waitForIdle()
        assertEquals(1, openCount)
    }

    @Test
    fun `second recomposition with openChartSettings=true still does not retrigger auto-open`() {
        // This is the primary gap flagged by the semantic reviewer:
        // LaunchedEffect(key) only re-runs when the key changes value.
        // A recomposition that leaves the key at true must NOT fire the callback again.
        var openCount = 0
        var recomposeTrigger by mutableStateOf(0)
        composeTestRule.setContent {
            // Reading recomposeTrigger subscribes this composition to changes in that state;
            // the always-true comparison avoids an "unused expression" compiler warning.
            check(recomposeTrigger >= 0)
            ConsumeOnceOpener(openChartSettings = true, onOpen = { openCount++ })
        }
        composeTestRule.waitForIdle()
        assertEquals("should open exactly once on initial composition", 1, openCount)

        recomposeTrigger = 1
        composeTestRule.waitForIdle()
        assertEquals("must not open a second time when key stays true across a recomposition", 1, openCount)

        recomposeTrigger = 2
        composeTestRule.waitForIdle()
        assertEquals("third recomposition with same key also must not fire", 1, openCount)
    }

    @Test
    fun `consumed flag prevents second open after key cycles false then true again`() {
        // Simulates a scenario where the nav arg key changes value and then returns to true.
        // The rememberSaveable consumed-flag must block a second trigger even when the
        // LaunchedEffect re-runs with a fresh true key after the false→true cycle.
        var openCount = 0
        var openChartSettings by mutableStateOf(false)
        composeTestRule.setContent {
            ConsumeOnceOpener(openChartSettings = openChartSettings, onOpen = { openCount++ })
        }
        composeTestRule.waitForIdle()
        assertEquals(0, openCount)

        openChartSettings = true
        composeTestRule.waitForIdle()
        assertEquals("first true must open once", 1, openCount)

        openChartSettings = false
        composeTestRule.waitForIdle()
        assertEquals("false does not open", 1, openCount)

        openChartSettings = true
        composeTestRule.waitForIdle()
        assertEquals("consumed flag must block the second true from opening again", 1, openCount)
    }
}

/**
 * Mirrors the consume-once guard in [DashboardRoute] without the Hilt dependency.
 *
 * Uses [rememberSaveable] (same as production) so the consumed flag survives the test's
 * recomposition cycles. The LaunchedEffect key is [openChartSettings] — identical to production —
 * so the effect only re-runs when the key value changes.
 */
@Composable
private fun ConsumeOnceOpener(
    openChartSettings: Boolean,
    onOpen: () -> Unit,
) {
    var consumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openChartSettings) {
        if (openChartSettings && !consumed) {
            consumed = true
            onOpen()
        }
    }
}
