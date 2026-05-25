package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun ConflictResolutionDialog(
    onKeepRemote: () -> Unit,
    onKeepLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_conflict_title)) },
        text = { Text(stringResource(R.string.sync_conflict_body)) },
        confirmButton = {
            TextButton(onClick = onKeepRemote) {
                Text(stringResource(R.string.sync_keep_remote))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepLocal) {
                Text(stringResource(R.string.sync_keep_local))
            }
        },
    )
}
