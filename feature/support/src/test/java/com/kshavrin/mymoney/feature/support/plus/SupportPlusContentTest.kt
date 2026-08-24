package com.kshavrin.mymoney.feature.support.plus

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.R
import com.kshavrin.mymoney.feature.support.paywall.PaywallCatalogState
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlan
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlanId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportPlusContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun loadingState() = SupportPlusState(catalogState = PaywallCatalogState.Loading)

    private fun availableState() =
        SupportPlusState(
            catalogState = PaywallCatalogState.Available,
            plans =
                listOf(
                    PaywallPlan(PaywallPlanId.Monthly, "€2.49 / month"),
                    PaywallPlan(PaywallPlanId.Yearly, "€19.99 / year"),
                ),
        )

    private fun activeEntitlement(): UserEntitlement.Plus =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = EntitlementState.ACTIVE,
            startsAt = Instant.parse("2026-08-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            graceEndsAt = null,
        )

    private fun setContent(
        state: SupportPlusState,
        onPlanSelected: (PaywallPlanId) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                SupportPlusContent(
                    state = state,
                    onPlanSelected = onPlanSelected,
                    onRetry = onRetry,
                )
            }
        }
    }

    // ─── Card header — always visible ────────────────────────────────────────────

    @Test
    fun `card always shows Plus illustration title and description`() {
        setContent(state = loadingState())

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_image_plus_description))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_support_entry_description))
            .assertIsDisplayed()
    }

    // ─── Info tooltip icon ───────────────────────────────────────────────────────

    @Test
    fun `info icon is present with the support_plus_info_tooltip content description`() {
        // The info "i" icon in the top-right corner uses the tooltip text as its contentDescription,
        // satisfying a11y and providing a stable test anchor per the SPEC.
        setContent(state = loadingState())

        composeTestRule
            .onNodeWithContentDescription(string(R.string.support_plus_info_tooltip))
            .assertIsDisplayed()
    }

    // ─── Loading catalog ─────────────────────────────────────────────────────────

    @Test
    fun `loading catalog shows prices loading text and hides plan column titles`() {
        setContent(state = loadingState())

        composeTestRule
            .onNodeWithText(string(R.string.paywall_prices_loading))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_monthly_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_yearly_title))
            .assertCountEquals(0)
    }

    // ─── Available catalog — not entitled ────────────────────────────────────────

    @Test
    fun `available catalog renders Monthly and Yearly columns with prices and two select buttons`() {
        setContent(state = availableState())

        composeTestRule
            .onNodeWithText(string(R.string.paywall_monthly_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_yearly_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("€2.49 / month")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("€19.99 / year")
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .assertCountEquals(2)
    }

    @Test
    fun `select plan button at index 0 emits Monthly and index 1 emits Yearly`() {
        val selected = mutableListOf<PaywallPlanId>()
        setContent(state = availableState(), onPlanSelected = { selected += it })

        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .get(0)
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(PaywallPlanId.Monthly), selected)
        }

        selected.clear()

        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .get(1)
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(PaywallPlanId.Yearly), selected)
        }
    }

    // ─── Entitled — PlusStatusCard ───────────────────────────────────────────────

    @Test
    fun `active subscription shows PlusStatusCard status text instead of plan columns`() {
        setContent(
            state =
                SupportPlusState(
                    catalogState = PaywallCatalogState.Available,
                    plans =
                        listOf(
                            PaywallPlan(PaywallPlanId.Monthly, "€2.49 / month"),
                            PaywallPlan(PaywallPlanId.Yearly, "€19.99 / year"),
                        ),
                    entitlement = activeEntitlement(),
                ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.paywall_status_active))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_monthly_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .assertCountEquals(0)
    }

    @Test
    fun `entitled state shows no purchase buttons regardless of catalog state`() {
        setContent(
            state =
                SupportPlusState(
                    catalogState = PaywallCatalogState.Available,
                    entitlement = activeEntitlement(),
                ),
        )

        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .assertCountEquals(0)
    }

    // ─── Error catalog ───────────────────────────────────────────────────────────

    @Test
    fun `catalog error shows error message and an enabled retry button`() {
        var retryCalls = 0
        setContent(
            state =
                SupportPlusState(
                    catalogState = PaywallCatalogState.Error,
                    plans =
                        listOf(
                            PaywallPlan(PaywallPlanId.Monthly),
                            PaywallPlan(PaywallPlanId.Yearly),
                        ),
                ),
            onRetry = { retryCalls++ },
        )

        composeTestRule
            .onNodeWithText(string(R.string.paywall_prices_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_retry))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, retryCalls)
        }
    }

    // ─── UnavailableInRegion catalog ─────────────────────────────────────────────

    @Test
    fun `region unavailable catalog explains the limitation without select buttons`() {
        setContent(
            state =
                SupportPlusState(
                    catalogState = PaywallCatalogState.UnavailableInRegion,
                    plans =
                        listOf(
                            PaywallPlan(PaywallPlanId.Monthly, "€2.49 / month"),
                            PaywallPlan(PaywallPlanId.Yearly, "€19.99 / year"),
                        ),
                ),
        )

        composeTestRule
            .onNodeWithText(string(R.string.paywall_region_unavailable))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .assertCountEquals(0)
    }
}
