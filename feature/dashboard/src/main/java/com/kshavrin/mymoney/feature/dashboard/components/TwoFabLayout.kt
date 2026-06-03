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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun TwoFabLayout(
    onMinusClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val expenseColor = MaterialTheme.colorScheme.tertiary
        val incomeColor = MaterialTheme.colorScheme.primary
        val containerColor = MaterialTheme.colorScheme.onPrimary

        FloatingActionButton(
            onClick = onMinusClick,
            containerColor = containerColor,
            contentColor = expenseColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            modifier = Modifier
                .size(96.dp)
                .border(width = 7.dp, color = expenseColor, shape = CircleShape),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.fab_expense),
                modifier = Modifier.size(48.dp),
            )
        }

        FloatingActionButton(
            onClick = onPlusClick,
            containerColor = containerColor,
            contentColor = incomeColor,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            modifier = Modifier
                .size(96.dp)
                .border(width = 7.dp, color = incomeColor, shape = CircleShape),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.fab_income),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
