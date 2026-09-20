package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.ui.geometry.Rect

enum class SpotlightShape { Circle, RoundedRect }

enum class CardPlacement { Above, Below }

fun cutoutRect(
    bounds: Rect,
    shape: SpotlightShape,
    padding: Float,
): Rect {
    val padded = bounds.inflate(padding)
    return when (shape) {
        SpotlightShape.Circle -> {
            val side = maxOf(padded.width, padded.height)
            val cx = padded.center.x
            val cy = padded.center.y
            Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        }
        SpotlightShape.RoundedRect -> padded
    }
}

fun cardPlacement(
    cutout: Rect,
    containerHeight: Float,
    cardHeight: Float,
): CardPlacement {
    val spaceAbove = cutout.top
    val spaceBelow = containerHeight - cutout.bottom
    return if (spaceAbove >= spaceBelow) CardPlacement.Above else CardPlacement.Below
}
