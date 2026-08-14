package com.kshavrin.mymoney.feature.support

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `loading state shows introduction and keeps ad and plus slots empty`() {
        setContent(state = SupportState())

        composeTestRule.onNodeWithText(string(R.string.support_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_description)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_coffee_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
    }

    @Test
    fun `ad and plus slots render between the introduction and purchase section`() {
        setContent(
            state = availableState(),
            adSlot = { androidx.compose.material3.Text("ad-slot") },
            plusSlot = { androidx.compose.material3.Text("plus-slot") },
        )

        composeTestRule.onNodeWithText("ad-slot").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("plus-slot").performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_title))
            .performScrollTo()
            .assertIsDisplayed()

        val orderedNodes =
            listOf(
                string(R.string.support_description),
                "ad-slot",
                "plus-slot",
                string(R.string.support_coffee_title),
            ).map { text ->
                composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
            }
        assertTrue(orderedNodes.zipWithNext().all { (first, second) -> first < second })
    }

    @Test
    fun `available coffee buttons use localized formatted prices and emit product ids`() {
        val events = mutableListOf<SupportEvent>()
        val state = availableState()
        setContent(state = state, onEvent = events::add)

        val smallLabel = string(R.string.support_coffee_small, "£1.99")
        val largeLabel = string(R.string.support_coffee_large, "£4.99")
        composeTestRule.onNodeWithText(smallLabel).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithText(largeLabel).performScrollTo().assertIsDisplayed().performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    SupportEvent.PurchaseClicked(COFFEE_SMALL_PRODUCT_ID),
                    SupportEvent.PurchaseClicked(COFFEE_LARGE_PRODUCT_ID),
                ),
                events,
            )
        }
    }

    @Test
    fun `purchase buttons stay at least 48dp and become disabled while purchase is active`() {
        val state = availableState().copy(isPurchaseInProgress = true)
        setContent(state = state)

        val smallButton =
            composeTestRule
                .onNodeWithText(string(R.string.support_coffee_small, "£1.99"))
                .performScrollTo()
        smallButton.assertIsNotEnabled()
        assertTrue(
            smallButton.fetchSemanticsNode().size.height >=
                with(composeTestRule.density) { 48.dp.roundToPx() },
        )
    }

    @Test
    fun `disabled build message is distinct and has no retry action`() {
        val supporterState = SupporterState(badgeEarned = true, purchaseCount = 3)
        setContent(
            state =
                SupportState(
                    billingState =
                        SupportBillingState.Unavailable(SupportUnavailableReason.DisabledInBuild),
                    supporterState = supporterState,
                ),
        )
        composeTestRule
            .onNodeWithText(string(R.string.support_unavailable_build))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_unavailable_region)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_badge)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_gratitude_count, 3)).assertIsDisplayed()
    }

    @Test
    fun `region message is distinct and has no retry action`() {
        val supporterState = SupporterState(badgeEarned = true, purchaseCount = 3)
        setContent(
            state =
                SupportState(
                    billingState =
                        SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableInRegion),
                    supporterState = supporterState,
                ),
        )
        composeTestRule
            .onNodeWithText(string(R.string.support_unavailable_region))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_unavailable_build)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_badge)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_gratitude_count, 3)).assertIsDisplayed()
    }

    @Test
    fun `pending state explains processing without an error or retry button`() {
        setContent(state = SupportState(billingState = SupportBillingState.Pending))

        composeTestRule.onNodeWithText(string(R.string.support_pending)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_network_error)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
    }

    @Test
    fun `network error exposes the retry event`() {
        val events = mutableListOf<SupportEvent>()
        setContent(
            state = SupportState(billingState = SupportBillingState.NetworkError),
            onEvent = events::add,
        )

        composeTestRule.onNodeWithText(string(R.string.support_network_error)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).performScrollTo().assertIsEnabled().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SupportEvent.RetryClicked), events)
        }
    }

    @Test
    fun `supporter badge and gratitude count render only for supporter state`() {
        setContent(
            state =
                availableState().copy(
                    supporterState = SupporterState(badgeEarned = true, purchaseCount = 3),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.support_badge)).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_gratitude))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_gratitude_count, 3))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `back icon emits back event`() {
        val events = mutableListOf<SupportEvent>()
        setContent(state = SupportState(), onEvent = events::add)

        composeTestRule.onNodeWithContentDescription(string(R.string.support_back)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SupportEvent.BackClicked), events)
        }
    }

    private fun availableState() =
        SupportState(
            billingState = SupportBillingState.Available,
            products =
                listOf(
                    SupportProduct(COFFEE_SMALL_PRODUCT_ID, "£1.99", "Small"),
                    SupportProduct(COFFEE_LARGE_PRODUCT_ID, "£4.99", "Large"),
                ),
        )

    private fun setContent(
        state: SupportState,
        onEvent: (SupportEvent) -> Unit = {},
        adSlot: @androidx.compose.runtime.Composable () -> Unit = {},
        plusSlot: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportScreen(
                    state = state,
                    onEvent = onEvent,
                    adSlot = adSlot,
                    plusSlot = plusSlot,
                )
            }
        }
    }

    private fun string(
        resourceId: Int,
        vararg args: Any,
    ): String = context.getString(resourceId, *args)
}
