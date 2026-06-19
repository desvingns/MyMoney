package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeonRingChartUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `outer container reserves glow allowance beyond the nominal diameter`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(fraction = 0.5f) {}
            }
        }

        val outerBounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedOuterSizePx = with(composeTestRule.density) { 264.dp.toPx() }
        val simpleGlowExtentPx =
            with(composeTestRule.density) {
                (200.dp + Spacing.neonRingGlowRadius * 2).toPx()
            }

        assertTrue(
            "outer chart container must stay wider than diameter + 2*glowRadius",
            outerBounds.width > simpleGlowExtentPx,
        )
        assertEquals(expectedOuterSizePx, outerBounds.width, 1f)
        assertEquals(expectedOuterSizePx, outerBounds.height, 1f)
    }

    @Test
    fun `center content fills the centered inner slot instead of the outer glow container`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(fraction = 0.5f) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .testTag(CENTER_CONTENT_TAG),
                    )
                }
            }
        }

        val outerBounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val centerBounds =
            composeTestRule
                .onNodeWithTag(CENTER_CONTENT_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedInnerDiameterPx = with(composeTestRule.density) { 160.dp.toPx() }

        assertEquals(expectedInnerDiameterPx, centerBounds.width, 1f)
        assertEquals(expectedInnerDiameterPx, centerBounds.height, 1f)
        assertTrue(
            "center content must stay inside the outer ring container",
            centerBounds.left >= outerBounds.left &&
                centerBounds.top >= outerBounds.top &&
                centerBounds.right <= outerBounds.right &&
                centerBounds.bottom <= outerBounds.bottom,
        )
        assertEquals(outerBounds.center.x, centerBounds.center.x, 1f)
        assertEquals(outerBounds.center.y, centerBounds.center.y, 1f)
    }

    @Test
    fun `default invocation without explicit gradient params renders chart node with correct outer bounds`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(fraction = 0.5f) {}
            }
        }

        val bounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedSizePx = with(composeTestRule.density) { 264.dp.toPx() }
        assertEquals(expectedSizePx, bounds.width, 1f)
        assertEquals(expectedSizePx, bounds.height, 1f)
    }

    @Test
    fun `explicit red gradient params are accepted and chart node exists with unchanged bounds`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(
                    fraction = 0.6f,
                    gradientStart = Color(0xFFFF4444),
                    gradientEnd = Color(0xFFCC0000),
                ) {}
            }
        }

        val bounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedSizePx = with(composeTestRule.density) { 264.dp.toPx() }
        assertEquals(expectedSizePx, bounds.width, 1f)
        assertEquals(expectedSizePx, bounds.height, 1f)
    }

    @Test
    fun `explicit green gradient params are accepted and chart node exists with unchanged bounds`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(
                    fraction = 0.9f,
                    gradientStart = Color(0xFF44FF88),
                    gradientEnd = Color(0xFF00CC44),
                ) {}
            }
        }

        val bounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedSizePx = with(composeTestRule.density) { 264.dp.toPx() }
        assertEquals(expectedSizePx, bounds.width, 1f)
        assertEquals(expectedSizePx, bounds.height, 1f)
    }

    @Test
    fun `zero fraction chart renders without gradient arc but outer node still present`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(
                    fraction = 0f,
                    gradientStart = Color(0xFFFF4444),
                    gradientEnd = Color(0xFFCC0000),
                ) {}
            }
        }

        composeTestRule
            .onNodeWithTag(NEON_RING_CHART_TAG)
            .assertExists()

        val bounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedSizePx = with(composeTestRule.density) { 264.dp.toPx() }
        assertEquals(expectedSizePx, bounds.width, 1f)
        assertEquals(expectedSizePx, bounds.height, 1f)
    }

    @Test
    fun `chart with gradient params passes center content through to inner slot`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonRingChart(
                    fraction = 0.5f,
                    gradientStart = Color(0xFFFF4444),
                    gradientEnd = Color(0xFFCC0000),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .testTag(CENTER_CONTENT_TAG),
                    )
                }
            }
        }

        val centerBounds =
            composeTestRule
                .onNodeWithTag(CENTER_CONTENT_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val outerBounds =
            composeTestRule
                .onNodeWithTag(NEON_RING_CHART_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedInnerDiameterPx = with(composeTestRule.density) { 160.dp.toPx() }

        assertEquals(expectedInnerDiameterPx, centerBounds.width, 1f)
        assertEquals(expectedInnerDiameterPx, centerBounds.height, 1f)
        assertEquals(outerBounds.center.x, centerBounds.center.x, 1f)
        assertEquals(outerBounds.center.y, centerBounds.center.y, 1f)
    }

    private companion object {
        const val CENTER_CONTENT_TAG = "neon_ring_center_content"
    }
}
