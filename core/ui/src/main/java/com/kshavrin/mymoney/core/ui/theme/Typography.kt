package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MoneyTypography = Typography(
    displayLarge   = TextStyle(fontFamily = FontFamily.Default, fontSize = 48.sp, fontWeight = FontWeight.Light,  lineHeight = 56.sp),
    displayMedium  = TextStyle(fontFamily = FontFamily.Default, fontSize = 36.sp, fontWeight = FontWeight.Light,  lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

val Typography.recordsHeaderBalanceValue: TextStyle
    get() = headlineMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
    )

val Typography.recordsHeaderSupportingLabel: TextStyle
    get() = labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    )

val Typography.dashboardTopBarTitle: TextStyle
    get() = headlineLarge.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardTopBarSubtitle: TextStyle
    get() = titleMedium.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardPeriodSelected: TextStyle
    get() = headlineMedium.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardPeriodUnselected: TextStyle
    get() = headlineMedium.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardBalanceLabel: TextStyle
    get() = titleLarge.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardBalanceValue: TextStyle
    get() = displayMedium.copy(
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardDonutCenterTotal: TextStyle
    get() = headlineLarge.copy(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardCalloutLabel: TextStyle
    get() = bodyMedium.copy(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardCalloutPercentage: TextStyle
    get() = headlineMedium.copy(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    )

val Typography.dashboardFabLabel: TextStyle
    get() = titleMedium.copy(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )

val Typography.goalListRowAmount: TextStyle
    get() = bodyMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    )

val Typography.goalListEmptyTitle: TextStyle
    get() = titleMedium.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    )
