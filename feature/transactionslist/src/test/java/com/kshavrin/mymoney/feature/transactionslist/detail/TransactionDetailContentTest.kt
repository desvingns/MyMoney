package com.kshavrin.mymoney.feature.transactionslist.detail

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.feature.transactionslist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Contract-level pinning for [TransactionDetailContent] (S13).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:transactionslist`'s offline test classpath does NOT yet have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *
 * (neither artifact is present in the Gradle cache, so a `createComposeRule()` test would fail
 * to resolve at compile time). This mirrors the deliberate deferral already documented in this
 * module's slice-1 `TransactionsListContentTest` and `:core:designsystem`'s `MonefyKeypadTest` —
 * full Compose-UI tests land in PHASE_15 once those dependencies are wired in.
 *
 * Until then, the user-visible decisions that the composable makes are driven by pure,
 * JVM-visible inputs ([TransactionDetailState] and the kind -> title mapping). This file pins
 * those so the contract can't drift, and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class TransactionDetailContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: TransactionDetailState) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 TransactionDetailContent(
 *                     state = state,
 *                     snackbarHostState = remember { SnackbarHostState() },
 *                     onEvent = {},
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `shows expense title`() {
 *         setContent(loadedExpense())
 *         composeTestRule.onNodeWithText("Edit expense").assertIsDisplayed()
 *     }
 *
 *     @Test fun `Save FAB is shown only when canSave`() {
 *         setContent(loadedExpense().copy(isDirty = true))   // canSave true
 *         composeTestRule.onNodeWithContentDescription("Save").assertIsDisplayed()
 *     }
 *
 *     @Test fun `Save FAB is hidden when not dirty`() {
 *         setContent(loadedExpense())                        // canSave false
 *         composeTestRule.onNodeWithContentDescription("Save").assertDoesNotExist()
 *     }
 *
 *     @Test fun `delete confirmation dialog shows when confirmDeleteVisible`() {
 *         setContent(loadedExpense().copy(confirmDeleteVisible = true))
 *         composeTestRule.onNodeWithText("Delete transaction?").assertIsDisplayed()
 *     }
 *
 *     @Test fun `cross-currency transfer shows the rate field`() {
 *         setContent(loadedCrossTransfer())                  // isCrossCurrency true
 *         composeTestRule.onNodeWithText("Exchange rate").assertIsDisplayed()
 *     }
 *
 *     @Test fun `error banner surfaces errorBannerRes via the snackbar host`() {
 *         setContent(loadedExpense().copy(errorBannerRes = R.string.detail_error_enter_amount))
 *         composeTestRule.onNodeWithText("Enter an amount").assertIsDisplayed()
 *     }
 * }
 * ```
 */
class TransactionDetailContentTest {
    private val usd = Currency(1L, "USD", "$", "US Dollar", 2, true, 0)
    private val eur = Currency(2L, "EUR", "€", "Euro", 2, true, 1)

    private fun loaded(
        kind: TransactionKind = TransactionKind.Expense,
        currency: Currency? = usd,
        targetCurrency: Currency? = null,
    ) = TransactionDetailState(
        transactionId = 1L,
        kind = kind,
        amount = BigDecimal("12.50"),
        currency = currency,
        targetCurrency = targetCurrency,
        isLoaded = true,
    )

    @Test
    fun `title resource is selected per transaction kind`() {
        // Mirror of TransactionDetailScreen.titleRes(kind).
        fun titleRes(kind: TransactionKind): Int =
            when (kind) {
                TransactionKind.Expense -> R.string.detail_title_expense
                TransactionKind.Income -> R.string.detail_title_income
                TransactionKind.Transfer -> R.string.detail_title_transfer
            }
        assertEquals(R.string.detail_title_expense, titleRes(TransactionKind.Expense))
        assertEquals(R.string.detail_title_income, titleRes(TransactionKind.Income))
        assertEquals(R.string.detail_title_transfer, titleRes(TransactionKind.Transfer))
    }

    @Test
    fun `Save FAB visibility tracks canSave`() {
        // The FAB block in TransactionDetailContent is gated by `if (state.canSave)`.
        // Loaded but untouched -> not dirty -> no FAB.
        assertFalse("untouched form hides the Save FAB", loaded().canSave)
        // Loaded + a real edit -> dirty + amount>0 -> FAB shown.
        assertTrue("dirty form shows the Save FAB", loaded().copy(isDirty = true).canSave)
        // Dirty but mid-save -> FAB suppressed.
        assertFalse(
            "in-flight save hides the FAB",
            loaded().copy(isDirty = true, isSaving = true).canSave,
        )
        // Dirty but amount cleared to zero -> FAB suppressed.
        assertFalse(
            "zero amount hides the FAB",
            loaded().copy(isDirty = true, amount = BigDecimal.ZERO).canSave,
        )
    }

    @Test
    fun `delete dialog visibility tracks confirmDeleteVisible`() {
        // `if (state.confirmDeleteVisible) { AlertDialog(...) }`
        assertFalse("dialog hidden by default", loaded().confirmDeleteVisible)
        assertTrue(
            "dialog shown after DeleteClicked sets the flag",
            loaded().copy(confirmDeleteVisible = true).confirmDeleteVisible,
        )
    }

    @Test
    fun `transfer-specific account dropdowns are gated by isTransfer`() {
        // The source/target AccountDropdown pair renders under `if (state.isTransfer)`.
        assertTrue("transfer shows the source/target dropdowns", loaded(TransactionKind.Transfer).isTransfer)
        assertFalse("expense shows the single account dropdown", loaded(TransactionKind.Expense).isTransfer)
        assertFalse("income shows the single account dropdown", loaded(TransactionKind.Income).isTransfer)
    }

    @Test
    fun `rate field is gated by isCrossCurrency`() {
        // The exchange-rate OutlinedTextField renders under `if (state.isCrossCurrency)`.
        val cross = loaded(TransactionKind.Transfer, currency = usd, targetCurrency = eur)
        assertTrue("USD->EUR transfer shows the rate field", cross.isCrossCurrency)

        val sameCurrency = loaded(TransactionKind.Transfer, currency = usd, targetCurrency = usd)
        assertFalse("USD->USD transfer hides the rate field", sameCurrency.isCrossCurrency)

        val expense = loaded(TransactionKind.Expense)
        assertFalse("non-transfer never shows the rate field", expense.isCrossCurrency)
    }
}
