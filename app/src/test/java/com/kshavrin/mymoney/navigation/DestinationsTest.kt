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

    // --- Settings sub-destinations (PHASE_13 S17 cloud sync, PHASE_14 S16 biometric lock) ---
    //
    // CLOUD_SYNC and LOCK_SCREEN are each registered as a top-level composable(...) in
    // MyMoneyNavHost and reached from the Settings root rows (onOpenCloudSync / onOpenBiometricLock).
    // Pin their canonical literal values so a rename of either constant fails loudly here rather than
    // silently breaking the registered route / navigate() target, mirroring the transaction-route
    // contract below.

    @Test
    fun `CLOUD_SYNC route is cloud_sync`() {
        assertEquals("cloud_sync", Destinations.CLOUD_SYNC)
    }

    @Test
    fun `LOCK_SCREEN route is lock_screen`() {
        assertEquals("lock_screen", Destinations.LOCK_SCREEN)
    }

    @Test
    fun `CLOUD_SYNC and LOCK_SCREEN routes are distinct`() {
        assertNotEquals(Destinations.CLOUD_SYNC, Destinations.LOCK_SCREEN)
    }

    // --- Transaction routes (PHASE_10 nav wiring) ---
    //
    // These constants are the canonical contract for the transaction screens
    // registered in MyMoneyNavHost. The feature/transaction module navigates with
    // hardcoded string literals (e.g. AddExpenseScreen's swap-toggle uses
    // "transaction/income") that have no compile-time link to these constants.
    // A real crash bug was fixed in PHASE_10 where the swap-toggle navigated to
    // bare "add_income"/"add_expense" strings that did NOT match the registered
    // routes. Locking the canonical values here regression-guards that drift:
    // if anyone renames a Destinations constant, these tests fail loudly and the
    // mismatch with the feature-module literals is caught in review.

    @Test
    fun `ADD_EXPENSE route is transaction expense`() {
        assertEquals("transaction/expense", Destinations.ADD_EXPENSE)
    }

    @Test
    fun `ADD_INCOME route is transaction income`() {
        assertEquals("transaction/income", Destinations.ADD_INCOME)
    }

    @Test
    fun `TRANSFER route is transaction transfer`() {
        assertEquals("transaction/transfer", Destinations.TRANSFER)
    }

    @Test
    fun `CURRENCY_RATE route is transaction currency_rate`() {
        assertEquals("transaction/currency_rate", Destinations.CURRENCY_RATE)
    }

    @Test
    fun `transaction routes are all distinct`() {
        val transactionRoutes = setOf(
            Destinations.ADD_EXPENSE,
            Destinations.ADD_INCOME,
            Destinations.TRANSFER,
            Destinations.CURRENCY_RATE,
        )
        assertEquals(4, transactionRoutes.size)
    }

    @Test
    fun `expense and income routes are not equal`() {
        assertNotEquals(Destinations.ADD_EXPENSE, Destinations.ADD_INCOME)
    }

    // The swap-toggle in AddExpenseScreen navigates to "transaction/income" while
    // popping "transaction/expense"; AddIncomeScreen does the mirror. These literals
    // must equal the registered routes or navigation crashes at runtime.
    @Test
    fun `expense screen swap-toggle target literal matches registered income route`() {
        val swapTarget = "transaction/income"
        val popTarget = "transaction/expense"
        assertEquals(Destinations.ADD_INCOME, swapTarget)
        assertEquals(Destinations.ADD_EXPENSE, popTarget)
    }

    @Test
    fun `income screen swap-toggle target literal matches registered expense route`() {
        val swapTarget = "transaction/expense"
        val popTarget = "transaction/income"
        assertEquals(Destinations.ADD_EXPENSE, swapTarget)
        assertEquals(Destinations.ADD_INCOME, popTarget)
    }

    @Test
    fun `currency rate route template is well-formed with fromId and toId query args`() {
        val template = "${Destinations.CURRENCY_RATE}?fromId={fromId}&toId={toId}"
        assertEquals("transaction/currency_rate?fromId={fromId}&toId={toId}", template)
        assertTrue(template.contains("{fromId}"))
        assertTrue(template.contains("{toId}"))
    }

    @Test
    fun `currency rate navigation literal matches its registered template base`() {
        val literal = "${Destinations.CURRENCY_RATE}?fromId=1&toId=2"
        assertEquals("transaction/currency_rate?fromId=1&toId=2", literal)
        assertTrue(literal.startsWith(Destinations.CURRENCY_RATE))
    }

    @Test
    fun `category edit route template from picker is well-formed with id kind and fromPicker args`() {
        val template = "${Destinations.CATEGORY_EDIT}/{id}?kind={kind}&fromPicker={fromPicker}"
        assertEquals(
            "dictionaries/categories/edit/{id}?kind={kind}&fromPicker={fromPicker}",
            template,
        )
        assertTrue(template.contains("{id}"))
        assertTrue(template.contains("{kind}"))
        assertTrue(template.contains("{fromPicker}"))
    }

    @Test
    fun `category edit from picker navigation literal matches its registered template base`() {
        val literal = "${Destinations.CATEGORY_EDIT}/-1?kind=Expense&fromPicker=true"
        assertEquals(
            "dictionaries/categories/edit/-1?kind=Expense&fromPicker=true",
            literal,
        )
        assertTrue(literal.startsWith("${Destinations.CATEGORY_EDIT}/"))
    }

    // --- Transaction detail route (SPEC: edit-transaction screen, PHASE_09 + UX-fixes-04) ---
    //
    // The Transaction Detail screen is reached via "${Destinations.TRANSACTION_DETAIL}/$id".
    // Locking the canonical value here prevents a rename from silently breaking the
    // navigate() call in TransactionsListScreen.

    @Test
    fun `TRANSACTION_DETAIL route is transaction_detail`() {
        assertEquals("transaction_detail", Destinations.TRANSACTION_DETAIL)
    }

    @Test
    fun `transaction detail route template is well-formed with transactionId path arg`() {
        val template = "${Destinations.TRANSACTION_DETAIL}/{transactionId}"
        assertEquals("transaction_detail/{transactionId}", template)
        assertTrue(template.contains("{transactionId}"))
    }

    @Test
    fun `transaction detail navigation literal is well-formed for a given id`() {
        val literal = "${Destinations.TRANSACTION_DETAIL}/42"
        assertEquals("transaction_detail/42", literal)
        assertTrue(literal.startsWith(Destinations.TRANSACTION_DETAIL))
    }

    @Test
    fun `TRANSACTION_DETAIL route does not collide with transaction or dictionary routes`() {
        val detailRoute = Destinations.TRANSACTION_DETAIL
        val otherRoutes = setOf(
            Destinations.ADD_EXPENSE,
            Destinations.ADD_INCOME,
            Destinations.TRANSFER,
            Destinations.TRANSACTIONS_LIST,
            Destinations.CATEGORIES_LIST,
            Destinations.CATEGORY_EDIT,
            Destinations.ACCOUNTS_LIST,
            Destinations.ACCOUNT_EDIT,
        )
        assertTrue(
            "TRANSACTION_DETAIL must not collide with other routes",
            detailRoute !in otherRoutes,
        )
    }

    @Test
    fun `destinations do not expose retired category picker route`() {
        val routeValues = Destinations::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { field ->
                field.isAccessible = true
                field.get(null) as String
            }

        assertTrue("Category picker route must stay retired", "transaction/category_picker" !in routeValues)
    }

    @Test
    fun `transaction routes do not collide with other navigation routes`() {
        val transactionRoutes = setOf(
            Destinations.ADD_EXPENSE,
            Destinations.ADD_INCOME,
            Destinations.TRANSFER,
            Destinations.CURRENCY_RATE,
        )
        val otherRoutes = setOf(
            Destinations.DECISION,
            Destinations.SPLASH,
            Destinations.ONBOARDING,
            Destinations.DASHBOARD,
            Destinations.TRANSACTIONS_LIST,
            Destinations.SETTINGS,
            Destinations.CATEGORIES_LIST,
            Destinations.CATEGORY_EDIT,
            Destinations.ACCOUNTS_LIST,
            Destinations.ACCOUNT_EDIT,
            Destinations.CURRENCIES_LIST,
            Destinations.CURRENCY_EDIT,
            Destinations.CLOUD_SYNC,
            Destinations.LOCK_SCREEN,
        )
        val intersection = transactionRoutes.intersect(otherRoutes)
        assertTrue(
            "Transaction routes must not overlap with other navigation routes; collisions: $intersection",
            intersection.isEmpty(),
        )
    }
}
