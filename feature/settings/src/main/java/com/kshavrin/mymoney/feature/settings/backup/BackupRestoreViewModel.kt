package com.kshavrin.mymoney.feature.settings.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.database.DatabaseFileNames
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.BackupSchemaTooNewException
import com.kshavrin.mymoney.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val appSettingsRepository: AppSettingsRepository,
        private val secureStorage: SecureStorage,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BackupRestoreState())
        val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<BackupRestoreAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<BackupRestoreAction> = _actions.asSharedFlow()

        init {
            refreshSize()
        }

        fun onEvent(event: BackupRestoreEvent) {
            when (event) {
                is BackupRestoreEvent.ExportFolderPicked -> export(event.treeUriString)
                is BackupRestoreEvent.ImportFilePicked -> import(event.documentUriString)
                is BackupRestoreEvent.ExportCsvFilePicked -> exportCsv(event.documentUriString)
                is BackupRestoreEvent.ImportCsvFilePicked ->
                    _state.value = _state.value.copy(pendingImportWizardUri = event.documentUriString)
                BackupRestoreEvent.ImportWizardNavigationConsumed ->
                    _state.value = _state.value.copy(pendingImportWizardUri = null)
                BackupRestoreEvent.ResetRequested -> {
                    _state.value = _state.value.copy(resetConfirmationVisible = true, errorBannerRes = null)
                }
                BackupRestoreEvent.ResetCancelled -> {
                    _state.value = _state.value.copy(resetConfirmationVisible = false)
                }
                BackupRestoreEvent.ResetConfirmed -> {
                    if (_state.value.resetConfirmationVisible) reset()
                }
                BackupRestoreEvent.DismissError -> _state.value = _state.value.copy(errorBannerRes = null)
            }
        }

        private fun export(treeUriString: String) {
            viewModelScope.launch {
                _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
                backupRepository
                    .exportDb(treeUriString)
                    .onSuccess {
                        _state.value = _state.value.copy(inProgress = false)
                        refreshSize()
                        _actions.emit(BackupRestoreAction.ExportSucceeded)
                    }.onFailure { failure(it) }
            }
        }

        private fun exportCsv(documentUriString: String) {
            viewModelScope.launch {
                _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
                backupRepository
                    .exportTransactionsCsv(documentUriString)
                    .onSuccess {
                        _state.value = _state.value.copy(inProgress = false)
                        _actions.emit(BackupRestoreAction.CsvExportSucceeded)
                    }.onFailure { failure(it, R.string.backup_export_csv_error) }
            }
        }

        private fun import(documentUriString: String) {
            viewModelScope.launch {
                _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
                backupRepository
                    .importDb(documentUriString)
                    .onSuccess {
                        _state.value = _state.value.copy(inProgress = false)
                        _actions.emit(BackupRestoreAction.RestartAfterRestore)
                    }.onFailure { throwable ->
                        val banner =
                            if (throwable is BackupSchemaTooNewException) {
                                R.string.backup_import_version_too_new
                            } else {
                                R.string.backup_error
                            }
                        failure(throwable, banner)
                    }
            }
        }

        private fun reset() {
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(
                        inProgress = true,
                        resetConfirmationVisible = false,
                        errorBannerRes = null,
                    )
                val failures = mutableListOf<Throwable>()
                runCatching { appSettingsRepository.reset() }
                    .exceptionOrNull()
                    ?.let(failures::add)
                backupRepository
                    .clearDatabase()
                    .exceptionOrNull()
                    ?.let(failures::add)
                runCatching {
                    withContext(ioDispatcher) { secureStorage.clearAll() }
                }.exceptionOrNull()
                    ?.let(failures::add)
                failures.forEach { it.reportToSentry() }
                _state.value =
                    _state.value.copy(
                        inProgress = false,
                        errorBannerRes = if (failures.isEmpty()) null else R.string.backup_reset_error,
                    )
                _actions.emit(BackupRestoreAction.RestartToOnboardingAfterReset(hadFailures = failures.isNotEmpty()))
            }
        }

        private fun failure(
            throwable: Throwable,
            errorBannerRes: Int = R.string.backup_error,
        ) {
            throwable.reportToSentry()
            _state.value =
                _state.value.copy(
                    inProgress = false,
                    resetConfirmationVisible = false,
                    errorBannerRes = errorBannerRes,
                )
        }

        private fun refreshSize() {
            viewModelScope.launch {
                val size =
                    withContext(ioDispatcher) {
                        context.getDatabasePath(DatabaseFileNames.DATABASE_NAME).length()
                    }
                _state.value = _state.value.copy(dbSizeBytes = size)
            }
        }

    }

data class BackupRestoreState(
    val dbSizeBytes: Long = 0L,
    val inProgress: Boolean = false,
    val errorBannerRes: Int? = null,
    val resetConfirmationVisible: Boolean = false,
    val pendingImportWizardUri: String? = null,
)

sealed interface BackupRestoreEvent {
    data class ExportFolderPicked(
        val treeUriString: String,
    ) : BackupRestoreEvent

    data class ImportFilePicked(
        val documentUriString: String,
    ) : BackupRestoreEvent

    data class ExportCsvFilePicked(
        val documentUriString: String,
    ) : BackupRestoreEvent

    data class ImportCsvFilePicked(
        val documentUriString: String,
    ) : BackupRestoreEvent

    data object ImportWizardNavigationConsumed : BackupRestoreEvent

    data object ResetRequested : BackupRestoreEvent

    data object ResetCancelled : BackupRestoreEvent

    data object ResetConfirmed : BackupRestoreEvent

    data object DismissError : BackupRestoreEvent
}

sealed interface BackupRestoreAction {
    data object ExportSucceeded : BackupRestoreAction

    data object CsvExportSucceeded : BackupRestoreAction

    data object RestartAfterRestore : BackupRestoreAction

    data class RestartToOnboardingAfterReset(
        val hadFailures: Boolean,
    ) : BackupRestoreAction
}
