package com.kshavrin.mymoney.core.designsystem.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.expenseAccent
import com.kshavrin.mymoney.core.ui.theme.incomeAccent
import com.kshavrin.mymoney.core.ui.theme.trendChartProjectionAbove
import com.kshavrin.mymoney.core.ui.theme.trendChartProjectionBelow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class BalanceTrendChartUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun styleWrapperTag(style: ChartStyle) = "balance_trend_chart_style_${style.name}"

    private fun chartWrapperTag(name: String) = "balance_trend_chart_wrapper_$name"

    private data class RenderedChart(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val solidColor: Int,
        val incomeColor: Int,
        val expenseColor: Int,
        val projectionAboveColor: Int,
        val projectionBelowColor: Int,
    )

    private fun setContent(
        points: List<Float>,
        showGridlines: Boolean = true,
        showLabels: Boolean = false,
        labels: List<String> = emptyList(),
        metricLabel: String? = null,
        colorRule: ChartColorRule = ChartColorRule.Default,
        style: ChartStyle = ChartStyle.Default,
        showProjection: Boolean = false,
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    labels = labels,
                    metricLabel = metricLabel,
                    showGridlines = showGridlines,
                    showLabels = showLabels,
                    colorRule = colorRule,
                    style = style,
                    showProjection = showProjection,
                )
            }
        }
    }

    private fun assertAllStylesRender(points: List<Float>) {
        composeTestRule.setContent {
            MyMoneyTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    ChartStyle.entries.forEach { style ->
                        Box(modifier = Modifier.testTag(styleWrapperTag(style))) {
                            BalanceTrendChart(
                                points = points,
                                modifier = Modifier.fillMaxWidth(),
                                style = style,
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onAllNodesWithTag(BALANCE_TREND_CHART_TAG)
            .assertCountEquals(ChartStyle.entries.size)
        ChartStyle.entries.forEach { style ->
            composeTestRule
                .onNodeWithTag(styleWrapperTag(style))
                .assertExists()
        }
    }

    private fun captureStylePixels(
        style: ChartStyle,
        colorRule: ChartColorRule = ChartColorRule.Default,
        showProjection: Boolean = false,
    ): IntArray {
        setContent(
            points = listOf(4f, -4f, 4f),
            showGridlines = false,
            style = style,
            colorRule = colorRule,
            showProjection = showProjection,
        )
        composeTestRule.waitForIdle()
        val image = composeTestRule.onNodeWithTag(BALANCE_TREND_CHART_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return pixels
    }

    private fun captureChart(
        points: List<Float>,
        style: ChartStyle,
        colorRule: ChartColorRule,
        showProjection: Boolean = false,
    ): RenderedChart {
        var solidColor = 0
        var incomeColor = 0
        var expenseColor = 0
        var projectionAboveColor = 0
        var projectionBelowColor = 0
        composeTestRule.setContent {
            MyMoneyTheme {
                solidColor = MaterialTheme.colorScheme.dashboardAuroraAccent.toArgb()
                incomeColor = MaterialTheme.colorScheme.incomeAccent.toArgb()
                expenseColor = MaterialTheme.colorScheme.expenseAccent.toArgb()
                projectionAboveColor = MaterialTheme.colorScheme.trendChartProjectionAbove.toArgb()
                projectionBelowColor = MaterialTheme.colorScheme.trendChartProjectionBelow.toArgb()
                BalanceTrendChart(
                    points = points,
                    modifier = Modifier.fillMaxWidth(),
                    showGridlines = false,
                    colorRule = colorRule,
                    style = style,
                    showProjection = showProjection,
                )
            }
        }
        composeTestRule.waitForIdle()
        val image = composeTestRule.onNodeWithTag(BALANCE_TREND_CHART_TAG).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return RenderedChart(
            pixels = pixels,
            width = image.width,
            height = image.height,
            solidColor = solidColor,
            incomeColor = incomeColor,
            expenseColor = expenseColor,
            projectionAboveColor = projectionAboveColor,
            projectionBelowColor = projectionBelowColor,
        )
    }

    @Test
    fun `five-point series renders a chart node with the expected testTag`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `crossing series renders a chart node`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `every chart style renders without crashing for a five-point series`() {
        assertAllStylesRender(listOf(10f, 6f, 12f, 12f, 15f))
    }

    @Test
    fun `every chart style renders without crashing for a single-point series`() {
        assertAllStylesRender(listOf(42f))
    }

    @Test
    fun `every chart style renders without crashing for an all-zero series`() {
        assertAllStylesRender(listOf(0f, 0f, 0f, 0f, 0f))
    }

    @Test
    fun `every chart style renders without crashing for a negative-only series`() {
        assertAllStylesRender(listOf(-8f, -3f, -11f, -5f, -2f))
    }

    @Test
    fun `every chart style renders without crashing for a zero-crossing series`() {
        assertAllStylesRender(listOf(4f, 3f, 1f, -2f, -3f))
    }

    @Test
    fun `three chart styles render distinct geometry families`() {
        val barsPixels = captureStylePixels(ChartStyle.Bars)
        val linePixels = captureStylePixels(ChartStyle.Line)
        val smoothPixels = captureStylePixels(ChartStyle.Smooth)

        assertEquals(barsPixels.size, linePixels.size)
        assertEquals(linePixels.size, smoothPixels.size)
        assertFalse("Bars must render different geometry from Line", barsPixels.contentEquals(linePixels))
        assertFalse("Line must render different geometry from Smooth", linePixels.contentEquals(smoothPixels))
        assertFalse("Bars must render different geometry from Smooth", barsPixels.contentEquals(smoothPixels))
    }

    @Test
    fun `default style renders the same geometry as Smooth`() {
        val defaultPixels = captureStylePixels(ChartStyle.Default)
        val smoothPixels = captureStylePixels(ChartStyle.Smooth)

        assertTrue(defaultPixels.contentEquals(smoothPixels))
    }

    @Test
    fun `all 24 frozen color rule projection and style cells render`() {
        val matrixCells =
            buildList {
                ChartColorRule.entries.forEach { colorRule ->
                    listOf(false, true).forEach { showProjection ->
                        ChartStyle.entries.forEach { style ->
                            add(Triple(colorRule, showProjection, style))
                        }
                    }
                }
            }
        composeTestRule.setContent {
            MyMoneyTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    matrixCells.forEach { (colorRule, showProjection, style) ->
                        val tag = "matrix_${colorRule.id}_${showProjection}_${style.name}"
                        Box(modifier = Modifier.testTag(tag)) {
                            BalanceTrendChart(
                                points = listOf(4f, -4f, 4f),
                                modifier = Modifier.fillMaxWidth(),
                                showGridlines = false,
                                colorRule = colorRule,
                                style = style,
                                showProjection = showProjection,
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(BALANCE_TREND_CHART_TAG)
            .assertCountEquals(24)
        matrixCells.forEach { (colorRule, showProjection, style) ->
            val tag = "matrix_${colorRule.id}_${showProjection}_${style.name}"
            composeTestRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun `projection changes line and smooth but leaves Bars unchanged for every color rule`() {
        ChartColorRule.entries.forEach { colorRule ->
            val barsWithoutProjection = captureStylePixels(ChartStyle.Bars, colorRule, showProjection = false)
            val barsWithProjection = captureStylePixels(ChartStyle.Bars, colorRule, showProjection = true)
            assertTrue(
                "Bars must ignore showProjection for ${colorRule.id}",
                barsWithoutProjection.contentEquals(barsWithProjection),
            )

            listOf(ChartStyle.Line, ChartStyle.Smooth).forEach { style ->
                val withoutProjection = captureStylePixels(style, colorRule, showProjection = false)
                val withProjection = captureStylePixels(style, colorRule, showProjection = true)
                assertFalse(
                    "${style.name} must draw its zero-axis projection for ${colorRule.id}",
                    withoutProjection.contentEquals(withProjection),
                )
            }
        }
    }

    @Test
    fun `projection is disabled by default`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    points = listOf(4f, -4f, 4f),
                    modifier = Modifier.fillMaxWidth(),
                    showGridlines = false,
                    style = ChartStyle.Line,
                )
            }
        }
        composeTestRule.waitForIdle()
        val defaultImage = composeTestRule.onNodeWithTag(BALANCE_TREND_CHART_TAG).captureToImage()
        val defaultPixels = IntArray(defaultImage.width * defaultImage.height)
        defaultImage.readPixels(defaultPixels)

        assertTrue(
            defaultPixels.contentEquals(
                captureStylePixels(
                    style = ChartStyle.Line,
                    colorRule = ChartColorRule.Default,
                    showProjection = false,
                ),
            ),
        )
    }

    @Test
    fun `every color rule renders its canonical color across all chart styles`() {
        val points = listOf(1000f, 1200f, 800f)

        ChartStyle.entries.forEach { style ->
            ChartColorRule.entries.forEach { colorRule ->
                val chart = captureChart(points, style, colorRule)
                val expectedColor =
                    when (colorRule) {
                        ChartColorRule.Solid -> chart.solidColor
                        ChartColorRule.AlwaysGreen -> chart.incomeColor
                        ChartColorRule.AlwaysRed -> chart.expenseColor
                        ChartColorRule.ByDirection -> chart.incomeColor
                    }

                assertTrue(
                    "${colorRule.id} must render its canonical color for ${style.name}",
                    imageContainsColor(chart.pixels, expectedColor, tolerance = 48),
                )
                if (colorRule == ChartColorRule.ByDirection) {
                    assertTrue(
                        "ByDirection must render the above segment in green for ${style.name}",
                        imageContainsColor(chart.pixels, chart.incomeColor, tolerance = 48),
                    )
                    assertTrue(
                        "ByDirection must render the below segment in red for ${style.name}",
                        imageContainsColor(chart.pixels, chart.expenseColor, tolerance = 48),
                    )
                }
            }
        }
    }

    @Test
    fun `ByDirection bars use both colors relative to the first point`() {
        val chart =
            captureChart(
                points = listOf(1000f, 1200f, 800f),
                style = ChartStyle.Bars,
                colorRule = ChartColorRule.ByDirection,
            )

        assertTrue(imageContainsColor(chart.pixels, chart.incomeColor, tolerance = 48))
        assertTrue(imageContainsColor(chart.pixels, chart.expenseColor, tolerance = 48))
    }

    @Test
    fun `projection uses MaterialTheme tokens above and below zero for every line color rule`() {
        listOf(ChartStyle.Line, ChartStyle.Smooth).forEach { style ->
            ChartColorRule.entries.forEach { colorRule ->
                val withoutProjection =
                    captureChart(
                        points = listOf(4f, -4f, 4f),
                        style = style,
                        colorRule = colorRule,
                    )
                val withProjection =
                    captureChart(
                        points = listOf(4f, -4f, 4f),
                        style = style,
                        colorRule = colorRule,
                        showProjection = true,
                    )

                assertTrue(
                    "${style.name}/${colorRule.id} projection must add green pixels above the zero axis",
                    containsNewPixelInRegion(
                        before = withoutProjection,
                        after = withProjection,
                        topInclusive = 0,
                        bottomExclusive = withProjection.height / 2,
                        predicate = { colorsMatch(it, withProjection.projectionAboveColor, tolerance = 48) },
                    ),
                )
                assertTrue(
                    "${style.name}/${colorRule.id} projection must add red pixels below the zero axis",
                    containsNewPixelInRegion(
                        before = withoutProjection,
                        after = withProjection,
                        topInclusive = withProjection.height / 2,
                        bottomExclusive = withProjection.height,
                        predicate = { colorsMatch(it, withProjection.projectionBelowColor, tolerance = 48) },
                    ),
                )
            }
        }
    }

    @Test
    fun `single-sign projection uses one fill color with the zero axis at the edge`() {
        val positiveWithoutProjection =
            captureChart(
                points = listOf(3f, 7f),
                style = ChartStyle.Line,
                colorRule = ChartColorRule.Solid,
            )
        val positiveWithProjection =
            captureChart(
                points = listOf(3f, 7f),
                style = ChartStyle.Line,
                colorRule = ChartColorRule.Solid,
                showProjection = true,
            )
        assertTrue(
            containsNewPixelInRegion(
                before = positiveWithoutProjection,
                after = positiveWithProjection,
                topInclusive = 0,
                bottomExclusive = positiveWithProjection.height,
                predicate = { colorsMatch(it, positiveWithProjection.projectionAboveColor, tolerance = 48) },
            ),
        )
        assertFalse(
            containsNewPixelInRegion(
                before = positiveWithoutProjection,
                after = positiveWithProjection,
                topInclusive = 0,
                bottomExclusive = positiveWithProjection.height,
                predicate = { colorsMatch(it, positiveWithProjection.projectionBelowColor, tolerance = 48) },
            ),
        )

        val negativeWithoutProjection =
            captureChart(
                points = listOf(-3f, -7f),
                style = ChartStyle.Line,
                colorRule = ChartColorRule.Solid,
            )
        val negativeWithProjection =
            captureChart(
                points = listOf(-3f, -7f),
                style = ChartStyle.Line,
                colorRule = ChartColorRule.Solid,
                showProjection = true,
            )
        assertTrue(
            containsNewPixelInRegion(
                before = negativeWithoutProjection,
                after = negativeWithProjection,
                topInclusive = 0,
                bottomExclusive = negativeWithProjection.height,
                predicate = { colorsMatch(it, negativeWithProjection.projectionBelowColor, tolerance = 48) },
            ),
        )
        assertFalse(
            containsNewPixelInRegion(
                before = negativeWithoutProjection,
                after = negativeWithProjection,
                topInclusive = 0,
                bottomExclusive = negativeWithProjection.height,
                predicate = { colorsMatch(it, negativeWithProjection.projectionAboveColor, tolerance = 48) },
            ),
        )
    }

    @Test
    fun `chartHeight remains the ninth positional parameter`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    listOf(1f, 2f, 3f),
                    Modifier.fillMaxWidth(),
                    emptyList(),
                    null,
                    false,
                    false,
                    ChartColorRule.Solid,
                    ChartStyle.Line,
                    120.dp,
                )
            }
        }

        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(with(composeTestRule.density) { 120.dp.toPx() }, bounds.height, 2f)
    }

    @Test
    fun `empty series renders a chart node without crashing`() {
        setContent(emptyList())
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `scrubbing to a new series redraws the chart and updates its summary`() {
        val points = mutableStateOf(listOf(10f, 20f, 30f))
        val initialLabels = listOf("Jan", "Mar")
        val updatedLabels = listOf("Apr", "Jun")

        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceTrendChart(
                    points = points.value,
                    modifier = Modifier.fillMaxWidth(),
                    labels = if (points.value.first() > 0f) initialLabels else updatedLabels,
                    metricLabel = "Balance",
                    showLabels = true,
                )
            }
        }
        composeTestRule.waitForIdle()

        val initialPeriod = targetString(R.string.balance_trend_chart_period_range, "Jan", "Mar")
        val initialDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                "Balance",
                initialPeriod,
                formatChartValue(10f),
                formatChartValue(30f),
                targetString(R.string.balance_trend_chart_direction_increasing),
            )
        val initialImage = composeTestRule.onNodeWithTag(BALANCE_TREND_CHART_TAG).captureToImage()
        val initialPixels = IntArray(initialImage.width * initialImage.height)
        initialImage.readPixels(initialPixels)
        composeTestRule.onNodeWithContentDescription(initialDescription).assertExists()

        composeTestRule.runOnIdle {
            points.value = listOf(-10f, -20f, -30f)
        }
        composeTestRule.waitForIdle()

        val updatedPeriod = targetString(R.string.balance_trend_chart_period_range, "Apr", "Jun")
        val updatedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                "Balance",
                updatedPeriod,
                formatChartValue(-10f),
                formatChartValue(-30f),
                targetString(R.string.balance_trend_chart_direction_decreasing),
            )
        val updatedImage = composeTestRule.onNodeWithTag(BALANCE_TREND_CHART_TAG).captureToImage()
        val updatedPixels = IntArray(updatedImage.width * updatedImage.height)
        updatedImage.readPixels(updatedPixels)

        composeTestRule.onNodeWithContentDescription(updatedDescription).assertExists()
        assertFalse(
            "chart pixels must change when the scrubbed series changes",
            initialPixels.contentEquals(updatedPixels),
        )
    }

    @Test
    fun `ByDirection summary reports direction relative to the start`() {
        val labels = listOf("0", "2", "4")
        val metricLabel = "Income + expense"
        val points = listOf(1234.5f, 1800f, 2500.75f)
        setContent(
            points = points,
            labels = labels,
            metricLabel = metricLabel,
            showLabels = true,
            colorRule = ChartColorRule.ByDirection,
        )

        val expectedPeriod = targetString(R.string.balance_trend_chart_period_range, labels.first(), labels.last())
        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                metricLabel,
                expectedPeriod,
                formatChartValue(points.first()),
                formatChartValue(points.last()),
                targetString(R.string.balance_trend_chart_direction_increasing),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun summaryReportsDecreasingDirection() {
        val points = listOf(9f, 4f)
        setContent(points = points, labels = listOf("09:00", "11:00"))

        val expectedPeriod = targetString(R.string.balance_trend_chart_period_range, "09:00", "11:00")
        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                targetString(R.string.balance_trend_chart_metric),
                expectedPeriod,
                formatChartValue(points.first()),
                formatChartValue(points.last()),
                targetString(R.string.balance_trend_chart_direction_decreasing),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun emptySummaryReportsSelectedPeriodNoDataAndUnchangedDirection() {
        setContent(emptyList())

        val expectedDescription =
            targetString(
                R.string.balance_trend_chart_cd,
                targetString(R.string.balance_trend_chart_metric),
                targetString(R.string.balance_trend_chart_period_selected),
                targetString(R.string.balance_trend_chart_no_data),
                targetString(R.string.balance_trend_chart_no_data),
                targetString(R.string.balance_trend_chart_direction_unchanged),
            )

        composeTestRule.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun `single-point series renders a chart node without crashing`() {
        setContent(listOf(42f))
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `chart without labels has height matching trendChartDefaultHeight`() {
        setContent(listOf(1f, 2f, 3f), showLabels = false)
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    @Test
    fun `chart with labels is taller than chart without labels`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.testTag(chartWrapperTag("without_labels"))) {
                        BalanceTrendChart(
                            points = listOf(1f, 2f, 3f),
                            modifier = Modifier.fillMaxWidth(),
                            showLabels = false,
                        )
                    }
                    Box(modifier = Modifier.testTag(chartWrapperTag("with_labels"))) {
                        BalanceTrendChart(
                            points = listOf(1f, 2f, 3f),
                            modifier = Modifier.fillMaxWidth(),
                            showLabels = true,
                            labels = listOf("Jan", "Feb", "Mar"),
                        )
                    }
                }
            }
        }
        val heightWithout =
            composeTestRule
                .onNodeWithTag(chartWrapperTag("without_labels"))
                .fetchSemanticsNode()
                .boundsInRoot.height

        val heightWith =
            composeTestRule
                .onNodeWithTag(chartWrapperTag("with_labels"))
                .fetchSemanticsNode()
                .boundsInRoot.height

        assertTrue(
            "chart with labels (height=$heightWith) must be taller than without ($heightWithout)",
            heightWith > heightWithout,
        )
    }

    @Test
    fun `Solid rule renders the chart node`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), colorRule = ChartColorRule.Solid)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `AlwaysGreen rule renders the chart node`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f), colorRule = ChartColorRule.AlwaysGreen)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `AlwaysRed rule renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), colorRule = ChartColorRule.AlwaysRed)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `ByDirection rule renders the chart node`() {
        setContent(listOf(1000f, 1200f, 800f), colorRule = ChartColorRule.ByDirection)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `showGridlines false still renders the chart node`() {
        setContent(listOf(1f, 2f, 3f), showGridlines = false)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `all-positive series 10 6 12 12 15 renders chart with correct default height`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f))
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    @Test
    fun `chart fills the available width so it is wider than its default height`() {
        setContent(listOf(1f, 5f, 3f, 7f, 2f))
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            "chart width (${bounds.width}) must exceed chart height (${bounds.height})",
            bounds.width > bounds.height,
        )
    }

    @Test
    fun `Smooth style with five-point positive series renders chart node without crash`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with zero-crossing series renders chart node without crash`() {
        setContent(listOf(4f, 3f, 1f, -2f, -3f), style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with all-zero series renders chart node without crash`() {
        setContent(listOf(0f, 0f, 0f, 0f, 0f), style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with single-point series renders chart node without crash`() {
        setContent(listOf(42f), style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with empty series renders chart node without crash`() {
        setContent(emptyList(), style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with ByDirection color rule renders chart node without crash`() {
        setContent(listOf(10f, 6f, 12f, 12f, 15f), colorRule = ChartColorRule.ByDirection, style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style with ByDirection negative series renders chart node without crash`() {
        setContent(listOf(-5f, -3f, -8f, -1f), colorRule = ChartColorRule.ByDirection, style = ChartStyle.Smooth)
        composeTestRule
            .onNodeWithTag(BALANCE_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `Smooth style height is unchanged and matches trendChartDefaultHeight`() {
        setContent(listOf(1f, 2f, 3f, 4f, 5f), style = ChartStyle.Smooth, showLabels = false)
        val bounds =
            composeTestRule
                .onNodeWithTag(BALANCE_TREND_CHART_TAG)
                .assertExists()
                .fetchSemanticsNode()
                .boundsInRoot
        val expectedHeightPx = with(composeTestRule.density) { 96.dp.toPx() }
        assertEquals(expectedHeightPx, bounds.height, 2f)
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)

    private fun formatChartValue(value: Float): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return NumberFormat
            .getNumberInstance(locale)
            .apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 6
            }.format(value.toDouble())
    }

    private fun imageContainsColor(
        pixels: IntArray,
        argb: Int,
        tolerance: Int = 16,
    ): Boolean =
        pixels.any { colorsMatch(it, argb, tolerance) }

    private fun containsNewPixelInRegion(
        before: RenderedChart,
        after: RenderedChart,
        topInclusive: Int,
        bottomExclusive: Int,
        predicate: (Int) -> Boolean,
    ): Boolean {
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        for (y in topInclusive until bottomExclusive) {
            for (x in 0 until after.width) {
                val index = y * after.width + x
                if (before.pixels[index] != after.pixels[index] && predicate(after.pixels[index])) {
                    return true
                }
            }
        }
        return false
    }

    private fun colorsMatch(
        actual: Int,
        expected: Int,
        tolerance: Int,
    ): Boolean {
        return kotlin.math.abs(channel(actual, 16) - channel(expected, 16)) <= tolerance &&
            kotlin.math.abs(channel(actual, 8) - channel(expected, 8)) <= tolerance &&
            kotlin.math.abs(channel(actual, 0) - channel(expected, 0)) <= tolerance
    }

    private fun channel(
        argb: Int,
        shift: Int,
    ): Int = (argb shr shift) and 0xFF
}
