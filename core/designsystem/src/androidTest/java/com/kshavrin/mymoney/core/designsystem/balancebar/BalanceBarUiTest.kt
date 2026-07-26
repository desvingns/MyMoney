package com.kshavrin.mymoney.core.designsystem.balancebar

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BalanceBarUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders the localized balance label next to the formatted amount`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(amount = "12 345,67 RUB", isPositive = true)
            }
        }

        composeTestRule
            .onNodeWithText("${targetString(R.string.balance_bar_label)} 12 345,67 RUB")
            .assertIsDisplayed()
    }

    @Test
    fun `invokes onClick when the bar is tapped`() {
        var clicks = 0

        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(
                    amount = "100,00 RUB",
                    isPositive = true,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag(BALANCE_BAR_TAG),
                )
            }
        }

        composeTestRule.onNodeWithTag(BALANCE_BAR_TAG).performClick()

        composeTestRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `places a glyph on both the left and the right of the centered amount`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(
                    amount = "100,00 RUB",
                    isPositive = true,
                    modifier = Modifier.testTag(BALANCE_BAR_TAG),
                )
            }
        }

        val barBounds =
            composeTestRule
                .onNodeWithTag(BALANCE_BAR_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val textBounds =
            composeTestRule
                .onNodeWithText("${targetString(R.string.balance_bar_label)} 100,00 RUB", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "balance bar must leave room for a glyph to the left of the amount",
            textBounds.left - barBounds.left > 1f,
        )
        assertTrue(
            "balance bar must leave room for a glyph to the right of the amount",
            barBounds.right - textBounds.right > 1f,
        )
    }

    @Test
    fun `positive balance tints the amount with the theme primary colour`() {
        var expectedTint = 0
        composeTestRule.setContent {
            MyMoneyTheme {
                expectedTint = MaterialTheme.colorScheme.primary.toArgb()
                BalanceBar(amount = "100,00 RUB", isPositive = true)
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "positive amount must be drawn with the theme primary colour",
            amountImageContainsColor(expectedTint),
        )
    }

    @Test
    fun `balance bar pill uses primary colour regardless of the sign of the amount`() {
        // isPositive is accepted for API compatibility but the pill colour no longer changes
        // based on sign — the bar always uses the theme primary colour as the pill background.
        var primaryTint = 0
        composeTestRule.setContent {
            MyMoneyTheme {
                primaryTint = MaterialTheme.colorScheme.primary.toArgb()
                BalanceBar(amount = "-100,00 RUB", isPositive = false)
            }
        }
        composeTestRule.waitForIdle()

        assertTrue(
            "balance bar pill must use the theme primary colour even for a negative amount",
            amountImageContainsColor(primaryTint),
        )
    }

    @Test
    fun `bar row exposes open records click action label for TalkBack`() {
        val expectedLabel = targetString(R.string.balance_bar_open_records_action)

        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(
                    amount = "100,00 RUB",
                    isPositive = true,
                    onClick = {},
                    modifier = Modifier.testTag(BALANCE_BAR_TAG),
                )
            }
        }

        val node =
            composeTestRule
                .onNodeWithTag(BALANCE_BAR_TAG)
                .fetchSemanticsNode()
        val onClickAction = node.config.getOrElseNullable(SemanticsActions.OnClick) { null }
        assertNotNull("balance bar row must have a click action", onClickAction)
        assertEquals(
            "click action label must match balance_bar_open_records_action string resource",
            expectedLabel,
            onClickAction!!.label,
        )
    }

    @Test
    fun `bar row exposes a merged content description combining label and amount`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(
                    amount = "99,00 RUB",
                    isPositive = true,
                    modifier = Modifier.testTag(BALANCE_BAR_TAG),
                )
            }
        }

        val node =
            composeTestRule
                .onNodeWithTag(BALANCE_BAR_TAG)
                .fetchSemanticsNode()
        val cd = node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
        assertNotNull("balance bar row must expose a content description", cd)
        val desc = cd!!.firstOrNull()
        assertNotNull("content description list must not be empty", desc)
        assertTrue(
            "content description must contain the amount",
            desc!!.contains("99,00 RUB"),
        )
    }

    @Test
    fun `flank icons are marked decorative and do not appear as separate a11y nodes`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                BalanceBar(
                    amount = "100,00 RUB",
                    isPositive = true,
                    modifier = Modifier.testTag(BALANCE_BAR_TAG),
                )
            }
        }

        // Icons have contentDescription = null; within the merged semantics tree they must
        // not produce child nodes with their own content descriptions.
        val node =
            composeTestRule
                .onNodeWithTag(BALANCE_BAR_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
        val childDescriptions =
            node.children
                .flatMap { child ->
                    child.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }.orEmpty()
                }
        assertTrue(
            "decorative flank icons must not contribute their own content descriptions",
            childDescriptions.none { it.isNotEmpty() && it != "${targetString(R.string.balance_bar_label)} 100,00 RUB" },
        )
    }

    private fun amountImageContainsColor(argb: Int): Boolean {
        val image =
            composeTestRule
                .onNode(hasText(targetString(R.string.balance_bar_label), substring = true))
                .captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        return pixels.any { colorsMatch(it, argb) }
    }

    private fun colorsMatch(
        a: Int,
        b: Int,
    ): Boolean {
        fun ch(
            v: Int,
            shift: Int,
        ) = (v shr shift) and 0xFF
        val tolerance = 12
        return kotlin.math.abs(ch(a, 16) - ch(b, 16)) <= tolerance &&
            kotlin.math.abs(ch(a, 8) - ch(b, 8)) <= tolerance &&
            kotlin.math.abs(ch(a, 0) - ch(b, 0)) <= tolerance
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val BALANCE_BAR_TAG = "dashboard_balance_bar"
    }
}
