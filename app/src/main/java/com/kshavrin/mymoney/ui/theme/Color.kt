package com.kshavrin.mymoney.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.kshavrin.mymoney.core.ui.theme.DarkColors
import com.kshavrin.mymoney.core.ui.theme.LightColors

val LightColorScheme: ColorScheme = LightColors
val DarkColorScheme: ColorScheme = DarkColors

val ColorScheme.dashboardTopBarNavigationIcon: Color
    get() = onSurface
