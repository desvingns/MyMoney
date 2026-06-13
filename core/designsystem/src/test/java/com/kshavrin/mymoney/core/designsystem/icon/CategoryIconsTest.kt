package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AssignmentReturn
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract tests for [categoryIcon].
 *
 * `categoryIcon` is a plain (non-@Composable) function returning [ImageVector]; both the
 * Material icon singletons and the locally-built vectors are pure data structures, so they
 * load and compare on the plain JVM test classpath in :core:designsystem (same as
 * MonefyAmountInputTest, which uses Compose unit types without Robolectric).
 *
 * Fallback contract (pinned exactly per the implementer's note — DO NOT loosen):
 *   - fallback (unknown key) is `Icons.Outlined.Category`;
 *   - `ic_cat_other` INTENTIONALLY maps to that SAME instance;
 *   - the 66 non-`other` keys each return something that is NOT the fallback;
 *   - the 66 non-`other` keys are pairwise distinct from each other.
 *
 * Material icon objects are cached singletons, so reference identity (===) is the valid
 * comparison; `assertSame` / `assertNotSame` use reference identity.
 */
class CategoryIconsTest {
    private val fallback: ImageVector = Icons.Outlined.Category

    /** The 67 documented category keys currently supported by the registry. */
    private val allKeys: List<String> =
        listOf(
            "ic_cat_bills",
            "ic_cat_food",
            "ic_cat_entertainment",
            "ic_cat_taxi",
            "ic_cat_housing",
            "ic_cat_sport",
            "ic_cat_gifts",
            "ic_cat_phone",
            "ic_cat_transport",
            "ic_cat_cafe",
            "ic_cat_car",
            "ic_cat_salary",
            "ic_cat_other",
            "ic_cat_hygiene",
            "ic_cat_pets",
            "ic_cat_health",
            "ic_cat_clothing",
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
        )

    /** The 66 real keys that must each resolve to a distinct, non-fallback vector. */
    private val nonOtherKeys: List<String> = allKeys.filterNot { it == "ic_cat_other" }

    // ---- exhaustiveness: the registry covers all documented keys ----

    @Test
    fun `covers exactly the documented category keys`() {
        assertEquals(67, allKeys.size)
        assertEquals(66, nonOtherKeys.size)
    }

    // ---- per-key mapping (mirrors the production `when`) ----

    @Test
    fun `ic_cat_bills maps to Outlined LocalOffer`() {
        assertSame(Icons.Outlined.LocalOffer, categoryIcon("ic_cat_bills"))
    }

    @Test
    fun `ic_cat_food maps to Outlined ShoppingBasket`() {
        assertSame(Icons.Outlined.ShoppingBasket, categoryIcon("ic_cat_food"))
    }

    @Test
    fun `ic_cat_entertainment maps to Outlined LocalBar`() {
        assertSame(Icons.Outlined.LocalBar, categoryIcon("ic_cat_entertainment"))
    }

    @Test
    fun `ic_cat_taxi maps to Outlined LocalTaxi`() {
        assertSame(Icons.Outlined.LocalTaxi, categoryIcon("ic_cat_taxi"))
    }

    @Test
    fun `ic_cat_housing maps to Outlined Home`() {
        assertSame(Icons.Outlined.Home, categoryIcon("ic_cat_housing"))
    }

    @Test
    fun `ic_cat_sport maps to AutoMirrored Outlined DirectionsRun`() {
        // The AutoMirrored variant, not the deprecated Icons.Outlined.DirectionsRun.
        assertSame(Icons.AutoMirrored.Outlined.DirectionsRun, categoryIcon("ic_cat_sport"))
    }

    @Test
    fun `ic_cat_gifts maps to Outlined CardGiftcard`() {
        assertSame(Icons.Outlined.CardGiftcard, categoryIcon("ic_cat_gifts"))
    }

    @Test
    fun `ic_cat_phone maps to Outlined Call`() {
        assertSame(Icons.Outlined.Call, categoryIcon("ic_cat_phone"))
    }

    @Test
    fun `ic_cat_transport maps to Outlined Train`() {
        assertSame(Icons.Outlined.Train, categoryIcon("ic_cat_transport"))
    }

    @Test
    fun `ic_cat_cafe maps to Outlined Restaurant`() {
        assertSame(Icons.Outlined.Restaurant, categoryIcon("ic_cat_cafe"))
    }

    @Test
    fun `ic_cat_car maps to Outlined DirectionsCar`() {
        assertSame(Icons.Outlined.DirectionsCar, categoryIcon("ic_cat_car"))
    }

    @Test
    fun `ic_cat_salary maps to Outlined Payments`() {
        assertSame(Icons.Outlined.Payments, categoryIcon("ic_cat_salary"))
    }

    @Test
    fun `ic_cat_groceries maps to Outlined LocalGroceryStore`() {
        assertSame(Icons.Outlined.LocalGroceryStore, categoryIcon("ic_cat_groceries"))
    }

    @Test
    fun `ic_cat_restaurant maps to Outlined RestaurantMenu`() {
        assertSame(Icons.Outlined.RestaurantMenu, categoryIcon("ic_cat_restaurant"))
    }

    @Test
    fun `ic_cat_fastfood maps to Outlined Fastfood`() {
        assertSame(Icons.Outlined.Fastfood, categoryIcon("ic_cat_fastfood"))
    }

    @Test
    fun `ic_cat_coffee maps to Outlined LocalCafe`() {
        assertSame(Icons.Outlined.LocalCafe, categoryIcon("ic_cat_coffee"))
    }

    @Test
    fun `ic_cat_bar maps to Outlined WineBar`() {
        assertSame(Icons.Outlined.WineBar, categoryIcon("ic_cat_bar"))
    }

    @Test
    fun `ic_cat_bus maps to Outlined DirectionsBus`() {
        assertSame(Icons.Outlined.DirectionsBus, categoryIcon("ic_cat_bus"))
    }

    @Test
    fun `ic_cat_fuel maps to Outlined LocalGasStation`() {
        assertSame(Icons.Outlined.LocalGasStation, categoryIcon("ic_cat_fuel"))
    }

    @Test
    fun `ic_cat_doctor maps to Outlined MedicalServices`() {
        assertSame(Icons.Outlined.MedicalServices, categoryIcon("ic_cat_doctor"))
    }

    @Test
    fun `ic_cat_beauty maps to Outlined Spa`() {
        assertSame(Icons.Outlined.Spa, categoryIcon("ic_cat_beauty"))
    }

    @Test
    fun `ic_cat_kids maps to Outlined ChildCare`() {
        assertSame(Icons.Outlined.ChildCare, categoryIcon("ic_cat_kids"))
    }

    @Test
    fun `ic_cat_travel maps to Outlined Luggage`() {
        assertSame(Icons.Outlined.Luggage, categoryIcon("ic_cat_travel"))
    }

    @Test
    fun `ic_cat_subscription maps to Outlined Subscriptions`() {
        assertSame(Icons.Outlined.Subscriptions, categoryIcon("ic_cat_subscription"))
    }

    @Test
    fun `ic_cat_internet maps to Outlined Wifi`() {
        assertSame(Icons.Outlined.Wifi, categoryIcon("ic_cat_internet"))
    }

    @Test
    fun `ic_cat_charity maps to Outlined VolunteerActivism`() {
        assertSame(Icons.Outlined.VolunteerActivism, categoryIcon("ic_cat_charity"))
    }

    @Test
    fun `ic_cat_freelance maps to Outlined Work`() {
        assertSame(Icons.Outlined.Work, categoryIcon("ic_cat_freelance"))
    }

    @Test
    fun `ic_cat_bonus maps to Outlined Stars`() {
        assertSame(Icons.Outlined.Stars, categoryIcon("ic_cat_bonus"))
    }

    @Test
    fun `ic_cat_dividends maps to Outlined TrendingUp`() {
        assertSame(Icons.Outlined.TrendingUp, categoryIcon("ic_cat_dividends"))
    }

    @Test
    fun `ic_cat_interest maps to Outlined Percent`() {
        assertSame(Icons.Outlined.Percent, categoryIcon("ic_cat_interest"))
    }

    @Test
    fun `ic_cat_rent_income maps to Outlined HomeWork`() {
        assertSame(Icons.Outlined.HomeWork, categoryIcon("ic_cat_rent_income"))
    }

    @Test
    fun `ic_cat_business_income maps to Outlined BusinessCenter`() {
        assertSame(Icons.Outlined.BusinessCenter, categoryIcon("ic_cat_business_income"))
    }

    @Test
    fun `ic_cat_sale maps to Outlined Sell`() {
        assertSame(Icons.Outlined.Sell, categoryIcon("ic_cat_sale"))
    }

    @Test
    fun `ic_cat_refund maps to Outlined AssignmentReturn`() {
        assertSame(Icons.Outlined.AssignmentReturn, categoryIcon("ic_cat_refund"))
    }

    @Test
    fun `ic_cat_gift_received maps to Outlined Redeem`() {
        assertSame(Icons.Outlined.Redeem, categoryIcon("ic_cat_gift_received"))
    }

    @Test
    fun `ic_cat_cashback maps to Outlined Paid`() {
        assertSame(Icons.Outlined.Paid, categoryIcon("ic_cat_cashback"))
    }

    @Test
    fun `ic_cat_pension maps to Outlined Elderly`() {
        assertSame(Icons.Outlined.Elderly, categoryIcon("ic_cat_pension"))
    }

    @Test
    fun `ic_cat_scholarship maps to Outlined WorkspacePremium`() {
        assertSame(Icons.Outlined.WorkspacePremium, categoryIcon("ic_cat_scholarship"))
    }

    @Test
    fun `ic_cat_investment_return maps to Outlined ShowChart`() {
        assertSame(Icons.Outlined.ShowChart, categoryIcon("ic_cat_investment_return"))
    }

    @Test
    fun `ic_cat_royalties maps to Outlined Copyright`() {
        assertSame(Icons.Outlined.Copyright, categoryIcon("ic_cat_royalties"))
    }

    @Test
    fun `ic_cat_tips maps to Outlined MonetizationOn`() {
        assertSame(Icons.Outlined.MonetizationOn, categoryIcon("ic_cat_tips"))
    }

    @Test
    fun `ic_cat_deposit_income maps to Outlined Savings`() {
        assertSame(Icons.Outlined.Savings, categoryIcon("ic_cat_deposit_income"))
    }

    // ---- fallback contract ----

    @Test
    fun `ic_cat_other maps to the same instance as the fallback`() {
        assertSame(fallback, categoryIcon("ic_cat_other"))
    }

    @Test
    fun `unknown key returns the fallback instance`() {
        assertSame(fallback, categoryIcon("ic_cat_nope"))
    }

    @Test
    fun `empty key returns the fallback instance`() {
        assertSame(fallback, categoryIcon(""))
    }

    @Test
    fun `garbage key returns the fallback instance`() {
        assertSame(fallback, categoryIcon("random"))
    }

    @Test
    fun `each non-other key returns a vector that is not the fallback`() {
        for (key in nonOtherKeys) {
            assertNotSame(
                "key '$key' must NOT resolve to the fallback (Icons.Outlined.Category)",
                fallback,
                categoryIcon(key),
            )
        }
    }

    @Test
    fun `all non-other keys are pairwise distinct vectors`() {
        val vectors: List<ImageVector> = nonOtherKeys.map { categoryIcon(it) }
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                assertTrue(
                    "'${nonOtherKeys[i]}' and '${nonOtherKeys[j]}' must resolve to different vectors",
                    vectors[i] !== vectors[j],
                )
            }
        }
    }

    // ---- stability: repeated calls are referentially stable (lazy singletons cached) ----

    @Test
    fun `repeated calls for the same key return the same instance`() {
        for (key in allKeys) {
            assertSame(
                "key '$key' must return a stable instance across calls",
                categoryIcon(key),
                categoryIcon(key),
            )
        }
    }
}
