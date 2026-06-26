package com.kshavrin.mymoney.feature.cloudsync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    viewModel: CloudSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CloudSyncAction.NavigateBack -> onBack()
                CloudSyncAction.LaunchDropboxAuth -> Unit
                CloudSyncAction.LaunchGoogleSignIn -> Unit
            }
        }
    }

    CloudSyncContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncContent(
    state: CloudSyncState,
    onEvent: (CloudSyncEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CloudSyncEvent.BackClicked) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sync_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            JournalStatusCard(state = state, onEvent = onEvent)
            TargetCard(
                title = stringResource(R.string.sync_dropbox_section),
                card = state.dropbox,
                onEvent = onEvent,
            )
            TargetCard(
                title = stringResource(R.string.sync_gdrive_section),
                card = state.drive,
                onEvent = onEvent,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.sync_auto_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    modifier = Modifier.testTag(CLOUD_SYNC_AUTO_SYNC_TAG),
                    checked = state.autoSyncEnabled,
                    onCheckedChange = { onEvent(CloudSyncEvent.AutoSyncToggled(it)) },
                )
            }
            state.errorBannerRes?.let { res ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(res),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEvent(CloudSyncEvent.DismissError) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.sync_dismiss),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalStatusCard(
    state: CloudSyncState,
    onEvent: (CloudSyncEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.sync_journal_status_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    state.lastSyncAtMs?.let { ms ->
                        stringResource(R.string.sync_last_at, formatTimestamp(ms))
                    } ?: stringResource(R.string.sync_last_never),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.folderId,
                onValueChange = { onEvent(CloudSyncEvent.FolderIdChanged(it)) },
                label = { Text(stringResource(R.string.sync_folder_id_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (state.folderId.isBlank()) {
                                R.string.sync_folder_not_configured
                            } else {
                                R.string.sync_folder_configured
                            },
                        ),
                    )
                },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CLOUD_SYNC_FOLDER_ID_TAG),
            )
            Button(
                modifier = Modifier.testTag(CLOUD_SYNC_SYNC_NOW_TAG),
                onClick = { onEvent(CloudSyncEvent.SyncNowClicked()) },
                enabled = state.folderId.isNotBlank() && !state.isSyncing,
            ) {
                if (state.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Spacing.l),
                        strokeWidth = Spacing.xxs,
                    )
                } else {
                    Text(stringResource(R.string.sync_now))
                }
            }
            Text(
                text = stringResource(R.string.sync_peer_devices),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.peerStatuses.isEmpty()) {
                Text(
                    text = stringResource(R.string.sync_peers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.peerStatuses.forEach { peer ->
                    PeerStatusRow(peer = peer)
                }
            }
        }
    }
}

@Composable
private fun PeerStatusRow(
    peer: PeerJournalState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.sync_peer_device, peer.deviceId),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        if (peer.upToDate) {
                            R.string.sync_peer_up_to_date
                        } else {
                            R.string.sync_peer_pending
                        },
                    ),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (peer.upToDate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
        Text(
            text = stringResource(R.string.sync_peer_modified_at, formatTimestamp(peer.modifiedAtMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                if (peer.pulledThroughMs > 0L) {
                    stringResource(R.string.sync_peer_pulled_through, formatTimestamp(peer.pulledThroughMs))
                } else {
                    stringResource(R.string.sync_peer_never_pulled)
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TargetCard(
    title: String,
    card: TargetCardState,
    onEvent: (CloudSyncEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = card.accountLabel ?: stringResource(R.string.sync_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.connected) {
                    OutlinedButton(
                        modifier = Modifier.testTag(card.target.controlTag("disconnect")),
                        onClick = { onEvent(CloudSyncEvent.DisconnectClicked(card.target)) },
                    ) {
                        Text(stringResource(R.string.sync_disconnect))
                    }
                } else {
                    Button(
                        modifier = Modifier.testTag(card.target.controlTag("connect")),
                        onClick = { onEvent(CloudSyncEvent.ConnectClicked(card.target)) },
                        enabled = card.enabled,
                    ) {
                        Text(stringResource(R.string.sync_connect))
                    }
                }
            }
        }
    }
}

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private const val CLOUD_SYNC_AUTO_SYNC_TAG = "cloud_sync_auto_sync"
private const val CLOUD_SYNC_FOLDER_ID_TAG = "cloud_sync_folder_id"
private const val CLOUD_SYNC_SYNC_NOW_TAG = "cloud_sync_sync_now"

private fun SyncTarget.controlTag(control: String): String =
    when (this) {
        SyncTarget.Dropbox -> "cloud_sync_dropbox_$control"
        SyncTarget.GoogleDrive -> "cloud_sync_google_drive_$control"
    }
