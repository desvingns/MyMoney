package com.kshavrin.mymoney.feature.cloudsync

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.services.drive.DriveScopes
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceSummary
import com.kshavrin.mymoney.core.sync.toCloudProvider
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.conflictAuthorContainer
import com.kshavrin.mymoney.core.ui.theme.conflictAuthorLabel
import com.kshavrin.mymoney.core.ui.theme.conflictLocalContainer
import com.kshavrin.mymoney.core.ui.theme.conflictLocalContent
import com.kshavrin.mymoney.core.ui.theme.conflictRemoteContainer
import com.kshavrin.mymoney.core.ui.theme.conflictRemoteContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncConnectedContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncConnectedContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncErrorContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncErrorContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncEntitlementWarningContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncEntitlementWarningContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncReadOnlyContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncReadOnlyContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncRetryingContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncRetryingContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncSleepingContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncSleepingContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncStartingContainer
import com.kshavrin.mymoney.core.ui.theme.sharedSyncStartingContent
import com.kshavrin.mymoney.core.ui.theme.sharedSyncStatusOutline
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.kshavrin.mymoney.core.network.BuildConfig as NetworkBuildConfig
import com.kshavrin.mymoney.core.sync.BuildConfig as SyncBuildConfig

@Composable
fun CloudSyncRoute(
    onBack: () -> Unit,
    onOpenPaywall: (PaywallEntryPoint) -> Unit = {},
    viewModel: CloudSyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var dropboxLaunchSequence by rememberSaveable { mutableStateOf(0) }
    var pendingDropboxLaunchSequence by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingGoogleAccountEmail by rememberSaveable { mutableStateOf<String?>(null) }
    val googleAuthorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val sharedCredentialManager = remember(context) { CredentialManager.create(context) }
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

    suspend fun launchSharedGoogleSignIn() {
        val activity = context.findActivity()
        val webClientId = sharedGoogleWebClientIdOrNull(NetworkBuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID)
        if (activity == null || webClientId == null) {
            viewModel.onEvent(CloudSyncEvent.SharedSignInFailed)
            return
        }
        val nonce = generateGoogleNonce()
        val result =
            try {
                sharedCredentialManager.getCredential(
                    context = activity,
                    request =
                        sharedGoogleCredentialRequest(
                            webClientId,
                            sharedGoogleCredentialNonce(nonce),
                            authorizedAccountsOnly = true,
                        ),
                )
            } catch (_: NoCredentialException) {
                try {
                    sharedCredentialManager.getCredential(
                        context = activity,
                        request =
                            sharedGoogleCredentialRequest(
                                webClientId,
                                sharedGoogleCredentialNonce(nonce),
                                authorizedAccountsOnly = false,
                            ),
                    )
                } catch (t: Throwable) {
                    t.reportToSentry()
                    viewModel.onEvent(CloudSyncEvent.SharedSignInFailed)
                    return
                }
            } catch (t: Throwable) {
                t.reportToSentry()
                viewModel.onEvent(CloudSyncEvent.SharedSignInFailed)
                return
            }
        val credential = result.credential as? CustomCredential
        val idToken =
            credential
                ?.takeIf { it.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL }
                ?.let {
                    runCatching { GoogleIdTokenCredential.createFrom(it.data).idToken }
                        .onFailure(Throwable::reportToSentry)
                        .getOrNull()
                }
        if (idToken.isNullOrBlank()) {
            viewModel.onEvent(CloudSyncEvent.SharedSignInFailed)
        } else {
            viewModel.onEvent(CloudSyncEvent.SharedSignInCompleted(idToken, nonce))
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

    DisposableEffect(lifecycleOwner, viewModel) {
        viewModel.onEvent(CloudSyncEvent.SharedRealtimeForegroundStarted)
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onEvent(CloudSyncEvent.SharedRealtimeForegroundStarted)
                    Lifecycle.Event.ON_STOP -> viewModel.onEvent(CloudSyncEvent.SharedRealtimeForegroundStopped)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onEvent(CloudSyncEvent.SharedRealtimeForegroundStopped)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CloudSyncAction.NavigateBack -> onBack()
                is CloudSyncAction.NavigateToPaywall -> onOpenPaywall(action.entryPoint)
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
                CloudSyncAction.LaunchSharedGoogleSignIn -> launchSharedGoogleSignIn()
                CloudSyncAction.ClearSharedGoogleCredentialState ->
                    runCatching {
                        sharedCredentialManager.clearCredentialState(ClearCredentialStateRequest())
                    }.onFailure(Throwable::reportToSentry)
                CloudSyncAction.RestartAfterInternalBackupRestore -> relaunchApplication(context)
                is CloudSyncAction.CopySharedInvite ->
                    context
                        .getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.sync_shared_invite_clip_label),
                                action.token,
                            ),
                        )
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
                },
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
    SharedDialogHost(state = state, onEvent = onEvent)
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
            if (state.shared.enabled) {
                state.shared.warning
                    ?.takeIf { state.shared.showsEntitlementWarning() }
                    ?.let { warning ->
                        SharedEntitlementWarningBanner(
                            state = state.shared,
                            warning = warning,
                            onEvent = onEvent,
                        )
                    }
                SharedCard(
                    state = state.shared,
                    otherProviderActive = state.binding != null && state.binding.provider != CloudProvider.Shared,
                    showInternalBackups = state.binding == null || state.binding.provider == CloudProvider.Shared,
                    isConnecting = state.isConnecting,
                    onEvent = onEvent,
                )
            }
            state.errorBannerRes
                ?.let { res ->
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
private fun SharedCard(
    state: SharedCardState,
    otherProviderActive: Boolean,
    showInternalBackups: Boolean,
    isConnecting: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    val isWriteAllowed = state.isWorkspaceAccessKnown && !state.isWorkspaceReadOnly
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.sync_shared_section), style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    when {
                        state.isLocalOnly ->
                            stringResource(R.string.sync_shared_local_only_active, state.workspaceName.orEmpty())
                        state.active ->
                            stringResource(R.string.sync_shared_active, state.workspaceName.orEmpty())
                        state.signedIn ->
                            stringResource(R.string.sync_shared_signed_in, state.accountEmail.orEmpty())
                        else -> stringResource(R.string.sync_shared_signed_out)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.active) {
                if (state.isLocalOnly) {
                    SharedLocalOnlyBanner(onEvent)
                } else {
                    if (!isWriteAllowed) SharedReadOnlyBanner(state.isWorkspaceAccessKnown)
                    SharedRealtimeStatusCard(
                        status = state.realtimeStatus,
                        enabled = isWriteAllowed,
                        onEvent = onEvent,
                    )
                }
            }
            when {
                otherProviderActive ->
                    Text(
                        text = stringResource(R.string.sync_shared_other_provider_active),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                !state.signedIn ->
                    Button(
                        modifier = Modifier.testTag(SyncTarget.Shared.controlTag("sign_in")),
                        onClick = { onEvent(CloudSyncEvent.SharedSignInClicked) },
                        enabled = state.enabled && !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_shared_sign_in))
                    }
                state.active -> {
                    if (state.conflictCount > 0) {
                        OutlinedButton(
                            modifier = Modifier.testTag(SyncTarget.Shared.controlTag("conflicts")),
                            onClick = { onEvent(CloudSyncEvent.SharedConflictsClicked) },
                            enabled = !isConnecting && isWriteAllowed,
                        ) {
                            Text(stringResource(R.string.sync_shared_review_conflicts, state.conflictCount))
                        }
                    }
                    Button(
                        modifier = Modifier.testTag(SyncTarget.Shared.controlTag("sync_now")),
                        onClick = { onEvent(CloudSyncEvent.SharedSyncNowClicked) },
                        enabled = !isConnecting && isWriteAllowed,
                    ) {
                        Text(stringResource(R.string.sync_shared_sync_now))
                    }
                    OutlinedButton(
                        modifier = Modifier.testTag(SyncTarget.Shared.controlTag("create_invite")),
                        onClick = { onEvent(CloudSyncEvent.SharedCreateInviteClicked) },
                        enabled = !isConnecting && isWriteAllowed,
                    ) {
                        Text(stringResource(R.string.sync_shared_create_invite))
                    }
                    OutlinedButton(
                        modifier = Modifier.testTag(SyncTarget.Shared.controlTag("disconnect")),
                        onClick = { onEvent(CloudSyncEvent.SharedDisconnectClicked) },
                        enabled = !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_shared_disconnect))
                    }
                    if (!state.isSoleOwner) {
                        OutlinedButton(
                            modifier = Modifier.testTag(SyncTarget.Shared.controlTag("leave")),
                            onClick = { onEvent(CloudSyncEvent.SharedLeaveClicked) },
                            enabled = !isConnecting,
                        ) {
                            Text(stringResource(R.string.sync_shared_leave))
                        }
                    }
                }
                else ->
                    Button(
                        modifier = Modifier.testTag(SyncTarget.Shared.controlTag("setup")),
                        onClick = { onEvent(CloudSyncEvent.SharedSetupClicked) },
                        enabled = !isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_shared_setup))
                    }
            }
            if (state.signedIn) {
                OutlinedButton(
                    modifier = Modifier.testTag(SyncTarget.Shared.controlTag("delete_account")),
                    onClick = { onEvent(CloudSyncEvent.SharedDeleteAccountClicked) },
                    enabled = !isConnecting,
                ) {
                    Text(stringResource(R.string.sync_shared_delete_account))
                }
            }
            if (showInternalBackups) {
                OutlinedButton(
                    modifier = Modifier.testTag(SyncTarget.Shared.controlTag("backups")),
                    onClick = { onEvent(CloudSyncEvent.SharedInternalBackupsClicked) },
                    enabled = !isConnecting,
                ) {
                    Text(stringResource(R.string.sync_shared_recovery_backups))
                }
            }
        }
    }
}

@Composable
private fun SharedLocalOnlyBanner(
    onEvent: (CloudSyncEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.sharedSyncReadOnlyContainer,
                contentColor = MaterialTheme.colorScheme.sharedSyncReadOnlyContent,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.sync_shared_local_only_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.sync_shared_local_only_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onEvent(CloudSyncEvent.SharedInternalBackupsClicked) }) {
                Text(stringResource(R.string.sync_shared_local_only_backups))
            }
        }
    }
}

@Composable
private fun SharedEntitlementWarningBanner(
    state: SharedCardState,
    warning: EntitlementWarning,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    val title =
        when (warning) {
            EntitlementWarning.TRIAL_ENDING_3D -> R.string.sync_shared_warning_trial_title
            EntitlementWarning.GRACE_ENTERED -> R.string.sync_shared_warning_grace_title
            EntitlementWarning.EXPIRY_IMMINENT_1D -> R.string.sync_shared_warning_expiry_title
        }
    val body =
        when (warning) {
            EntitlementWarning.TRIAL_ENDING_3D -> stringResource(R.string.sync_shared_warning_trial_body)
            EntitlementWarning.GRACE_ENTERED ->
                stringResource(
                    R.string.sync_shared_warning_grace_body,
                    state.entitlementGraceEndsAt?.let { formatTimestamp(it.toEpochMilli()) }.orEmpty(),
                )

            EntitlementWarning.EXPIRY_IMMINENT_1D -> stringResource(R.string.sync_shared_warning_expiry_body)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.sharedSyncEntitlementWarningContainer,
                contentColor = MaterialTheme.colorScheme.sharedSyncEntitlementWarningContent,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (state.isWorkspaceOwner || (!state.active && state.entitlementState != EntitlementState.NONE)) {
                    Button(onClick = { onEvent(CloudSyncEvent.WarningActionClicked) }) {
                        Text(stringResource(R.string.sync_shared_warning_renew))
                    }
                }
                TextButton(onClick = { onEvent(CloudSyncEvent.WarningDismissed) }) {
                    Text(stringResource(R.string.sync_dismiss))
                }
            }
        }
    }
}

@Composable
private fun SharedReadOnlyBanner(
    isWorkspaceAccessKnown: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.sharedSyncReadOnlyContainer,
                contentColor = MaterialTheme.colorScheme.sharedSyncReadOnlyContent,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text =
                    stringResource(
                        if (isWorkspaceAccessKnown) {
                            R.string.sync_shared_read_only_title
                        } else {
                            R.string.sync_shared_read_only_unavailable_title
                        },
                    ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    stringResource(
                        if (isWorkspaceAccessKnown) {
                            R.string.sync_shared_read_only_body
                        } else {
                            R.string.sync_shared_read_only_unavailable_body
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SharedRealtimeStatusCard(
    status: SharedRealtimeStatus,
    enabled: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    if (status == SharedRealtimeStatus.Inactive || status == SharedRealtimeStatus.EntitlementRequired) return
    val container =
        when (status) {
            SharedRealtimeStatus.Connected -> MaterialTheme.colorScheme.sharedSyncConnectedContainer
            SharedRealtimeStatus.Starting -> MaterialTheme.colorScheme.sharedSyncStartingContainer
            is SharedRealtimeStatus.Sleeping -> MaterialTheme.colorScheme.sharedSyncSleepingContainer
            is SharedRealtimeStatus.Retrying -> MaterialTheme.colorScheme.sharedSyncRetryingContainer
            SharedRealtimeStatus.Error -> MaterialTheme.colorScheme.sharedSyncErrorContainer
            SharedRealtimeStatus.Inactive,
            SharedRealtimeStatus.EntitlementRequired,
            -> return
        }
    val content =
        when (status) {
            SharedRealtimeStatus.Connected -> MaterialTheme.colorScheme.sharedSyncConnectedContent
            SharedRealtimeStatus.Starting -> MaterialTheme.colorScheme.sharedSyncStartingContent
            is SharedRealtimeStatus.Sleeping -> MaterialTheme.colorScheme.sharedSyncSleepingContent
            is SharedRealtimeStatus.Retrying -> MaterialTheme.colorScheme.sharedSyncRetryingContent
            SharedRealtimeStatus.Error -> MaterialTheme.colorScheme.sharedSyncErrorContent
            SharedRealtimeStatus.Inactive,
            SharedRealtimeStatus.EntitlementRequired,
            -> return
        }
    val statusText =
        when (status) {
            SharedRealtimeStatus.Connected -> stringResource(R.string.sync_shared_status_connected)
            SharedRealtimeStatus.Starting -> stringResource(R.string.sync_shared_status_starting)
            is SharedRealtimeStatus.Sleeping -> stringResource(R.string.sync_shared_status_sleeping)
            is SharedRealtimeStatus.Retrying -> stringResource(R.string.sync_shared_status_retrying, status.retryAttempt)
            SharedRealtimeStatus.Error -> stringResource(R.string.sync_shared_status_error)
            SharedRealtimeStatus.Inactive,
            SharedRealtimeStatus.EntitlementRequired,
            -> return
        }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("cloud_sync_shared_realtime_status"),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.sharedSyncStatusOutline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status == SharedRealtimeStatus.Error) {
                OutlinedButton(
                    modifier = Modifier.testTag("cloud_sync_shared_realtime_retry"),
                    onClick = { onEvent(CloudSyncEvent.SharedRetryRealtimeClicked) },
                    enabled = enabled,
                ) {
                    Text(stringResource(R.string.sync_shared_retry_realtime))
                }
            }
        }
    }
}

@Composable
private fun SharedDialogHost(
    state: CloudSyncState,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    when (state.sharedDialog) {
        SharedDialog.Setup -> SharedSetupDialog(importLocalData = state.importLocalData, onEvent = onEvent)
        is SharedDialog.RecoverRemoteWorkspace ->
            SharedRemoteWorkspaceRecoveryDialog(
                workspace = state.sharedDialog.workspace,
                importLocalData = state.importLocalData,
                isConnecting = state.isConnecting,
                onEvent = onEvent,
            )
        SharedDialog.Conflicts -> SharedConflictsDialog(conflicts = state.conflicts, onEvent = onEvent)
        SharedDialog.InternalBackups -> SharedInternalBackupsDialog(backups = state.internalBackups, onEvent = onEvent)
        SharedDialog.AdPlusExpired ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.AdPlusExpiryDeclined) },
                title = { Text(stringResource(R.string.sync_shared_ad_plus_expired_title)) },
                text = { Text(stringResource(R.string.sync_shared_ad_plus_expired_body)) },
                confirmButton = {
                    Button(onClick = { onEvent(CloudSyncEvent.AdPlusExpiryRenewClicked) }) {
                        Text(stringResource(R.string.sync_shared_ad_plus_expired_renew))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(CloudSyncEvent.AdPlusExpiryDeclined) }) {
                        Text(stringResource(R.string.sync_shared_ad_plus_expired_decline))
                    }
                },
            )
        is SharedDialog.Invite -> SharedInviteDialog(token = state.sharedDialog.token, onEvent = onEvent)
        is SharedDialog.ConfirmInternalBackupRestore ->
            SharedInternalBackupRestoreDialog(
                isConnecting = state.isConnecting,
                onEvent = onEvent,
            )
        SharedDialog.ConfirmDisconnect ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                title = { Text(stringResource(R.string.sync_shared_disconnect_title)) },
                text = { Text(stringResource(R.string.sync_shared_disconnect_body)) },
                confirmButton = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        TextButton(
                            modifier = Modifier.testTag("cloud_sync_shared_disconnect_keep_server_data"),
                            onClick = { onEvent(CloudSyncEvent.SharedConfirmDisconnectKeepServerData) },
                        ) {
                            Text(stringResource(R.string.sync_shared_disconnect_keep_server_data))
                        }
                        Button(
                            modifier = Modifier.testTag("cloud_sync_shared_disconnect_delete_workspace"),
                            onClick = { onEvent(CloudSyncEvent.SharedConfirmDisconnectDeleteWorkspace) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(stringResource(R.string.sync_shared_delete_workspace))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("cloud_sync_shared_disconnect_cancel"),
                        onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                    ) {
                        Text(stringResource(R.string.sync_migration_cancel))
                    }
                },
            )
        SharedDialog.ConfirmLeave ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                title = { Text(stringResource(R.string.sync_shared_leave_title)) },
                text = { Text(stringResource(R.string.sync_shared_leave_body)) },
                confirmButton = {
                    Button(onClick = { onEvent(CloudSyncEvent.SharedConfirmLeave) }) {
                        Text(stringResource(R.string.sync_shared_leave))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) }) {
                        Text(stringResource(R.string.sync_migration_cancel))
                    }
                },
            )
        SharedDialog.ConfirmDeleteAccount ->
            AlertDialog(
                onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                title = { Text(stringResource(R.string.sync_shared_delete_account_title)) },
                text = { Text(stringResource(R.string.sync_shared_delete_account_body)) },
                confirmButton = {
                    Button(
                        modifier = Modifier.testTag("cloud_sync_shared_delete_account_confirm"),
                        onClick = { onEvent(CloudSyncEvent.SharedConfirmDeleteAccount) },
                        enabled = !state.isConnecting,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Text(stringResource(R.string.sync_shared_delete_account))
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("cloud_sync_shared_delete_account_cancel"),
                        onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                        enabled = !state.isConnecting,
                    ) {
                        Text(stringResource(R.string.sync_migration_cancel))
                    }
                },
            )
        null -> Unit
    }
}

@Composable
private fun SharedInternalBackupsDialog(
    backups: List<BackupFile>,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_recovery_backups_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (backups.isEmpty()) {
                    Text(stringResource(R.string.sync_shared_recovery_backups_empty))
                } else {
                    backups.forEach { backup ->
                        OutlinedButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("cloud_sync_shared_backup_${backup.lastModifiedEpochMs}"),
                            onClick = { onEvent(CloudSyncEvent.SharedInternalBackupRestoreClicked(backup)) },
                        ) {
                            Text(
                                stringResource(
                                    R.string.sync_shared_recovery_backup_item,
                                    backup.name,
                                    formatTimestamp(backup.lastModifiedEpochMs),
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) }) {
                Text(stringResource(R.string.sync_dismiss))
            }
        },
    )
}

@Composable
private fun SharedInternalBackupRestoreDialog(
    isConnecting: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_restore_backup_title)) },
        text = { Text(stringResource(R.string.sync_shared_restore_backup_body)) },
        confirmButton = {
            Button(
                onClick = { onEvent(CloudSyncEvent.SharedConfirmInternalBackupRestore) },
                enabled = !isConnecting,
            ) {
                Text(stringResource(R.string.sync_shared_restore_backup_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                enabled = !isConnecting,
            ) {
                Text(stringResource(R.string.sync_shared_restore_backup_cancel))
            }
        },
    )
}

@Composable
private fun SharedInviteDialog(
    token: String,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.sync_shared_invite_body))
                Text(
                    modifier = Modifier.testTag("cloud_sync_shared_invite_token"),
                    text = token,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("cloud_sync_shared_copy_invite"),
                onClick = { onEvent(CloudSyncEvent.SharedCopyInviteClicked) },
            ) {
                Text(stringResource(R.string.sync_shared_copy_invite))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) }) {
                Text(stringResource(R.string.sync_dismiss))
            }
        },
    )
}

@Composable
private fun SharedSetupDialog(
    importLocalData: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_setup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag("cloud_sync_shared_name"),
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_shared_workspace_name)) },
                )
                Text(
                    text = stringResource(R.string.sync_shared_create_owner_pays),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().testTag("cloud_sync_shared_token"),
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sync_shared_invite_token)) },
                )
                Text(
                    text = stringResource(R.string.sync_shared_join_owner_pays),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.sync_shared_import_choice_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ImportChoiceRow(
                    selected = !importLocalData,
                    label = stringResource(R.string.sync_shared_import_none),
                    testTag = "cloud_sync_shared_import_none",
                    onClick = { onEvent(CloudSyncEvent.SharedImportChoiceChanged(false)) },
                )
                ImportChoiceRow(
                    selected = importLocalData,
                    label = stringResource(R.string.sync_shared_import_publish),
                    testTag = "cloud_sync_shared_import_publish",
                    onClick = { onEvent(CloudSyncEvent.SharedImportChoiceChanged(true)) },
                )
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("cloud_sync_shared_create"),
                onClick = { onEvent(CloudSyncEvent.SharedCreateWorkspace(name)) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.sync_shared_create))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(
                    modifier = Modifier.testTag("cloud_sync_shared_join"),
                    onClick = { onEvent(CloudSyncEvent.SharedJoinWorkspace(token)) },
                    enabled = token.isNotBlank(),
                ) {
                    Text(stringResource(R.string.sync_shared_join))
                }
                TextButton(onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) }) {
                    Text(stringResource(R.string.sync_migration_cancel))
                }
            }
        },
    )
}

@Composable
private fun SharedRemoteWorkspaceRecoveryDialog(
    workspace: SharedWorkspaceSummary,
    importLocalData: Boolean,
    isConnecting: Boolean,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_recovery_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.sync_shared_recovery_body, workspace.name))
                Text(
                    text = stringResource(R.string.sync_shared_import_choice_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ImportChoiceRow(
                    selected = !importLocalData,
                    label = stringResource(R.string.sync_shared_import_none),
                    testTag = "cloud_sync_shared_recovery_import_none",
                    onClick = { onEvent(CloudSyncEvent.SharedImportChoiceChanged(false)) },
                )
                ImportChoiceRow(
                    selected = importLocalData,
                    label = stringResource(R.string.sync_shared_import_publish),
                    testTag = "cloud_sync_shared_recovery_import_publish",
                    onClick = { onEvent(CloudSyncEvent.SharedImportChoiceChanged(true)) },
                )
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("cloud_sync_shared_recovery_confirm"),
                onClick = { onEvent(CloudSyncEvent.SharedConfirmRemoteWorkspaceRecovery) },
                enabled = !isConnecting,
            ) {
                Text(stringResource(R.string.sync_shared_recovery_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
                enabled = !isConnecting,
            ) {
                Text(stringResource(R.string.sync_migration_cancel))
            }
        },
    )
}

@Composable
private fun ImportChoiceRow(
    selected: Boolean,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SharedConflictsDialog(
    conflicts: List<ConflictUi>,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(CloudSyncEvent.SharedDialogDismissed) },
        title = { Text(stringResource(R.string.sync_shared_conflicts_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (conflicts.isEmpty()) {
                    Text(stringResource(R.string.sync_shared_no_conflicts))
                } else {
                    conflicts.forEach { conflict -> ConflictRow(conflict = conflict, onEvent = onEvent) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(CloudSyncEvent.SharedDialogDismissed) }) {
                Text(stringResource(R.string.sync_dismiss))
            }
        },
    )
}

@Composable
private fun ConflictRow(
    conflict: ConflictUi,
    onEvent: (CloudSyncEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(text = conflict.entityKind, style = MaterialTheme.typography.titleSmall)
        ConflictPanel(
            container = MaterialTheme.colorScheme.conflictLocalContainer,
            content = MaterialTheme.colorScheme.conflictLocalContent,
            authorId = conflict.localAuthorId,
            summary = conflict.localSummary,
            actionLabel = stringResource(R.string.sync_shared_keep_local),
            testTag = "cloud_sync_conflict_keep_local",
            onResolve = {
                onEvent(CloudSyncEvent.SharedResolveConflict(conflict.conflictId, conflict.localOperationId))
            },
        )
        ConflictPanel(
            container = MaterialTheme.colorScheme.conflictRemoteContainer,
            content = MaterialTheme.colorScheme.conflictRemoteContent,
            authorId = conflict.remoteAuthorId,
            summary = conflict.remoteSummary,
            actionLabel = stringResource(R.string.sync_shared_keep_remote),
            testTag = "cloud_sync_conflict_keep_remote",
            onResolve = {
                onEvent(CloudSyncEvent.SharedResolveConflict(conflict.conflictId, conflict.remoteOperationId))
            },
        )
    }
}

@Composable
private fun ConflictPanel(
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    authorId: String,
    summary: ConflictSummaryUi,
    actionLabel: String,
    testTag: String,
    onResolve: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(container)
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = stringResource(R.string.sync_shared_conflict_author, authorId),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.conflictAuthorLabel,
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.conflictAuthorContainer)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
        Text(
            text =
                when (summary) {
                    ConflictSummaryUi.Deleted -> stringResource(R.string.sync_shared_conflict_deleted)
                    is ConflictSummaryUi.Text -> summary.value
                },
            style = MaterialTheme.typography.bodySmall,
            color = content,
        )
        Button(modifier = Modifier.testTag(testTag), onClick = onResolve) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun providerName(target: SyncTarget): String =
    stringResource(
        when (target) {
            SyncTarget.Dropbox -> R.string.sync_dropbox_section
            SyncTarget.GoogleDrive -> R.string.sync_gdrive_section
            SyncTarget.Shared -> R.string.sync_shared_section
        },
    )

@Composable
private fun providerName(provider: CloudProvider): String =
    stringResource(
        when (provider) {
            CloudProvider.Dropbox -> R.string.sync_dropbox_section
            CloudProvider.GoogleDrive -> R.string.sync_gdrive_section
            CloudProvider.Shared -> R.string.sync_shared_section
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
private const val NONCE_BYTES = 32
private const val PLACEHOLDER_PREFIX = "PLACEHOLDER_"
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
        SyncTarget.Shared -> "cloud_sync_shared_$control"
    }

private fun relaunchApplication(context: Context) {
    val component = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)?.component)
    context.startActivity(Intent.makeRestartActivityTask(component))
    Process.killProcess(Process.myPid())
}

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun generateGoogleNonce(): String =
    ByteArray(NONCE_BYTES)
        .also(SecureRandom()::nextBytes)
        .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }

internal fun sharedGoogleCredentialNonce(rawNonce: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(rawNonce.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun normalizeSharedGoogleWebClientId(configuredClientId: String): String =
    configuredClientId
        .trim()
        .removeSurrounding("<", ">")
        .trim()

internal fun sharedGoogleWebClientIdOrNull(configuredClientId: String): String? =
    normalizeSharedGoogleWebClientId(configuredClientId)
        .takeIf { it.isNotBlank() && !it.startsWith(PLACEHOLDER_PREFIX) }

internal fun sharedGoogleCredentialRequest(
    webClientId: String,
    nonce: String,
    authorizedAccountsOnly: Boolean,
): GetCredentialRequest =
    GetCredentialRequest
        .Builder()
        .addCredentialOption(
            GetGoogleIdOption
                .Builder()
                .setFilterByAuthorizedAccounts(authorizedAccountsOnly)
                .setAutoSelectEnabled(authorizedAccountsOnly)
                .setServerClientId(normalizeSharedGoogleWebClientId(webClientId))
                .setNonce(nonce)
                .build(),
        ).build()
