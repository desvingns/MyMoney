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

// Place the hint card on the side of the cutout that actually fits [cardHeight]; if both fit, pick
// the roomier side; if neither fits, still pick the roomier side and let the card scroll/clamp.
fun cardPlacement(
    cutout: Rect,
    containerHeight: Float,
    cardHeight: Float,
): CardPlacement {
    val spaceAbove = cutout.top
    val spaceBelow = containerHeight - cutout.bottom
    val fitsAbove = spaceAbove >= cardHeight
    val fitsBelow = spaceBelow >= cardHeight
    return when {
        fitsAbove && fitsBelow -> if (spaceAbove >= spaceBelow) CardPlacement.Above else CardPlacement.Below
        fitsAbove -> CardPlacement.Above
        fitsBelow -> CardPlacement.Below
        else -> if (spaceAbove >= spaceBelow) CardPlacement.Above else CardPlacement.Below
    }
}
