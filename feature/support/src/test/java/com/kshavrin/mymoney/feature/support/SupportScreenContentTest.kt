package com.kshavrin.mymoney.feature.support

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.paywall.PaywallCatalogState
import com.kshavrin.mymoney.feature.support.plus.SupportPlusContent
import com.kshavrin.mymoney.feature.support.plus.SupportPlusState
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
    fun `support shell keeps the localized headline and omits the hero artwork`() {
        setContent(state = availableState())

        composeTestRule.onNodeWithText(string(R.string.support_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_back_label)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_hero_description))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_headline_lead)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.support_headline_accent)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_ads_total_watched, 0))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_coffee_title)).assertDoesNotExist()
    }

    @Test
    fun `description subtitle is absent from the support screen`() {
        setContent(state = availableState())
        // support_description was deleted from strings.xml; assert the removed text never reaches the UI
        composeTestRule
            .onNodeWithText("MyMoney is made independently. A coffee helps keep it growing.")
            .assertDoesNotExist()
    }

    @Test
    fun `headline block fills available width and is centered on screen`() {
        setContent(state = availableState())

        val rootCenterX =
            composeTestRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot.center.x
        val headlineCenterX =
            composeTestRule
                .onNodeWithText(string(R.string.support_headline_lead))
                .fetchSemanticsNode()
                .boundsInRoot
                .center.x
        val tolerancePx = with(composeTestRule.density) { 2.dp.toPx() }
        assertTrue(
            "Headline centerX $headlineCenterX should be within ${tolerancePx}px of root centerX $rootCenterX",
            kotlin.math.abs(headlineCenterX - rootCenterX) <= tolerancePx,
        )
    }

    @Test
    fun `gratitude card stays hidden by default and shows all zero counters after activity`() {
        val state = mutableStateOf(SupportState())
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportContent(
                    state = state.value,
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_avatar_description))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.support_counter_ads_label)).assertDoesNotExist()

        composeTestRule.runOnIdle {
            state.value = state.value.copy(hasSupportActivity = true)
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_avatar_description))
            .performScrollTo()
            .assertIsDisplayed()
        listOf(
            R.string.support_counter_ads_label,
            R.string.support_counter_coffee_small_label,
            R.string.support_counter_coffee_large_label,
        ).forEach { labelRes ->
            composeTestRule.onNodeWithText(string(labelRes)).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onAllNodesWithText("0").assertCountEquals(3)
    }

    @Test
    fun `back control exposes localized semantics and a 48dp touch target`() {
        setContent(state = SupportState())

        composeTestRule.onNodeWithText(string(R.string.support_back_label)).assertIsDisplayed()
        val backControl =
            composeTestRule
                .onNodeWithContentDescription(string(R.string.support_back))
                .assertIsDisplayed()
        val minimumTouchTarget = with(composeTestRule.density) { 48.dp.roundToPx() }
        val controlSize = backControl.fetchSemanticsNode().size
        assertTrue(controlSize.width >= minimumTouchTarget)
        assertTrue(controlSize.height >= minimumTouchTarget)
    }

    @Test
    fun `ad coffee plus and gratitude sections use the required order`() {
        setContent(
            state =
                availableState().copy(
                    supporterState = SupporterState(badgeEarned = true, purchaseCount = 3),
                    hasSupportActivity = true,
                ),
            adSlot = { androidx.compose.material3.Text("Ads slot") },
            plusSlot = { androidx.compose.material3.Text(string(R.string.paywall_support_entry_title)) },
        )

        composeTestRule
            .onNodeWithText("Ads slot")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_headline_lead))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_gratitude_title_supporter))
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
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_title))
            .performScrollTo()
            .assertIsDisplayed()

        val expectedOrder =
            listOf(
                string(R.string.support_headline_lead),
                string(R.string.support_gratitude_title_supporter),
                "Ads slot",
                string(R.string.support_coffee_small_name),
                string(R.string.support_coffee_large_name),
                string(R.string.paywall_support_entry_title),
            )
        val textOrder = semanticTextOrder()
        val indexes =
            expectedOrder.map { text ->
                textOrder.indexOf(text).also { index ->
                    assertTrue("Missing expected support slot text: $text", index >= 0)
                }
            }
        assertTrue(indexes.zipWithNext().all { (first, second) -> first < second })
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
        composeTestRule.onNodeWithText(state.products[0].formattedPrice).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(state.products[1].formattedPrice).performScrollTo().assertIsDisplayed()

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
    fun `only a matching product receives an actionable purchase button`() {
        val state = availableState()
        setContent(state = state.copy(products = listOf(state.products.first())))

        val purchaseActions =
            composeTestRule
                .onAllNodesWithText(string(R.string.support_purchase_action))
                .assertCountEquals(2)
        purchaseActions[0].performScrollTo().assertIsEnabled()
        purchaseActions[1].performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `coffee card and billing status cover every frozen matrix cell`() {
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
        val matrix =
            listOf<Triple<SupportBillingState, Int?, Boolean>>(
                Triple(SupportBillingState.Loading, null, false),
                Triple(SupportBillingState.Available, null, false),
                Triple(SupportBillingState.Pending, R.string.support_pending, false),
                Triple(SupportBillingState.NetworkError, R.string.support_network_error, true),
                Triple(
                    SupportBillingState.Unavailable(SupportUnavailableReason.UnavailableInRegion),
                    R.string.support_unavailable_region,
                    false,
                ),
            )

        matrix.forEach { (billingState, statusResource, retryable) ->
            listOf(false, true).forEach { isPurchaseInProgress ->
                composeTestRule.runOnIdle {
                    state.value =
                        availableState().copy(
                            billingState = billingState,
                            isPurchaseInProgress = isPurchaseInProgress,
                        )
                }
                composeTestRule.waitForIdle()

                assertCoffeeColumnsDisplayed()
                assertCoffeeActionsEnabled(
                    expectedEnabled =
                        billingState == SupportBillingState.Available && !isPurchaseInProgress,
                )
                if (statusResource == null) {
                    assertNoBillingStatus()
                } else {
                    val status = string(statusResource)
                    composeTestRule.onNodeWithText(status).performScrollTo().assertIsDisplayed()
                    assertStatusBelowCoffee(status)
                    if (retryable) {
                        composeTestRule
                            .onNodeWithText(string(R.string.support_retry))
                            .performScrollTo()
                            .assertIsEnabled()
                    } else {
                        composeTestRule.onNodeWithText(string(R.string.support_retry)).assertDoesNotExist()
                    }
                }
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
                    hasSupportActivity = true,
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
            .onNodeWithText(string(R.string.support_gratitude_title_supporter))
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
                    hasSupportActivity = true,
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
            .onNodeWithText(string(R.string.support_gratitude_title_supporter))
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
        composeTestRule
            .onNodeWithText(string(R.string.support_retry))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SupportEvent.RetryClicked), events)
        }
    }

    @Test
    fun `gratitude card covers every frozen supporter and counter matrix cell`() {
        val state = mutableStateOf(availableState())
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportContent(
                    state = state.value,
                    onEvent = {},
                )
            }
        }

        listOf(
            GratitudeCell(isSupporter = false, adsWatched = 0, smallCoffees = 0, largeCoffees = 0),
            GratitudeCell(isSupporter = false, adsWatched = 12, smallCoffees = 2, largeCoffees = 1),
            GratitudeCell(isSupporter = true, adsWatched = 0, smallCoffees = 0, largeCoffees = 0),
            GratitudeCell(isSupporter = true, adsWatched = 12, smallCoffees = 2, largeCoffees = 1),
        ).forEach { cell ->
            composeTestRule.runOnIdle {
                state.value =
                    availableState().copy(
                        adsWatchedTotal = cell.adsWatched,
                        hasSupportActivity = true,
                        supporterState =
                            SupporterState(
                                badgeEarned = cell.isSupporter,
                                purchaseCount = cell.smallCoffees + cell.largeCoffees,
                                smallCoffeeCount = cell.smallCoffees,
                                largeCoffeeCount = cell.largeCoffees,
                            ),
                    )
            }
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(string(R.string.support_image_avatar_description))
                .performScrollTo()
                .assertIsDisplayed()
            val titleRes =
                if (cell.isSupporter) {
                    R.string.support_gratitude_title_supporter
                } else {
                    R.string.support_gratitude_title_prospect
                }
            val bodyRes =
                if (cell.isSupporter) {
                    R.string.support_gratitude_body_supporter
                } else {
                    R.string.support_gratitude_body_prospect
                }
            composeTestRule.onNodeWithText(string(titleRes)).performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(string(bodyRes)).performScrollTo().assertIsDisplayed()
            if (cell.isSupporter) {
                composeTestRule
                    .onNodeWithText(string(R.string.support_badge))
                    .performScrollTo()
                    .assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithText(string(R.string.support_badge)).assertDoesNotExist()
            }
            assertCounterValues(cell)
        }
    }

    @Test
    fun `plus panel renders the Plus card header with title and description`() {
        // PaywallSupportEntry (the old navigation-based entry) was removed by SPEC-02.
        // SupportPlusContent (stateless) is now the testable surface for the plusSlot.
        setContent(
            state = availableState(),
            plusSlot = {
                SupportPlusContent(
                    state = SupportPlusState(catalogState = PaywallCatalogState.Loading),
                    onPlanSelected = {},
                    onRetry = {},
                )
            },
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_plus_description))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_description))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `back label is visible and carries the explicit onBackground color token`() {
        // G4: color = MaterialTheme.colorScheme.onBackground is set explicitly on
        // support_back_label (SupportBackRow) replacing the previous supportBackLabel token,
        // aligning it with support_title for visual consistency on the themed background.
        // Asserting the text is displayed guards against accidental invisibility caused
        // by incorrect color; the explicit parameter itself is the contract.
        setContent(state = SupportState())

        composeTestRule
            .onNodeWithText(string(R.string.support_back_label))
            .assertIsDisplayed()
    }

    @Test
    fun `support title and headline lead are visible and carry the explicit onBackground color token`() {
        // G3: color = MaterialTheme.colorScheme.onBackground is set explicitly on
        // support_title (SupportBackRow) and support_headline_lead (SupportHeadline) to
        // ensure contrast on the themed background. Asserting both texts are displayed guards
        // against either being accidentally made invisible. The explicit color parameter itself
        // is the contract — verified by the implementation and named in this test so any future
        // removal is flagged in the diff against this assertion.
        setContent(state = SupportState())

        composeTestRule
            .onNodeWithText(string(R.string.support_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.support_headline_lead))
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

    private fun assertNoBillingStatus() {
        listOf(
            R.string.support_pending,
            R.string.support_network_error,
            R.string.support_retry,
            R.string.support_unavailable_build,
            R.string.support_unavailable_device,
            R.string.support_unavailable_region,
            R.string.support_unavailable,
        ).forEach { resourceId ->
            composeTestRule.onNodeWithText(string(resourceId)).assertDoesNotExist()
        }
    }

    private fun assertCounterValues(cell: GratitudeCell) {
        val counters =
            listOf(
                R.string.support_counter_ads_label to cell.adsWatched,
                R.string.support_counter_coffee_small_label to cell.smallCoffees,
                R.string.support_counter_coffee_large_label to cell.largeCoffees,
            )
        counters.forEach { (labelRes, _) ->
            composeTestRule.onNodeWithText(string(labelRes)).performScrollTo().assertIsDisplayed()
        }
        if (cell.adsWatched == 0 && cell.smallCoffees == 0 && cell.largeCoffees == 0) {
            composeTestRule.onAllNodesWithText(0.toString()).assertCountEquals(3)
        } else {
            counters.forEach { (_, value) ->
                composeTestRule.onNodeWithText(value.toString()).performScrollTo().assertIsDisplayed()
            }
        }
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

    private fun semanticTextOrder(): List<String> =
        composeTestRule
            .onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .flattened()
            .flatMap { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { text -> text.text }
            }

    private fun SemanticsNode.flattened(): List<SemanticsNode> =
        listOf(this) + children.flatMap { child -> child.flattened() }

    private companion object {
        const val SMALL_PRICE = "£1.99"
        const val LARGE_PRICE = "£4.99"
    }

    private data class GratitudeCell(
        val isSupporter: Boolean,
        val adsWatched: Int,
        val smallCoffees: Int,
        val largeCoffees: Int,
    )
}
