package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.csv.CategoryMergeMapping
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.MergeAction
import com.kshavrin.mymoney.core.domain.csv.MonefyCsvImportParser
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import com.kshavrin.mymoney.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportWizardViewModel
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val categoryRepository: CategoryRepository = NoOpCategoryRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val uri: String = savedStateHandle.toRoute<Destinations.ImportWizard>().uri

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
                is ImportWizardEvent.MergeActionSelected ->
                    onMergeActionSelected(event.importCategoryName, event.target)
                is ImportWizardEvent.MergeResultNameChanged ->
                    onMergeResultNameChanged(event.importCategoryName, event.resultName)
                ImportWizardEvent.DismissError ->
                    _state.value = _state.value.copy(errorBannerRes = null)
                ImportWizardEvent.ConfigureLaterClicked -> finishWizard()
                ImportWizardEvent.ConfigureNowClicked -> startCategoryConfig()
                is ImportWizardEvent.ConfigNameChanged -> onConfigNameChanged(event.value)
                is ImportWizardEvent.ConfigIconChanged -> onConfigIconChanged(event.value)
                is ImportWizardEvent.ConfigColorChanged -> onConfigColorChanged(event.value)
                ImportWizardEvent.ConfigNextClicked -> onConfigNext()
                ImportWizardEvent.ConfigBackClicked -> onConfigBack()
                ImportWizardEvent.CloseClicked -> finishWizard()
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
                ImportWizardStep.ManualMerge ->
                    _state.value = _state.value.copy(step = ImportWizardStep.Confirm)
                ImportWizardStep.Confirm -> Unit
                ImportWizardStep.ConfigGate -> Unit
                ImportWizardStep.CategoryConfig -> Unit
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
            if (_state.value.categoryStrategy is ImportCategoryStrategy.AppendManualMerge) {
                val rows = unmatchedMergeRows()
                if (rows.isNotEmpty()) {
                    _state.value =
                        _state.value.copy(
                            step = ImportWizardStep.ManualMerge,
                            mergeRows = rows,
                        )
                    return
                }
            }
            _state.value = _state.value.copy(step = ImportWizardStep.Confirm)
        }

        private fun unmatchedMergeRows(): List<MergeRow> {
            val existingKeysByKind: Map<CategoryKind, Set<String>> =
                existingCategories
                    .groupBy { it.kind }
                    .mapValues { (_, summaries) ->
                        summaries.mapTo(mutableSetOf()) { MonefyCsvImportParser.normalizeName(it.name) }
                    }
            return _state.value.preview
                ?.categories
                .orEmpty()
                .filter { category ->
                    val key = MonefyCsvImportParser.normalizeName(category.name)
                    key !in existingKeysByKind[category.kind].orEmpty()
                }.map { category ->
                    MergeRow(
                        importCategoryName = category.name,
                        kind = category.kind,
                        candidates =
                            existingCategories.filter { it.kind == category.kind },
                    )
                }
        }

        private fun onMergeActionSelected(
            importCategoryName: String,
            target: ExistingCategorySummary?,
        ) {
            val updated =
                _state.value.mergeRows.map { row ->
                    if (row.importCategoryName == importCategoryName) {
                        if (target == null) {
                            row.copy(targetId = null, resultName = "")
                        } else {
                            row.copy(targetId = target.id, resultName = target.name)
                        }
                    } else {
                        row
                    }
                }
            _state.value = _state.value.copy(mergeRows = updated)
        }

        private fun onMergeResultNameChanged(
            importCategoryName: String,
            resultName: String,
        ) {
            val updated =
                _state.value.mergeRows.map { row ->
                    if (row.importCategoryName == importCategoryName) {
                        row.copy(resultName = resultName)
                    } else {
                        row
                    }
                }
            _state.value = _state.value.copy(mergeRows = updated)
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
                ImportWizardStep.ManualMerge ->
                    _state.value = _state.value.copy(step = ImportWizardStep.CategoryStrategy)
                ImportWizardStep.Confirm -> onBackFromConfirm()
                ImportWizardStep.ConfigGate -> finishWizard()
                ImportWizardStep.CategoryConfig -> onConfigBack()
            }
        }

        private fun onBackFromConfirm() {
            val previous =
                when {
                    _state.value.dataStrategy == ImportDataStrategy.ReplaceAll -> ImportWizardStep.DataStrategy
                    _state.value.categoryStrategy == ImportCategoryStrategy.ReplaceCurrent &&
                        _state.value.orphanCategories.isNotEmpty() -> ImportWizardStep.OrphanDecisions
                    _state.value.categoryStrategy is ImportCategoryStrategy.AppendManualMerge &&
                        _state.value.mergeRows.isNotEmpty() -> ImportWizardStep.ManualMerge
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
                        val categories = loadResultingCategories()
                        _state.value =
                            _state.value.copy(
                                inProgress = false,
                                step = ImportWizardStep.ConfigGate,
                                configCategories = categories,
                            )
                        _actions.emit(ImportWizardAction.CommitSucceeded)
                    }.onFailure { failure(it) }
            }
        }

        private suspend fun loadResultingCategories(): List<Category> =
            runCatching { categoryRepository.observeAll().first() }.getOrDefault(emptyList())

        private fun startCategoryConfig() {
            if (_state.value.configCategories.isEmpty()) {
                finishWizard()
                return
            }
            loadConfigDraftFrom(index = 0)
            _state.value = _state.value.copy(step = ImportWizardStep.CategoryConfig, configIndex = 0)
        }

        private fun loadConfigDraftFrom(index: Int) {
            val category = _state.value.configCategories.getOrNull(index) ?: return
            _state.value =
                _state.value.copy(
                    configName = category.name,
                    configIconKey = category.iconKey,
                    configColorHex = category.colorHex,
                )
        }

        private fun onConfigNameChanged(value: String) {
            _state.value = _state.value.copy(configName = value)
        }

        private fun onConfigIconChanged(value: String) {
            _state.value = _state.value.copy(configIconKey = value)
        }

        private fun onConfigColorChanged(value: String) {
            _state.value = _state.value.copy(configColorHex = value)
        }

        private fun onConfigNext() {
            val s = _state.value
            val current =
                s.configCategories.getOrNull(s.configIndex) ?: run {
                    finishWizard()
                    return
                }
            val name = s.configName.ifBlank { current.name }
            viewModelScope.launch {
                runCatching {
                    categoryRepository.upsert(
                        current.copy(
                            name = name,
                            iconKey = s.configIconKey,
                            colorHex = s.configColorHex,
                        ),
                    )
                }.onFailure {
                    failure(it)
                    return@launch
                }

                val updated =
                    s.configCategories.toMutableList().apply {
                        set(s.configIndex, current.copy(name = name, iconKey = s.configIconKey, colorHex = s.configColorHex))
                    }
                if (s.configIndex >= s.configCategories.lastIndex) {
                    _state.value = _state.value.copy(configCategories = updated)
                    finishWizard()
                } else {
                    val nextIndex = s.configIndex + 1
                    _state.value =
                        _state.value.copy(
                            configCategories = updated,
                            configIndex = nextIndex,
                        )
                    loadConfigDraftFrom(nextIndex)
                }
            }
        }

        private fun onConfigBack() {
            val s = _state.value
            if (s.configIndex == 0) {
                _state.value = _state.value.copy(step = ImportWizardStep.ConfigGate)
            } else {
                val prevIndex = s.configIndex - 1
                _state.value = _state.value.copy(configIndex = prevIndex)
                loadConfigDraftFrom(prevIndex)
            }
        }

        private fun finishWizard() {
            viewModelScope.launch { _actions.emit(ImportWizardAction.Finished) }
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
    ManualMerge,
    Confirm,
    ConfigGate,
    CategoryConfig,
}

data class OrphanCategory(
    val name: String,
    val transactionCount: Int,
)

/**
 * A single non-matching import category awaiting a manual decision (D6). [targetId] `null` means the
 * default "create new" action; a non-null id merges into the chosen existing category with [resultName].
 */
data class MergeRow(
    val importCategoryName: String,
    val kind: CategoryKind,
    val candidates: List<ExistingCategorySummary>,
    val targetId: Long? = null,
    val resultName: String = "",
) {
    val isMergeInto: Boolean
        get() = targetId != null
}

data class ImportWizardState(
    val step: ImportWizardStep = ImportWizardStep.Preview,
    val inProgress: Boolean = false,
    val preview: ImportPreview? = null,
    val dataStrategy: ImportDataStrategy = ImportDataStrategy.Append,
    val categoryStrategy: ImportCategoryStrategy = ImportCategoryStrategy.Append,
    val orphanCategories: List<OrphanCategory> = emptyList(),
    val orphanDecisions: Map<String, OrphanDecision> = emptyMap(),
    val mergeRows: List<MergeRow> = emptyList(),
    val destructiveConfirmationVisible: Boolean = false,
    val errorBannerRes: Int? = null,
    val configCategories: List<Category> = emptyList(),
    val configIndex: Int = 0,
    val configName: String = "",
    val configIconKey: String = "",
    val configColorHex: String = "",
) {
    val configTotal: Int
        get() = configCategories.size

    val configPosition: Int
        get() = configIndex + 1

    val isLastConfigStep: Boolean
        get() = configCategories.isNotEmpty() && configIndex >= configCategories.lastIndex

    val configCurrentKind: CategoryKind?
        get() = configCategories.getOrNull(configIndex)?.kind

    val isDestructive: Boolean
        get() =
            dataStrategy == ImportDataStrategy.ReplaceAll ||
                orphanDecisions.values.any { it is OrphanDecision.DeleteTransactions }

    fun toPlan(): ImportPlan =
        ImportPlan(
            dataStrategy = dataStrategy,
            categoryStrategy =
                when {
                    dataStrategy == ImportDataStrategy.ReplaceAll -> ImportCategoryStrategy.ReplaceCurrent
                    categoryStrategy is ImportCategoryStrategy.AppendManualMerge ->
                        ImportCategoryStrategy.AppendManualMerge(mergeMappings())
                    else -> categoryStrategy
                },
            orphanDecisions = orphanDecisions,
        )

    private fun mergeMappings(): List<CategoryMergeMapping> =
        mergeRows.map { row ->
            CategoryMergeMapping(
                importCategoryName = row.importCategoryName,
                action =
                    if (row.targetId == null) {
                        MergeAction.CreateNew(name = row.importCategoryName)
                    } else {
                        val target = row.candidates.first { it.id == row.targetId }
                        MergeAction.MergeInto(
                            targetCategoryName = target.name,
                            targetId = target.id,
                            resultName = row.resultName.ifBlank { target.name },
                        )
                    },
            )
        }
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

    data class MergeActionSelected(
        val importCategoryName: String,
        val target: ExistingCategorySummary?,
    ) : ImportWizardEvent

    data class MergeResultNameChanged(
        val importCategoryName: String,
        val resultName: String,
    ) : ImportWizardEvent

    data object DismissError : ImportWizardEvent

    data object ConfigureLaterClicked : ImportWizardEvent

    data object ConfigureNowClicked : ImportWizardEvent

    data class ConfigNameChanged(
        val value: String,
    ) : ImportWizardEvent

    data class ConfigIconChanged(
        val value: String,
    ) : ImportWizardEvent

    data class ConfigColorChanged(
        val value: String,
    ) : ImportWizardEvent

    data object ConfigNextClicked : ImportWizardEvent

    data object ConfigBackClicked : ImportWizardEvent

    data object CloseClicked : ImportWizardEvent
}

sealed interface ImportWizardAction {
    data object Cancel : ImportWizardAction

    data object CommitSucceeded : ImportWizardAction

    data object Finished : ImportWizardAction
}

private object NoOpCategoryRepository : CategoryRepository {
    override fun observeByKind(kind: CategoryKind) = kotlinx.coroutines.flow.flowOf(emptyList<Category>())

    override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<Category>())

    override suspend fun findById(id: Long): Category? = null

    override suspend fun upsert(category: Category): Long = 0L

    override suspend fun upsertAll(categories: List<Category>) = Unit

    override suspend fun archive(id: Long) = Unit
}
