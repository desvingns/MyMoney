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
    val dashboardPeriodIndicatorWidth = 88.dp
    val dashboardPeriodIndicatorHeight = 4.dp
    val dashboardBalancePanelHeight = 84.dp
    val dashboardBalancePanelMaxWidth = 245.dp
    val dashboardBalancePanelBorderWidth = 1.dp
    val dashboardFabSize = 94.dp
    val dashboardFabHorizontalPadding = 44.dp
    val dashboardFabOutlineWidth = 3.6.dp
    val dashboardFabIconSize = 29.dp
    val dashboardFabLabelTopPadding = 16.dp
    val dashboardDonutExplodedOffset = 8.dp
    val dashboardDonutCenterDividerWidth = 52.dp
    val dashboardDonutCenterDividerThickness = 1.dp
    val dashboardDonutCalloutIconSize = 40.dp
    val dashboardDonutLeaderLineThickness = 1.dp
    val neonRingDiameter = 200.dp
    val neonRingStrokeWidth = 16.dp
    val neonRingGlowRadius = 16.dp
    val neonRingGlowSpread = 4.dp
    val dashboardRingCenterBadgeMinHeight = 52.dp
    val dashboardRingCenterBadgeMaxHeight = 56.dp
    val dashboardRingCenterBadgeHorizontalInset = 32.dp
    val dashboardRingCenterBadgeHorizontalPadding = 12.dp
    val dashboardRingCenterBadgeVerticalPadding = 8.dp
    val dashboardRingCenterBadgeRowMinHeight = 19.dp
    val dashboardRingCenterBadgeDividerThickness = 1.dp
    val dashboardRingCenterBadgeBottomInset = 16.dp
    val dashboardTileHeight = 76.dp
    val dashboardTileCornerRadius = 16.dp
    val dashboardTileIconChipSize = 44.dp
    val dashboardTileProgressBarHeight = 4.dp
    val goalListIconCircleSize = 40.dp
    val goalListIconSize = 22.dp

    // Dashboard top-bar — period-title auto-shrink layout.
    // The bar has no fixed height when the period spans two lines; instead it
    // wraps its content.  dashboardTopBarMinHeight is the 1-line baseline so the
    // bar never collapses below the icon touch targets.  dashboardTopBarIconGlyphSize
    // is the rendered icon size (smaller than Spacing.xxl = 32dp) so the left/right
    // icon clusters reclaim horizontal space for the period title without shrinking
    // the 48dp IconButton touch target.
    val dashboardTopBarMinHeight = 44.dp
    val dashboardTopBarIconGlyphSize = 24.dp

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

    // Dashboard «Все счета» Separate mode — vertical gap between per-currency balance cards
    // in the stacked scroll column. 12dp (Spacing.m) keeps the stack dense enough to show
    // 2-3 cards without scrolling on a typical phone, while remaining visually distinct from
    // the card's own internal padding.
    val dashboardCurrencyCardStackSpacing = 12.dp

    // Balance trend chart geometry (BalanceTrendChart).
    // Default composable height: tall enough to show meaningful slope at a glance
    // without dominating the dashboard card.
    val trendChartDefaultHeight = 96.dp

    // Stroke width of the polyline series.
    val trendChartLineStrokeWidth = 2.dp

    // Radius of the filled dot at each data point.
    val trendChartPointRadius = 3.dp

    // Radius of the glowing last-point marker (outer halo circle).
    val trendChartMarkerRadius = 5.dp

    // BlurMaskFilter sigma for the marker glow — matches neonRingGlowRadius scale.
    val trendChartMarkerGlowRadius = 8.dp

    // Stroke width of the zero baseline and vertical decorative grid lines.
    val trendChartGridLineStrokeWidth = 1.dp

    // Vertical padding inside the chart canvas so points at extremes are not clipped.
    val trendChartVerticalPadding = 8.dp

    // Horizontal padding so the first/last point is not clipped by the glow radius.
    val trendChartHorizontalPadding = 8.dp

    // Height reserved for period labels below the canvas when showLabels = true.
    val trendChartLabelHeight = 16.dp

    // Chart settings bottom sheet (ChartSettingsSheet — SPEC 06).
    // Each settings row (style picker item, segmented row, toggle row) uses the M3
    // ListItem minimum touch target height of 56dp so controls remain comfortably
    // tappable without explicit vertical padding overrides.
    val chartSettingsSheetRowHeight = 56.dp

    // Style-picker thumbnail inside the horizontal style scroll.
    // 72×40dp gives enough surface to convey the polyline shape at a glance
    // while fitting ≥4 thumbnails in a 320dp+ sheet width before scrolling.
    val chartSettingsStyleThumbWidth = 72.dp
    val chartSettingsStyleThumbHeight = 40.dp

    // Vertical gap between labelled sections inside the sheet
    // (e.g. between «Тип периода» and «Метрика»).
    // Uses 8dp (Spacing.s) so sections are clearly grouped without large dead space
    // on a compact phone screen.
    val chartSettingsSheetSectionGap = 8.dp

    // «График скрыт» hint strip height — thin placeholder shown on the dashboard
    // when chartVisible = false; tall enough to be tappable (44dp minimum) while
    // clearly signalling the chart is collapsed rather than missing.
    val chartHiddenHintHeight = 44.dp
}
