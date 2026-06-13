package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract tests for [accountIcon].
 *
 * `accountIcon` is a plain (non-@Composable) function returning [ImageVector]; all resolved
 * vectors are Material Outlined singletons — pure data structures that load on the plain JVM
 * test classpath without Robolectric.
 *
 * :core:designsystem MUST NOT depend on :feature:dictionaries, so the authoritative
 * ACCOUNT_ICON_KEYS list is not imported here. Instead, [allKeys] is declared locally and kept
 * in sync with AccountIcons.kt — 24 entries matching the production when-expression exactly.
 *
 * Fallback contract (pinned exactly per the implementer's note — DO NOT loosen):
 *   - fallback (unknown / null / empty key) is `Icons.Outlined.AccountBalanceWallet`;
 *   - `ic_account_wallet` INTENTIONALLY maps to that SAME instance — it IS the generic wallet
 *     glyph and doubles as the fallback (analogous to `ic_cat_other` in CategoryIconsTest);
 *   - the 23 non-wallet keys each return something that is NOT the fallback;
 *   - the 23 non-wallet keys are pairwise distinct from each other.
 *
 * Material icon objects are cached singletons, so reference identity (===) is the valid
 * comparison; `assertSame` / `assertNotSame` use reference identity.
 */
class AccountIconsTest {
    private val fallback: ImageVector = Icons.Outlined.AccountBalanceWallet

    /** All 24 keys from AccountIcons.kt (local copy — :core:designsystem may not import :feature). */
    private val allKeys: List<String> =
        listOf(
            "ic_account_wallet",
            "ic_account_cash",
            "ic_account_cash_bills",
            "ic_account_coins",
            "ic_account_card",
            "ic_account_debit_card",
            "ic_account_credit_score",
            "ic_account_ewallet",
            "ic_account_bank",
            "ic_account_atm",
            "ic_account_savings",
            "ic_account_safe",
            "ic_account_investment",
            "ic_account_crypto",
            "ic_account_currency_exchange",
            "ic_account_loan",
            "ic_account_cheque",
            "ic_account_business",
            "ic_account_pension",
            "ic_account_insurance",
            "ic_account_rewards",
            "ic_account_gift_card",
            "ic_account_transit_card",
            "ic_account_family",
        )

    /** The 23 keys that must each resolve to a distinct, non-fallback vector. */
    private val nonWalletKeys: List<String> = allKeys.filterNot { it == "ic_account_wallet" }

    // ---- exhaustiveness ----

    @Test
    fun `covers exactly the twenty-four documented account keys`() {
        assertEquals(24, allKeys.size)
        assertEquals(23, nonWalletKeys.size)
    }

    // ---- per-key mapping (mirrors the production when-expression) ----

    @Test
    fun `ic_account_cash maps to Outlined Payments`() {
        assertSame(Icons.Outlined.Payments, accountIcon("ic_account_cash"))
    }

    @Test
    fun `ic_account_card maps to Outlined CreditCard`() {
        assertSame(Icons.Outlined.CreditCard, accountIcon("ic_account_card"))
    }

    @Test
    fun `ic_account_bank maps to Outlined AccountBalance`() {
        assertSame(Icons.Outlined.AccountBalance, accountIcon("ic_account_bank"))
    }

    @Test
    fun `ic_account_savings maps to Outlined Savings`() {
        assertSame(Icons.Outlined.Savings, accountIcon("ic_account_savings"))
    }

    @Test
    fun `ic_account_debit_card maps to Outlined Payment`() {
        assertSame(Icons.Outlined.Payment, accountIcon("ic_account_debit_card"))
    }

    @Test
    fun `ic_account_credit_score maps to Outlined CreditScore`() {
        assertSame(Icons.Outlined.CreditScore, accountIcon("ic_account_credit_score"))
    }

    @Test
    fun `ic_account_cash_bills maps to Outlined AttachMoney`() {
        assertSame(Icons.Outlined.AttachMoney, accountIcon("ic_account_cash_bills"))
    }

    @Test
    fun `ic_account_coins maps to Outlined Toll`() {
        assertSame(Icons.Outlined.Toll, accountIcon("ic_account_coins"))
    }

    @Test
    fun `ic_account_ewallet maps to Outlined Wallet`() {
        assertSame(Icons.Outlined.Wallet, accountIcon("ic_account_ewallet"))
    }

    @Test
    fun `ic_account_crypto maps to Outlined CurrencyBitcoin`() {
        assertSame(Icons.Outlined.CurrencyBitcoin, accountIcon("ic_account_crypto"))
    }

    @Test
    fun `ic_account_safe maps to Outlined Lock`() {
        assertSame(Icons.Outlined.Lock, accountIcon("ic_account_safe"))
    }

    @Test
    fun `ic_account_investment maps to Outlined TrendingUp`() {
        assertSame(Icons.Outlined.TrendingUp, accountIcon("ic_account_investment"))
    }

    @Test
    fun `ic_account_loan maps to Outlined RequestQuote`() {
        assertSame(Icons.Outlined.RequestQuote, accountIcon("ic_account_loan"))
    }

    @Test
    fun `ic_account_business maps to Outlined BusinessCenter`() {
        assertSame(Icons.Outlined.BusinessCenter, accountIcon("ic_account_business"))
    }

    @Test
    fun `ic_account_currency_exchange maps to Outlined CurrencyExchange`() {
        assertSame(Icons.Outlined.CurrencyExchange, accountIcon("ic_account_currency_exchange"))
    }

    @Test
    fun `ic_account_atm maps to Outlined LocalAtm`() {
        assertSame(Icons.Outlined.LocalAtm, accountIcon("ic_account_atm"))
    }

    @Test
    fun `ic_account_cheque maps to Outlined ReceiptLong`() {
        assertSame(Icons.Outlined.ReceiptLong, accountIcon("ic_account_cheque"))
    }

    @Test
    fun `ic_account_rewards maps to Outlined Stars`() {
        assertSame(Icons.Outlined.Stars, accountIcon("ic_account_rewards"))
    }

    @Test
    fun `ic_account_insurance maps to Outlined Shield`() {
        assertSame(Icons.Outlined.Shield, accountIcon("ic_account_insurance"))
    }

    @Test
    fun `ic_account_pension maps to Outlined Elderly`() {
        assertSame(Icons.Outlined.Elderly, accountIcon("ic_account_pension"))
    }

    @Test
    fun `ic_account_gift_card maps to Outlined CardGiftcard`() {
        assertSame(Icons.Outlined.CardGiftcard, accountIcon("ic_account_gift_card"))
    }

    @Test
    fun `ic_account_transit_card maps to Outlined Commute`() {
        assertSame(Icons.Outlined.Commute, accountIcon("ic_account_transit_card"))
    }

    @Test
    fun `ic_account_family maps to Outlined Group`() {
        assertSame(Icons.Outlined.Group, accountIcon("ic_account_family"))
    }

    // ---- fallback contract ----

    @Test
    fun `ic_account_wallet maps to the same instance as the fallback`() {
        assertSame(fallback, accountIcon("ic_account_wallet"))
    }

    @Test
    fun `unknown key returns the fallback instance`() {
        assertSame(fallback, accountIcon("ic_account_nope"))
    }

    @Test
    fun `empty key returns the fallback instance`() {
        assertSame(fallback, accountIcon(""))
    }

    @Test
    fun `garbage key returns the fallback instance`() {
        assertSame(fallback, accountIcon("random"))
    }

    @Test
    fun `null key returns the fallback instance`() {
        assertSame(fallback, accountIcon(null))
    }

    @Test
    fun `each non-wallet key returns a vector that is not the fallback`() {
        for (key in nonWalletKeys) {
            assertNotSame(
                "key '$key' must NOT resolve to the fallback (Icons.Outlined.AccountBalanceWallet)",
                fallback,
                accountIcon(key),
            )
        }
    }

    @Test
    fun `the twenty-three non-wallet keys are pairwise distinct vectors`() {
        val vectors: List<ImageVector> = nonWalletKeys.map { accountIcon(it) }
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                assertTrue(
                    "'${nonWalletKeys[i]}' and '${nonWalletKeys[j]}' must resolve to different vectors",
                    vectors[i] !== vectors[j],
                )
            }
        }
    }

    // ---- stability: repeated calls are referentially stable (Material singletons cached) ----

    @Test
    fun `repeated calls for the same key return the same instance`() {
        for (key in allKeys) {
            assertSame(
                "key '$key' must return a stable instance across calls",
                accountIcon(key),
                accountIcon(key),
            )
        }
    }
}
