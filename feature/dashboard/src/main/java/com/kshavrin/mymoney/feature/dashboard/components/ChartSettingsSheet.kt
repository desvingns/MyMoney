package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.chart.ChartStyle
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartSettingsSectionLabel
import com.kshavrin.mymoney.feature.dashboard.CHART_POINT_COUNT_RANGE
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.ChartPeriodType
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.R
import com.kshavrin.mymoney.feature.dashboard.chartMetricLabelRes
import com.kshavrin.mymoney.feature.dashboard.chartStyleLabelRes
import com.kshavrin.mymoney.feature.dashboard.toId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartSettingsSheet(
    config: ChartConfig,
    onEvent: (DashboardEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(CHART_SETTINGS_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.l)
                    .padding(bottom = Spacing.chartSettingsSheetRowHeight),
            verticalArrangement = Arrangement.spacedBy(Spacing.chartSettingsSheetSectionGap),
        ) {
            Text(
                text = stringResource(R.string.chart_settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            SectionLabel(stringResource(R.string.chart_settings_section_mode))
            SegmentedRow(
                options = autoModeOptions(),
                selected = config.autoMode,
                onSelected = { onEvent(DashboardEvent.ChartAutoModeChanged(it)) },
            )

            SectionLabel(stringResource(R.string.chart_settings_section_style))
            StyleRow(
                selected = config.style,
                metricLabel = stringResource(chartMetricLabelRes(config.metric)),
                onSelected = { onEvent(DashboardEvent.ChartStyleChanged(it)) },
            )

            if (!config.autoMode) {
                SectionLabel(stringResource(R.string.chart_settings_section_period))
                SegmentedRow(
                    options = periodTypeOptions(),
                    selected = config.periodType,
                    onSelected = { onEvent(DashboardEvent.ChartPeriodTypeChanged(it)) },
                )

                SectionLabel(stringResource(R.string.chart_settings_section_points))
                PointCountStepper(
                    count = config.pointCount,
                    onChange = { onEvent(DashboardEvent.ChartPointCountChanged(it)) },
                )
            }

            SectionLabel(stringResource(R.string.chart_settings_section_metric))
            SegmentedRow(
                options = metricOptions(),
                selected = config.metric,
                onSelected = { onEvent(DashboardEvent.ChartMetricChanged(it)) },
            )

            SectionLabel(stringResource(R.string.chart_settings_section_color))
            SegmentedRow(
                options = colorRuleOptions(),
                selected = config.colorRule,
                onSelected = { onEvent(DashboardEvent.ChartColorRuleChanged(it)) },
            )

            SectionLabel(stringResource(R.string.chart_settings_section_display))
            ToggleRow(
                label = stringResource(R.string.chart_settings_gridlines),
                contentDescription = stringResource(R.string.chart_settings_gridlines),
                checked = config.showGridlines,
                testTag = CHART_SETTINGS_GRIDLINES_TAG,
                onCheckedChange = { onEvent(DashboardEvent.ChartGridlinesToggled(it)) },
            )
            ToggleRow(
                label = stringResource(R.string.chart_settings_labels),
                contentDescription = stringResource(R.string.chart_settings_labels),
                checked = config.showLabels,
                testTag = CHART_SETTINGS_LABELS_TAG,
                onCheckedChange = { onEvent(DashboardEvent.ChartLabelsToggled(it)) },
            )
            ToggleRow(
                label = stringResource(R.string.chart_settings_projection),
                contentDescription = stringResource(R.string.chart_settings_projection_cd),
                checked = config.showProjection,
                testTag = CHART_SETTINGS_PROJECTION_TAG,
                onCheckedChange = { onEvent(DashboardEvent.ChartProjectionToggled(it)) },
            )
            ToggleRow(
                label = stringResource(R.string.chart_settings_visible),
                contentDescription = stringResource(R.string.chart_settings_visible),
                checked = config.visible,
                testTag = CHART_SETTINGS_VISIBLE_TAG,
                onCheckedChange = { onEvent(DashboardEvent.ChartVisibilityChanged(it)) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.chartSettingsSectionLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StyleRow(
    selected: ChartStyle,
    metricLabel: String,
    onSelected: (ChartStyle) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ChartStyle.entries.forEach { style ->
            val isSelected = style == selected
            val styleLabel = stringResource(chartStyleLabelRes(style))
            val borderColor =
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier =
                    Modifier
                        .width(Spacing.chartSettingsStyleThumbWidth)
                        .height(Spacing.chartSettingsStyleThumbHeight)
                        .border(
                            width = if (isSelected) Spacing.xxs else Spacing.dashboardBalancePanelBorderWidth,
                            color = borderColor,
                            shape = MaterialTheme.shapes.small,
                        ).clickable { onSelected(style) }
                        .testTag(chartStyleThumbTag(style))
                        .clearAndSetSemantics {
                            contentDescription = styleLabel
                            this.selected = isSelected
                        },
                contentAlignment = Alignment.Center,
            ) {
                BalanceTrendChart(
                    points = STYLE_PREVIEW_POINTS,
                    metricLabel = metricLabel,
                    showGridlines = false,
                    showLabels = false,
                    style = style,
                )
            }
        }
    }
}

@Composable
private fun <T> SegmentedRow(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.testTag(option.testTag),
            ) {
                Text(text = option.label, maxLines = 2)
            }
        }
    }
}

@Composable
private fun PointCountStepper(
    count: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.chartSettingsSheetRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconButton(
            onClick = { onChange(count - 1) },
            enabled = count > CHART_POINT_COUNT_RANGE.first,
            modifier = Modifier.testTag(CHART_SETTINGS_POINTS_DECREASE_TAG),
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.chart_settings_points_decrease),
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(CHART_SETTINGS_POINTS_VALUE_TAG),
        )
        IconButton(
            onClick = { onChange(count + 1) },
            enabled = count < CHART_POINT_COUNT_RANGE.last,
            modifier = Modifier.testTag(CHART_SETTINGS_POINTS_INCREASE_TAG),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.chart_settings_points_increase),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    contentDescription: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.chartSettingsSheetRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier =
                Modifier
                    .heightIn(min = Spacing.chartSettingsSheetRowHeight)
                    .testTag(testTag)
                    .semantics { this.contentDescription = contentDescription },
        )
    }
}

private data class SegmentOption<T>(
    val value: T,
    val label: String,
    val testTag: String,
)

@Composable
private fun autoModeOptions(): List<SegmentOption<Boolean>> =
    listOf(
        SegmentOption(true, stringResource(R.string.chart_settings_mode_auto), CHART_SETTINGS_MODE_AUTO_TAG),
        SegmentOption(false, stringResource(R.string.chart_settings_mode_manual), CHART_SETTINGS_MODE_MANUAL_TAG),
    )

@Composable
private fun periodTypeOptions(): List<SegmentOption<ChartPeriodType>> =
    listOf(
        SegmentOption(ChartPeriodType.Follow, stringResource(R.string.chart_settings_period_follow), chartPeriodTag(ChartPeriodType.Follow)),
        SegmentOption(ChartPeriodType.Day, stringResource(R.string.chart_settings_period_day), chartPeriodTag(ChartPeriodType.Day)),
        SegmentOption(ChartPeriodType.Week, stringResource(R.string.chart_settings_period_week), chartPeriodTag(ChartPeriodType.Week)),
        SegmentOption(ChartPeriodType.Month, stringResource(R.string.chart_settings_period_month), chartPeriodTag(ChartPeriodType.Month)),
        SegmentOption(ChartPeriodType.Year, stringResource(R.string.chart_settings_period_year), chartPeriodTag(ChartPeriodType.Year)),
    )

@Composable
private fun metricOptions(): List<SegmentOption<ChartMetric>> =
    listOf(
        SegmentOption(ChartMetric.CUMULATIVE, stringResource(R.string.chart_settings_metric_cumulative), chartMetricTag(ChartMetric.CUMULATIVE)),
        SegmentOption(ChartMetric.PERIOD_NET, stringResource(R.string.chart_settings_metric_period_net), chartMetricTag(ChartMetric.PERIOD_NET)),
        SegmentOption(ChartMetric.INCOME_EXPENSE, stringResource(R.string.chart_settings_metric_income_expense), chartMetricTag(ChartMetric.INCOME_EXPENSE)),
    )

@Composable
private fun colorRuleOptions(): List<SegmentOption<ChartColorRule>> =
    listOf(
        SegmentOption(ChartColorRule.Solid, stringResource(R.string.chart_settings_color_solid), chartColorTag(ChartColorRule.Solid)),
        SegmentOption(
            ChartColorRule.AlwaysGreen,
            stringResource(R.string.chart_settings_color_always_green),
            chartColorTag(ChartColorRule.AlwaysGreen),
        ),
        SegmentOption(
            ChartColorRule.AlwaysRed,
            stringResource(R.string.chart_settings_color_always_red),
            chartColorTag(ChartColorRule.AlwaysRed),
        ),
        SegmentOption(
            ChartColorRule.ByDirection,
            stringResource(R.string.chart_settings_color_by_direction),
            chartColorTag(ChartColorRule.ByDirection),
        ),
    )

private val STYLE_PREVIEW_POINTS = listOf(-1f, 0.5f, -0.5f, 1.2f, 0.8f)

const val CHART_SETTINGS_SHEET_TAG = "chart_settings_sheet"
const val CHART_SETTINGS_MODE_AUTO_TAG = "chart_settings_mode_auto"
const val CHART_SETTINGS_MODE_MANUAL_TAG = "chart_settings_mode_manual"
const val CHART_SETTINGS_GRIDLINES_TAG = "chart_settings_gridlines"
const val CHART_SETTINGS_LABELS_TAG = "chart_settings_labels"
const val CHART_SETTINGS_PROJECTION_TAG = "chart_settings_projection"
const val CHART_SETTINGS_VISIBLE_TAG = "chart_settings_visible"
const val CHART_SETTINGS_POINTS_DECREASE_TAG = "chart_settings_points_decrease"
const val CHART_SETTINGS_POINTS_INCREASE_TAG = "chart_settings_points_increase"
const val CHART_SETTINGS_POINTS_VALUE_TAG = "chart_settings_points_value"

fun chartStyleThumbTag(style: ChartStyle): String = "chart_settings_style_${style.toId()}"

fun chartPeriodTag(periodType: ChartPeriodType): String = "chart_settings_period_${periodType.toId()}"

fun chartMetricTag(metric: ChartMetric): String = "chart_settings_metric_${metric.toId()}"

fun chartColorTag(colorRule: ChartColorRule): String = "chart_settings_color_${colorRule.toId()}"
