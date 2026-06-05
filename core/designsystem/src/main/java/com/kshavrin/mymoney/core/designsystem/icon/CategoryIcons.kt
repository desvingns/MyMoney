package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ChildFriendly
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HomeRepairService
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.Liquor
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tram
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Clothing
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Dentist
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Health
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Hygiene
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Pets
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Shoes

fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "ic_cat_bills" -> Icons.Outlined.LocalOffer
    "ic_cat_food" -> Icons.Outlined.ShoppingBasket
    "ic_cat_entertainment" -> Icons.Outlined.LocalBar
    "ic_cat_taxi" -> Icons.Outlined.LocalTaxi
    "ic_cat_housing" -> Icons.Outlined.Home
    "ic_cat_sport" -> Icons.AutoMirrored.Outlined.DirectionsRun
    "ic_cat_gifts" -> Icons.Outlined.CardGiftcard
    "ic_cat_phone" -> Icons.Outlined.Call
    "ic_cat_transport" -> Icons.Outlined.Train
    "ic_cat_cafe" -> Icons.Outlined.Restaurant
    "ic_cat_car" -> Icons.Outlined.DirectionsCar
    "ic_cat_salary" -> Icons.Outlined.Payments
    "ic_cat_hygiene" -> Hygiene
    "ic_cat_pets" -> Pets
    "ic_cat_health" -> Health
    "ic_cat_clothing" -> Clothing
    "ic_cat_groceries" -> Icons.Outlined.LocalGroceryStore
    "ic_cat_restaurant" -> Icons.Outlined.RestaurantMenu
    "ic_cat_fastfood" -> Icons.Outlined.Fastfood
    "ic_cat_coffee" -> Icons.Outlined.LocalCafe
    "ic_cat_bar" -> Icons.Outlined.WineBar
    "ic_cat_alcohol" -> Icons.Outlined.Liquor
    "ic_cat_bus" -> Icons.Outlined.DirectionsBus
    "ic_cat_tram" -> Icons.Outlined.Tram
    "ic_cat_flight" -> Icons.Outlined.Flight
    "ic_cat_bike" -> Icons.Outlined.DirectionsBike
    "ic_cat_fuel" -> Icons.Outlined.LocalGasStation
    "ic_cat_parking" -> Icons.Outlined.LocalParking
    "ic_cat_shoes" -> Shoes
    "ic_cat_electronics" -> Icons.Outlined.Devices
    "ic_cat_books" -> Icons.Outlined.MenuBook
    "ic_cat_rent" -> Icons.Outlined.Apartment
    "ic_cat_utilities" -> Icons.Outlined.Bolt
    "ic_cat_water" -> Icons.Outlined.WaterDrop
    "ic_cat_furniture" -> Icons.Outlined.Chair
    "ic_cat_repair" -> Icons.Outlined.HomeRepairService
    "ic_cat_pharmacy" -> Icons.Outlined.LocalPharmacy
    "ic_cat_doctor" -> Icons.Outlined.MedicalServices
    "ic_cat_dentist" -> Dentist
    "ic_cat_gym" -> Icons.Outlined.FitnessCenter
    "ic_cat_beauty" -> Icons.Outlined.Spa
    "ic_cat_education" -> Icons.Outlined.School
    "ic_cat_kids" -> Icons.Outlined.ChildCare
    "ic_cat_baby" -> Icons.Outlined.ChildFriendly
    "ic_cat_travel" -> Icons.Outlined.Luggage
    "ic_cat_hotel" -> Icons.Outlined.Hotel
    "ic_cat_subscription" -> Icons.Outlined.Subscriptions
    "ic_cat_streaming" -> Icons.Outlined.LiveTv
    "ic_cat_internet" -> Icons.Outlined.Wifi
    "ic_cat_charity" -> Icons.Outlined.VolunteerActivism
    "ic_cat_other" -> Icons.Outlined.Category
    else -> Icons.Outlined.Category
}

private object CategoryVectors {

    val Hygiene: ImageVector by lazy {
        categoryVector("category_hygiene") {
            moveTo(4f, 13f)
            lineTo(14f, 13f)
            lineTo(14f, 18f)
            arcTo(2f, 2f, 0f, false, true, 12f, 20f)
            lineTo(6f, 20f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18f)
            close()
            moveTo(14f, 14.5f)
            lineTo(18f, 14.5f)
            moveTo(18f, 14.5f)
            lineTo(18f, 5f)
            arcTo(1f, 1f, 0f, false, true, 19f, 4f)
            lineTo(20f, 4f)
            moveTo(5.5f, 13f)
            lineTo(5.5f, 9f)
            moveTo(8f, 13f)
            lineTo(8f, 9f)
            moveTo(10.5f, 13f)
            lineTo(10.5f, 9f)
            moveTo(12.5f, 13f)
            lineTo(12.5f, 9f)
        }
    }

    val Pets: ImageVector by lazy {
        categoryVector("category_pets") {
            moveTo(7f, 5f)
            lineTo(9f, 9f)
            lineTo(15f, 9f)
            lineTo(17f, 5f)
            lineTo(17f, 12f)
            arcTo(5f, 5f, 0f, false, true, 7f, 12f)
            close()
            moveTo(9.5f, 13f)
            lineTo(9.5f, 13.01f)
            moveTo(14.5f, 13f)
            lineTo(14.5f, 13.01f)
            moveTo(12f, 15f)
            lineTo(11f, 17f)
            moveTo(12f, 15f)
            lineTo(13f, 17f)
        }
    }

    val Health: ImageVector by lazy {
        categoryVector("category_health") {
            moveTo(12f, 3f)
            arcTo(2f, 2f, 0f, false, true, 14f, 5f)
            lineTo(14f, 14.5f)
            arcTo(4f, 4f, 0f, true, true, 10f, 14.5f)
            lineTo(10f, 5f)
            arcTo(2f, 2f, 0f, false, true, 12f, 3f)
            close()
            moveTo(12f, 9f)
            lineTo(12f, 16f)
        }
    }

    val Clothing: ImageVector by lazy {
        categoryVector("category_clothing") {
            moveTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(20f, 7f)
            lineTo(18f, 11f)
            lineTo(16f, 10f)
            lineTo(16f, 20f)
            lineTo(8f, 20f)
            lineTo(8f, 10f)
            lineTo(6f, 11f)
            lineTo(4f, 7f)
            close()
            moveTo(9f, 4f)
            arcTo(3f, 2f, 0f, false, false, 15f, 4f)
        }
    }

    val Shoes: ImageVector by lazy {
        categoryVector("category_shoes") {
            moveTo(4f, 15f)
            lineTo(7.5f, 15f)
            lineTo(10f, 12f)
            lineTo(13f, 12f)
            lineTo(15f, 14f)
            lineTo(19f, 15.5f)
            arcTo(1f, 1f, 0f, false, true, 20f, 16.5f)
            lineTo(20f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(10f, 12f)
            lineTo(10f, 9f)
            moveTo(12f, 12f)
            lineTo(12f, 10f)
            moveTo(14f, 13f)
            lineTo(14f, 11.5f)
            moveTo(7f, 15f)
            lineTo(7f, 13f)
        }
    }

    val Dentist: ImageVector by lazy {
        categoryVector("category_dentist") {
            moveTo(12f, 4f)
            arcTo(4f, 4f, 0f, false, true, 16f, 8f)
            lineTo(16f, 12f)
            lineTo(14f, 19f)
            lineTo(12f, 16f)
            lineTo(10f, 19f)
            lineTo(8f, 12f)
            lineTo(8f, 8f)
            arcTo(4f, 4f, 0f, false, true, 12f, 4f)
            close()
        }
    }
}

private fun categoryVector(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathData(pathBuilder),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
