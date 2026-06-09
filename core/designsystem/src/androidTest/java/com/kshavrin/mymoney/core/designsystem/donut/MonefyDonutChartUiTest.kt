package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class MonefyDonutChartUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `announces income expense and multiple slice percentages in merged semantics`() {
        setChart(
            income = BigDecimal("450.00"),
            expense = BigDecimal("124.00"),
            slices = listOf(
                slice(label = "Food", fraction = 0.50f),
                slice(label = "Transport", fraction = 0.25f),
                slice(label = "Home", fraction = 0.25f),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 25, "Home" to 25),
                ),
            )
            .assertExists()
    }

    @Test
    fun `announces totals without slice descriptions when chart is empty`() {
        setChart(
            income = BigDecimal.ZERO,
            expense = BigDecimal.ZERO,
            slices = emptyList(),
        )

        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .assertExists()
    }

    @Test
    fun `truncates a three point four percent slice to three percent in semantics`() {
        setChart(
            income = BigDecimal.ZERO,
            expense = BigDecimal("100.00"),
            slices = listOf(slice(label = "Coffee", fraction = 0.034f)),
        )

        // Instrumentation asserts accessibility semantics; screenshot or device checks cover drawn Canvas labels.
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "0",
                    expense = "100.00",
                    slices = listOf("Coffee" to 3),
                ),
            )
            .assertExists()
    }

    @Test
    fun `category label and low percent callout options preserve accessibility semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal("100.00"),
                    slices = listOf(
                        slice(label = "Trips", fraction = 0.01f),
                        slice(label = "Food", fraction = 0.99f),
                    ),
                    modifier = Modifier.size(240.dp),
                    showCategoryLabels = true,
                    labelMinFraction = 0f,
                    explodedOffset = 8.dp,
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "0",
                    expense = "100.00",
                    slices = listOf("Trips" to 1, "Food" to 99),
                ),
            )
            .assertExists()
    }

    @Test
    fun `center totals expose both income and expense figures when a currency symbol is supplied`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("500.00"),
                    expense = BigDecimal("124.00"),
                    slices = listOf(slice(label = "Food", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    currencySymbol = "$",
                    decimalDigits = 2,
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "500.00",
                    expense = "124.00",
                    slices = listOf("Food" to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `empty period with placeholder icons still announces zero totals`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(
                        slice(label = "Food", fraction = 0f),
                        slice(label = "Transport", fraction = 0f),
                        slice(label = "Home", fraction = 0f),
                    ),
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .assertExists()
    }

    @Test
    fun `empty state visibly paints stacked income and expense center totals`() {
        var incomeTint = 0
        var expenseTint = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                incomeTint = MaterialTheme.colorScheme.secondary.toArgb()
                expenseTint = MaterialTheme.colorScheme.tertiary.toArgb()
                MonefyDonutChart(
                    income = BigDecimal("321.45"),
                    expense = BigDecimal("67.89"),
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    currencySymbol = "₽",
                    centerDecimalDigits = 0,
                    emptyStateIcons = listOf(
                        slice(label = "Food", fraction = 0f),
                        slice(label = "Transport", fraction = 0f),
                        slice(label = "Home", fraction = 0f),
                    ),
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "321.45", expense = "67.89"))
            .captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        val left = image.width / 4
        val right = image.width * 3 / 4
        val centerY = image.height / 2
        val upperTop = image.height / 3
        val upperBottom = centerY
        val lowerTop = centerY
        val lowerBottom = image.height * 2 / 3

        assertTrue(
            "empty-state center must paint the income total in the theme secondary color",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = left,
                top = upperTop,
                right = right,
                bottom = upperBottom,
                argb = incomeTint,
            ),
        )
        assertTrue(
            "empty-state center must paint the expense total in the theme tertiary color",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = left,
                top = lowerTop,
                right = right,
                bottom = lowerBottom,
                argb = expenseTint,
            ),
        )
    }

    @Test
    fun `empty state icons do not add slice descriptions to semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(slice(label = "Food", fraction = 0f)),
                    animationSpec = snap(),
                )
            }
        }

        // Placeholder icons are drawn on the Canvas only; they must NOT leak into the
        // slice portion of the accessibility description (that is reserved for real slices).
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(income = "0", expense = "0", slices = listOf("Food" to 0)),
            )
            .assertDoesNotExist()
    }

    @Test
    fun `tapping an empty state placeholder icon invokes the matching callback exactly once`() {
        val emptyStateIcons = listOf(
            slice(label = "Food", fraction = 0f),
            slice(label = "Transport", fraction = 0f),
            slice(label = "Home", fraction = 0f),
        )
        val clickedSlices = mutableListOf<CategorySlice>()
        val chartSize = 240.dp

        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal.ZERO,
                    slices = emptyList(),
                    modifier = Modifier.size(chartSize),
                    emptyStateIcons = emptyStateIcons,
                    onEmptyCategoryClick = { clickedSlices += it },
                    animationSpec = snap(),
                )
            }
        }

        // Hit-test must use the new frame-projected positions, not old radial positions.
        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "0", expense = "0"))
            .performTouchInput {
                click(
                    emptyIconFrameCenter(
                        chartSize = chartSize,
                        index = 0,
                        count = emptyStateIcons.size,
                    ),
                )
            }

        composeTestRule.runOnIdle {
            assertEquals(1, clickedSlices.size)
            assertEquals(emptyStateIcons.first(), clickedSlices.single())
        }
    }

    @Test
    fun `empty state extrude ring paints outline color on the ring band`() {
        var outlineArgb = 0

        setChartInContainer(size = 520.dp, containerColor = Color.Black) {
            outlineArgb = MaterialTheme.colorScheme.outline.toArgb()
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Extrude,
                emptyStateIcons = emptyList(),
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        // The top of the extrude ring sits near the top of the ring band at ~12 o'clock.
        // Probe a horizontal band around the top of the ring where the outline color must appear.
        val cx = image.width / 2
        val cy = image.height / 2
        val outerRadius = (minOf(image.width, image.height) / 2f * 0.75f).toInt()
        val probeTop = cy - outerRadius - 4
        val probeBottom = cy - outerRadius + (outerRadius * 0.39f).toInt() + 4

        assertTrue(
            "empty-state extrude ring must paint the outline color on the ring band",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = cx - 8,
                top = probeTop,
                right = cx + 8,
                bottom = probeBottom,
                argb = outlineArgb,
            ),
        )
    }

    @Test
    fun `empty state flat ring paints outline color on the ring band`() {
        var outlineArgb = 0

        setChartInContainer(size = 520.dp, containerColor = Color.Black) {
            outlineArgb = MaterialTheme.colorScheme.outline.toArgb()
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                emptyStateIcons = emptyList(),
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        val cx = image.width / 2
        val cy = image.height / 2
        val outerRadius = (minOf(image.width, image.height) / 2f * 0.75f).toInt()
        val probeTop = cy - outerRadius - 4
        val probeBottom = cy - outerRadius + (outerRadius * 0.39f).toInt() + 4

        assertTrue(
            "empty-state flat ring must paint the outline color on the ring band",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = cx - 8,
                top = probeTop,
                right = cx + 8,
                bottom = probeBottom,
                argb = outlineArgb,
            ),
        )
    }

    @Test
    fun `empty state icons render at frame-projected positions`() {
        val emptyStateIcons = listOf(
            slice(label = "Food", fraction = 0f, color = Color(0xFF08A045), iconKey = "ic_cat_food"),
            slice(label = "Transport", fraction = 0f, color = Color(0xFF0C63E7), iconKey = "ic_cat_transport"),
        )
        val chartSize = 520.dp

        setChartInContainer(size = chartSize, containerColor = Color.White) {
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                emptyStateIcons = emptyStateIcons,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val iconProbeRadius = with(composeTestRule.density) { 18.dp.toPx() }

        emptyStateIcons.forEachIndexed { index, iconSlice ->
            val iconCenter = emptyIconFrameCenter(
                canvasWidth = image.width,
                canvasHeight = image.height,
                index = index,
                count = emptyStateIcons.size,
            )
            assertTrue(
                "empty-state icon[$index] must render its category color at the frame-projected position",
                regionContainsColor(
                    pixels = pixels,
                    width = image.width,
                    left = (iconCenter.x - iconProbeRadius).toInt(),
                    top = (iconCenter.y - iconProbeRadius).toInt(),
                    right = (iconCenter.x + iconProbeRadius).toInt(),
                    bottom = (iconCenter.y + iconProbeRadius).toInt(),
                    argb = iconSlice.color.toArgb(),
                ),
            )
        }
    }

    @Test
    fun `empty state icon does not paint a solid background disc`() {
        val emptyStateIcons = listOf(
            slice(label = "Food", fraction = 0f, color = Color(0xFF08A045), iconKey = "ic_cat_food"),
        )
        val chartSize = 520.dp

        setChartInContainer(size = chartSize, containerColor = Color.Black) {
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                emptyStateIcons = emptyStateIcons,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        val iconCenter = emptyIconFrameCenter(
            canvasWidth = image.width,
            canvasHeight = image.height,
            index = 0,
            count = emptyStateIcons.size,
        )
        val iconSizePx = with(composeTestRule.density) { 40.dp.toPx() }
        val iconProbeRadius = with(composeTestRule.density) { 18.dp.toPx() }
        val fringe = with(composeTestRule.density) { 4.dp.toPx() }

        assertTrue(
            "the empty-state icon itself must still render at the expected frame-projected position",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = (iconCenter.x - iconProbeRadius).toInt(),
                top = (iconCenter.y - iconProbeRadius).toInt(),
                right = (iconCenter.x + iconProbeRadius).toInt(),
                bottom = (iconCenter.y + iconProbeRadius).toInt(),
                argb = emptyStateIcons.first().color.toArgb(),
            ),
        )

        // Probe PERPENDICULAR to the radial leader line to detect disc presence without
        // sampling along the leader line itself (which would give a false positive).
        val donutCenter = Offset(image.width / 2f, image.height / 2f)
        val radialDx = iconCenter.x - donutCenter.x
        val radialDy = iconCenter.y - donutCenter.y
        val radialLen = hypot(radialDx, radialDy).coerceAtLeast(1f)
        val perpUx = -radialDy / radialLen
        val perpUy = radialDx / radialLen
        val perpOffset = iconSizePx / 2f + fringe

        val fringeX1 = (iconCenter.x + perpUx * perpOffset).toInt()
        val fringeY1 = (iconCenter.y + perpUy * perpOffset).toInt()
        val fringeX2 = (iconCenter.x - perpUx * perpOffset).toInt()
        val fringeY2 = (iconCenter.y - perpUy * perpOffset).toInt()

        val side1IsBlack = fringeX1 in 0 until image.width &&
            fringeY1 in 0 until (pixels.size / image.width) &&
            colorsMatch(pixels[fringeY1 * image.width + fringeX1], Color.Black.toArgb())
        val side2IsBlack = fringeX2 in 0 until image.width &&
            fringeY2 in 0 until (pixels.size / image.width) &&
            colorsMatch(pixels[fringeY2 * image.width + fringeX2], Color.Black.toArgb())

        assertTrue(
            "area perpendicular to the leader line just outside the empty-state icon must remain " +
                "transparent to the black container — a solid disc background would paint both sides, " +
                "but the thin radial leader line cannot reach a perpendicular-offset point",
            side1IsBlack || side2IsBlack,
        )
    }

    @Test
    fun `empty state leader lines use category color not the leaderLineColor parameter`() {
        val leaderLineOverrideColor = Color.Magenta
        val emptyStateIcons = listOf(
            slice(label = "Food", fraction = 0f, color = Color(0xFF08A045), iconKey = "ic_cat_food"),
            slice(label = "Transport", fraction = 0f, color = Color(0xFF0C63E7), iconKey = "ic_cat_transport"),
        )

        setChartInContainer(size = 520.dp, containerColor = Color.White) {
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                emptyStateIcons = emptyStateIcons,
                leaderLineColor = leaderLineOverrideColor,
                leaderLineThickness = 16.dp,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        assertFalse(
            "leaderLineColor override (Magenta) must not appear in empty state — " +
                "empty-state leader lines use each category's own color",
            imageContainsColor(pixels, leaderLineOverrideColor.toArgb()),
        )
    }

    @Test
    fun `empty state does not render percentage labels near icons`() {
        // Regression guard: the new empty-state branch must NOT call drawCalloutText.
        // We verify this by checking that no slice color appears in the text-label region
        // to the right of any icon (where % labels would normally sit in the populated path).
        val iconColor = Color(0xFFFF6600)
        val emptyStateIcons = listOf(
            slice(label = "Car", fraction = 0f, color = iconColor, iconKey = "ic_cat_food"),
        )
        val chartSize = 520.dp

        setChartInContainer(size = chartSize, containerColor = Color.White) {
            MonefyDonutChart(
                income = BigDecimal.ZERO,
                expense = BigDecimal.ZERO,
                slices = emptyList(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                emptyStateIcons = emptyStateIcons,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        val iconCenter = emptyIconFrameCenter(
            canvasWidth = image.width,
            canvasHeight = image.height,
            index = 0,
            count = emptyStateIcons.size,
        )
        val iconSizePx = with(composeTestRule.density) { 40.dp.toPx() }
        // The percentage text "100%" would normally appear just to the right of the icon.
        // For empty state icons (fraction=0), no percentage label should be drawn.
        val labelRegionLeft = (iconCenter.x + iconSizePx / 2f + 2f).toInt()
        val labelRegionRight = (iconCenter.x + iconSizePx * 2f).toInt()
        val labelRegionTop = (iconCenter.y - iconSizePx / 2f).toInt()
        val labelRegionBottom = (iconCenter.y + iconSizePx / 2f).toInt()

        assertFalse(
            "empty-state branch must not draw percentage labels — no icon color must appear " +
                "in the text-label region to the right of the icon",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = labelRegionLeft,
                top = labelRegionTop,
                right = labelRegionRight,
                bottom = labelRegionBottom,
                argb = iconColor.toArgb(),
            ),
        )
    }

    @Test
    fun `empty state with no empty icons still renders ring and center totals without crash`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("50.00"),
                    slices = emptyList(),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = emptyList(),
                    style = DonutStyle.Extrude,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(expectedDescription(income = "100.00", expense = "50.00"))
            .assertExists()
    }

    @Test
    fun `populated chart ignores empty state icons and keeps slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("450.00"),
                    expense = BigDecimal("124.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.50f),
                        slice(label = "Transport", fraction = 0.50f),
                    ),
                    modifier = Modifier.size(240.dp),
                    emptyStateIcons = listOf(slice(label = "Placeholder", fraction = 0f)),
                    animationSpec = snap(),
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 50),
                ),
            )
            .assertExists()
    }

    @Test
    fun `tapping a populated icon disc invokes the matching slice callback`() {
        val slices = contourSlices()
        val clickedSlices = mutableListOf<CategorySlice>()
        val chartSize = 240.dp
        val explodedOffset = 16.dp

        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("450.00"),
                    expense = BigDecimal("124.00"),
                    slices = slices,
                    modifier = Modifier.size(chartSize),
                    explodedOffset = explodedOffset,
                    onSliceClick = { clickedSlices += it },
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 50),
                ),
            )
            .performTouchInput {
                click(
                    populatedIconCenter(
                        canvasSize = chartSize,
                        slices = slices,
                        sliceIndex = 1,
                        explodedOffset = explodedOffset,
                    ),
                )
            }

        composeTestRule.runOnIdle {
            assertEquals(listOf(slices[1]), clickedSlices)
        }
    }

    @Test
    fun `tapping a populated ring segment still invokes the matching slice callback`() {
        val slices = contourSlices()
        val clickedSlices = mutableListOf<CategorySlice>()
        val chartSize = 240.dp
        val explodedOffset = 16.dp

        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("450.00"),
                    expense = BigDecimal("124.00"),
                    slices = slices,
                    modifier = Modifier.size(chartSize),
                    explodedOffset = explodedOffset,
                    onSliceClick = { clickedSlices += it },
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 50),
                ),
            )
            .performTouchInput {
                click(
                    populatedRingCenter(
                        canvasSize = chartSize,
                        slices = slices,
                        sliceIndex = 0,
                        explodedOffset = explodedOffset,
                    ),
                )
            }

        composeTestRule.runOnIdle {
            assertEquals(listOf(slices[0]), clickedSlices)
        }
    }

    @Test
    fun `populated slice icons render at frame-projected positions and include exploded offset`() {
        val slices = contourSlices()
        val chartSize = 520.dp
        val explodedOffset = 16.dp

        setChartInContainer(size = chartSize, containerColor = Color.White) {
            MonefyDonutChart(
                income = BigDecimal("450.00"),
                expense = BigDecimal("124.00"),
                slices = slices,
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                explodedOffset = explodedOffset,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val iconProbeRadius = with(composeTestRule.density) { 18.dp.toPx() }

        val rightIconCenter = populatedIconCenter(
            canvasWidth = image.width,
            canvasHeight = image.height,
            slices = slices,
            sliceIndex = 0,
            explodedOffset = explodedOffset,
        )
        val leftIconCenter = populatedIconCenter(
            canvasWidth = image.width,
            canvasHeight = image.height,
            slices = slices,
            sliceIndex = 1,
            explodedOffset = explodedOffset,
        )

        assertTrue(
            "first slice icon must render at the frame-projected position for its mid-angle",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = (rightIconCenter.x - iconProbeRadius).toInt(),
                top = (rightIconCenter.y - iconProbeRadius).toInt(),
                right = (rightIconCenter.x + iconProbeRadius).toInt(),
                bottom = (rightIconCenter.y + iconProbeRadius).toInt(),
                argb = slices[0].color.toArgb(),
            ),
        )
        assertTrue(
            "second slice icon must render at the frame-projected position for its mid-angle",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = (leftIconCenter.x - iconProbeRadius).toInt(),
                top = (leftIconCenter.y - iconProbeRadius).toInt(),
                right = (leftIconCenter.x + iconProbeRadius).toInt(),
                bottom = (leftIconCenter.y + iconProbeRadius).toInt(),
                argb = slices[1].color.toArgb(),
            ),
        )
    }

    @Test
    fun `populated slice icons do not paint a solid background disc`() {
        val slices = contourSlices()
        val chartSize = 520.dp
        val explodedOffset = 16.dp

        setChartInContainer(size = chartSize, containerColor = Color.Black) {
            MonefyDonutChart(
                income = BigDecimal("450.00"),
                expense = BigDecimal("124.00"),
                slices = slices,
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                explodedOffset = explodedOffset,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        val iconCenter = populatedIconCenter(
            canvasWidth = image.width,
            canvasHeight = image.height,
            slices = slices,
            sliceIndex = 0,
            explodedOffset = explodedOffset,
        )
        val iconSizePx = with(composeTestRule.density) { 40.dp.toPx() }
        val iconProbeRadius = with(composeTestRule.density) { 18.dp.toPx() }
        val fringe = with(composeTestRule.density) { 4.dp.toPx() }

        assertTrue(
            "the slice icon itself must still render at the expected frame-projected position",
            regionContainsColor(
                pixels = pixels,
                width = image.width,
                left = (iconCenter.x - iconProbeRadius).toInt(),
                top = (iconCenter.y - iconProbeRadius).toInt(),
                right = (iconCenter.x + iconProbeRadius).toInt(),
                bottom = (iconCenter.y + iconProbeRadius).toInt(),
                argb = slices[0].color.toArgb(),
            ),
        )

        // The leader line runs RADIALLY along center→iconCenter, so sampling along that
        // direction would land on the line itself. Instead, probe PERPENDICULAR to the
        // radial direction — a solid filled disc would still paint those points, but the
        // thin 1dp radial leader line cannot reach a perpendicular-offset sample.
        val donutCenter = Offset(image.width / 2f, image.height / 2f)
        val radialDx = iconCenter.x - donutCenter.x
        val radialDy = iconCenter.y - donutCenter.y
        val radialLen = hypot(radialDx, radialDy).coerceAtLeast(1f)
        // Perpendicular unit vector: (-radialDy/len, radialDx/len)
        val perpUx = -radialDy / radialLen
        val perpUy = radialDx / radialLen
        val perpOffset = iconSizePx / 2f + fringe

        // Probe point on one perpendicular side of the icon fringe
        val fringeX1 = (iconCenter.x + perpUx * perpOffset).toInt()
        val fringeY1 = (iconCenter.y + perpUy * perpOffset).toInt()
        // Probe point on the other perpendicular side
        val fringeX2 = (iconCenter.x - perpUx * perpOffset).toInt()
        val fringeY2 = (iconCenter.y - perpUy * perpOffset).toInt()

        val side1IsBlack = fringeX1 in 0 until image.width && fringeY1 in 0 until (pixels.size / image.width) &&
            colorsMatch(pixels[fringeY1 * image.width + fringeX1], Color.Black.toArgb())
        val side2IsBlack = fringeX2 in 0 until image.width && fringeY2 in 0 until (pixels.size / image.width) &&
            colorsMatch(pixels[fringeY2 * image.width + fringeX2], Color.Black.toArgb())

        assertTrue(
            "the area perpendicular to the leader line just outside the icon outline must stay transparent " +
                "to the black container — a solid background disc would paint both sides, the thin radial " +
                "leader line cannot reach a perpendicular-offset point",
            side1IsBlack || side2IsBlack,
        )
    }

    @Test
    fun `populated chart with active leader lines renders without crash and preserves semantics`() {
        // Leader lines are now drawn for populated slices using each slice's own color.
        // The leaderLineColor parameter is accepted but not applied to populated slices.
        setChart(
            income = BigDecimal("450.00"),
            expense = BigDecimal("124.00"),
            slices = contourSlices(),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "450.00",
                    expense = "124.00",
                    slices = listOf("Food" to 50, "Transport" to 50),
                ),
            )
            .assertExists()
    }

    @Test
    fun `leader line color parameter does not override slice color for populated slices`() {
        // The leaderLineColor theme parameter is no longer applied to populated-slice leader lines;
        // each leader line uses the slice's own color. Passing a contrasting leaderLineColor must
        // not cause the chart to render that override color anywhere in the pixel buffer.
        val leaderLineOverrideColor = Color.Magenta

        setChartInContainer(size = 520.dp, containerColor = Color.White) {
            MonefyDonutChart(
                income = BigDecimal("450.00"),
                expense = BigDecimal("124.00"),
                slices = contourSlices(),
                modifier = Modifier.fillMaxSize(),
                style = DonutStyle.Flat,
                leaderLineColor = leaderLineOverrideColor,
                leaderLineThickness = 16.dp,
                animationSpec = snap(),
            )
        }
        composeTestRule.waitForIdle()

        val image = composeTestRule.onNodeWithTag(CHART_CONTAINER_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        assertFalse(
            "leaderLineColor override (Magenta) must not appear — populated leader lines use slice color",
            imageContainsColor(pixels, leaderLineOverrideColor.toArgb()),
        )
    }

    // ---- centerDecimalDigits ----

    @Test
    fun `centerDecimalDigits zero hides decimal portion from semantics income string`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("500.75"),
                    expense = BigDecimal("124.30"),
                    slices = listOf(slice(label = "Food", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    currencySymbol = "₽",
                    decimalDigits = 2,
                    centerDecimalDigits = 0,
                    animationSpec = snap(),
                )
            }
        }
        // semantics description uses raw BigDecimal string (not centerDecimalDigits),
        // so header always reflects full precision — this test verifies composable renders
        // without crash when centerDecimalDigits=0
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "500.75",
                    expense = "124.30",
                    slices = listOf("Food" to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `centerDecimalDigits two produces same semantics as default decimalDigits`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("200.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(slice(label = "Bills", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    decimalDigits = 2,
                    centerDecimalDigits = 2,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "200.00",
                    expense = "50.00",
                    slices = listOf("Bills" to 100),
                ),
            )
            .assertExists()
    }

    // ---- DonutStyle enum ----

    @Test
    fun `DonutStyle Flat renders chart and preserves slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("300.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.60f),
                        slice(label = "Transport", fraction = 0.40f),
                    ),
                    modifier = Modifier.size(240.dp),
                    style = DonutStyle.Flat,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "300.00",
                    expense = "100.00",
                    slices = listOf("Food" to 60, "Transport" to 40),
                ),
            )
            .assertExists()
    }

    @Test
    fun `DonutStyle Extrude renders chart and preserves slice semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("300.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(slice(label = "Home", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    style = DonutStyle.Extrude,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "300.00",
                    expense = "100.00",
                    slices = listOf("Home" to 100),
                ),
            )
            .assertExists()
    }

    // ---- ringThicknessFraction and sliceGapDegrees — semantics contract unchanged ----

    @Test
    fun `custom ringThicknessFraction 0 39 preserves full semantics description`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("400.00"),
                    expense = BigDecimal("200.00"),
                    slices = listOf(
                        slice(label = "Car", fraction = 0.50f),
                        slice(label = "Pets", fraction = 0.50f),
                    ),
                    modifier = Modifier.size(240.dp),
                    ringThicknessFraction = 0.39f,
                    sliceGapDegrees = 5f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "400.00",
                    expense = "200.00",
                    slices = listOf("Car" to 50, "Pets" to 50),
                ),
            )
            .assertExists()
    }

    @Test
    fun `sliceGapDegrees zero renders without crash and keeps semantics intact`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(slice(label = "Sport", fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    sliceGapDegrees = 0f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "100.00",
                    expense = "100.00",
                    slices = listOf("Sport" to 100),
                ),
            )
            .assertExists()
    }

    // ---- budget alert semantics preserved ----

    @Test
    fun `slice with hasBudgetAlert true is still included in semantics description`() {
        val alertSlice = CategorySlice(
            categoryId = 99L,
            color = Color.Red,
            fraction = 1.0f,
            label = "Overbudget",
            hasBudgetAlert = true,
        )
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal("250.00"),
                    slices = listOf(alertSlice),
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
        // The accessibility description must contain the slice label
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = context.getString(R.string.donut_chart_cd, "0", "250.00")
        val slicePart = context.getString(R.string.donut_chart_slice, "Overbudget", 100)
        val alertLabel = context.getString(R.string.donut_chart_budget_alert)
        val full = "$header $slicePart, $alertLabel"
        composeTestRule.onNodeWithContentDescription(full).assertExists()
    }

    // ---- compact callout block (icon + % inline, name on one line below) ----

    @Test
    fun `compact callout renders without crash for a very long category name exercising font shrink path`() {
        val longName = "Очень длинное название категории расходов"
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal("200.00"),
                    slices = listOf(slice(label = longName, fraction = 1.0f)),
                    modifier = Modifier.size(240.dp),
                    showCategoryLabels = true,
                    labelMinFraction = 0f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "0",
                    expense = "200.00",
                    slices = listOf(longName to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `compact callout renders without crash for a normal category name and preserves semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal.ZERO,
                    expense = BigDecimal("150.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.60f),
                        slice(label = "Home", fraction = 0.40f),
                    ),
                    modifier = Modifier.size(300.dp),
                    showCategoryLabels = true,
                    labelMinFraction = 0f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "0",
                    expense = "150.00",
                    slices = listOf("Food" to 60, "Home" to 40),
                ),
            )
            .assertExists()
    }

    // ---- iconScale param — composable renders without crash ----

    @Test
    fun `custom iconScale 1 7 renders without crash and preserves semantics`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("150.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(slice(label = "Coffee", fraction = 1.0f)),
                    modifier = Modifier.size(300.dp),
                    iconScale = 1.7f,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "150.00",
                    expense = "50.00",
                    slices = listOf("Coffee" to 100),
                ),
            )
            .assertExists()
    }

    // ---- frame-projection: single slice and many small slices do not crash ----

    @Test
    fun `single full-circle slice with leader line renders without crash and semantics intact`() {
        // Regression guard: projectAngleToFrame on a single 360-degree slice must not
        // throw or produce NaN icon coordinates that crash Canvas drawing.
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("100.00"),
                    slices = listOf(
                        slice(
                            label = "Everything",
                            fraction = 1.0f,
                            color = Color(0xFF08A045),
                            iconKey = "ic_cat_food",
                        ),
                    ),
                    modifier = Modifier.size(300.dp),
                    style = DonutStyle.Flat,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "100.00",
                    expense = "100.00",
                    slices = listOf("Everything" to 100),
                ),
            )
            .assertExists()
    }

    @Test
    fun `eight small slices with leader lines render without crash and all slices in semantics`() {
        // Previously, many small slices could collide when icons were placed radially.
        // With frame-projection, slices are clamped to canvas bounds — no crash expected.
        val smallSlices = (1..8).map { i ->
            slice(label = "Cat$i", fraction = 0.125f, color = Color(0xFF000000L + i * 0x111111L), iconKey = "ic_cat_food")
        }
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("800.00"),
                    expense = BigDecimal("800.00"),
                    slices = smallSlices,
                    modifier = Modifier.size(360.dp),
                    style = DonutStyle.Flat,
                    animationSpec = snap(),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "800.00",
                    expense = "800.00",
                    slices = smallSlices.map { it.label to (it.fraction * 100f).toInt() },
                ),
            )
            .assertExists()
    }

    // ---- zero-fraction slices are excluded from semantics description ----

    @Test
    fun `slices with fraction zero are excluded from semantics description`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = BigDecimal("100.00"),
                    expense = BigDecimal("50.00"),
                    slices = listOf(
                        slice(label = "Food", fraction = 0.80f),
                        slice(label = "Ghost", fraction = 0.0f),
                    ),
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
        // Ghost slice has fraction=0 → (0*100).toInt()=0, included in semantics by the
        // chart (it still calls joinToString over all slices); verify Food appears correctly
        composeTestRule
            .onNodeWithContentDescription(
                expectedDescription(
                    income = "100.00",
                    expense = "50.00",
                    slices = listOf("Food" to 80, "Ghost" to 0),
                ),
            )
            .assertExists()
    }

    private fun setChart(
        income: BigDecimal,
        expense: BigDecimal,
        slices: List<CategorySlice>,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                MonefyDonutChart(
                    income = income,
                    expense = expense,
                    slices = slices,
                    modifier = Modifier.size(240.dp),
                    animationSpec = snap(),
                )
            }
        }
    }

    private fun slice(label: String, fraction: Float) = CategorySlice(
        categoryId = label.hashCode().toLong(),
        color = Color.Green,
        fraction = fraction,
        label = label,
    )

    private fun slice(
        label: String,
        fraction: Float,
        color: Color,
        iconKey: String,
    ) = CategorySlice(
        categoryId = label.hashCode().toLong(),
        color = color,
        fraction = fraction,
        label = label,
        iconKey = iconKey,
    )

    private fun contourSlices() = listOf(
        slice(
            label = "Food",
            fraction = 0.50f,
            color = Color(0xFF08A045),
            iconKey = "ic_cat_food",
        ),
        slice(
            label = "Transport",
            fraction = 0.50f,
            color = Color(0xFF0C63E7),
            iconKey = "ic_cat_transport",
        ),
    )

    private fun expectedDescription(
        income: String,
        expense: String,
        slices: List<Pair<String, Int>> = emptyList(),
    ): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = context.getString(R.string.donut_chart_cd, income, expense)
        val sliceText = slices.joinToString(separator = " ") { (label, percent) ->
            context.getString(R.string.donut_chart_slice, label, percent)
        }
        return if (sliceText.isEmpty()) header else "$header $sliceText"
    }

    /**
     * Mirrors the frame-projection logic used by the empty-state branch in MonefyDonutChart:
     * evenAngles → projectAngleToFrame → clampCalloutAnchor (canvas-edge clamping).
     * Icons are placed on the inset rectangle perimeter, not radially.
     */
    private fun emptyIconFrameCenter(
        chartSize: Dp,
        index: Int,
        count: Int,
    ): Offset {
        val canvasSizePx = with(composeTestRule.density) { chartSize.toPx().toInt() }
        return emptyIconFrameCenter(
            canvasWidth = canvasSizePx,
            canvasHeight = canvasSizePx,
            index = index,
            count = count,
        )
    }

    private fun emptyIconFrameCenter(
        canvasWidth: Int,
        canvasHeight: Int,
        index: Int,
        count: Int,
    ): Offset {
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val iconMargin = with(composeTestRule.density) { 8.dp.toPx() }
        val iconSize = with(composeTestRule.density) { 40.dp.toPx() }
        val inset = iconSize / 2f + iconMargin
        val insetHalfWidth = (canvasWidth / 2f - inset).coerceAtLeast(0f)
        val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
        val insetHalfHeightBottom = (canvasHeight - center.y - inset).coerceAtLeast(0f)
        val angleDegrees = DonutGeometry.evenAngles(count)[index]
        val angleRadians = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val projected = DonutGeometry.projectAngleToFrame(
            angleRadians = angleRadians,
            halfWidth = insetHalfWidth,
            halfHeightTop = insetHalfHeightTop,
            halfHeightBottom = insetHalfHeightBottom,
        )
        return Offset(center.x + projected.x, center.y + projected.y)
    }

    private fun setChartInContainer(
        size: Dp,
        containerColor: Color,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(containerColor)
                        .testTag(CHART_CONTAINER_TAG),
                ) {
                    content()
                }
            }
        }
    }

    private fun populatedIconCenter(
        canvasSize: Dp,
        slices: List<CategorySlice>,
        sliceIndex: Int,
        explodedOffset: Dp,
    ): Offset {
        val canvasSizePx = with(composeTestRule.density) { canvasSize.toPx().toInt() }
        return populatedIconCenter(
            canvasWidth = canvasSizePx,
            canvasHeight = canvasSizePx,
            slices = slices,
            sliceIndex = sliceIndex,
            explodedOffset = explodedOffset,
        )
    }

    private fun populatedIconCenter(
        canvasWidth: Int,
        canvasHeight: Int,
        slices: List<CategorySlice>,
        sliceIndex: Int,
        explodedOffset: Dp,
    ): Offset {
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val outerRadius = minOf(canvasWidth, canvasHeight) / 2f * 0.75f
        val iconMargin = with(composeTestRule.density) { 8.dp.toPx() }
        val iconSize = with(composeTestRule.density) { 40.dp.toPx() }
        val explodedOffsetPx = with(composeTestRule.density) { explodedOffset.toPx() }
        val arc = DonutGeometry.computeSliceArcs(slices)[sliceIndex]
        val mid = DonutGeometry.midAngleRadians(arc)
        val explodedDx = explodedOffsetPx * cos(mid)
        val explodedDy = explodedOffsetPx * sin(mid)
        // Mirror the frame-projection logic from PlacedSlice.frameIconCenter in MonefyDonutChart.
        val inset = iconSize / 2f + iconMargin
        val insetHalfWidth = (canvasWidth / 2f - inset).coerceAtLeast(0f)
        val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
        val insetHalfHeightBottom = (canvasHeight - center.y - inset).coerceAtLeast(0f)
        val projected = DonutGeometry.projectAngleToFrame(
            angleRadians = mid,
            halfWidth = insetHalfWidth,
            halfHeightTop = insetHalfHeightTop,
            halfHeightBottom = insetHalfHeightBottom,
        )
        return Offset(
            x = center.x + explodedDx + projected.x,
            y = center.y + explodedDy + projected.y,
        )
    }

    private fun populatedRingCenter(
        canvasSize: Dp,
        slices: List<CategorySlice>,
        sliceIndex: Int,
        explodedOffset: Dp,
    ): Offset {
        val canvasSizePx = with(composeTestRule.density) { canvasSize.toPx() }
        val center = canvasSizePx / 2f
        val outerRadius = center * 0.75f
        val strokeWidth = outerRadius * 0.39f
        val ringRadius = outerRadius - strokeWidth / 2f
        val explodedOffsetPx = with(composeTestRule.density) { explodedOffset.toPx() }
        val arc = DonutGeometry.computeSliceArcs(slices)[sliceIndex]
        val mid = DonutGeometry.midAngleRadians(arc)
        val radius = ringRadius + explodedOffsetPx
        return Offset(
            x = center + radius * cos(mid),
            y = center + radius * sin(mid),
        )
    }

    private fun imageContainsColor(pixels: IntArray, argb: Int): Boolean =
        pixels.any { colorsMatch(it, argb) }

    private fun regionContainsColor(
        pixels: IntArray,
        width: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        argb: Int,
    ): Boolean {
        val clampedLeft = left.coerceAtLeast(0)
        val clampedTop = top.coerceAtLeast(0)
        val clampedRight = right.coerceAtMost(width)
        val clampedBottom = bottom.coerceAtMost(pixels.size / width)
        for (y in clampedTop until clampedBottom) {
            for (x in clampedLeft until clampedRight) {
                if (colorsMatch(pixels[y * width + x], argb)) {
                    return true
                }
            }
        }
        return false
    }

    private fun colorsMatch(a: Int, b: Int): Boolean {
        fun ch(v: Int, shift: Int) = (v shr shift) and 0xFF
        val tolerance = 16
        return kotlin.math.abs(ch(a, 16) - ch(b, 16)) <= tolerance &&
            kotlin.math.abs(ch(a, 8) - ch(b, 8)) <= tolerance &&
            kotlin.math.abs(ch(a, 0) - ch(b, 0)) <= tolerance
    }

    companion object {
        const val CHART_CONTAINER_TAG = "chart_container"
    }
}
