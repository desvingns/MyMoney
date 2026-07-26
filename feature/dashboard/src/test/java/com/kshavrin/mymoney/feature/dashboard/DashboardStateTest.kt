package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class DashboardStateTest {
    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )
    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "€",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )
    private val account =
        Account(
            id = 7L,
            name = "Cash",
            currencyId = usd.id,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_acc_cash",
            isDefault = true,
            sortOrder = 0,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            isArchived = false,
        )

    @Test
    fun `specific account exposes the selected account and its currency`() {
        val state =
            DashboardState(
                accounts = listOf(account),
                currencies = listOf(usd, eur),
                dashboardSelection = DashboardSelection.SpecificAccount(account),
            )

        assertEquals(account, state.currentAccount)
        assertEquals(usd, state.currentCurrency)
        assertFalse(state.isSeparateMode)
    }

    @Test
    fun `convert-to selection exposes the target currency without an account`() {
        val state =
            DashboardState(
                accounts = listOf(account),
                currencies = listOf(usd, eur),
                dashboardSelection =
                    DashboardSelection.AllAccounts(
                        foldMode = AllAccountsFoldMode.ConvertTo(eur),
                    ),
            )

        assertNull(state.currentAccount)
        assertEquals(eur, state.currentCurrency)
        assertFalse(state.isSeparateMode)
    }

    @Test
    fun `separate all-accounts selection has no current currency and marks separate mode`() {
        val state =
            DashboardState(
                currencies = listOf(usd, eur),
                dashboardSelection =
                    DashboardSelection.AllAccounts(
                        foldMode = AllAccountsFoldMode.Separate,
                    ),
            )

        assertNull(state.currentAccount)
        assertNull(state.currentCurrency)
        assertTrue(state.isSeparateMode)
    }
}
