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

    // Dashboard FAB row (three-button «Dashboard Final» redesign — RealFabs).
    // Three 94dp neon-outline rings are laid out with space-between justification.
    // dashboardFabRowHorizontalPadding is the side inset (mockup: padding '12px 26px 18px'
    // → 26px each side) so the outer FABs sit ~26dp from the screen edge.
    // dashboardFabRowVerticalPaddingTop / Bottom preserve the mockup's asymmetric
    // vertical spacing (12dp above, 18dp below) which visually grounds the FABs at the
    // bottom of the screen without excessive dead space above them.
    // Distinct from dashboardFabHorizontalPadding (44dp) which was the per-FAB centering
    // offset used in the two-FAB TwoFabLayout.
    val dashboardFabRowHorizontalPadding = 26.dp
    val dashboardFabRowVerticalPaddingTop = 12.dp
    val dashboardFabRowVerticalPaddingBottom = 18.dp
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
    val dashboardTopBarMinHeight = 56.dp
    val dashboardTopBarIconGlyphSize = 24.dp

    // Dashboard top-bar (single-row «Dashboard Final» redesign) — mint underline
    // bar beneath the period title label.  The bar is always visible (not tied to
    // selection state) and serves as a decorative accent per the «Dashboard Final»
    // mockup (RealTopBar: width 78, height 4, borderRadius 2, color NeonMint).
    // Distinct from dashboardPeriodIndicatorWidth (88dp) which was used for the
    // two-row layout's horizontal selection strip.
    val dashboardPeriodUnderlineWidth = 78.dp
    val dashboardPeriodUnderlineHeight = 4.dp
    val dashboardPeriodUnderlineRadius = 2.dp

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

    // Compact height for the mini trend chart embedded inside a CurrencyBalanceCard
    // («Все счета → раздельно»). 48dp fits one visible slope without labels or
    // decorative grid lines, leaves room for the balance text above it in the card,
    // and respects the M3 minimum touch-target floor for the surrounding card.
    val trendChartMiniHeight = 48.dp

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

    val dashboardAuroraHostHorizontalPaddingWide = 8.dp
    val dashboardAuroraCardPaddingTop = 18.dp
    val dashboardAuroraCardPaddingHorizontal = 18.dp

    val dashboardAuroraCardPaddingBottom = 14.dp
    val dashboardAuroraCardPaddingTopCompact = 16.dp
    val dashboardAuroraCardPaddingBottomCompact = 12.dp

    val dashboardAuroraChartHeight = 116.dp
    val dashboardAuroraChartHeightCompact = 110.dp

    val dashboardAuroraPillPaddingVertical = 5.dp

    val dashboardAuroraPillGap = 10.dp

    val dashboardAuroraValueBottomMargin = 2.dp
    val dashboardAuroraPillBottomMargin = 12.dp
    val dashboardAuroraPillBottomMarginCompact = 10.dp

    // Dashboard inline category accordion (SPEC 02 — CategoryRecordsInlineList).
    // Read-only record rows inside the expanded block have no tap-target requirement, so
    // 40dp (vs the tile's 76dp or M3's 56dp sheet row) keeps the list compact while
    // remaining legible at body text scale.
    val dashboardInlineRecordRowHeight = 40.dp

    // Aurora hero card — dark display panel geometry.
    // dashboardAuroraInnerPanelInset: margin from the card's inner edge to the panel so the
    // neon border has visible breathing room on all four sides.  7dp sits in the 6–8dp spec
    // range and aligns to the 4dp grid (4+3 — close enough to avoid awkward half-pixels).
    // dashboardAuroraInnerPanelFeather: how far the panel's perimeter fades to transparent
    // via a radial/edge gradient on all four corners/edges.  20dp is mid-range of the 18–22dp
    // spec, keeps the fade wide enough to be perceived as soft at typical display densities.
    val dashboardAuroraInnerPanelInset = 7.dp
    val dashboardAuroraInnerPanelFeather = 20.dp
}
