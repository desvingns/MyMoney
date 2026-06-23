package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardActionExpense
import com.kshavrin.mymoney.core.ui.theme.dashboardActionIncome
import com.kshavrin.mymoney.core.ui.theme.dashboardActionTransfer
import com.kshavrin.mymoney.core.ui.theme.dashboardNeonBackground
import com.kshavrin.mymoney.feature.dashboard.R

// Three neon outline-ring FABs on one row («Dashboard Final» mockup — RealFabs):
// − expense (coral) · ⇄ transfer (cyan) · + income (mint), justified space-between.
@Composable
fun ThreeFabLayout(
    onMinusClick: () -> Unit,
    onTransferClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.dashboardFabRowHorizontalPadding,
                    end = Spacing.dashboardFabRowHorizontalPadding,
                    top = Spacing.dashboardFabRowVerticalPaddingTop,
                    bottom = Spacing.dashboardFabRowVerticalPaddingBottom,
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonOutlineFab(
            onClick = onMinusClick,
            color = MaterialTheme.colorScheme.dashboardActionExpense,
            icon = Icons.Filled.Remove,
            contentDescription = stringResource(R.string.fab_expense_content_description),
        )
        NeonOutlineFab(
            onClick = onTransferClick,
            color = MaterialTheme.colorScheme.dashboardActionTransfer,
            icon = Icons.Filled.SwapHoriz,
            contentDescription = stringResource(R.string.fab_transfer_content_description),
        )
        NeonOutlineFab(
            onClick = onPlusClick,
            color = MaterialTheme.colorScheme.dashboardActionIncome,
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.fab_income_content_description),
        )
    }
}

@Composable
private fun NeonOutlineFab(
    onClick: () -> Unit,
    color: Color,
    icon: ImageVector,
    contentDescription: String,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.dashboardNeonBackground,
        contentColor = color,
        shape = CircleShape,
        elevation =
            FloatingActionButtonDefaults.elevation(
                defaultElevation = Spacing.none,
                pressedElevation = Spacing.none,
            ),
        modifier =
            Modifier
                .size(Spacing.dashboardFabSize)
                .shadow(
                    elevation = Spacing.s,
                    shape = CircleShape,
                    ambientColor = color.copy(alpha = 0.4f),
                    spotColor = color.copy(alpha = 0.4f),
                ).border(
                    width = Spacing.dashboardFabOutlineWidth,
                    color = color,
                    shape = CircleShape,
                ),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(Spacing.dashboardFabIconSize),
        )
    }
}
