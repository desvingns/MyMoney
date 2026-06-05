package com.kshavrin.mymoney.feature.dictionaries.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class IconCatalogTest {

    private val fallback = Icons.Outlined.Category

    @Test
    fun `expense icon keys match the documented expense catalog order`() {
        assertEquals(
            listOf(
                "ic_cat_clothing",
                "ic_cat_bills",
                "ic_cat_food",
                "ic_cat_entertainment",
                "ic_cat_taxi",
                "ic_cat_housing",
                "ic_cat_health",
                "ic_cat_pets",
                "ic_cat_sport",
                "ic_cat_gifts",
                "ic_cat_phone",
                "ic_cat_transport",
                "ic_cat_hygiene",
                "ic_cat_cafe",
                "ic_cat_car",
                "ic_cat_groceries",
                "ic_cat_restaurant",
                "ic_cat_fastfood",
                "ic_cat_coffee",
                "ic_cat_bar",
                "ic_cat_alcohol",
                "ic_cat_bus",
                "ic_cat_tram",
                "ic_cat_flight",
                "ic_cat_bike",
                "ic_cat_fuel",
                "ic_cat_parking",
                "ic_cat_shoes",
                "ic_cat_electronics",
                "ic_cat_books",
                "ic_cat_rent",
                "ic_cat_utilities",
                "ic_cat_water",
                "ic_cat_furniture",
                "ic_cat_repair",
                "ic_cat_pharmacy",
                "ic_cat_doctor",
                "ic_cat_dentist",
                "ic_cat_gym",
                "ic_cat_beauty",
                "ic_cat_education",
                "ic_cat_kids",
                "ic_cat_baby",
                "ic_cat_travel",
                "ic_cat_hotel",
                "ic_cat_subscription",
                "ic_cat_streaming",
                "ic_cat_internet",
                "ic_cat_charity",
            ),
            EXPENSE_ICON_KEYS,
        )
    }

    @Test
    fun `every expense icon key resolves to a non fallback category vector`() {
        for (iconKey in EXPENSE_ICON_KEYS) {
            assertNotSame(
                "expense key '$iconKey' must resolve to a registered category icon",
                fallback,
                categoryIcon(iconKey),
            )
        }
    }

    @Test
    fun `income icon keys match the documented income catalog order`() {
        assertEquals(
            listOf(
                "ic_cat_salary",
                "ic_cat_other",
                "ic_cat_freelance",
                "ic_cat_bonus",
                "ic_cat_dividends",
                "ic_cat_interest",
                "ic_cat_rent_income",
                "ic_cat_business_income",
                "ic_cat_sale",
                "ic_cat_refund",
                "ic_cat_gift_received",
                "ic_cat_cashback",
                "ic_cat_pension",
                "ic_cat_scholarship",
                "ic_cat_investment_return",
                "ic_cat_royalties",
                "ic_cat_tips",
                "ic_cat_deposit_income",
            ),
            INCOME_ICON_KEYS,
        )
    }

    @Test
    fun `every non other income icon key resolves to a non fallback category vector`() {
        for (iconKey in INCOME_ICON_KEYS.filterNot { it == "ic_cat_other" }) {
            assertNotSame(
                "income key '$iconKey' must resolve to a registered category icon",
                fallback,
                categoryIcon(iconKey),
            )
        }
    }

    @Test
    fun `income other key intentionally resolves to the fallback category vector`() {
        assertSame(fallback, categoryIcon("ic_cat_other"))
    }
}
