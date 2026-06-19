package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingCenterLayoutTest {
    @Test
    fun `wide layout keeps balance and badge at full scale and grows badge to max height`() {
        val layout = calculateLayout(availableWidth = 1_000f, availableHeight = 1_000f)

        assertEquals(1f, layout.balanceScale, SEARCH_TOLERANCE)
        assertEquals(1f, layout.badgeScale, SEARCH_TOLERANCE)
        assertEquals(BADGE_MAX_HEIGHT, layout.badgeHeight, SEARCH_TOLERANCE)
    }

    @Test
    fun `narrow layout shrinks long balance to fit available width`() {
        val availableWidth = 150f
        val layout = calculateLayout(availableWidth = availableWidth, availableHeight = 200f)

        assertTrue("long balance must shrink", layout.balanceScale < 1f)
        assertTrue(
            "scaled balance must fit the available width",
            measureWidth(PERIOD_NET, BALANCE_FONT_SIZE * layout.balanceScale) <= availableWidth,
        )
    }

    @Test
    fun `layout fits width and height including both badge lines`() {
        val availableWidth = 150f
        val availableHeight = 116f
        val layout = calculateLayout(availableWidth = availableWidth, availableHeight = availableHeight)

        val balanceWidth = measureWidth(PERIOD_NET, BALANCE_FONT_SIZE * layout.balanceScale)
        val incomeWidth = measureWidth(INCOME, BADGE_FONT_SIZE * layout.badgeScale)
        val expenseWidth = measureWidth(EXPENSE, BADGE_FONT_SIZE * layout.badgeScale)
        val maxBadgeTextWidth = availableWidth - BADGE_HORIZONTAL_INSET * 2 - BADGE_HORIZONTAL_PADDING * layout.badgeScale * 2
        val badgeWidth =
            maxOf(incomeWidth, expenseWidth) +
                BADGE_HORIZONTAL_PADDING * layout.badgeScale * 2 +
                BADGE_HORIZONTAL_INSET * 2
        val contentHeight =
            LABEL_LINE_HEIGHT +
                BALANCE_LINE_HEIGHT * layout.balanceScale +
                TOP_GAP +
                layout.badgeHeight +
                BADGE_BOTTOM_INSET

        assertTrue("balance and complete badge must fit width", maxOf(balanceWidth, badgeWidth) <= availableWidth)
        assertTrue("badge text must fit the inset-bounded badge width", maxOf(incomeWidth, expenseWidth) <= maxBadgeTextWidth)
        assertTrue("label, balance, and complete two-line badge must fit height", contentHeight <= availableHeight)
    }

    @Test
    fun `height-constrained layout shrinks badge once divider consumes the free zone`() {
        val availableHeight = LABEL_LINE_HEIGHT + BALANCE_LINE_HEIGHT + TOP_GAP + 54f + BADGE_BOTTOM_INSET
        val layout = calculateLayout(availableWidth = 1_000f, availableHeight = availableHeight)
        val availableLowerZone =
            availableHeight -
                LABEL_LINE_HEIGHT -
                BALANCE_LINE_HEIGHT * layout.balanceScale -
                TOP_GAP -
                BADGE_BOTTOM_INSET

        assertTrue("divider-aware free-zone fit must shrink the badge scale", layout.badgeScale < 1f)
        assertTrue("badge height must stay within the available lower zone", layout.badgeHeight <= availableLowerZone + SEARCH_TOLERANCE)
        assertTrue("badge still respects the configured minimum height", layout.badgeHeight >= BADGE_MIN_HEIGHT)
    }

    @Test
    fun `unavoidably small box preserves readable minimum font floors`() {
        val layout = calculateLayout(availableWidth = 1f, availableHeight = 1f)

        assertEquals(MINIMUM_BALANCE_FONT_SIZE / BALANCE_FONT_SIZE, layout.balanceScale, 0f)
        assertEquals(MINIMUM_BADGE_FONT_SIZE / BADGE_FONT_SIZE, layout.badgeScale, 0f)
        assertTrue(BALANCE_FONT_SIZE * layout.balanceScale > 0f)
        assertTrue(BADGE_FONT_SIZE * layout.badgeScale > 0f)
    }

    @Test
    fun `same constraints and measurements produce identical scales`() {
        val first = calculateLayout(availableWidth = 150f, availableHeight = 100f)
        val second = calculateLayout(availableWidth = 150f, availableHeight = 100f)

        assertEquals(first, second)
    }

    private fun calculateLayout(
        availableWidth: Float,
        availableHeight: Float,
    ): RingCenterLayout =
        calculateRingCenterLayout(
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            label = LABEL,
            periodNet = PERIOD_NET,
            income = INCOME,
            expense = EXPENSE,
            labelStyle = TextStyle(fontSize = LABEL_FONT_SIZE.sp, lineHeight = LABEL_LINE_HEIGHT.sp),
            balanceStyle = TextStyle(fontSize = BALANCE_FONT_SIZE.sp, lineHeight = BALANCE_LINE_HEIGHT.sp),
            badgeStyle = TextStyle(fontSize = BADGE_FONT_SIZE.sp, lineHeight = BADGE_LINE_HEIGHT.sp),
            minimumBalanceFontSize = MINIMUM_BALANCE_FONT_SIZE.sp,
            minimumBadgeFontSize = MINIMUM_BADGE_FONT_SIZE.sp,
            topGap = TOP_GAP,
            badgeHorizontalPadding = BADGE_HORIZONTAL_PADDING,
            badgeVerticalPadding = BADGE_VERTICAL_PADDING,
            badgeLineGap = BADGE_LINE_GAP,
            badgeMinHeight = BADGE_MIN_HEIGHT,
            badgeMaxHeight = BADGE_MAX_HEIGHT,
            badgeHorizontalInset = BADGE_HORIZONTAL_INSET,
            badgeDividerThickness = BADGE_DIVIDER_THICKNESS,
            badgeRowMinHeight = BADGE_ROW_MIN_HEIGHT,
            badgeBottomInset = BADGE_BOTTOM_INSET,
            measure = { text, style ->
                MeasuredText(
                    width = measureWidth(text, style.fontSize.value),
                    height = style.lineHeight.value,
                )
            },
        )

    private fun measureWidth(
        text: String,
        fontSize: Float,
    ): Float = text.length * fontSize * GLYPH_WIDTH_FACTOR

    private companion object {
        const val LABEL = "Balance"
        const val PERIOD_NET = "1,234,567 ₽"
        const val INCOME = "↑ 85,000 ₽"
        const val EXPENSE = "↓ 47,350 ₽"

        const val LABEL_FONT_SIZE = 14f
        const val LABEL_LINE_HEIGHT = 20f
        const val BALANCE_FONT_SIZE = 48f
        const val BALANCE_LINE_HEIGHT = 52f
        const val BADGE_FONT_SIZE = 13f
        const val BADGE_LINE_HEIGHT = 18f
        const val MINIMUM_BALANCE_FONT_SIZE = 14f
        const val MINIMUM_BADGE_FONT_SIZE = 11f
        const val TOP_GAP = 8f
        const val BADGE_HORIZONTAL_PADDING = 12f
        const val BADGE_VERTICAL_PADDING = 8f
        const val BADGE_LINE_GAP = 0f
        const val BADGE_MIN_HEIGHT = 52f
        const val BADGE_MAX_HEIGHT = 56f
        const val BADGE_HORIZONTAL_INSET = 32f
        const val BADGE_DIVIDER_THICKNESS = 1f
        const val BADGE_ROW_MIN_HEIGHT = 19f
        const val BADGE_BOTTOM_INSET = 16f
        const val GLYPH_WIDTH_FACTOR = 0.5f
        const val SEARCH_TOLERANCE = 0.001f
    }
}
