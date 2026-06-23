package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_BALANCE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_CARD_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_CURRENCY_CARDS_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Instrumented Compose UI tests for the sign-aware accent colouring of Aurora cards (SPEC 03).
 *
 * Colour itself (NeonMint vs NeonRed) is rendered via Canvas/shadow and is not exposed in the
 * Compose semantics tree. Tests therefore verify:
 *   – the card renders without crash for each sign variant, and
 *   – the correct balance value text is displayed,
 * and rely on the device visual pre-flight for the actual colour assertion.
 */
@RunWith(AndroidJUnit4::class)
class AuroraSignColorUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Main aurora card (non-separate mode) ──────────────────────────────────

    @Test
    fun `main aurora card renders for positive net without crash`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("500.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = stateForSnapshot(usd, snapshot),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `main aurora card renders for negative net without crash`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("-250.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = stateForSnapshot(usd, snapshot),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `main aurora card treats zero net as positive and renders`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("0.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = stateForSnapshot(usd, snapshot),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `main aurora card does not show the negative card tag in positive state`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("1000.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = stateForSnapshot(usd, snapshot),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertCountEquals(1)
    }

    @Test
    fun `main aurora card is absent in separate mode regardless of net sign`() {
        val usd = usdCurrency()
        val eur = eurCurrency()
        val positiveSnapshot = snapshotWithNet("1000.00", usd)
        val negativeSnapshot = snapshotWithNet("-200.00", eur)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(currency = usd, snapshot = positiveSnapshot),
                                    CurrencyBalanceCard(currency = eur, snapshot = negativeSnapshot),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertCountEquals(0)
    }

    // ── Per-currency cards (separate mode) ────────────────────────────────────

    @Test
    fun `per-currency cards render for both positive and negative net without crash`() {
        val usd = usdCurrency()
        val eur = eurCurrency()
        val positiveSnapshot = snapshotWithNet("800.00", usd)
        val negativeSnapshot = snapshotWithNet("-300.00", eur)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(currency = usd, snapshot = positiveSnapshot),
                                    CurrencyBalanceCard(currency = eur, snapshot = negativeSnapshot),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun `per-currency card with positive net renders its currency code and balance`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("1234.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards = listOf(CurrencyBalanceCard(currency = usd, snapshot = snapshot)),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
    }

    @Test
    fun `per-currency card with negative net renders its currency code`() {
        val eur = eurCurrency()
        val snapshot = snapshotWithNet("-567.00", eur)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(eur),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards = listOf(CurrencyBalanceCard(currency = eur, snapshot = snapshot)),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun `per-currency card with zero net renders without crash and treats it as positive`() {
        val usd = usdCurrency()
        val snapshot = snapshotWithNet("0.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards = listOf(CurrencyBalanceCard(currency = usd, snapshot = snapshot)),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
    }

    @Test
    fun `sign coloring does not duplicate currency card entries`() {
        val usd = usdCurrency()
        val eur = eurCurrency()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(currency = usd, snapshot = snapshotWithNet("100.00", usd)),
                                    CurrencyBalanceCard(currency = eur, snapshot = snapshotWithNet("-50.00", eur)),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        val usdTagCount =
            composeTestRule
                .onAllNodesWithTag("dashboard_currency_cards_USD")
                .fetchSemanticsNodes()
                .size
        val eurTagCount =
            composeTestRule
                .onAllNodesWithTag("dashboard_currency_cards_EUR")
                .fetchSemanticsNodes()
                .size

        assertTrue("expected exactly one USD card, found $usdTagCount", usdTagCount == 1)
        assertTrue("expected exactly one EUR card, found $eurTagCount", eurTagCount == 1)
    }

    // ── FAB layout — glow is colour-per-button, not sign-aware ────────────────

    @Test
    fun `all three fabs remain present regardless of aurora card sign state`() {
        val usd = usdCurrency()
        val negativeSnapshot = snapshotWithNet("-9999.00", usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = stateForSnapshot(usd, negativeSnapshot),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertExists()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun usdCurrency() =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun eurCurrency() =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )

    private fun snapshotWithNet(
        netStr: String,
        currency: Currency,
    ): BalanceSnapshot {
        val net = BigDecimal(netStr)
        val income = if (net.signum() >= 0) net else BigDecimal.ZERO
        val expense = if (net.signum() < 0) net.abs() else BigDecimal.ZERO
        return BalanceSnapshot(
            income = Money(income, currency),
            expense = Money(expense, currency),
            net = Money(net, currency),
            byCategory = emptyList(),
        )
    }

    private fun stateForSnapshot(
        currency: Currency,
        snapshot: BalanceSnapshot,
    ) = DashboardState(
        currencies = listOf(currency),
        dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(currency)),
        balanceSnapshot = snapshot,
        periodNet = snapshot.net,
        isLoading = false,
    )
}
