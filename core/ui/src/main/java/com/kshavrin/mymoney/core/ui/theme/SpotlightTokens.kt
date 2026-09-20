package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Spacing.spotlightCutoutPadding: Dp get() = 8.dp
val Spacing.spotlightCardMaxWidth: Dp get() = 320.dp
val Spacing.spotlightCutoutRingStroke: Dp get() = 2.dp
val Spacing.spotlightCutoutCornerRadius: Dp get() = 20.dp

val Shapes.spotlightCard: Shape get() = RoundedCornerShape(20.dp)

val Typography.spotlightTitle: TextStyle
    get() = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)

val Typography.spotlightBody: TextStyle
    get() = bodyMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)

val Typography.spotlightProgress: TextStyle
    get() = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
