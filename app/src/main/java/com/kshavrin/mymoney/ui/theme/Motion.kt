package com.kshavrin.mymoney.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class Motion(
    val durationShort: Int = 200,
    val durationMedium: Int = 300,
    val durationLong: Int = 500,
    val easeStandard: Easing = FastOutSlowInEasing,
    val easeEmphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)

val LocalMotion = staticCompositionLocalOf { Motion() }
