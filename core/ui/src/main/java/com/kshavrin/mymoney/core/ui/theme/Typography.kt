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
