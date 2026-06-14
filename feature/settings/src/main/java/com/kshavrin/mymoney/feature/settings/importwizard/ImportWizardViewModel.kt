package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportWizardViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val appSettingsRepository: AppSettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val uri: String = savedStateHandle.get<String>(KEY_URI).orEmpty()

        private val _state = MutableStateFlow(ImportWizardState())
        val state: StateFlow<ImportWizardState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<ImportWizardAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<ImportWizardAction> = _actions.asSharedFlow()

        private var staged: StagedImport? = null
        private var existingCategories: List<ExistingCategorySummary> = emptyList()

        init {
            parse()
        }

        fun onEvent(event: ImportWizardEvent) {
            when (event) {
                ImportWizardEvent.Retry -> parse()
                is ImportWizardEvent.DataStrategySelected ->
                    _state.value = _state.value.copy(dataStrategy = event.strategy)
                is ImportWizardEvent.CategoryStrategySelected ->
                    _state.value = _state.value.copy(categoryStrategy = event.strategy)
                ImportWizardEvent.NextClicked -> onNext()
                ImportWizardEvent.BackClicked -> onBack()
                ImportWizardEvent.DestructiveConfirmRequested ->
                    _state.value = _state.value.copy(destructiveConfirmationVisible = true)
                ImportWizardEvent.DestructiveConfirmed -> {
                    _state.value = _state.value.copy(destructiveConfirmationVisible = false)
                    commit()
                }
                ImportWizardEvent.DestructiveDismissed ->
                    _state.value = _state.value.copy(destructiveConfirmationVisible = false)
                is ImportWizardEvent.OrphanDecided -> onOrphanDecided(event.categoryName, event.decision)
                ImportWizardEvent.DismissError ->
                    _state.value = _state.value.copy(errorBannerRes = null)
            }
        }

        private fun parse() {
            viewModelScope.launch {
                _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
                backupRepository
                    .parseImport(uri)
                    .onSuccess { result ->
                        staged = result
                        existingCategories = backupRepository.existingCategorySummaries()
                        _state.value =
                            _state.value.copy(
                                inProgress = false,
                                preview = result.preview,
                                step = ImportWizardStep.Preview,
                            )
                    }.onFailure { failure(it) }
            }
        }

        private fun onNext() {
            when (_state.value.step) {
                ImportWizardStep.Preview ->
                    _state.value = _state.value.copy(step = ImportWizardStep.DataStrategy)
                ImportWizardStep.DataStrategy -> {
                    if (_state.value.dataStrategy == ImportDataStrategy.ReplaceAll) {
                        _state.value = _state.value.copy(step = ImportWizardStep.Confirm)
                    } else {
                        _state.value = _state.value.copy(step = ImportWizardStep.CategoryStrategy)
                    }
                }
                ImportWizardStep.CategoryStrategy -> onCategoryStrategyNext()
                ImportWizardStep.OrphanDecisions -> Unit
                ImportWizardStep.Confirm -> Unit
            }
        }

        private fun onCategoryStrategyNext() {
            if (_state.value.categoryStrategy == ImportCategoryStrategy.ReplaceCurrent) {
                val pending = nonEmptyExistingCategoryNames()
                if (pending.isNotEmpty()) {
                    _state.value =
                        _state.value.copy(
                            step = ImportWizardStep.OrphanDecisions,
                            orphanCategories = pending,
                        )
                    return
                }
            }
            _state.value = _state.value.copy(step = ImportWizardStep.Confirm)
        }

        private fun onOrphanDecided(
            categoryName: String,
            decision: OrphanDecision,
        ) {
            val updated = _state.value.orphanDecisions + (categoryName to decision)
            val next = _state.value.copy(orphanDecisions = updated)
            _state.value =
                if (updated.keys.containsAll(next.orphanCategories.map { it.name })) {
                    next.copy(step = ImportWizardStep.Confirm)
                } else {
                    next
                }
        }

        private fun onBack() {
            when (_state.value.step) {
                ImportWizardStep.Preview -> viewModelScope.launch { _actions.emit(ImportWizardAction.Cancel) }
                ImportWizardStep.DataStrategy ->
                    _state.value = _state.value.copy(step = ImportWizardStep.Preview)
                ImportWizardStep.CategoryStrategy ->
                    _state.value = _state.value.copy(step = ImportWizardStep.DataStrategy)
                ImportWizardStep.OrphanDecisions ->
                    _state.value = _state.value.copy(step = ImportWizardStep.CategoryStrategy)
                ImportWizardStep.Confirm -> onBackFromConfirm()
            }
        }

        private fun onBackFromConfirm() {
            val previous =
                when {
                    _state.value.dataStrategy == ImportDataStrategy.ReplaceAll -> ImportWizardStep.DataStrategy
                    _state.value.categoryStrategy == ImportCategoryStrategy.ReplaceCurrent &&
                        _state.value.orphanCategories.isNotEmpty() -> ImportWizardStep.OrphanDecisions
                    else -> ImportWizardStep.CategoryStrategy
                }
            _state.value = _state.value.copy(step = previous)
        }

        private fun commit() {
            val current = staged ?: return
            viewModelScope.launch {
                _state.value = _state.value.copy(inProgress = true, errorBannerRes = null)
                backupRepository
                    .commitImport(current, _state.value.toPlan())
                    .onSuccess { focus ->
                        if (focus != null) {
                            appSettingsRepository.update {
                                it.copy(
                                    importFocusEpochMs = focus.occurredAtEpochMs,
                                    importFocusCurrencyId = focus.currencyId,
                                )
                            }
                        }
                        _state.value = _state.value.copy(inProgress = false)
                        _actions.emit(ImportWizardAction.CommitSucceeded)
                    }.onFailure { failure(it) }
            }
        }

        private fun nonEmptyExistingCategoryNames(): List<OrphanCategory> =
            existingCategories
                .filter { it.transactionCount > 0 }
                .map { OrphanCategory(name = it.name, transactionCount = it.transactionCount) }

        private fun failure(throwable: Throwable) {
            throwable.reportToSentry()
            _state.value =
                _state.value.copy(
                    inProgress = false,
                    errorBannerRes = R.string.import_wizard_error,
                )
        }

        private companion object {
            const val KEY_URI = "uri"
        }
    }

enum class ImportWizardStep {
    Preview,
    DataStrategy,
    CategoryStrategy,
    OrphanDecisions,
    Confirm,
}

data class OrphanCategory(
    val name: String,
    val transactionCount: Int,
)

data class ImportWizardState(
    val step: ImportWizardStep = ImportWizardStep.Preview,
    val inProgress: Boolean = false,
    val preview: ImportPreview? = null,
    val dataStrategy: ImportDataStrategy = ImportDataStrategy.Append,
    val categoryStrategy: ImportCategoryStrategy = ImportCategoryStrategy.Append,
    val orphanCategories: List<OrphanCategory> = emptyList(),
    val orphanDecisions: Map<String, OrphanDecision> = emptyMap(),
    val destructiveConfirmationVisible: Boolean = false,
    val errorBannerRes: Int? = null,
) {
    val isDestructive: Boolean
        get() =
            dataStrategy == ImportDataStrategy.ReplaceAll ||
                orphanDecisions.values.any { it is OrphanDecision.DeleteTransactions }

    fun toPlan(): ImportPlan =
        ImportPlan(
            dataStrategy = dataStrategy,
            categoryStrategy =
                if (dataStrategy == ImportDataStrategy.ReplaceAll) {
                    ImportCategoryStrategy.ReplaceCurrent
                } else {
                    categoryStrategy
                },
            orphanDecisions = orphanDecisions,
        )
}

sealed interface ImportWizardEvent {
    data object Retry : ImportWizardEvent

    data class DataStrategySelected(
        val strategy: ImportDataStrategy,
    ) : ImportWizardEvent

    data class CategoryStrategySelected(
        val strategy: ImportCategoryStrategy,
    ) : ImportWizardEvent

    data object NextClicked : ImportWizardEvent

    data object BackClicked : ImportWizardEvent

    data object DestructiveConfirmRequested : ImportWizardEvent

    data object DestructiveConfirmed : ImportWizardEvent

    data object DestructiveDismissed : ImportWizardEvent

    data class OrphanDecided(
        val categoryName: String,
        val decision: OrphanDecision,
    ) : ImportWizardEvent

    data object DismissError : ImportWizardEvent
}

sealed interface ImportWizardAction {
    data object Cancel : ImportWizardAction

    data object CommitSucceeded : ImportWizardAction
}
