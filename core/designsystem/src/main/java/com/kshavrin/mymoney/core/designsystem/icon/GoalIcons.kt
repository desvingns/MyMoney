package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.vector.ImageVector

fun goalIcon(iconKey: String?): ImageVector =
    when (iconKey) {
        "ic_goal_home" -> Icons.Outlined.Home
        "ic_goal_car" -> Icons.Outlined.DirectionsCar
        "ic_goal_travel" -> Icons.Outlined.Flight
        "ic_goal_education" -> Icons.Outlined.School
        "ic_goal_emergency" -> Icons.Outlined.HealthAndSafety
        "ic_goal_wedding" -> Icons.Outlined.Diamond
        "ic_goal_gadget" -> Icons.Outlined.Devices
        "ic_goal_gift" -> Icons.Outlined.Redeem
        "ic_goal_health" -> Icons.Outlined.MedicalServices
        "ic_goal_retirement" -> Icons.Outlined.BeachAccess
        "ic_goal_renovation" -> Icons.Outlined.Construction
        "ic_goal_family" -> Icons.Outlined.FamilyRestroom
        "ic_goal_other" -> Icons.Outlined.Flag
        else -> Icons.Outlined.Flag
    }
