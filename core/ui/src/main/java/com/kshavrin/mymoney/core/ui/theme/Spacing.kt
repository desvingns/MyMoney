package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val transactionFormChooseCategoryMinHeight = 72.dp
    val heroAppBarHeight = 64.dp
    val dashboardPeriodRowHeight = 80.dp
    val dashboardPeriodIndicatorWidth = 88.dp
    val dashboardPeriodIndicatorHeight = 4.dp
    val dashboardBalancePanelHeight = 84.dp
    val dashboardBalancePanelMaxWidth = 245.dp
    val dashboardBalancePanelBorderWidth = 1.dp
    val dashboardFabSize = 90.dp
    val dashboardFabHorizontalPadding = 44.dp
    val dashboardFabOutlineWidth = 4.dp
    val dashboardFabLabelTopPadding = 16.dp
    val dashboardDonutExplodedOffset = 8.dp
    val dashboardDonutCenterDividerWidth = 52.dp
    val dashboardDonutCenterDividerThickness = 1.dp
    val dashboardDonutCalloutIconSize = 40.dp
    val dashboardDonutLeaderLineThickness = 1.dp
    val goalListIconCircleSize = 40.dp
    val goalListIconSize = 22.dp

    // Edit-form delete button — taller than a standard OutlinedButton so the
    // destructive action stays clearly separate from the save FAB.
    val transactionFormDeleteButtonHeight = 52.dp

    // Import-migration wizard — per-category config step (SPEC 06).
    // Color swatch diameter in the color picker grid; 32dp gives 5 swatches/row
    // in a 160dp+ container while remaining large enough for tap targets.
    val wizardColorSwatchSize = 32.dp

    // Icon picker item (background touch target) in the category icon picker;
    // 48dp matches the M3 minimum touch target recommendation.
    val wizardIconPickerItemSize = 48.dp
}
