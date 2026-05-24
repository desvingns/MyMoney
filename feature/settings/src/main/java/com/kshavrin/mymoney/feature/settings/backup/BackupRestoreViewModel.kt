package com.kshavrin.mymoney.feature.settings.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
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
class BackupRestoreViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupRestoreState())
    val state: StateFlow<BackupRestoreState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<BackupRestoreAction>(
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
            BackupRestoreEvent.DismissError -> _state.value = _state.value.copy(errorBannerRes = null)
        }
    }

    private fun export(treeUriString: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
            backupRepository.exportDb(treeUriString)
                .onSuccess {
                    _state.value = _state.value.copy(inProgress = false)
                    refreshSize()
                    _actions.emit(BackupRestoreAction.ExportSucceeded)
                }
                .onFailure { failure(it) }
        }
    }

    private fun import(documentUriString: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
            backupRepository.importDb(documentUriString)
                .onSuccess {
                    _state.value = _state.value.copy(inProgress = false)
                    _actions.emit(BackupRestoreAction.RestartAfterRestore)
                }
                .onFailure { failure(it) }
        }
    }

    private fun failure(throwable: Throwable) {
        throwable.reportToSentry()
        _state.value = _state.value.copy(inProgress = false, errorBannerRes = R.string.backup_error)
    }

    private fun refreshSize() {
        viewModelScope.launch {
            val size = withContext(ioDispatcher) {
                context.getDatabasePath(DATABASE_NAME).length()
            }
            _state.value = _state.value.copy(dbSizeBytes = size)
        }
    }

    private companion object {
        const val DATABASE_NAME = "monefy.db"
    }
}

data class BackupRestoreState(
    val dbSizeBytes: Long = 0L,
    val inProgress: Boolean = false,
    val errorBannerRes: Int? = null,
)

sealed interface BackupRestoreEvent {
    data class ExportFolderPicked(val treeUriString: String) : BackupRestoreEvent
    data class ImportFilePicked(val documentUriString: String) : BackupRestoreEvent
    data object DismissError : BackupRestoreEvent
}

sealed interface BackupRestoreAction {
    data object ExportSucceeded : BackupRestoreAction
    data object RestartAfterRestore : BackupRestoreAction
}
