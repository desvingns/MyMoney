package com.kshavrin.mymoney.feature.cloudsync

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.android.AuthActivity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.sync.BuildConfig as SyncBuildConfig
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.toCloudProvider
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var dropboxLaunchSequence by rememberSaveable { mutableStateOf(0) }
    var pendingDropboxLaunchSequence by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingGoogleAccountEmail by rememberSaveable { mutableStateOf<String?>(null) }
    val googleAuthorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val migrationBackupDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) {
                viewModel.onEvent(CloudSyncEvent.CancelMigration)
            } else {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }.onFailure {
                    it.reportToSentry()
                    viewModel.onEvent(CloudSyncEvent.CancelMigration)
                }.onSuccess {
                    viewModel.onEvent(CloudSyncEvent.MigrationBackupDirectorySelected(treeUri.toString()))
                }
            }
        }
    val googleAuthorizationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val authorizationResult =
                if (result.resultCode == Activity.RESULT_OK) {
                    runCatching {
                        googleAuthorizationClient.getAuthorizationResultFromIntent(result.data)
                    }.onFailure { it.reportToSentry() }.getOrNull()
                } else {
                    null
                }
            if (authorizationResult == null || pendingGoogleAccountEmail.isNullOrBlank()) {
                viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
            } else {
                viewModel.onEvent(
                    CloudSyncEvent.AuthenticationCompleted(
                        target = SyncTarget.GoogleDrive,
                        payload = pendingGoogleAccountEmail.orEmpty(),
                    ),
                )
            }
        }

    fun launchGoogleAuthorization(accountEmail: String) {
        googleAuthorizationClient
            .authorize(googleDriveAuthorizationRequest(accountEmail))
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let { pendingIntent ->
                        runCatching {
                            googleAuthorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                            )
                        }.onFailure {
                            it.reportToSentry()
                            viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
                        }
                    } ?: viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
                } else {
                    viewModel.onEvent(
                        CloudSyncEvent.AuthenticationCompleted(
                            target = SyncTarget.GoogleDrive,
                            payload = accountEmail,
                        ),
                    )
                }
            }.addOnFailureListener {
                it.reportToSentry()
                viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
            }
    }

    val accountPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim()
            if (result.resultCode != Activity.RESULT_OK || email.isNullOrBlank()) {
                viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
            } else {
                pendingGoogleAccountEmail = email
                launchGoogleAuthorization(email)
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && pendingDropboxLaunchSequence != null) {
                    val completedLaunchSequence = pendingDropboxLaunchSequence ?: return@LifecycleEventObserver
                    pendingDropboxLaunchSequence = null
                    if (completedLaunchSequence != dropboxLaunchSequence) return@LifecycleEventObserver
                    val credential = runCatching { Auth.getDbxCredential() }.getOrNull()
                    viewModel.onEvent(
                        credential?.let {
                            CloudSyncEvent.AuthenticationCompleted(SyncTarget.Dropbox, it.toString())
                        } ?: CloudSyncEvent.AuthenticationFailed,
                    )
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CloudSyncAction.NavigateBack -> onBack()
                CloudSyncAction.LaunchDropboxAuth -> {
                    if (SyncBuildConfig.DROPBOX_APP_KEY == DROPBOX_APP_KEY_PLACEHOLDER) {
                        viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
                    } else {
                        runCatching {
                            AuthActivity.result = null
                            dropboxLaunchSequence += 1
                            pendingDropboxLaunchSequence = dropboxLaunchSequence
                            Auth.startOAuth2PKCE(
                                context,
                                SyncBuildConfig.DROPBOX_APP_KEY,
                                DbxRequestConfig.newBuilder(DROPBOX_CLIENT_IDENTIFIER).build(),
                                DROPBOX_OAUTH_SCOPES,
                            )
                        }.onFailure {
                            pendingDropboxLaunchSequence = null
                            viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
                        }
                    }
                }
                CloudSyncAction.RequestMigrationBackupDirectory ->
                    migrationBackupDirectoryLauncher.launch(null)
                CloudSyncAction.LaunchGoogleDriveAuth ->
                    runCatching {
                        accountPickerLauncher.launch(
                            AccountPicker.newChooseAccountIntent(
                                AccountPicker.AccountChooserOptions
                                    .Builder()
                                    .setAllowableAccountsTypes(listOf(GOOGLE_ACCOUNT_TYPE))
                                    .build(),
                            ),
                        )
                    }.onFailure {
                        it.reportToSentry()
                        viewModel.onEvent(CloudSyncEvent.AuthenticationFailed)
                    }
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
    when (val migration = state.migration) {
        is MigrationUiState.AwaitingBackup ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.CancelMigration) },
                title = { Text(stringResource(R.string.sync_migration_backup_title)) },
                text = { Text(stringResource(R.string.sync_migration_backup_body, providerName(migration.target))) },
                confirmButton = {
                    TextButton(onClick = { onEvent(CloudSyncEvent.CancelMigration) }) {
                        Text(stringResource(R.string.sync_migration_cancel))
                    }
                }
            )
        is MigrationUiState.Reviewing ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.CancelMigration) },
                title = { Text(stringResource(R.string.sync_migration_review_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.sync_migration_review_body,
                            providerName(migration.target),
                            migration.conflictCount,
                        ),
                    )
                },
                confirmButton = {
                    Button(onClick = { onEvent(CloudSyncEvent.ConfirmMigration(MigrationResolution.UseTarget)) }) {
                        Text(stringResource(R.string.sync_migration_use_target))
                    }
                },
                dismissButton = {
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { onEvent(CloudSyncEvent.ConfirmMigration(MigrationResolution.KeepLocal)) }) {
                            Text(stringResource(R.string.sync_migration_keep_local))
                        }
                        TextButton(onClick = { onEvent(CloudSyncEvent.CancelMigration) }) {
                            Text(stringResource(R.string.sync_migration_cancel))
                        }
                    }
                },
            )
        null -> Unit
    }
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
            BindingStatusCard(state = state)
            ProviderCard(
                title = stringResource(R.string.sync_dropbox_section),
                card = state.dropbox,
                bindingProvider = state.binding?.provider,
                isConnecting = state.isConnecting,
                onEvent = onEvent,
            )
            ProviderCard(
                title = stringResource(R.string.sync_gdrive_section),
                card = state.drive,
                bindingProvider = state.binding?.provider,
                isConnecting = state.isConnecting,
                onEvent = onEvent,
            )
            state.errorBannerRes?.let { res ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(res),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEvent(CloudSyncEvent.DismissError) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.sync_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingStatusCard(state: CloudSyncState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.sync_status_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    state.binding?.let { binding ->
                        stringResource(R.string.sync_active_binding, providerName(binding.provider), binding.accountLabel)
                    } ?: stringResource(R.string.sync_no_active_binding),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    state.lastSyncAtMs?.let { ms ->
                        stringResource(R.string.sync_last_at, formatTimestamp(ms))
                    } ?: stringResource(R.string.sync_last_never),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.requiresProviderChoice) {
                Text(
                    text = stringResource(R.string.sync_provider_choice_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    title: String,
    card: TargetCardState,
    bindingProvider: CloudProvider?,
    isConnecting: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    val isActive = bindingProvider == card.target.toCloudProvider()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = card.accountLabel ?: stringResource(R.string.sync_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                isActive ->
                    OutlinedButton(
                        modifier = Modifier.testTag(card.target.controlTag("disconnect")),
                        onClick = { onEvent(CloudSyncEvent.DisconnectClicked(card.target)) },
                        enabled = !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_disconnect))
                    }
                bindingProvider != null ->
                    OutlinedButton(
                        modifier = Modifier.testTag(card.target.controlTag("switch")),
                        onClick = { onEvent(CloudSyncEvent.SwitchClicked(card.target)) },
                        enabled = card.enabled && !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_switch))
                    }
                card.connected ->
                    Button(
                        modifier = Modifier.testTag(card.target.controlTag("use_connected")),
                        onClick = { onEvent(CloudSyncEvent.UseConnectedProviderClicked(card.target)) },
                        enabled = card.enabled && !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_use_connected))
                    }
                else ->
                    Button(
                        modifier = Modifier.testTag(card.target.controlTag("connect")),
                        onClick = { onEvent(CloudSyncEvent.ConnectClicked(card.target)) },
                        enabled = card.enabled && !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_connect))
                    }
            }
        }
    }
}

@Composable
private fun providerName(target: SyncTarget): String =
    stringResource(
        when (target) {
            SyncTarget.Dropbox -> R.string.sync_dropbox_section
            SyncTarget.GoogleDrive -> R.string.sync_gdrive_section
        },
    )

@Composable
private fun providerName(provider: CloudProvider): String =
    stringResource(
        when (provider) {
            CloudProvider.Dropbox -> R.string.sync_dropbox_section
            CloudProvider.GoogleDrive -> R.string.sync_gdrive_section
        },
    )

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun googleDriveAuthorizationRequest(accountEmail: String): AuthorizationRequest {
    val policy = googleDriveAuthorizationPolicy(accountEmail)
    val builder =
        AuthorizationRequest
        .builder()
        .setRequestedScopes(policy.scopeUris.map(::Scope))
        .setAccount(Account(policy.accountName, policy.accountType))
        .setOptOutIncludingGrantedScopes(true)
    if (policy.requestsConsentPrompt) {
        builder.setPrompt(AuthorizationRequest.Prompt.CONSENT)
    }
    return builder.build()
}

internal data class GoogleDriveAuthorizationPolicy(
    val accountName: String,
    val accountType: String,
    val scopeUris: List<String>,
    val requestsConsentPrompt: Boolean,
)

internal fun googleDriveAuthorizationPolicy(accountEmail: String): GoogleDriveAuthorizationPolicy =
    GoogleDriveAuthorizationPolicy(
        accountName = accountEmail,
        accountType = GOOGLE_ACCOUNT_TYPE,
        scopeUris = listOf(DriveScopes.DRIVE_APPDATA),
        requestsConsentPrompt = false,
    )

private const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val DROPBOX_APP_KEY_PLACEHOLDER = "PLACEHOLDER_DROPBOX_APP_KEY"
private const val DROPBOX_CLIENT_IDENTIFIER = "MyMoney/1.0"
private val DROPBOX_OAUTH_SCOPES =
    listOf(
        "account_info.read",
        "files.metadata.read",
        "files.content.read",
        "files.content.write",
    )

private fun SyncTarget.controlTag(control: String): String =
    when (this) {
        SyncTarget.Dropbox -> "cloud_sync_dropbox_$control"
        SyncTarget.GoogleDrive -> "cloud_sync_google_drive_$control"
    }
