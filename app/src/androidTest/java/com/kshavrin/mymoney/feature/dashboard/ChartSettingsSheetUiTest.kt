package com.kshavrin.mymoney.feature.dashboard

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_GRIDLINES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_LABELS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_MODE_AUTO_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_MODE_MANUAL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_DECREASE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_INCREASE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_POINTS_VALUE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_PROJECTION_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_SHEET_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_VISIBLE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.ChartSettingsSheet
import com.kshavrin.mymoney.feature.dashboard.components.chartColorTag
import com.kshavrin.mymoney.feature.dashboard.components.chartMetricTag
import com.kshavrin.mymoney.feature.dashboard.components.chartPeriodTag
import com.kshavrin.mymoney.feature.dashboard.components.chartStyleThumbTag
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ChartSettingsSheetUiTest {
    @get:Rule
    val composeTestRule = createComposeRule().apply { enableAccessibilityChecks() }

    private fun defaultConfig() =
        ChartConfig(
            visible = true,
            style = ChartStyle.Line,
            periodType = ChartPeriodType.Follow,
            pointCount = DEFAULT_CHART_POINT_COUNT,
            metric = ChartMetric.CUMULATIVE,
            showGridlines = true,
            showLabels = true,
            showProjection = false,
            colorRule = ChartColorRule.ByDirection,
            // Manual mode: keeps period-type and point-count controls visible so that all
            // pre-existing stepper/period tests can assert on them without extra `.copy(autoMode=false)`.
            autoMode = false,
        )

    private fun setSheet(
        config: ChartConfig = defaultConfig(),
        onEvent: (DashboardEvent) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                ChartSettingsSheet(config = config, onEvent = onEvent, onDismiss = onDismiss)
            }
        }
    }

    @Test
    fun `sheet renders with its test tag`() {
        setSheet()
        composeTestRule.onNodeWithTag(CHART_SETTINGS_SHEET_TAG).assertExists()
    }

    @Test
    fun `sheet renders exactly the three ChartStyle previews`() {
        setSheet()
        assertEquals("the chart style contract has exactly three previews", 3, ChartStyle.entries.size)
        ChartStyle.entries.forEach { style ->
            composeTestRule
                .onNodeWithTag(chartStyleThumbTag(style))
                .assertExists()
        }
    }

    @Test
    fun `style thumbs expose the localized label resource for every chart family`() {
        setSheet()
        val labelResources =
            mapOf(
                ChartStyle.Bars to R.string.chart_settings_style_bars,
                ChartStyle.Line to R.string.chart_settings_style_line,
                ChartStyle.Smooth to R.string.chart_settings_style_smooth,
            )
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        labelResources.forEach { (style, resourceId) ->
            composeTestRule
                .onNodeWithTag(chartStyleThumbTag(style))
                .assertContentDescriptionEquals(context.getString(resourceId))
        }
    }

    @Test
    fun `tapping each style thumb emits exactly its selected chart family`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        ChartStyle.entries.forEachIndexed { index, style ->
            composeTestRule
                .onNodeWithTag(chartStyleThumbTag(style))
                .performScrollTo()
                .performClick()

            composeTestRule.runOnIdle {
                assertEquals(
                    ChartStyle.entries.take(index + 1).map { DashboardEvent.ChartStyleChanged(it) },
                    captured,
                )
            }
        }
    }

    @Test
    fun `tapping a style thumb emits ChartStyleChanged with the selected style`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartStyleThumbTag(ChartStyle.Bars))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartStyleChanged(Bars); got $captured",
                captured.any { it == DashboardEvent.ChartStyleChanged(ChartStyle.Bars) },
            )
        }
    }

    @Test
    fun `period type segmented button Follow exists and is rendered`() {
        setSheet()
        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Follow))
            .assertExists()
    }

    @Test
    fun `tapping period type Month emits ChartPeriodTypeChanged Month`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Month))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPeriodTypeChanged(Month); got $captured",
                captured.any { it == DashboardEvent.ChartPeriodTypeChanged(ChartPeriodType.Month) },
            )
        }
    }

    @Test
    fun `point count stepper shows the current count value`() {
        setSheet(config = defaultConfig().copy(pointCount = 7))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_VALUE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping increase point count emits ChartPointCountChanged with count plus one`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(pointCount = 5),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPointCountChanged(6); got $captured",
                captured.any { it == DashboardEvent.ChartPointCountChanged(6) },
            )
        }
    }

    @Test
    fun `tapping decrease point count emits ChartPointCountChanged with count minus one`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(pointCount = 5),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartPointCountChanged(4); got $captured",
                captured.any { it == DashboardEvent.ChartPointCountChanged(4) },
            )
        }
    }

    @Test
    fun `decrease button is disabled when point count is at minimum`() {
        setSheet(config = defaultConfig().copy(pointCount = CHART_POINT_COUNT_RANGE.first))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun `increase button is disabled when point count is at maximum`() {
        setSheet(config = defaultConfig().copy(pointCount = CHART_POINT_COUNT_RANGE.last))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun `increase and decrease buttons are both enabled for a mid-range point count`() {
        setSheet(config = defaultConfig().copy(pointCount = 6))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .assertIsEnabled()
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .assertIsEnabled()
    }

    @Test
    fun `metric segmented buttons for all three metrics exist`() {
        setSheet()
        ChartMetric.entries.forEach { metric ->
            composeTestRule
                .onNodeWithTag(chartMetricTag(metric))
                .assertExists()
        }
    }

    @Test
    fun `tapping metric PERIOD_NET emits ChartMetricChanged PERIOD_NET`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartMetricTag(ChartMetric.PERIOD_NET))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartMetricChanged(PERIOD_NET); got $captured",
                captured.any { it == DashboardEvent.ChartMetricChanged(ChartMetric.PERIOD_NET) },
            )
        }
    }

    @Test
    fun `color rule buttons for all four rules exist`() {
        setSheet()
        val rules =
            listOf(
                ChartColorRule.Solid,
                ChartColorRule.AlwaysGreen,
                ChartColorRule.AlwaysRed,
                ChartColorRule.ByDirection,
            )
        assertEquals("the chart color contract has exactly four modes", 4, rules.size)
        assertEquals(ChartColorRule.entries.toSet(), rules.toSet())
        rules.forEach { rule ->
            composeTestRule
                .onNodeWithTag(chartColorTag(rule))
                .assertExists()
        }
    }

    @Test
    fun `color rule buttons expose the localized label for every mode`() {
        val locale = Locale.US
        val context = localizedContext(locale)
        setLocalizedSheet(locale, defaultConfig().copy(autoMode = true))
        mapOf(
            ChartColorRule.Solid to R.string.chart_settings_color_solid,
            ChartColorRule.AlwaysGreen to R.string.chart_settings_color_always_green,
            ChartColorRule.AlwaysRed to R.string.chart_settings_color_always_red,
            ChartColorRule.ByDirection to R.string.chart_settings_color_by_direction,
        ).forEach { (rule, resourceId) ->
            composeTestRule
                .onNodeWithText(context.getString(resourceId))
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithTag(chartColorTag(rule))
                .assertExists()
        }
    }

    @Test
    fun `tapping color rule ByDirection emits ChartColorRuleChanged ByDirection`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(onEvent = { captured += it })

        composeTestRule
            .onNodeWithTag(chartColorTag(ChartColorRule.ByDirection))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(DashboardEvent.ChartColorRuleChanged(ChartColorRule.ByDirection)),
                captured,
            )
        }
    }

    @Test
    fun `gridlines toggle is on when showGridlines is true`() {
        setSheet(config = defaultConfig().copy(showGridlines = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .assertIsOn()
    }

    @Test
    fun `gridlines toggle is off when showGridlines is false`() {
        setSheet(config = defaultConfig().copy(showGridlines = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping gridlines toggle emits ChartGridlinesToggled with the new state`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(showGridlines = true),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartGridlinesToggled(false); got $captured",
                captured.any { it == DashboardEvent.ChartGridlinesToggled(false) },
            )
        }
    }

    @Test
    fun `labels toggle is on when showLabels is true`() {
        setSheet(config = defaultConfig().copy(showLabels = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .assertIsOn()
    }

    @Test
    fun `labels toggle is off when showLabels is false`() {
        setSheet(config = defaultConfig().copy(showLabels = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping labels toggle emits ChartLabelsToggled with the new state`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(showLabels = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_LABELS_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartLabelsToggled(true); got $captured",
                captured.any { it == DashboardEvent.ChartLabelsToggled(true) },
            )
        }
    }

    @Test
    fun `projection toggle is off when showProjection is false`() {
        setSheet(config = defaultConfig().copy(showProjection = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_PROJECTION_TAG)
            .assertIsOff()
    }

    @Test
    fun `projection toggle is on when showProjection is true`() {
        setSheet(config = defaultConfig().copy(showProjection = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_PROJECTION_TAG)
            .assertIsOn()
    }

    @Test
    fun `tapping projection toggle to enable emits ChartProjectionToggled true`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(showProjection = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_PROJECTION_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(DashboardEvent.ChartProjectionToggled(true)),
                captured,
            )
        }
    }

    @Test
    fun `projection toggle has localized content description and a 48dp touch target`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setSheet()

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_PROJECTION_TAG)
            .assertContentDescriptionEquals(context.getString(R.string.chart_settings_projection_cd))
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
    }

    @Test
    fun `color and projection labels render in Russian`() {
        val locale = Locale.forLanguageTag("ru-RU")
        val context = localizedContext(locale)
        setLocalizedSheet(locale, defaultConfig().copy(autoMode = true))

        mapOf(
            ChartColorRule.Solid to R.string.chart_settings_color_solid,
            ChartColorRule.AlwaysGreen to R.string.chart_settings_color_always_green,
            ChartColorRule.AlwaysRed to R.string.chart_settings_color_always_red,
            ChartColorRule.ByDirection to R.string.chart_settings_color_by_direction,
        ).forEach { (_, resourceId) ->
            composeTestRule
                .onNodeWithText(context.getString(resourceId))
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_PROJECTION_TAG)
            .performScrollTo()
            .assertContentDescriptionEquals(context.getString(R.string.chart_settings_projection_cd))
    }

    @Test
    fun `visible toggle is on when visible is true`() {
        setSheet(config = defaultConfig().copy(visible = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .assertIsOn()
    }

    @Test
    fun `visible toggle is off when visible is false`() {
        setSheet(config = defaultConfig().copy(visible = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .assertIsOff()
    }

    @Test
    fun `tapping visible toggle to hide emits ChartVisibilityChanged false`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(visible = true),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartVisibilityChanged(false); got $captured",
                captured.any { it == DashboardEvent.ChartVisibilityChanged(false) },
            )
        }
    }

    @Test
    fun `tapping visible toggle to show emits ChartVisibilityChanged true`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(visible = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartVisibilityChanged(true); got $captured",
                captured.any { it == DashboardEvent.ChartVisibilityChanged(true) },
            )
        }
    }

    // -------------------------------------------------------------------------
    // Auto / Manual mode toggle
    // -------------------------------------------------------------------------

    @Test
    fun `auto mode button exists when autoMode is true`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_MODE_AUTO_TAG)
            .assertExists()
    }

    @Test
    fun `manual mode button exists when autoMode is false`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_MODE_MANUAL_TAG)
            .assertExists()
    }

    @Test
    fun `both mode buttons are visible regardless of current mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule.onNodeWithTag(CHART_SETTINGS_MODE_AUTO_TAG).assertExists()
        composeTestRule.onNodeWithTag(CHART_SETTINGS_MODE_MANUAL_TAG).assertExists()
    }

    @Test
    fun `period type controls are not present when autoMode is true`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Follow))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Month))
            .assertDoesNotExist()
    }

    @Test
    fun `point count stepper is not present when autoMode is true`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_VALUE_TAG)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_DECREASE_TAG)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_INCREASE_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `period type controls are present when autoMode is false`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(chartPeriodTag(ChartPeriodType.Follow))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `point count stepper is present when autoMode is false`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_POINTS_VALUE_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `tapping manual mode button emits ChartAutoModeChanged false`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(autoMode = true),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_MODE_MANUAL_TAG)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartAutoModeChanged(false); got $captured",
                captured.any { it == DashboardEvent.ChartAutoModeChanged(false) },
            )
        }
    }

    @Test
    fun `tapping auto mode button emits ChartAutoModeChanged true`() {
        val captured = mutableListOf<DashboardEvent>()
        setSheet(
            config = defaultConfig().copy(autoMode = false),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_MODE_AUTO_TAG)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartAutoModeChanged(true); got $captured",
                captured.any { it == DashboardEvent.ChartAutoModeChanged(true) },
            )
        }
    }

    @Test
    fun `style thumbs are visible in auto mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(chartStyleThumbTag(ChartStyle.Line))
            .assertExists()
    }

    @Test
    fun `style thumbs are visible in manual mode`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(chartStyleThumbTag(ChartStyle.Line))
            .assertExists()
    }

    @Test
    fun `metric buttons are visible in auto mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(chartMetricTag(ChartMetric.CUMULATIVE))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `metric buttons are visible in manual mode`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(chartMetricTag(ChartMetric.CUMULATIVE))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `color rule buttons are visible in auto mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(chartColorTag(ChartColorRule.Solid))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `color rule buttons are visible in manual mode`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(chartColorTag(ChartColorRule.Solid))
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `gridlines toggle is visible in auto mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `gridlines toggle is visible in manual mode`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_GRIDLINES_TAG)
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `visible toggle is present in auto mode`() {
        setSheet(config = defaultConfig().copy(autoMode = true))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun `visible toggle is present in manual mode`() {
        setSheet(config = defaultConfig().copy(autoMode = false))
        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_VISIBLE_TAG)
            .performScrollTo()
            .assertExists()
    }

    private fun setLocalizedSheet(
        locale: Locale,
        config: ChartConfig = defaultConfig(),
    ) {
        val context = localizedContext(locale)
        val configuration = context.resources.configuration
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
            ) {
                MyMoneyTheme {
                    ChartSettingsSheet(
                        config = config,
                        onEvent = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    private fun localizedContext(locale: Locale): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return baseContext.createConfigurationContext(configuration)
    }
}
