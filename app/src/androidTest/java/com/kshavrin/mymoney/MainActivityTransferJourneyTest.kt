package com.kshavrin.mymoney

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import com.kshavrin.mymoney.feature.dashboard.R as DashboardR
import com.kshavrin.mymoney.feature.onboarding.R as OnboardingR
import com.kshavrin.mymoney.feature.transaction.R as TransactionR

/**
 * J2 — cross-currency transfer (AS-6, AS-7, TDD §4.8).
 *
 * Two accounts of different currencies with no stored rate: selecting the target auto-navigates to
 * S27, the rate is captured, the form returns to S03, and saving stores the transfer as a SINGLE
 * row carrying both legs (source + target accounts, amount + converted toAmount + exchangeRate).
 *
 * The second account is the precondition (the seeder makes only one), so it is inserted via the
 * repository AFTER the app has seeded — before navigating to the transfer form so the form's
 * ViewModel picks it up.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTransferJourneyTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var currencyRepository: CurrencyRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Test
    fun crossCurrencyTransferStoresSingleRowWithBothLegs() {
        hiltRule.inject()

        // Onboarding visible => the splash seeder has finished (currencies + Cash account exist).
        val skip = targetString(OnboardingR.string.onboarding_skip)
        waitForText(skip)

        // Precondition: a second account in a DIFFERENT currency than the seeded Cash account.
        val cash = runBlocking { accountRepository.observeActive().first().first() }
        val otherCurrency =
            runBlocking {
                currencyRepository.observeActive().first().first { it.id != cash.currencyId }
            }
        val now = Instant.now()
        runBlocking {
            accountRepository.upsert(
                Account(
                    id = 0L,
                    name = SAVINGS,
                    currencyId = otherCurrency.id,
                    initialBalance = BigDecimal.ZERO,
                    type = AccountType.Cash,
                    colorHex = "#4A8FCB",
                    iconKey = "ic_account_cash",
                    isDefault = false,
                    sortOrder = 1,
                    createdAt = now,
                    updatedAt = now,
                    isArchived = false,
                ),
            )
        }

        composeRule.onNodeWithText(skip).performClick()

        // Dashboard -> Transfer form
        val expenseFab = targetString(DashboardR.string.fab_expense_content_description)
        waitForContentDescription(expenseFab)
        composeRule
            .onAllNodesWithContentDescription(targetString(DashboardR.string.dashboard_transfer))[0]
            .performClick()

        // S03 transfer form: select the second account as target (source defaults to Cash).
        waitForText(targetString(TransactionR.string.new_transfer_title))
        composeRule
            .onNode(
                hasText(targetString(TransactionR.string.target_label)) and hasClickAction(),
            ).performClick()
        waitForText(SAVINGS)
        composeRule.onNodeWithText(SAVINGS).performClick()

        // Different currencies + no stored rate => auto-navigation to S27.
        waitForText(targetString(TransactionR.string.currency_rate_title))
        composeRule.onNode(hasSetTextAction()).performTextInput("2")
        waitUntilEnabledTextClicked(targetString(TransactionR.string.currency_rate_save))

        // Back on S03: enter an amount and save the transfer.
        waitForText(targetString(TransactionR.string.new_transfer_title))
        composeRule.onNodeWithText("0").performClick() // reveal keypad
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText("5").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText("5") and hasClickAction()).performClick()
        composeRule
            .onNodeWithContentDescription(targetString(TransactionR.string.currency_rate_save))
            .performClick()

        // Back on the dashboard.
        waitForContentDescription(expenseFab)

        // AS-7: the transfer is stored as exactly ONE row carrying both legs (AS-6 rate applied).
        val txns = runBlocking { transactionRepository.observeAll().first() }
        assertEquals("expected a single transfer row", 1, txns.size)
        val transfer = txns.single()
        assertEquals(TransactionKind.Transfer, transfer.kind)
        assertEquals(cash.id, transfer.accountId)
        assertEquals(savingsAccountId(), transfer.toAccountId)
        assertNotNull("converted target amount (second leg) must be present", transfer.toAmount)
        assertNotNull("applied exchange rate must be present", transfer.exchangeRate)
    }

    private fun savingsAccountId(): Long =
        runBlocking {
            accountRepository
                .observeActive()
                .first()
                .first { it.name == SAVINGS }
                .id
        }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(cd: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithContentDescription(cd).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilEnabledTextClicked(text: String) {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).performClick()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private companion object {
        const val TIMEOUT = 20_000L
        const val SAVINGS = "Savings"
    }
}
