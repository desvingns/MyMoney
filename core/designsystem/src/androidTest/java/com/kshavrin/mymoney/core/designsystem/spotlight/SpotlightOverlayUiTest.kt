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
private const val STEP_TITLE = "Step title"
private const val SKIP_LABEL = "Skip all"
private const val PRIMARY_LABEL = "Next"

@RunWith(AndroidJUnit4::class)
class SpotlightOverlayUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Circle shape ──────────────────────────────────────────────────────────

    @Test
    fun circleTapInsideCutoutReachesUnderlyingButton() {
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
                        stepTitle = STEP_TITLE,
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
    fun circleTapOutsideCutoutIsBlocked() {
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
                        stepTitle = STEP_TITLE,
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

    // Matrix cell 4: circle shape, unregistered target, outside tap blocked.
    @Test
    fun circleUnregisteredTargetStillBlocksOutsideTap() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(56.dp)
                            .testTag(TAG_OUTSIDE),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = "unregistered_key", shape = SpotlightShape.Circle),
                        stepTitle = STEP_TITLE,
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
        composeTestRule.runOnIdle {
            assertFalse("unregistered circle target: whole scrim must block taps", clicked)
        }
    }

    // ── RoundedRect shape ─────────────────────────────────────────────────────

    @Test
    fun roundedRectTapInsideCutoutReachesUnderlyingButton() {
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
                        stepTitle = STEP_TITLE,
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
    fun roundedRectTapOutsideCutoutIsBlocked() {
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
                        stepTitle = STEP_TITLE,
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

    // Matrix cell 8: rounded-rect shape, unregistered target, outside tap blocked.
    @Test
    fun roundedRectUnregisteredTargetStillBlocksOutsideTap() {
        var clicked = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { clicked = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(56.dp)
                            .testTag(TAG_OUTSIDE),
                    ) {}
                    SpotlightOverlay(
                        registry = registry,
                        cutout = SpotlightCutout(key = "unregistered_key", shape = SpotlightShape.RoundedRect),
                        stepTitle = STEP_TITLE,
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
        composeTestRule.runOnIdle {
            assertFalse("unregistered rounded rect target: whole scrim must block taps", clicked)
        }
    }

    // ── Buttons and card ──────────────────────────────────────────────────────

    @Test
    fun skipAndPrimaryButtonsAreDisplayedAndAtLeast48dp() {
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    stepTitle = STEP_TITLE,
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
    fun skipButtonInvokesOnSkip() {
        var skipped = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    stepTitle = STEP_TITLE,
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
    fun primaryButtonInvokesOnPrimary() {
        var advanced = false
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = null,
                    stepTitle = STEP_TITLE,
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
    fun noCrashWhenCutoutKeyIsUnregistered() {
        composeTestRule.setContent {
            val registry = rememberSpotlightRegistry()
            MyMoneyTheme {
                SpotlightOverlay(
                    registry = registry,
                    cutout = SpotlightCutout(key = "missing_key", shape = SpotlightShape.Circle),
                    stepTitle = STEP_TITLE,
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
    fun nullRegistryInSpotlightTargetIsNoop() {
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
