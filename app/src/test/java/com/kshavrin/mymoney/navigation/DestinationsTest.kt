package com.kshavrin.mymoney.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun `CATEGORIES_LIST route is dictionaries categories`() {
        assertEquals("dictionaries/categories", Destinations.CATEGORIES_LIST)
    }

    @Test
    fun `CATEGORY_EDIT base route is dictionaries categories edit`() {
        assertEquals("dictionaries/categories/edit", Destinations.CATEGORY_EDIT)
    }

    @Test
    fun `ACCOUNTS_LIST route is dictionaries accounts`() {
        assertEquals("dictionaries/accounts", Destinations.ACCOUNTS_LIST)
    }

    @Test
    fun `ACCOUNT_EDIT base route is dictionaries accounts edit`() {
        assertEquals("dictionaries/accounts/edit", Destinations.ACCOUNT_EDIT)
    }

    @Test
    fun `CURRENCIES_LIST route is dictionaries currencies`() {
        assertEquals("dictionaries/currencies", Destinations.CURRENCIES_LIST)
    }

    @Test
    fun `CURRENCY_EDIT base route is dictionaries currencies edit`() {
        assertEquals("dictionaries/currencies/edit", Destinations.CURRENCY_EDIT)
    }

    @Test
    fun `dictionary list routes are all distinct`() {
        val listRoutes = setOf(
            Destinations.CATEGORIES_LIST,
            Destinations.ACCOUNTS_LIST,
            Destinations.CURRENCIES_LIST,
        )
        assertEquals(3, listRoutes.size)
    }

    @Test
    fun `dictionary edit base routes are all distinct`() {
        val editRoutes = setOf(
            Destinations.CATEGORY_EDIT,
            Destinations.ACCOUNT_EDIT,
            Destinations.CURRENCY_EDIT,
        )
        assertEquals(3, editRoutes.size)
    }

    @Test
    fun `dictionary edit base routes differ from their list routes`() {
        assertNotEquals(Destinations.CATEGORIES_LIST, Destinations.CATEGORY_EDIT)
        assertNotEquals(Destinations.ACCOUNTS_LIST, Destinations.ACCOUNT_EDIT)
        assertNotEquals(Destinations.CURRENCIES_LIST, Destinations.CURRENCY_EDIT)
    }

    @Test
    fun `category edit add route uses -1 sentinel for create`() {
        val addRoute = "${Destinations.CATEGORY_EDIT}/-1"
        assertEquals("dictionaries/categories/edit/-1", addRoute)
    }

    @Test
    fun `account edit add route uses -1 sentinel for create`() {
        val addRoute = "${Destinations.ACCOUNT_EDIT}/-1"
        assertEquals("dictionaries/accounts/edit/-1", addRoute)
    }

    @Test
    fun `currency edit add route uses -1 sentinel for create`() {
        val addRoute = "${Destinations.CURRENCY_EDIT}/-1"
        assertEquals("dictionaries/currencies/edit/-1", addRoute)
    }

    @Test
    fun `category edit route with id appends id segment`() {
        val editRoute = "${Destinations.CATEGORY_EDIT}/42"
        assertEquals("dictionaries/categories/edit/42", editRoute)
    }

    @Test
    fun `account edit route with id appends id segment`() {
        val editRoute = "${Destinations.ACCOUNT_EDIT}/7"
        assertEquals("dictionaries/accounts/edit/7", editRoute)
    }

    @Test
    fun `currency edit route with id appends id segment`() {
        val editRoute = "${Destinations.CURRENCY_EDIT}/3"
        assertEquals("dictionaries/currencies/edit/3", editRoute)
    }

    @Test
    fun `category edit template route includes id navArg placeholder`() {
        val template = "${Destinations.CATEGORY_EDIT}/{id}"
        assertEquals("dictionaries/categories/edit/{id}", template)
        assertTrue(template.contains("{id}"))
    }

    @Test
    fun `account edit template route includes id navArg placeholder`() {
        val template = "${Destinations.ACCOUNT_EDIT}/{id}"
        assertEquals("dictionaries/accounts/edit/{id}", template)
        assertTrue(template.contains("{id}"))
    }

    @Test
    fun `currency edit template route includes id navArg placeholder`() {
        val template = "${Destinations.CURRENCY_EDIT}/{id}"
        assertEquals("dictionaries/currencies/edit/{id}", template)
        assertTrue(template.contains("{id}"))
    }

    @Test
    fun `dictionary routes do not collide with non-dictionary routes`() {
        val dictionaryRoutes = setOf(
            Destinations.CATEGORIES_LIST,
            Destinations.CATEGORY_EDIT,
            Destinations.ACCOUNTS_LIST,
            Destinations.ACCOUNT_EDIT,
            Destinations.CURRENCIES_LIST,
            Destinations.CURRENCY_EDIT,
        )
        val otherRoutes = setOf(
            Destinations.DECISION,
            Destinations.SPLASH,
            Destinations.ONBOARDING,
            Destinations.DASHBOARD,
            Destinations.ADD_EXPENSE,
            Destinations.ADD_INCOME,
            Destinations.TRANSFER,
            Destinations.TRANSACTIONS_LIST,
            Destinations.SETTINGS,
            Destinations.CLOUD_SYNC,
            Destinations.LOCK_SCREEN,
        )
        val intersection = dictionaryRoutes.intersect(otherRoutes)
        assertTrue(
            "Dictionary routes must not overlap with other navigation routes; collisions: $intersection",
            intersection.isEmpty(),
        )
    }
}
