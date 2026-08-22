package com.kshavrin.mymoney.feature.support

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    fun `support shell displays back row hero and localized headline`() {
        setContent(state = availableState())

        composeTestRule.onNodeWithText(string(R.string.support_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_back_label)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_hero_description))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_headline_lead)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_headline_accent)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_description))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_total_watched, 0))
            .assertDoesNotExist()
    }

    @Test
    fun `ad coffee plus and gratitude sections use the required order`() {
        setContent(
            state =
                availableState().copy(
                    supporterState = SupporterState(badgeEarned = true, purchaseCount = 3),
                ),
            adSlot = { androidx.compose.material3.Text(string(R.string.support_ads_title)) },
            plusSlot = { androidx.compose.material3.Text(string(R.string.paywall_support_entry_title)) },
        )

        composeTestRule
            .onNodeWithText(string(R.string.support_ads_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_small_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_gratitude)).performScrollTo().assertIsDisplayed()

        val orderedNodes =
            listOf(
                string(R.string.support_ads_title),
                string(R.string.support_coffee_small_name),
                string(R.string.paywall_support_entry_title),
                string(R.string.support_gratitude),
            ).map { text ->
                composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
            }
        assertTrue(orderedNodes.zipWithNext().all { (first, second) -> first < second })
    }

    @Test
    fun `loading state keeps coffee columns without a billing status row`() {
        setContent(
            state = availableState().copy(billingState = SupportBillingState.Loading),
        )

        assertCoffeeColumnsDisplayed()
        assertCoffeeActionsEnabled(expectedEnabled = false)
        composeTestRule.onNodeWithText(string(R.string.support_pending)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_network_error)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
    }

    @Test
    fun `available coffee columns use localized names formatted prices illustrations and actions`() {
        val events = mutableListOf<SupportEvent>()
        val state = availableState()
        setContent(state = state, onEvent = events::add)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_coffee_small_description))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_coffee_large_description))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_small_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_large_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(SMALL_PRICE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(LARGE_PRICE).performScrollTo().assertIsDisplayed()

        val purchaseActions =
            composeTestRule
                .onAllNodesWithText(string(R.string.support_purchase_action))
                .assertCountEquals(2)
        purchaseActions[0].performScrollTo().assertIsEnabled().performClick()
        purchaseActions[1].performScrollTo().assertIsEnabled().performClick()

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

        val purchaseActions =
            composeTestRule
                .onAllNodesWithText(string(R.string.support_purchase_action))
                .assertCountEquals(2)
        purchaseActions[0].performScrollTo().assertIsNotEnabled()
        purchaseActions[1].performScrollTo().assertIsNotEnabled()
        assertTrue(
            purchaseActions[0].fetchSemanticsNode().size.height >=
                with(composeTestRule.density) { 48.dp.roundToPx() },
        )
    }

    @Test
    fun `coffee card stays visible and CTAs follow billing availability across the frozen matrix`() {
        val state = mutableStateOf(availableState())
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportContent(
                    state = state.value,
                    onEvent = {},
                    adSlot = {},
                    plusSlot = {},
                )
            }
        }
        val billingStates =
            listOf<SupportBillingState>(
                SupportBillingState.Loading,
                SupportBillingState.Available,
                SupportBillingState.Pending,
                SupportBillingState.NetworkError,
                SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableInRegion),
            )

        billingStates.forEach { billingState ->
            listOf(false, true).forEach { isPurchaseInProgress ->
                composeTestRule.runOnIdle {
                    state.value =
                        availableState().copy(
                            billingState = billingState,
                            isPurchaseInProgress = isPurchaseInProgress,
                        )
                }

                assertCoffeeColumnsDisplayed()
                assertCoffeeActionsEnabled(
                    expectedEnabled =
                        billingState == SupportBillingState.Available && !isPurchaseInProgress,
                )
            }
        }
    }

    @Test
    fun `disabled build message is distinct and has no retry action`() {
        val supporterState = SupporterState(badgeEarned = true, purchaseCount = 3)
        setContent(
            state =
                availableState().copy(
                    billingState =
                        SupportBillingState.Unavailable(SupportUnavailableReason.DisabledInBuild),
                    supporterState = supporterState,
                ),
        )
        composeTestRule
            .onNodeWithText(string(R.string.support_unavailable_build))
            .performScrollTo()
            .assertIsDisplayed()
        assertCoffeeColumnsDisplayed()
        assertCoffeeActionsEnabled(expectedEnabled = false)
        assertStatusBelowCoffee(string(R.string.support_unavailable_build))
        composeTestRule.onNodeWithText(string(R.string.support_unavailable_region)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_badge)).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_gratitude_count, 3))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `region message is distinct and has no retry action`() {
        val supporterState = SupporterState(badgeEarned = true, purchaseCount = 3)
        setContent(
            state =
                availableState().copy(
                    billingState =
                        SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableInRegion),
                    supporterState = supporterState,
                ),
        )
        composeTestRule
            .onNodeWithText(string(R.string.support_unavailable_region))
            .performScrollTo()
            .assertIsDisplayed()
        assertCoffeeColumnsDisplayed()
        assertCoffeeActionsEnabled(expectedEnabled = false)
        assertStatusBelowCoffee(string(R.string.support_unavailable_region))
        composeTestRule.onNodeWithText(string(R.string.support_unavailable_build)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_badge)).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_gratitude_count, 3))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `pending state explains processing without an error or retry button`() {
        setContent(state = availableState().copy(billingState = SupportBillingState.Pending))

        composeTestRule.onNodeWithText(string(R.string.support_pending)).performScrollTo().assertIsDisplayed()
        assertCoffeeColumnsDisplayed()
        assertCoffeeActionsEnabled(expectedEnabled = false)
        assertStatusBelowCoffee(string(R.string.support_pending))
        composeTestRule.onNodeWithText(string(R.string.support_network_error)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
    }

    @Test
    fun `network error exposes the retry event`() {
        val events = mutableListOf<SupportEvent>()
        setContent(
            state = availableState().copy(billingState = SupportBillingState.NetworkError),
            onEvent = events::add,
        )

        composeTestRule.onNodeWithText(string(R.string.support_network_error)).performScrollTo().assertIsDisplayed()
        assertCoffeeColumnsDisplayed()
        assertCoffeeActionsEnabled(expectedEnabled = false)
        assertStatusBelowCoffee(string(R.string.support_network_error))
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
                    SupportProduct(
                        COFFEE_SMALL_PRODUCT_ID,
                        SMALL_PRICE,
                        string(R.string.support_coffee_small_name),
                    ),
                    SupportProduct(
                        COFFEE_LARGE_PRODUCT_ID,
                        LARGE_PRICE,
                        string(R.string.support_coffee_large_name),
                    ),
                ),
        )

    private fun assertCoffeeColumnsDisplayed() {
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_small_name))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_coffee_large_name))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun assertCoffeeActionsEnabled(expectedEnabled: Boolean) {
        val purchaseActions =
            composeTestRule
                .onAllNodesWithText(string(R.string.support_purchase_action))
                .assertCountEquals(2)
        repeat(2) { index ->
            val action = purchaseActions[index].performScrollTo()
            if (expectedEnabled) {
                action.assertIsEnabled()
            } else {
                action.assertIsNotEnabled()
            }
        }
    }

    private fun assertStatusBelowCoffee(status: String) {
        val statusNode = composeTestRule.onNodeWithText(status).performScrollTo()
        val coffeeTop =
            composeTestRule
                .onNodeWithText(string(R.string.support_coffee_small_name))
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        assertTrue(coffeeTop < statusNode.fetchSemanticsNode().boundsInRoot.top)
    }

    private fun setContent(
        state: SupportState,
        onEvent: (SupportEvent) -> Unit = {},
        adSlot: @androidx.compose.runtime.Composable () -> Unit = {},
        plusSlot: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportContent(
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

    private companion object {
        const val SMALL_PRICE = "£1.99"
        const val LARGE_PRICE = "£4.99"
    }
}
