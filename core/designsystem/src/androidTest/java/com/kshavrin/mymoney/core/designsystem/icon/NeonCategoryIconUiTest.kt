package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeonCategoryIconUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `neon category icon renders with stable touch tile bounds`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                NeonCategoryIcon(
                    iconKey = "ic_cat_food",
                    contentDescription = "Food icon",
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Food icon")
            .assertIsDisplayed()
            .assertWidthIsEqualTo(56.dp)
            .assertHeightIsEqualTo(56.dp)
    }

    @Test
    fun `neon category icons render reference bitmap artwork`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .background(Color.Magenta)
                                .testTag("Food icon background"),
                    ) {
                        NeonCategoryIcon(
                            iconKey = "ic_cat_food",
                            contentDescription = "Food icon",
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .background(Color.Magenta)
                                .testTag("Entertainment icon background"),
                    ) {
                        NeonCategoryIcon(
                            iconKey = "ic_cat_entertainment",
                            contentDescription = "Entertainment icon",
                        )
                    }
                }
            }
        }

        val normalPixels =
            composeTestRule
                .onNodeWithTag("Food icon background")
                .captureToImage()
                .toPixelMap()
        val entertainmentPixels =
            composeTestRule
                .onNodeWithTag("Entertainment icon background")
                .captureToImage()
                .toPixelMap()
        val cornerDistance = averageOuterCornerDistanceFrom(Color.Magenta, normalPixels)

        assertTrue(
            "Reference tile should let the host background show through rounded corners: " +
                "distance=$cornerDistance, corners=${outerCornerSummary(normalPixels)}.",
            cornerDistance < 0.18f,
        )
        assertTrue(
            "Reference tile should keep bright neon linework.",
            maxInteriorLuminance(normalPixels) > 0.55f,
        )
        assertTrue(
            "Different category keys should render different bitmap artwork.",
            differentPixelCount(normalPixels, entertainmentPixels) > normalPixels.width * normalPixels.height / 10,
        )
    }

    private fun averageOuterCornerDistanceFrom(
        expected: Color,
        pixelMap: PixelMap,
    ): Float {
        var total = 0f
        var count = 0
        val corners: List<Pair<Int, Int>> =
            listOf(
                0 to 0,
                pixelMap.width - 1 to 0,
                0 to pixelMap.height - 1,
                pixelMap.width - 1 to pixelMap.height - 1,
            )
        corners.forEach { (x, y) ->
            val color = pixelMap[x, y]
            total +=
                kotlin.math.abs(color.red - expected.red) +
                kotlin.math.abs(color.green - expected.green) +
                kotlin.math.abs(color.blue - expected.blue)
            count += 1
        }
        return total / count
    }

    private fun outerCornerSummary(pixelMap: PixelMap): String =
        listOf(
            0 to 0,
            pixelMap.width - 1 to 0,
            0 to pixelMap.height - 1,
            pixelMap.width - 1 to pixelMap.height - 1,
        ).joinToString(prefix = "[", postfix = "]") { (x, y) ->
            val color = pixelMap[x, y]
            "(${color.red},${color.green},${color.blue},${color.alpha})"
        }

    private fun maxInteriorLuminance(pixelMap: PixelMap): Float {
        var max = 0f
        val xStart = pixelMap.width / 5
        val xEnd = pixelMap.width * 4 / 5
        val yStart = pixelMap.height / 5
        val yEnd = pixelMap.height * 4 / 5
        for (x in xStart until xEnd) {
            for (y in yStart until yEnd) {
                max = maxOf(max, pixelMap[x, y].luminance())
            }
        }
        return max
    }

    private fun differentPixelCount(
        first: PixelMap,
        second: PixelMap,
    ): Int {
        var count = 0
        val width = minOf(first.width, second.width)
        val height = minOf(first.height, second.height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val a = first[x, y]
                val b = second[x, y]
                val delta =
                    kotlin.math.abs(a.red - b.red) +
                        kotlin.math.abs(a.green - b.green) +
                        kotlin.math.abs(a.blue - b.blue)
                if (delta > 0.18f) count += 1
            }
        }
        return count
    }
}
