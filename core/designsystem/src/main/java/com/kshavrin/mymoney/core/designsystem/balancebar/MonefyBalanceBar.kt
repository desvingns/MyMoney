package com.kshavrin.mymoney.core.designsystem.balancebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.R

@Suppress("UNUSED_PARAMETER")
@Composable
fun MonefyBalanceBar(
    amount: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val label = stringResource(R.string.balance_bar_label)
    val pillColor = MaterialTheme.colorScheme.primary
    val pillContent = MaterialTheme.colorScheme.onPrimary
    val flankTint = pillColor.copy(alpha = 0.7f)

    Row(
        modifier = modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = flankTint,
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            color = pillColor,
            contentColor = pillContent,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "$label $amount",
                style = MaterialTheme.typography.titleMedium,
                color = pillContent,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = flankTint,
        )
    }
}
