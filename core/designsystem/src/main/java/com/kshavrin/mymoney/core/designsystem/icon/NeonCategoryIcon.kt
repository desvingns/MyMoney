package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import com.kshavrin.mymoney.core.ui.theme.tileSurface

@Suppress("UNUSED_PARAMETER")
@Composable
fun NeonCategoryIcon(
    iconKey: String,
    accent: Color = categoryIconAccent(iconKey),
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selected: Boolean = false,
    containerSize: Dp = NeonCategoryIconDefaults.ContainerSize,
    iconSize: Dp = NeonCategoryIconDefaults.IconSize,
    shape: Shape = NeonCategoryIconDefaults.Shape,
) {
    Image(
        painter = painterResource(categoryNeonIconRes(iconKey)),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
            modifier
                .size(containerSize)
                .neonIconSemantics(contentDescription),
    )
}

@Composable
fun NeonIconTile(
    imageVector: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selected: Boolean = false,
    containerSize: Dp = NeonCategoryIconDefaults.ContainerSize,
    iconSize: Dp = NeonCategoryIconDefaults.IconSize,
    shape: Shape = NeonCategoryIconDefaults.Shape,
) {
    val borderAlpha = if (selected) 0.78f else 0.34f
    val washAlpha = if (selected) 0.18f else 0.10f
    val glowAlpha = if (selected) 0.36f else 0.24f
    Box(
        modifier =
            modifier
                .size(containerSize)
                .neonIconSemantics(contentDescription)
                .clip(shape)
                .background(MaterialTheme.colorScheme.tileSurface)
                .drawWithContent {
                    val radius = size.minDimension * 0.62f
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        accent.copy(alpha = glowAlpha),
                                        accent.copy(alpha = 0f),
                                    ),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = washAlpha),
                        cornerRadius = CornerRadius(size.minDimension * 0.22f),
                    )
                    drawContent()
                }.border(
                    border = BorderStroke(1.dp, accent.copy(alpha = borderAlpha)),
                    shape = shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = accent.copy(alpha = 0.22f),
            modifier = Modifier.size(iconSize + 10.dp),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = accent.copy(alpha = 0.94f),
            modifier = Modifier.size(iconSize + 2.dp),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier.size(iconSize),
        )
    }
}

object NeonCategoryIconDefaults {
    val ContainerSize = 56.dp
    val IconSize = 32.dp
    val PickerContainerSize = 84.dp
    val PickerIconSize = 46.dp
    val FormGridContainerSize = 64.dp
    val FormGridIconSize = 34.dp
    val WizardIconSize = 28.dp
    val DashboardTileIconSize = 28.dp
    val ListIconSize = 30.dp
    val CompactContainerSize = 40.dp
    val CompactIconSize = 24.dp
    val RowContainerSize = 36.dp
    val RowIconSize = 22.dp
    val Shape = RoundedCornerShape(16.dp)
}

fun categoryIconAccent(iconKey: String): Color =
    parseCategoryIconColor(categoryIconDominantHex(iconKey))

private fun Modifier.neonIconSemantics(contentDescription: String?): Modifier =
    if (contentDescription == null) {
        this
    } else {
        semantics { this.contentDescription = contentDescription }
    }

private fun parseCategoryIconColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
    return Color(argb.toLong(16))
}
