package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.picker.CATEGORY_REFERENCE_ICON_KEYS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CategoryIconsTest {
    private val documentedKeys: List<String> = CATEGORY_REFERENCE_ICON_KEYS

    @Test
    fun `covers the documented category bitmap keys`() {
        assertEquals(82, documentedKeys.size)
    }

    @Test
    fun `every documented key maps to a neon bitmap asset`() {
        documentedKeys.forEach { key ->
            assertNotNull("key '$key' must resolve to a neon bitmap tile", categoryNeonIconResOrNull(key))
        }
    }

    @Test
    fun `documented category keys do not share duplicate bitmap assets`() {
        val resolvedAssets = documentedKeys.map { key -> categoryNeonIconResOrNull(key) }

        assertEquals(documentedKeys.size, resolvedAssets.toSet().size)
    }

    @Test
    fun `base reference keys map to the matching tile assets`() {
        val expected =
            mapOf(
                "ic_cat_clothing" to R.drawable.category_neon_clothing,
                "ic_cat_bills" to R.drawable.category_neon_tag,
                "ic_cat_food" to R.drawable.category_neon_food_basket,
                "ic_cat_entertainment" to R.drawable.category_neon_martini,
                "ic_cat_taxi" to R.drawable.category_neon_taxi,
                "ic_cat_housing" to R.drawable.category_neon_house,
                "ic_cat_health" to R.drawable.category_neon_thermometer,
                "ic_cat_pets" to R.drawable.category_neon_cat,
                "ic_cat_sport" to R.drawable.category_neon_runner,
                "ic_cat_gifts" to R.drawable.category_neon_gift,
                "ic_cat_phone" to R.drawable.category_neon_phone,
                "ic_cat_transport" to R.drawable.category_neon_train,
                "ic_cat_restaurant" to R.drawable.category_neon_restaurant,
                "ic_cat_car" to R.drawable.category_neon_car,
                "ic_cat_groceries" to R.drawable.category_neon_shopping_cart,
                "ic_cat_coffee" to R.drawable.category_neon_coffee,
                "ic_cat_books" to R.drawable.category_neon_book,
                "ic_cat_rent" to R.drawable.category_neon_city,
                "ic_cat_utilities" to R.drawable.category_neon_lightning,
                "ic_cat_water" to R.drawable.category_neon_water,
                "ic_cat_furniture" to R.drawable.category_neon_sofa,
                "ic_cat_repair" to R.drawable.category_neon_briefcase,
                "ic_cat_pharmacy" to R.drawable.category_neon_medical_kit,
                "ic_cat_dentist" to R.drawable.category_neon_tooth,
                "ic_cat_gym" to R.drawable.category_neon_dumbbell,
                "ic_cat_beauty" to R.drawable.category_neon_plant,
            )

        expected.forEach { (key, asset) ->
            assertEquals(asset, categoryNeonIconResOrNull(key))
        }
    }

    @Test
    fun `new mockup keys map to the matching tile assets`() {
        val expected =
            mapOf(
                "ic_cat_education" to R.drawable.category_neon_education,
                "ic_cat_presentation" to R.drawable.category_neon_presentation,
                "ic_cat_stationery" to R.drawable.category_neon_stationery,
                "ic_cat_notebook" to R.drawable.category_neon_notebook,
                "ic_cat_delivery" to R.drawable.category_neon_delivery,
                "ic_cat_mail" to R.drawable.category_neon_mail,
                "ic_cat_cleaning" to R.drawable.category_neon_cleaning,
                "ic_cat_laundry" to R.drawable.category_neon_laundry,
                "ic_cat_haircare" to R.drawable.category_neon_haircare,
                "ic_cat_glasses" to R.drawable.category_neon_glasses,
                "ic_cat_jewelry" to R.drawable.category_neon_jewelry,
                "ic_cat_watch" to R.drawable.category_neon_watch,
                "ic_cat_camera" to R.drawable.category_neon_camera,
                "ic_cat_electronics" to R.drawable.category_neon_electronics,
                "ic_cat_cloud_upload" to R.drawable.category_neon_cloud_upload,
                "ic_cat_legal" to R.drawable.category_neon_legal,
                "ic_cat_law" to R.drawable.category_neon_law,
                "ic_cat_moving" to R.drawable.category_neon_moving,
                "ic_cat_paint" to R.drawable.category_neon_paint,
                "ic_cat_garden" to R.drawable.category_neon_garden,
                "ic_cat_art" to R.drawable.category_neon_art,
                "ic_cat_music" to R.drawable.category_neon_music,
                "ic_cat_spa" to R.drawable.category_neon_spa,
                "ic_cat_eco" to R.drawable.category_neon_eco,
                "ic_cat_baby" to R.drawable.category_neon_baby,
                "ic_cat_care" to R.drawable.category_neon_care,
                "ic_cat_party" to R.drawable.category_neon_party,
                "ic_cat_security" to R.drawable.category_neon_security,
                "ic_cat_cash" to R.drawable.category_neon_cash,
                "ic_cat_freelance" to R.drawable.category_neon_freelance,
                "ic_cat_investment_growth" to R.drawable.category_neon_investment_growth,
                "ic_cat_currency_exchange" to R.drawable.category_neon_currency_exchange,
                "ic_cat_savings" to R.drawable.category_neon_savings,
                "ic_cat_home_key" to R.drawable.category_neon_home_key,
                "ic_cat_insurance" to R.drawable.category_neon_insurance,
                "ic_cat_discount" to R.drawable.category_neon_discount,
                "ic_cat_bank" to R.drawable.category_neon_bank,
                "ic_cat_internet" to R.drawable.category_neon_internet,
                "ic_cat_streaming" to R.drawable.category_neon_streaming,
                "ic_cat_audio" to R.drawable.category_neon_audio,
                "ic_cat_makeup" to R.drawable.category_neon_makeup,
                "ic_cat_hygiene" to R.drawable.category_neon_hygiene,
                "ic_cat_doctor" to R.drawable.category_neon_doctor,
                "ic_cat_kids" to R.drawable.category_neon_kids,
                "ic_cat_games" to R.drawable.category_neon_games,
                "ic_cat_tickets" to R.drawable.category_neon_tickets,
                "ic_cat_flight" to R.drawable.category_neon_flight,
                "ic_cat_hotel" to R.drawable.category_neon_hotel,
                "ic_cat_parking" to R.drawable.category_neon_parking,
                "ic_cat_fuel" to R.drawable.category_neon_fuel,
                "ic_cat_tools" to R.drawable.category_neon_tools,
                "ic_cat_charity" to R.drawable.category_neon_charity,
                "ic_cat_credit_card" to R.drawable.category_neon_credit_card,
                "ic_cat_tips" to R.drawable.category_neon_tips,
                "ic_cat_wallet" to R.drawable.category_neon_wallet,
                "ic_cat_technology" to R.drawable.category_neon_technology,
            )

        expected.forEach { (key, asset) ->
            assertEquals(asset, categoryNeonIconResOrNull(key))
        }
    }

    @Test
    fun `unknown key is not treated as a category neon asset`() {
        assertNull(categoryNeonIconResOrNull("ic_cat_nope"))
        assertNull(categoryNeonIconResOrNull(""))
        assertNull(categoryNeonIconResOrNull("random"))
    }

    @Test
    fun `unknown key uses the tag tile fallback resource`() {
        assertEquals(R.drawable.category_neon_tag, categoryNeonIconRes("ic_cat_nope"))
    }

    @Test
    fun `legacy vector resolver no longer exposes the old category glyph set`() {
        val fallback = Icons.Outlined.Category

        assertSame(fallback, categoryIcon("ic_cat_food"))
        assertSame(fallback, categoryIcon("ic_cat_other"))
        assertSame(fallback, categoryIcon("random"))
    }
}
