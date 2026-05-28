package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun ConflictResolutionDialog(
    remoteTimestamp: String,
    localTimestamp: String,
    onKeepRemote: () -> Unit,
    onKeepLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.sync_conflict_body))
                Text(
                    text = stringResource(R.string.sync_conflict_remote, remoteTimestamp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.sync_conflict_local, localTimestamp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
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
