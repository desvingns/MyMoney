package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val soundPlayer = LocalSoundPlayer.current

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            soundPlayer.play(SoundKey.DELETE)
            onDelete()
            // The row stays soft-deleted only after the UNDO window lapses; reset the visual
            // so the same item position is reusable if the user taps UNDO and paging re-emits.
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = Spacing.l),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.transactions_list_delete),
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
                Text(
                    text = stringResource(R.string.transactions_list_delete),
                    color = MaterialTheme.colorScheme.onTertiary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
        },
    ) {
        content()
    }
}
