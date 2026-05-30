package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Train
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Clothing
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Health
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Hygiene
import com.kshavrin.mymoney.core.designsystem.icon.CategoryVectors.Pets

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
