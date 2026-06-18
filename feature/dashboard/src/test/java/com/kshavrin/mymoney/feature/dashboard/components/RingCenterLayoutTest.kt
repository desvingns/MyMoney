package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingCenterLayoutTest {
    @Test
    fun `wide layout keeps balance and badge at full scale`() {
        val layout = calculateLayout(availableWidth = 1_000f, availableHeight = 1_000f)

        assertEquals(1f, layout.balanceScale, SEARCH_TOLERANCE)
        assertEquals(1f, layout.badgeScale, SEARCH_TOLERANCE)
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
        val availableHeight = 100f
        val layout = calculateLayout(availableWidth = availableWidth, availableHeight = availableHeight)

        val balanceWidth = measureWidth(PERIOD_NET, BALANCE_FONT_SIZE * layout.balanceScale)
        val incomeWidth = measureWidth(INCOME, BADGE_FONT_SIZE * layout.badgeScale)
        val expenseWidth = measureWidth(EXPENSE, BADGE_FONT_SIZE * layout.badgeScale)
        val badgeWidth = maxOf(incomeWidth, expenseWidth) + BADGE_HORIZONTAL_PADDING * layout.badgeScale * 2
        val badgeHeight =
            BADGE_LINE_HEIGHT * layout.badgeScale * 2 +
                BADGE_LINE_GAP * layout.badgeScale +
                BADGE_VERTICAL_PADDING * layout.badgeScale * 2
        val contentHeight =
            LABEL_LINE_HEIGHT +
                BALANCE_LINE_HEIGHT * layout.balanceScale +
                TOP_GAP +
                badgeHeight

        assertTrue("balance and complete badge must fit width", maxOf(balanceWidth, badgeWidth) <= availableWidth)
        assertTrue("label, balance, and complete two-line badge must fit height", contentHeight <= availableHeight)
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
        const val BADGE_FONT_SIZE = 12f
        const val BADGE_LINE_HEIGHT = 16f
        const val MINIMUM_BALANCE_FONT_SIZE = 14f
        const val MINIMUM_BADGE_FONT_SIZE = 11f
        const val TOP_GAP = 8f
        const val BADGE_HORIZONTAL_PADDING = 16f
        const val BADGE_VERTICAL_PADDING = 4f
        const val BADGE_LINE_GAP = 2f
        const val GLYPH_WIDTH_FACTOR = 0.5f
        const val SEARCH_TOLERANCE = 0.001f
    }
}
