package com.kshavrin.mymoney.core.domain.seed

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InitialDataSeeder @Inject constructor(
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun seedIfNeeded(now: Instant, locale: Locale = Locale.getDefault()): Boolean = withContext(ioDispatcher) {
        val existingCurrencies = currencyRepository.observeAll().first()
        if (existingCurrencies.isNotEmpty()) {
            return@withContext false
        }

        currencyRepository.upsertAll(DEFAULT_CURRENCIES)

        val russian = locale.language == "ru"

        val targetCode = runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull() ?: "USD"
        val targetCurrency = currencyRepository.findByCode(targetCode)
            ?: currencyRepository.findByCode("USD")
            ?: throw IllegalStateException("USD seed missing")

        accountRepository.upsert(
            Account(
                id = 0L,
                name = if (russian) ACCOUNT_NAME_RU else ACCOUNT_NAME_EN,
                currencyId = targetCurrency.id,
                initialBalance = BigDecimal.ZERO,
                type = AccountType.Cash,
                colorHex = "#7AC794",
                iconKey = "ic_account_cash",
                isDefault = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
                isArchived = false,
            ),
        )

        val expenseCategories = EXPENSE_CATEGORY_SEEDS.mapIndexed { index, seed ->
            Category(
                id = 0L,
                name = if (russian) seed.nameRu else seed.nameEn,
                kind = CategoryKind.Expense,
                iconKey = seed.iconKey,
                colorHex = seed.colorHex,
                sortOrder = index,
                isDefault = true,
                isArchived = false,
                createdAt = now,
            )
        }
        val incomeCategories = DEFAULT_INCOME_CATEGORIES.mapIndexed { index, seed ->
            Category(
                id = 0L,
                name = if (russian) seed.nameRu else seed.nameEn,
                kind = CategoryKind.Income,
                iconKey = seed.iconKey,
                colorHex = seed.colorHex,
                sortOrder = index,
                isDefault = true,
                isArchived = false,
                createdAt = now,
            )
        }
        categoryRepository.upsertAll(expenseCategories + incomeCategories)
        true
    }

    internal data class CategorySeed(
        val nameEn: String,
        val nameRu: String,
        val iconKey: String,
        val colorHex: String,
    )

    private companion object {
        const val ACCOUNT_NAME_EN = "Cash"
        const val ACCOUNT_NAME_RU = "Наличные"

        val DEFAULT_CURRENCIES = listOf(
            Currency(0L, "USD", "$", "US Dollar", 2, true, 0),
            Currency(0L, "EUR", "€", "Euro", 2, true, 1),
            Currency(0L, "RUB", "₽", "Russian Ruble", 2, true, 2),
            Currency(0L, "GBP", "£", "British Pound", 2, true, 3),
            Currency(0L, "JPY", "¥", "Japanese Yen", 0, true, 4),
            Currency(0L, "CNY", "¥", "Chinese Yuan", 2, true, 5),
            Currency(0L, "KRW", "₩", "Korean Won", 0, true, 6),
            Currency(0L, "INR", "₹", "Indian Rupee", 2, true, 7),
            Currency(0L, "AUD", "A$", "Australian Dollar", 2, true, 8),
            Currency(0L, "CAD", "C$", "Canadian Dollar", 2, true, 9),
            Currency(0L, "CHF", "Fr", "Swiss Franc", 2, true, 10),
            Currency(0L, "HKD", "HK$", "Hong Kong Dollar", 2, true, 11),
            Currency(0L, "NZD", "NZ$", "New Zealand Dollar", 2, true, 12),
            Currency(0L, "SGD", "S$", "Singapore Dollar", 2, true, 13),
            Currency(0L, "MXN", "$", "Mexican Peso", 2, true, 14),
            Currency(0L, "BRL", "R$", "Brazilian Real", 2, true, 15),
            Currency(0L, "TRY", "₺", "Turkish Lira", 2, true, 16),
            Currency(0L, "ZAR", "R", "South African Rand", 2, true, 17),
            Currency(0L, "PLN", "zł", "Polish Zloty", 2, true, 18),
            Currency(0L, "UAH", "₴", "Ukrainian Hryvnia", 2, true, 19),
        )

        val EXPENSE_CATEGORY_SEEDS = listOf(
            CategorySeed("Clothing", "Одежда", "ic_cat_clothing", "#9C5BB8"),
            CategorySeed("Bills", "Счета", "ic_cat_bills", "#C9A227"),
            CategorySeed("Food", "Продукты", "ic_cat_food", "#E07AAE"),
            CategorySeed("Entertainment", "Развлечения", "ic_cat_entertainment", "#F08A3E"),
            CategorySeed("Taxi", "Такси", "ic_cat_taxi", "#E0A52C"),
            CategorySeed("Housing", "Жильё", "ic_cat_housing", "#4A8FCB"),
            CategorySeed("Health", "Здоровье", "ic_cat_health", "#D85A5A"),
            CategorySeed("Pets", "Питомцы", "ic_cat_pets", "#3DA98A"),
            CategorySeed("Sport", "Спорт", "ic_cat_sport", "#7AC29A"),
            CategorySeed("Gifts", "Подарки", "ic_cat_gifts", "#D9A4A4"),
            CategorySeed("Phone", "Телефон", "ic_cat_phone", "#9CBBA8"),
            CategorySeed("Transport", "Транспорт", "ic_cat_transport", "#E07A7A"),
            CategorySeed("Hygiene", "Личная гигиена", "ic_cat_hygiene", "#3A4F8C"),
            CategorySeed("Cafe", "Кафе и рестораны", "ic_cat_cafe", "#7A9685"),
            CategorySeed("Car", "Автомобиль", "ic_cat_car", "#4A5870"),
        )

        val DEFAULT_INCOME_CATEGORIES = listOf(
            CategorySeed("Salary", "Зарплата", "ic_cat_salary", "#7AC29A"),
            CategorySeed("Other", "Прочее", "ic_cat_other", "#9CBBA8"),
        )
    }
}
