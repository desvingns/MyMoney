package com.kshavrin.mymoney.feature.support.googlesignin

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.support.R

@Composable
fun GoogleSignInDialog(
    activity: Activity,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: GoogleSignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.status) {
        if (state.status == GoogleSignInStatus.Success) {
            onSignedIn()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        GoogleSignInDialogContent(
            status = state.status,
            onSignIn = { viewModel.signIn(activity) },
            onCancel = onDismiss,
        )
    }
}

@Composable
fun GoogleSignInDialogContent(
    status: GoogleSignInStatus,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Spacing.l),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xs,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                text = stringResource(R.string.support_google_sign_in_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (status) {
                GoogleSignInStatus.Loading -> LoadingBody()
                GoogleSignInStatus.Error -> ErrorBody()
                GoogleSignInStatus.Idle,
                GoogleSignInStatus.Success,
                ->
                    Text(
                        text = stringResource(R.string.support_google_sign_in_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = Spacing.minimumTouchTargetSize),
                ) {
                    Text(
                        text = stringResource(R.string.support_google_sign_in_cancel),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = onSignIn,
                    enabled = status != GoogleSignInStatus.Loading,
                    modifier = Modifier.heightIn(min = Spacing.minimumTouchTargetSize),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (status == GoogleSignInStatus.Error) {
                                    R.string.support_google_sign_in_retry
                                } else {
                                    R.string.support_google_sign_in_action
                                },
                            ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.support_google_sign_in_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBody() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.s),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            text = stringResource(R.string.support_google_sign_in_error),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
