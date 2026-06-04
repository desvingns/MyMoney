package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val MoneyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val Shapes.recordsHeaderBalance: Shape
    get() = large

val Shapes.recordsHeaderControl: Shape
    get() = extraLarge

val Shapes.dashboardBalancePanel: Shape
    get() = RoundedCornerShape(12.dp)

val Shapes.dashboardPeriodIndicator: Shape
    get() = RoundedCornerShape(999.dp)
