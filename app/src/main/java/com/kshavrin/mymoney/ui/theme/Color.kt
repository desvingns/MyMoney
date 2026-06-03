package com.kshavrin.mymoney.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val ColorScheme.recordsHeaderStripContainer: Color
    get() = surfaceVariant

val ColorScheme.recordsHeaderBalanceContainer: Color
    get() = primaryContainer

val ColorScheme.recordsHeaderBalanceContent: Color
    get() = onPrimaryContainer

val ColorScheme.recordsHeaderBalanceOutline: Color
    get() = primary

val ColorScheme.recordsHeaderBalanceShadow: Color
    get() = primary.copy(alpha = 0.28f)

val ColorScheme.recordsHeaderSortTint: Color
    get() = primary

val ColorScheme.recordsHeaderSortContainer: Color
    get() = surface.copy(alpha = 0f)
