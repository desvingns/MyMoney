package com.kshavrin.mymoney.feature.support.paywall

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.support.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaywallScreenContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `support and shared sync entry points keep one catalog but change title and intro`() {
        val entryPoint = mutableStateOf(PaywallEntryPoint.SupportSection)
        composeTestRule.setContent {
            MyMoneyTheme {
                PaywallScreen(
                    entryPoint = entryPoint.value,
                    state = availableState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.paywall_title_support)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_intro_support)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_monthly_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_yearly_title)).performScrollTo().assertIsDisplayed()

        composeTestRule.runOnIdle { entryPoint.value = PaywallEntryPoint.SharedSyncGate }

        composeTestRule.onNodeWithText(string(R.string.paywall_title_shared_sync)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_intro_shared_sync)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_title_support)).assertCountEquals(0)
    }

    @Test
    fun `available catalog shows prices and yearly trial and emits selected plan`() {
        val events = mutableListOf<PaywallEvent>()
        setContent(state = availableState(), onEvent = events::add)

        composeTestRule.onAllNodesWithText(string(R.string.paywall_monthly_fallback_price)).assertCountEquals(0)
        composeTestRule.onNodeWithText("€2.49 / month").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("€19.99 / year").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_yearly_trial)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_select_plan)).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(string(R.string.paywall_select_plan)).get(0).performClick()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_select_plan))
            .get(1)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    PaywallEvent.PlanSelected(PaywallPlanId.Monthly),
                    PaywallEvent.PlanSelected(PaywallPlanId.Yearly),
                ),
                events,
            )
        }
    }

    @Test
    fun `loading catalog explains that prices are being loaded`() {
        setContent(state = PaywallState())

        composeTestRule.onNodeWithText(string(R.string.paywall_prices_loading)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_monthly_title)).assertCountEquals(0)
    }

    @Test
    fun `region unavailable explains the limitation without purchase actions`() {
        setContent(
            state =
                availableState().copy(
                    catalogState = PaywallCatalogState.UnavailableInRegion,
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_region_unavailable)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_select_plan)).assertCountEquals(0)
    }

    @Test
    fun `catalog error exposes retry event and fallback prices`() {
        val events = mutableListOf<PaywallEvent>()
        setContent(
            state =
                PaywallState(
                    catalogState = PaywallCatalogState.Error,
                    plans = PaywallPlanId.entries.map { PaywallPlan(it) },
                ),
            onEvent = events::add,
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_prices_error)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.paywall_retry)).performScrollTo().assertIsEnabled().performClick()
        composeTestRule.onNodeWithText(string(R.string.paywall_monthly_fallback_price)).performScrollTo().assertIsDisplayed()

        composeTestRule.runOnIdle {
            assertEquals(listOf(PaywallEvent.RetryClicked), events)
        }
    }

    @Test
    fun `purchase processing hides plan actions and shows progress copy`() {
        setContent(
            state =
                availableState().copy(
                    purchaseState = PaywallPurchaseState.InProgress,
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_purchase_processing)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_select_plan)).assertCountEquals(0)
    }

    @Test
    fun `active subscription shows renewal status instead of plans`() {
        val expiresAt = Instant.parse("2026-09-14T00:00:00Z")
        setContent(
            state =
                availableState().copy(
                    entitlement = subscriptionEntitlement(EntitlementState.ACTIVE, expiresAt = expiresAt),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_status_active)).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_renews_on, formatDate(expiresAt)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_monthly_title)).assertCountEquals(0)
    }

    @Test
    fun `grace entitlement uses graceEndsAt for access end date`() {
        val expiresAt = Instant.parse("2026-08-15T00:00:00Z")
        val graceEndsAt = Instant.parse("2026-08-22T00:00:00Z")
        setContent(
            state =
                availableState().copy(
                    entitlement =
                        subscriptionEntitlement(
                            state = EntitlementState.GRACE,
                            expiresAt = expiresAt,
                            graceEndsAt = graceEndsAt,
                        ),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_status_grace)).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.paywall_access_ends_on, formatDate(graceEndsAt)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(string(R.string.paywall_renews_on, formatDate(expiresAt)))
            .assertCountEquals(0)
    }

    @Test
    fun `reward and whitelist active sources use source-aware status copy`() {
        val entitlement =
            mutableStateOf(
                UserEntitlement.Plus(
                    source = EntitlementSource.AD_REWARD,
                    state = EntitlementState.ACTIVE,
                    startsAt = Instant.parse("2026-08-14T00:00:00Z"),
                    expiresAt = null,
                    graceEndsAt = null,
                ),
            )
        composeTestRule.setContent {
            MyMoneyTheme {
                PaywallScreen(
                    entryPoint = PaywallEntryPoint.SupportSection,
                    state = availableState().copy(entitlement = entitlement.value),
                    onEvent = {},
                )
            }
        }
        composeTestRule.onNodeWithText(string(R.string.paywall_status_reward_active)).performScrollTo().assertIsDisplayed()

        composeTestRule.runOnIdle {
            entitlement.value =
                UserEntitlement.Plus(
                    source = EntitlementSource.WHITELIST,
                    state = EntitlementState.ACTIVE,
                    startsAt = Instant.parse("2026-08-14T00:00:00Z"),
                    expiresAt = null,
                    graceEndsAt = null,
                )
        }
        composeTestRule.onNodeWithText(string(R.string.paywall_status_whitelist_active)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `local only entitlement shows device-only status and no plans`() {
        setContent(
            state =
                availableState().copy(
                    entitlement = subscriptionEntitlement(EntitlementState.LOCAL_ONLY, expiresAt = null),
                ),
        )

        composeTestRule.onNodeWithText(string(R.string.paywall_status_local_only)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.paywall_monthly_title)).assertCountEquals(0)
    }

    @Test
    fun `back icon emits back event`() {
        val events = mutableListOf<PaywallEvent>()
        setContent(state = PaywallState(), onEvent = events::add)

        composeTestRule.onNodeWithContentDescription(string(R.string.paywall_back)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(PaywallEvent.BackClicked), events)
        }
    }

    @Test
    fun `support entry emits open paywall callback`() {
        var opened = false
        composeTestRule.setContent {
            MyMoneyTheme {
                PaywallSupportEntry(onOpenPaywall = { opened = true })
            }
        }

        composeTestRule.onNodeWithText(string(R.string.paywall_support_entry_action)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, opened)
        }
    }

    private fun availableState() =
        PaywallState(
            catalogState = PaywallCatalogState.Available,
            plans =
                listOf(
                    PaywallPlan(PaywallPlanId.Monthly, "€2.49 / month"),
                    PaywallPlan(PaywallPlanId.Yearly, "€19.99 / year"),
                ),
        )

    private fun subscriptionEntitlement(
        state: EntitlementState,
        expiresAt: Instant?,
        graceEndsAt: Instant? = null,
    ) =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = state,
            startsAt = Instant.parse("2026-08-14T00:00:00Z"),
            expiresAt = expiresAt,
            graceEndsAt = graceEndsAt,
        )

    private fun setContent(
        entryPoint: PaywallEntryPoint = PaywallEntryPoint.SupportSection,
        state: PaywallState,
        onEvent: (PaywallEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                PaywallScreen(
                    entryPoint = entryPoint,
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }

    private fun formatDate(instant: Instant): String =
        instant
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

    private fun string(
        resourceId: Int,
        vararg args: Any,
    ): String = context.getString(resourceId, *args)
}
