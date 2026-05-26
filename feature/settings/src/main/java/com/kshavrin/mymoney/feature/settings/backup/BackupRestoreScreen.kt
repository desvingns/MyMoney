package com.kshavrin.mymoney.feature.settings.backup

import android.content.Intent
import android.os.Process
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.settings.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupRestoreRoute(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val exportMessage = stringResource(R.string.backup_export_success)
    val csvExportMessage = stringResource(R.string.backup_export_csv_success)
    val csvImportMessage = stringResource(R.string.backup_import_csv_success)
    val restoreMessage = stringResource(R.string.backup_import_success)
    val resetMessage = stringResource(R.string.backup_reset_success)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.onEvent(BackupRestoreEvent.ExportFolderPicked(uri.toString()))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onEvent(BackupRestoreEvent.ImportFilePicked(uri.toString()))
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            viewModel.onEvent(BackupRestoreEvent.ExportCsvFilePicked(uri.toString()))
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onEvent(BackupRestoreEvent.ImportCsvFilePicked(uri.toString()))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                BackupRestoreAction.ExportSucceeded -> snackbarHostState.showSnackbar(exportMessage)
                BackupRestoreAction.CsvExportSucceeded -> snackbarHostState.showSnackbar(csvExportMessage)
                BackupRestoreAction.CsvImportSucceeded -> snackbarHostState.showSnackbar(csvImportMessage)
                BackupRestoreAction.RestartAfterRestore -> {
                    Toast.makeText(context, restoreMessage, Toast.LENGTH_LONG).show()
                    relaunchApplication(context)
                }
                is BackupRestoreAction.RestartToOnboardingAfterReset -> {
                    val message = if (action.hadFailures) {
                        context.getString(R.string.backup_reset_error)
                    } else {
                        resetMessage
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    relaunchApplication(context)
                }
            }
        }
    }

    BackupRestoreContent(
        state = state,
        onExport = { exportLauncher.launch(null) },
        onImport = { importLauncher.launch(arrayOf(IMPORT_MIME_TYPE)) },
        onExportCsv = { csvExportLauncher.launch(csvExportName()) },
        onImportCsv = { csvImportLauncher.launch(arrayOf(CSV_MIME_TYPE, TEXT_MIME_TYPE)) },
        onRequestReset = { viewModel.onEvent(BackupRestoreEvent.ResetRequested) },
        onDismissReset = { viewModel.onEvent(BackupRestoreEvent.ResetCancelled) },
        onConfirmReset = { viewModel.onEvent(BackupRestoreEvent.ResetConfirmed) },
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreContent(
    state: BackupRestoreState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onRequestReset: () -> Unit = {},
    onDismissReset: () -> Unit = {},
    onConfirmReset: () -> Unit = {},
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                text = stringResource(R.string.backup_section_local_file),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onExport,
                enabled = !state.inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export_db))
            }
            OutlinedButton(
                onClick = onImport,
                enabled = !state.inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_import_db))
            }
            OutlinedButton(
                onClick = onExportCsv,
                enabled = !state.inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_export_csv))
            }
            OutlinedButton(
                onClick = onImportCsv,
                enabled = !state.inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_import_csv))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s))
            Text(
                text = stringResource(R.string.backup_section_reset),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.backup_size_label,
                    Formatter.formatShortFileSize(context, state.dbSizeBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRequestReset,
                enabled = !state.inProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_reset_cta))
            }
            state.errorBannerRes?.let { res ->
                Text(
                    text = stringResource(res),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    if (state.resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDismissReset,
            title = { Text(stringResource(R.string.backup_reset_title)) },
            text = { Text(stringResource(R.string.delete_database_message)) },
            confirmButton = {
                Button(
                    onClick = onConfirmReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.backup_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReset) {
                    Text(stringResource(R.string.backup_reset_cancel))
                }
            },
        )
    }
}

private const val IMPORT_MIME_TYPE = "application/octet-stream"
private const val CSV_MIME_TYPE = "text/csv"
private const val TEXT_MIME_TYPE = "text/plain"
private val CSV_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneId.systemDefault())

private fun csvExportName(): String =
    "mymoney_transactions_${CSV_TIMESTAMP_FORMATTER.format(Instant.now())}.csv"

private fun relaunchApplication(context: android.content.Context) {
    val component = checkNotNull(
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component,
    )
    context.startActivity(Intent.makeRestartActivityTask(component))
    Process.killProcess(Process.myPid())
}
