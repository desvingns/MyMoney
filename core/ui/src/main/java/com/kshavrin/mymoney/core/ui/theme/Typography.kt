package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MoneyTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 48.sp, fontWeight = FontWeight.Light, lineHeight = 56.sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 36.sp, fontWeight = FontWeight.Light, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    )

val Typography.recordsHeaderBalanceValue: TextStyle
    get() =
        headlineMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 28.sp,
        )

val Typography.recordsHeaderSupportingLabel: TextStyle
    get() =
        labelLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

val Typography.recordsFilterChipLabel: TextStyle
    get() =
        labelLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

val Typography.dashboardTopBarTitle: TextStyle
    get() =
        headlineLarge.copy(
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardTopBarSubtitle: TextStyle
    get() =
        titleMedium.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardPeriodSelected: TextStyle
    get() =
        headlineMedium.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardPeriodUnselected: TextStyle
    get() =
        headlineMedium.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardBalanceLabel: TextStyle
    get() =
        titleLarge.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardBalanceValue: TextStyle
    get() =
        displayMedium.copy(
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardDonutCenterTotal: TextStyle
    get() =
        headlineLarge.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardCalloutLabel: TextStyle
    get() =
        bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardCalloutPercentage: TextStyle
    get() =
        bodyLarge.copy(
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardFabLabel: TextStyle
    get() =
        titleMedium.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardRingBalanceValue: TextStyle
    get() =
        displayLarge.copy(
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardRingBalanceLabel: TextStyle
    get() =
        labelLarge.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardIncomeExpenseBadge: TextStyle
    get() =
        labelMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardCategoryTileTitle: TextStyle
    get() =
        bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        )

val Typography.dashboardCategoryTileAmount: TextStyle
    get() =
        titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
        )

val Typography.goalListRowAmount: TextStyle
    get() =
        bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

val Typography.goalListEmptyTitle: TextStyle
    get() =
        titleMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 24.sp,
        )

val Typography.goalCreditProjectionAmount: TextStyle
    get() =
        headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            lineHeight = 28.sp,
        )

val Typography.goalCreditProjectionLabel: TextStyle
    get() =
        bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

val Typography.goalBreakdownSectionHeader: TextStyle
    get() =
        titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

val Typography.goalBreakdownRowLabel: TextStyle
    get() =
        bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

// Transaction leaf row tokens (S11 «Операции» list — TransactionLeaf)
// Note text displayed between the amount and the date when transaction.note is non-empty.
// Single line, overflow ellipsis; uses labelMedium weight so it stays visually subordinate
// to the amount (bodyMedium) and date (labelSmall).
val Typography.transactionLeafNote: TextStyle
    get() =
        labelMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 16.sp,
        )

// Dashboard top-bar — period-title auto-shrink floor.
// The PeriodSwitcher shrinks the font from dashboardPeriodSelected.fontSize (22sp) down
// to this floor to ensure the label always fits within the available width on a single
// line (single-token periods) or two lines (range/multi-line periods).  Never go below
// this value for readability (SPEC constraint: floor >= 14sp).
val dashboardPeriodTitleMinSp = 14.sp

// Transfer list row tokens (S12 «Переводы» tab)
// «Счёт A → Счёт B» — the primary route label in each transfer row.
val Typography.transferRowRoute: TextStyle
    get() =
        bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
            lineHeight = 24.sp,
        )

// Amount + date metadata line below the route label.
val Typography.transferRowMeta: TextStyle
    get() =
        bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

// Dashboard «Все счета» Separate mode — ISO currency code badge in the per-currency card
// header (e.g. «USD», «KZT»). Sized between labelLarge and titleMedium so it reads as a
// prominent-but-secondary label above the main balance value.
val Typography.dashboardCurrencyCardCurrencyCode: TextStyle
    get() =
        titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            lineHeight = 22.sp,
        )

// Chart settings bottom sheet (ChartSettingsSheet — SPEC 06).
// Section header label — sits above each group of controls («Стиль», «Тип периода», etc.).
// labelMedium weight keeps it clearly subordinate to the sheet title while remaining
// legible as a group separator; uppercase letter-spacing convention lifted from M3 list
// subheader pattern.
val Typography.chartSettingsSectionLabel: TextStyle
    get() =
        labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            lineHeight = 16.sp,
        )

// «График скрыт» hint text — single-line prompt inside the collapsed chart strip.
// bodyMedium weight matches secondary-information density; kept at default size to
// be scannable at a glance without drawing too much attention away from the balance figures.
val Typography.chartHiddenHint: TextStyle
    get() =
        bodyMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 20.sp,
        )

// ── Aurora hero card typography (SecAurora — 03_balance-variants.jsx) ────────

// Uppercase balance label above the value — 11sp/700, wide letter-spacing (1sp).
// The developer applies TextTransform.Uppercase at the call site; the style carries
// weight + size + tracking so both are consistent.
// Note: dashboardBalanceLabel (20sp/Medium) and dashboardRingBalanceLabel (14sp/Medium)
// serve different roles — this token is specifically the Aurora centred label.
val Typography.dashboardAuroraBalanceLabel: TextStyle
    get() =
        labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            lineHeight = 16.sp,
        )

// Big balance value inside the Aurora card — 36sp/ExtraBold, tight letter-spacing (−1sp).
// Distinct from dashboardBalanceValue (40sp/Bold) used in the standalone balance panel
// and dashboardRingBalanceValue (48sp/Bold) used in the neon ring.
val Typography.dashboardAuroraBalanceValue: TextStyle
    get() =
        displayMedium.copy(
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            lineHeight = 40.sp,
        )

// Auto-shrink floor for the Aurora hero balance value.  Like the top-bar period title,
// the value starts at dashboardAuroraBalanceValue.fontSize (36sp) and steps down 1sp at a
// time when it would overflow the card width — a long value under a large accessibility
// fontScale.  This floor keeps even a 9-digit value legible at fontScale 2.0 while staying
// on a single line (no wrapping, no clipping).
val dashboardAuroraBalanceValueMinSp = 16.sp

val Typography.dashboardAuroraBalanceValueCompact: TextStyle
    get() =
        displayMedium.copy(
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
            lineHeight = 30.sp,
        )

val Typography.supportDescription: TextStyle
    get() = bodyLarge

val Typography.supportCardTitle: TextStyle
    get() = titleLarge

val Typography.supportStatusMessage: TextStyle
    get() = bodyMedium

val Typography.supporterBadgeLabel: TextStyle
    get() = labelLarge

val Typography.supporterGratitudeCount: TextStyle
    get() = titleMedium

// Lifetime "total ads watched" counter at the top of the Support screen
// (TotalAdsWatchedBadge). Secondary info label — subordinate to supportDescription
// (bodyLarge) and not inside a card. labelMedium/Normal keeps it legible without
// competing with the intro copy or the rewarded-ad card below.
val Typography.supportAdCounterLabel: TextStyle
    get() =
        labelMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 16.sp,
        )
