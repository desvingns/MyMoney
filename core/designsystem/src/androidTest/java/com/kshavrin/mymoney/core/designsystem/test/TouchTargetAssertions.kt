package com.kshavrin.mymoney.core.designsystem.test

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Dp

fun SemanticsNodeInteraction.assertTouchWidthIsAtLeast(
    expectedMinWidth: Dp,
): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("Failed to retrieve touch bounds of the node.")
    val actualWidth = node.touchBoundsInRoot.width / node.layoutInfo.density.density
    assertAtLeast(actualWidth, expectedMinWidth.value, "width")
    return this
}

fun SemanticsNodeInteraction.assertTouchHeightIsAtLeast(
    expectedMinHeight: Dp,
): SemanticsNodeInteraction {
    val node = fetchSemanticsNode("Failed to retrieve touch bounds of the node.")
    val actualHeight = node.touchBoundsInRoot.height / node.layoutInfo.density.density
    assertAtLeast(actualHeight, expectedMinHeight.value, "height")
    return this
}

private fun assertAtLeast(
    actual: Float,
    expected: Float,
    subject: String,
) {
    if (actual < expected - 0.5f) {
        throw AssertionError("Actual touch $subject is ${actual}dp, expected at least ${expected}dp")
    }
}
