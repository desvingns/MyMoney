package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val MoneyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val Shapes.recordsHeaderBalance: Shape
    get() = large

val Shapes.recordsHeaderControl: Shape
    get() = extraLarge

val Shapes.recordsFilterChip: Shape
    get() = extraLarge

val Shapes.dashboardBalancePanel: Shape
    get() = RoundedCornerShape(12.dp)

val Shapes.dashboardPeriodIndicator: Shape
    get() = RoundedCornerShape(999.dp)

// Import-migration wizard — selectable strategy option card shape.
// medium (12dp) keeps cards visually consistent with M3 ElevatedCard defaults
// while matching other rounded containers in the app.
val Shapes.wizardStrategyCard: Shape
    get() = medium

// Aurora hero card container — 24dp corner radius per SecAurora (03_balance-variants.jsx).
// Sits between M3 large (16dp) and extraLarge (28dp); a named alias avoids a raw
// RoundedCornerShape(24.dp) at each call site in the developer's composable.
val Shapes.dashboardAuroraCard: Shape
    get() = RoundedCornerShape(24.dp)

// Income/expense pill inside the Aurora card — 20dp full-pill radius per SecAurora.
// Used for both income (↑) and expense (↓) pill containers.
val Shapes.dashboardAuroraPill: Shape
    get() = RoundedCornerShape(20.dp)

val Shapes.supportCard: Shape
    get() = large

val Shapes.supporterBadge: Shape
    get() = extraLarge
