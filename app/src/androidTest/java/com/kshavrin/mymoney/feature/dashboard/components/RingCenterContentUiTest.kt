package com.kshavrin.mymoney.feature.dashboard.components

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RingCenterContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `period net is rounded for display while source money stays unchanged`() {
        val locale = Locale.forLanguageTag("ru-RU")
        val periodNet = money("37650.49")

        setLocalizedContent(locale) {
            RingCenterContent(
                periodNet = periodNet,
                income = money("85000"),
                expense = money("47350"),
                modifier = Modifier.size(width = 220.dp, height = 180.dp),
            )
        }

        val expectedAmount = formattedAmount("37650.49", locale)
        composeTestRule.onNodeWithText(expectedAmount).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(localizedContext(locale).getString(R.string.dashboard_ring_balance))
            .assertIsDisplayed()
        assertFalse("display amount must not include kopecks", expectedAmount.contains(",49"))
        assertEquals(BigDecimal("37650.49"), periodNet.amount)
    }

    @Test
    fun `income and expense badge shows both integer fixture amounts`() {
        val locale = Locale.forLanguageTag("ru-RU")
        val context = localizedContext(locale)
        setLocalizedContent(locale) {
            RingCenterContent(
                periodNet = money("37650.49"),
                income = money("85000"),
                expense = money("47350"),
                modifier = Modifier.size(width = 220.dp, height = 180.dp),
            )
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.dashboard_ring_income, formattedAmount("85000", locale)))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.dashboard_ring_expense, formattedAmount("47350", locale)))
            .assertIsDisplayed()
    }

    @Test
    fun `english resources are visible in the ring center`() {
        val locale = Locale.US
        val context = localizedContext(locale)

        setLocalizedContent(locale) {
            RingCenterContent(
                periodNet = money("37650.49"),
                income = money("85000"),
                expense = money("47350"),
                modifier = Modifier.size(width = 220.dp, height = 180.dp),
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.dashboard_ring_balance)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.dashboard_ring_income, formattedAmount("85000", locale)))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.dashboard_ring_expense, formattedAmount("47350", locale)))
            .assertIsDisplayed()
    }

    @Test
    fun `long content shrinks deterministically and remains inside a narrow inner box`() {
        val locale = Locale.US
        val longNet = money("1234567")

        setLocalizedContent(locale) {
            Box(
                Modifier
                    .size(width = 240.dp, height = 180.dp)
                    .testTag(WIDE_BOX_TAG),
            ) {
                RingCenterContent(longNet, money("85000"), money("47350"))
            }
            Box(
                Modifier
                    .size(width = 150.dp, height = 125.dp)
                    .testTag(NARROW_BOX_TAG),
            ) {
                RingCenterContent(longNet, money("85000"), money("47350"))
            }
        }

        val context = localizedContext(locale)
        val amount = formattedAmount("1234567", locale)
        val income = context.getString(R.string.dashboard_ring_income, formattedAmount("85000", locale))
        val expense = context.getString(R.string.dashboard_ring_expense, formattedAmount("47350", locale))
        val wideAmountBounds = nodeInBox(amount, WIDE_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val narrowAmountBounds = nodeInBox(amount, NARROW_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val firstNarrowBounds = narrowAmountBounds

        composeTestRule.waitForIdle()
        val secondNarrowBounds = nodeInBox(amount, NARROW_BOX_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("narrow balance text must shrink", narrowAmountBounds.width < wideAmountBounds.width)
        assertEquals(firstNarrowBounds, secondNarrowBounds)
        assertNodeInsideBox(amount, NARROW_BOX_TAG)
        assertNodeInsideBox(context.getString(R.string.dashboard_ring_balance), NARROW_BOX_TAG)
        assertNodeInsideBox(income, NARROW_BOX_TAG)
        assertNodeInsideBox(expense, NARROW_BOX_TAG)
    }

    @Test
    fun `complete two line badge stays ordered and bounded in narrow content`() {
        val locale = Locale.US
        val context = localizedContext(locale)
        val income = context.getString(R.string.dashboard_ring_income, formattedAmount("85000", locale))
        val expense = context.getString(R.string.dashboard_ring_expense, formattedAmount("47350", locale))
        setLocalizedContent(locale) {
            Box(
                Modifier
                    .size(width = 150.dp, height = 125.dp)
                    .testTag(NARROW_BOX_TAG),
            ) {
                RingCenterContent(money("37650.49"), money("85000"), money("47350"))
            }
        }

        val incomeBounds = nodeInBox(income, NARROW_BOX_TAG).fetchSemanticsNode().boundsInRoot
        val expenseBounds = nodeInBox(expense, NARROW_BOX_TAG).fetchSemanticsNode().boundsInRoot

        assertFalse("income line must have a non-zero size", incomeBounds.isEmpty)
        assertFalse("expense line must have a non-zero size", expenseBounds.isEmpty)
        assertTrue("income line must remain above expense line", incomeBounds.bottom <= expenseBounds.top)
        assertNodeInsideBox(income, NARROW_BOX_TAG)
        assertNodeInsideBox(expense, NARROW_BOX_TAG)
    }

    @Test
    fun `badge fills the lower zone with divider and stays read only`() {
        val locale = Locale.US
        val context = localizedContext(locale)
        val periodNetText = formattedAmount("37650.49", locale)

        setLocalizedContent(locale) {
            Box(
                Modifier
                    .size(width = 220.dp, height = 180.dp)
                    .testTag(WIDE_BOX_TAG),
            ) {
                RingCenterContent(
                    periodNet = money("37650.49"),
                    income = money("85000"),
                    expense = money("47350"),
                )
            }
        }

        val boxBounds =
            composeTestRule
                .onNodeWithTag(WIDE_BOX_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val badgeBounds =
            composeTestRule
                .onNodeWithTag(BADGE_TAG, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val dividerBounds =
            composeTestRule
                .onNodeWithTag(BADGE_DIVIDER_TAG, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        composeTestRule
            .onNodeWithTag(BADGE_TAG, useUnmergedTree = true)
            .assertHeightIsEqualTo(56.dp)
            .assertHasNoClickAction()
        composeTestRule
            .onNodeWithTag(BADGE_DIVIDER_TAG, useUnmergedTree = true)
            .assertHeightIsEqualTo(1.dp)
        composeTestRule
            .onNodeWithText(periodNetText, useUnmergedTree = true)
            .assertHasNoClickAction()

        assertDpEquals("badge must keep the configured left inset", badgeBounds.left - boxBounds.left, 32.dp)
        assertDpEquals("badge must keep the configured right inset", boxBounds.right - badgeBounds.right, 32.dp)
        assertDpEquals("badge must anchor to the lower zone with the bottom inset only", boxBounds.bottom - badgeBounds.bottom, 16.dp)
        assertTrue("divider must sit between income and expense rows", dividerBounds.top >= badgeBounds.top && dividerBounds.bottom <= badgeBounds.bottom)
        assertNodeInsideBox(context.getString(R.string.dashboard_ring_balance), WIDE_BOX_TAG)
        assertNodeInsideBox(periodNetText, WIDE_BOX_TAG)
    }

    private fun nodeInBox(
        text: String,
        boxTag: String,
    ) = composeTestRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(boxTag)),
        useUnmergedTree = true,
    )

    private fun assertNodeInsideBox(
        text: String,
        boxTag: String,
    ) {
        val boxBounds = composeTestRule.onNodeWithTag(boxTag).fetchSemanticsNode().boundsInRoot
        val nodeBounds = nodeInBox(text, boxTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("$text must stay within the left edge", nodeBounds.left >= boxBounds.left)
        assertTrue("$text must stay within the top edge", nodeBounds.top >= boxBounds.top)
        assertTrue("$text must stay within the right edge", nodeBounds.right <= boxBounds.right)
        assertTrue("$text must stay within the bottom edge", nodeBounds.bottom <= boxBounds.bottom)
    }

    private fun assertDpEquals(
        message: String,
        actualPx: Float,
        expectedDp: Dp,
    ) {
        val expectedPx = with(composeTestRule.density) { expectedDp.toPx() }
        assertEquals(message, expectedPx, actualPx, 1f)
    }

    private fun setLocalizedContent(
        locale: Locale,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val context = localizedContext(locale)
        val configuration = context.resources.configuration
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
            ) {
                MyMoneyTheme(content = content)
            }
        }
    }

    private fun localizedContext(locale: Locale): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return baseContext.createConfigurationContext(configuration)
    }

    private fun money(amount: String) = Money(BigDecimal(amount), ruble)

    private fun formattedAmount(
        amount: String,
        locale: Locale,
    ): String =
        MoneyFormatter.format(
            amount = BigDecimal(amount),
            currencySymbol = ruble.symbol,
            decimalDigits = 0,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )

    private companion object {
        const val WIDE_BOX_TAG = "ring_center_wide_box"
        const val NARROW_BOX_TAG = "ring_center_narrow_box"
        const val BADGE_TAG = "ring_center_badge"
        const val BADGE_DIVIDER_TAG = "ring_center_badge_divider"

        val ruble =
            Currency(
                id = 1L,
                code = "RUB",
                symbol = "₽",
                name = "Russian Ruble",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            )
    }
}
