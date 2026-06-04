package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardActionExpense
import com.kshavrin.mymoney.core.ui.theme.dashboardActionIncome
import com.kshavrin.mymoney.core.ui.theme.dashboardFabLabel
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun TwoFabLayout(
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier,
    expenseLabel: String = stringResource(R.string.fab_expense_label),
    incomeLabel: String = stringResource(R.string.fab_income_label),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.dashboardFabHorizontalPadding, vertical = Spacing.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        val expenseColor = MaterialTheme.colorScheme.dashboardActionExpense
        val incomeColor = MaterialTheme.colorScheme.dashboardActionIncome
        val containerColor = MaterialTheme.colorScheme.onPrimary

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = onMinusClick,
                containerColor = containerColor,
                contentColor = expenseColor,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = Spacing.none,
                    pressedElevation = Spacing.none,
                ),
                modifier = Modifier
                    .size(Spacing.dashboardFabSize)
                    .border(
                        width = Spacing.dashboardFabOutlineWidth,
                        color = expenseColor,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.fab_expense),
                    modifier = Modifier.size(Spacing.xxl),
                )
            }
            Text(
                text = expenseLabel,
                style = MaterialTheme.typography.dashboardFabLabel,
                color = expenseColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.dashboardFabLabelTopPadding),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(
                onClick = onPlusClick,
                containerColor = containerColor,
                contentColor = incomeColor,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = Spacing.none,
                    pressedElevation = Spacing.none,
                ),
                modifier = Modifier
                    .size(Spacing.dashboardFabSize)
                    .border(
                        width = Spacing.dashboardFabOutlineWidth,
                        color = incomeColor,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.fab_income),
                    modifier = Modifier.size(Spacing.xxl),
                )
            }
            Text(
                text = incomeLabel,
                style = MaterialTheme.typography.dashboardFabLabel,
                color = incomeColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.dashboardFabLabelTopPadding),
            )
        }
    }
}
