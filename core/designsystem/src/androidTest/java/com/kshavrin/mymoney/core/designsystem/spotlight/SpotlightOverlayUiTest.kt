package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.designsystem.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.core.designsystem.test.assertTouchWidthIsAtLeast
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG_TARGET = "spotlight_target_btn"
private const val TAG_OUTSIDE = "spotlight_outside_btn"
private const val SKIP_LABEL = "Skip all"
private const val PRIMARY_LABEL = "Next"

@RunWith(AndroidJUnit4::class)
class SpotlightOverlayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Circle shape ──────────────────────────────────────────────────────────

    @Test
    fun circle_tap_inside_cutout_reaches_underlying_button() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag(TAG_TARGET)
                            .spotlightTarget(registry, TAG_TARGET),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = TAG_TARGET, shape = SpotlightShape.Circle),
                        card = { Text("Card content") },
                        skipLabel = SKIP_LABEL,
                        primaryLabel = PRIMARY_LABEL,
                        onSkip = {},
                        onPrimary = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TAG_TARGET).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertTrue("tap inside circle cutout should reach button", clicked) }
    }

    @Test
    fun circle_tap_outside_cutout_is_blocked() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag(TAG_TARGET)
                            .spotlightTarget(registry, TAG_TARGET),
                    ) {}
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(56.dp)
                            .testTag(TAG_OUTSIDE),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = TAG_TARGET, shape = SpotlightShape.Circle),
                        card = { Text("Card content") },
                        skipLabel = SKIP_LABEL,
                        primaryLabel = PRIMARY_LABEL,
                        onSkip = {},
                        onPrimary = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TAG_OUTSIDE).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertFalse("tap outside circle cutout should be blocked", clicked) }
    }

    // ── RoundedRect shape ─────────────────────────────────────────────────────

    @Test
    fun rounded_rect_tap_inside_cutout_reaches_underlying_button() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag(TAG_TARGET)
                            .spotlightTarget(registry, TAG_TARGET),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = TAG_TARGET, shape = SpotlightShape.RoundedRect),
                        card = { Text("Card content") },
                        skipLabel = SKIP_LABEL,
                        primaryLabel = PRIMARY_LABEL,
                        onSkip = {},
                        onPrimary = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TAG_TARGET).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertTrue("tap inside rounded rect cutout should reach button", clicked) }
    }

    @Test
    fun rounded_rect_tap_outside_cutout_is_blocked() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag(TAG_TARGET)
                            .spotlightTarget(registry, TAG_TARGET),
                    ) {}
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(56.dp)
                            .testTag(TAG_OUTSIDE),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = TAG_TARGET, shape = SpotlightShape.RoundedRect),
                        card = { Text("Card content") },
                        skipLabel = SKIP_LABEL,
                        primaryLabel = PRIMARY_LABEL,
                        onSkip = {},
                        onPrimary = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TAG_OUTSIDE).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertFalse("tap outside rounded rect cutout should be blocked", clicked) }
    }

    // ── Buttons and card ──────────────────────────────────────────────────────

    @Test
    fun skip_and_primary_buttons_are_displayed_and_at_least_48dp() {
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    card = { Text("Hint text") },
                    skipLabel = SKIP_LABEL,
                    primaryLabel = PRIMARY_LABEL,
                    onSkip = {},
                    onPrimary = {},
                )
            }
        }
        composeTestRule.onNodeWithText(SKIP_LABEL).assertIsDisplayed()
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
        composeTestRule.onNodeWithText(PRIMARY_LABEL).assertIsDisplayed()
            .assertTouchHeightIsAtLeast(48.dp)
            .assertTouchWidthIsAtLeast(48.dp)
    }

    @Test
    fun skip_button_invokes_onSkip() {
        var skipped = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    card = {},
                    skipLabel = SKIP_LABEL,
                    primaryLabel = PRIMARY_LABEL,
                    onSkip = { skipped = true },
                    onPrimary = {},
                )
            }
        }
        composeTestRule.onNodeWithText(SKIP_LABEL).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertTrue(skipped) }
    }

    @Test
    fun primary_button_invokes_onPrimary() {
        var advanced = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    card = {},
                    skipLabel = SKIP_LABEL,
                    primaryLabel = PRIMARY_LABEL,
                    onSkip = {},
                    onPrimary = { advanced = true },
                )
            }
        }
        composeTestRule.onNodeWithText(PRIMARY_LABEL).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertTrue(advanced) }
    }

    @Test
    fun no_crash_when_cutout_key_is_unregistered() {
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = SpotlightCutout(key = "missing_key", shape = SpotlightShape.Circle),
                    card = { Text("Waiting") },
                    skipLabel = SKIP_LABEL,
                    primaryLabel = PRIMARY_LABEL,
                    onSkip = {},
                    onPrimary = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(SKIP_LABEL).assertIsDisplayed()
    }

    @Test
    fun null_registry_in_spotlight_target_is_noop() {
        var clicked = false
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .testTag(TAG_TARGET)
                            .spotlightTarget(registry = null, key = TAG_TARGET),
                    ) {}
                }
            }
        }
        composeTestRule.onNodeWithTag(TAG_TARGET).performTouchInput { click(center) }
        composeTestRule.runOnIdle { assertTrue("null registry: button should still be clickable", clicked) }
    }
}
