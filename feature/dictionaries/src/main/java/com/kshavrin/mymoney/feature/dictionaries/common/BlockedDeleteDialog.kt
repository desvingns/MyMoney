package com.kshavrin.mymoney.feature.dictionaries.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.feature.dictionaries.R

@Composable
fun BlockedDeleteDialog(
    entityName: String,
    transactionCount: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dictionaries_blocked_delete_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.dictionaries_blocked_delete_message,
                    transactionCount,
                    entityName,
                    transactionCount,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dictionaries_ok))
            }
        },
    )
}
