package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.ui.geometry.Rect

enum class SpotlightShape { Circle, RoundedRect }

// Above / Below the cutout when the card fits there; Inside when the cutout is so tall that neither
// gap fits the card — the caller then anchors the card in free space (above the controls row) and
// lets it scroll, so it is never clipped off-screen.
enum class CardPlacement { Above, Below, Inside }

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
// the roomier side; if neither fits (a tall cutout, e.g. the full-height side panel) return Inside so
// the caller anchors it in free space and lets it scroll — never clipped off-screen.
// [containerHeight] is the usable height for the card (typically the top of the controls row), so a
// Below result is guaranteed not to collide with the controls.
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
        else -> CardPlacement.Inside
    }
}
