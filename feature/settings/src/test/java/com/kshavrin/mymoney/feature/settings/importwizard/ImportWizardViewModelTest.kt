package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.csv.CsvImportFormat
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.MergeAction
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.csv.PreviewCategory
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CsvImportFocus
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWizardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ------------------------------------------------------------------ fakes

    private inner class FakeCategoryRepository : CategoryRepository {
        val upsertedCategories: MutableList<Category> = mutableListOf()
        private val _categories = MutableStateFlow<List<Category>>(emptyList())

        fun seed(categories: List<Category>) {
            _categories.value = categories
        }

        fun simulateUpsertFailure(throwable: Throwable = RuntimeException("upsert failed")) {
            upsertFailure = throwable
        }

        private var upsertFailure: Throwable? = null

        override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
            MutableStateFlow(_categories.value.filter { it.kind == kind })

        override fun observeAll(): Flow<List<Category>> = _categories

        override suspend fun findById(id: Long): Category? =
            _categories.value.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category): Long {
            upsertFailure?.let { throw it }
            upsertedCategories += category
            return category.id
        }

        override suspend fun upsertAll(categories: List<Category>) {
            categories.forEach { upsert(it) }
        }

        override suspend fun archive(id: Long) = Unit
    }

    /**
     * Extends the module-level FakeBackupRepository to add the three wizard-specific operations
     * that have default (failure) implementations on the interface. Each operation can be
     * individually seeded or told to fail.
     */
    private inner class FakeWizardBackupRepository : BackupRepository {
        // Tracking
        val committedPlans: MutableList<ImportPlan> = mutableListOf()

        // Seeds
        private var parseResult: Result<StagedImport> =
            Result.success(
                StagedImport(
                    format = CsvImportFormat.MyMoney,
                    records = emptyList(),
                    preview = defaultPreview(),
                ),
            )
        private var categorySummaries: List<ExistingCategorySummary> = emptyList()
        private var commitResult: Result<CsvImportFocus?> = Result.success(null)

        fun seedParseResult(staged: StagedImport) {
            parseResult = Result.success(staged)
        }

        fun simulateParseFailure(throwable: Throwable = RuntimeException("parse failed")) {
            parseResult = Result.failure(throwable)
        }

        fun seedCategories(categories: List<ExistingCategorySummary>) {
            categorySummaries = categories
        }

        fun seedCommitFocus(focus: CsvImportFocus?) {
            commitResult = Result.success(focus)
        }

        fun simulateCommitFailure(throwable: Throwable = RuntimeException("commit failed")) {
            commitResult = Result.failure(throwable)
        }

        // BackupRepository interface (mandatory methods)
        override suspend fun exportDb(treeUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun importDb(documentUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> = Result.success(Unit)

        // Wizard operations
        override suspend fun parseImport(documentUriString: String): Result<StagedImport> = parseResult

        override suspend fun existingCategorySummaries(): List<ExistingCategorySummary> = categorySummaries

        override suspend fun commitImport(
            staged: StagedImport,
            plan: ImportPlan,
        ): Result<CsvImportFocus?> {
            committedPlans += plan
            return commitResult
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun defaultPreview() =
        ImportPreview(
            rowCount = 10,
            categories = emptySet(),
            accounts = emptySet(),
            dateRange = null,
        )

    private fun defaultStaged() =
        StagedImport(
            format = CsvImportFormat.MyMoney,
            records = emptyList(),
            preview = defaultPreview(),
        )

    private fun buildViewModel(
        repo: FakeWizardBackupRepository,
        settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        categoryRepo: FakeCategoryRepository = FakeCategoryRepository(),
        uri: String = "content://doc/import.csv",
    ): ImportWizardViewModel =
        ImportWizardViewModel(
            backupRepository = repo,
            appSettingsRepository = settings,
            categoryRepository = categoryRepo,
            savedStateHandle = SavedStateHandle(mapOf("uri" to uri)),
        )

    private fun testCategory(
        id: Long,
        name: String,
        kind: CategoryKind = CategoryKind.Expense,
    ): Category =
        Category(
            id = id,
            name = name,
            kind = kind,
            iconKey = "food",
            colorHex = "#FF0000",
            textColor = "#FFFFFF",
            sortOrder = id.toInt(),
            isDefault = false,
            isArchived = false,
            createdAt = Instant.EPOCH,
        )

    // ------------------------------------------------------------------ parse / preview

    @Test
    fun `initial parse succeeds and moves to Preview step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.state.test {
                val state = awaitItem()
                assertEquals(ImportWizardStep.Preview, state.step)
                assertFalse(state.inProgress)
                assertNotNull(state.preview)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial parse sets preview row count from staged import`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedParseResult(
                StagedImport(
                    format = CsvImportFormat.Monefy,
                    records = emptyList(),
                    preview = ImportPreview(rowCount = 42, categories = emptySet(), accounts = emptySet(), dateRange = null),
                ),
            )
            val vm = buildViewModel(repo)

            vm.state.test {
                assertEquals(42, awaitItem().preview?.rowCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `parse failure sets error banner and clears progress`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.simulateParseFailure()
            val vm = buildViewModel(repo)

            vm.state.test {
                val state = awaitItem()
                assertEquals(R.string.import_wizard_error, state.errorBannerRes)
                assertFalse(state.inProgress)
                assertNull(state.preview)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Retry re-parses the uri and recovers from a previous failure`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.simulateParseFailure()
            val vm = buildViewModel(repo)

            repo.seedParseResult(defaultStaged())
            vm.onEvent(ImportWizardEvent.Retry)

            vm.state.test {
                val state = awaitItem()
                assertNull(state.errorBannerRes)
                assertNotNull(state.preview)
                assertEquals(ImportWizardStep.Preview, state.step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ step navigation

    @Test
    fun `NextClicked from Preview advances to DataStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.DataStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `NextClicked from DataStrategy with Append advances to CategoryStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.CategoryStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `NextClicked from DataStrategy with AppendDedup advances to CategoryStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.AppendDedup))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.CategoryStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ReplaceAll skips CategoryStrategy and advances directly to Confirm step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.Confirm, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `NextClicked from CategoryStrategy with Append advances to Confirm step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.Append))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.Confirm, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ReplaceCurrent with no non-empty categories advances directly to Confirm step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Groceries", kind = CategoryKind.Expense, transactionCount = 0),
                ),
            )
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.Confirm, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ReplaceCurrent with non-empty categories advances to OrphanDecisions step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Groceries", kind = CategoryKind.Expense, transactionCount = 5),
                ),
            )
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.OrphanDecisions, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `orphan categories list contains only categories with non-zero transaction count`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Active", kind = CategoryKind.Expense, transactionCount = 3),
                    ExistingCategorySummary(id = 2L, name = "Empty", kind = CategoryKind.Income, transactionCount = 0),
                ),
            )
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                val state = awaitItem()
                assertEquals(1, state.orphanCategories.size)
                assertEquals("Active", state.orphanCategories.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ orphan decisions

    @Test
    fun `OrphanDecided KeepCategory accumulates the decision in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                    ExistingCategorySummary(id = 2L, name = "Food", kind = CategoryKind.Expense, transactionCount = 1),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.KeepCategory))

            vm.state.test {
                val state = awaitItem()
                assertEquals(ImportWizardStep.OrphanDecisions, state.step)
                assertEquals(OrphanDecision.KeepCategory, state.orphanDecisions["Bills"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deciding all orphans advances automatically to Confirm step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.KeepCategory))

            vm.state.test {
                assertEquals(ImportWizardStep.Confirm, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OrphanDecided DeleteTransactions marks the decision in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.DeleteTransactions))

            vm.state.test {
                assertEquals(OrphanDecision.DeleteTransactions, awaitItem().orphanDecisions["Bills"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `plan built from orphan decisions includes all accumulated decisions`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                    ExistingCategorySummary(id = 2L, name = "Food", kind = CategoryKind.Expense, transactionCount = 1),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.KeepCategory))
            vm.onEvent(ImportWizardEvent.OrphanDecided("Food", OrphanDecision.DeleteTransactions))

            vm.state.test {
                val state = awaitItem()
                val plan = state.toPlan()
                assertEquals(OrphanDecision.KeepCategory, plan.orphanDecisions["Bills"])
                assertEquals(OrphanDecision.DeleteTransactions, plan.orphanDecisions["Food"])
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ destructive confirmation

    @Test
    fun `ReplaceAll strategy makes isDestructive true`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))

            vm.state.test {
                assertTrue(awaitItem().isDestructive)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DeleteTransactions orphan decision makes isDestructive true`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.DeleteTransactions))

            vm.state.test {
                assertTrue(awaitItem().isDestructive)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `non-destructive import does not make isDestructive true`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append))

            vm.state.test {
                assertFalse(awaitItem().isDestructive)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DestructiveConfirmRequested shows the confirmation dialog`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DestructiveConfirmRequested)

            vm.state.test {
                assertTrue(awaitItem().destructiveConfirmationVisible)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DestructiveDismissed hides the confirmation dialog without committing`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DestructiveConfirmRequested)
            vm.onEvent(ImportWizardEvent.DestructiveDismissed)

            vm.state.test {
                assertFalse(awaitItem().destructiveConfirmationVisible)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(repo.committedPlans.isEmpty())
        }

    @Test
    fun `DestructiveConfirmed hides the confirmation dialog and runs commit`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.DestructiveConfirmRequested)
            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            vm.state.test {
                assertFalse(awaitItem().destructiveConfirmationVisible)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repo.committedPlans.size)
        }

    // ------------------------------------------------------------------ commit

    @Test
    fun `successful commit without focus emits CommitSucceeded action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.DestructiveConfirmed)
                assertEquals(ImportWizardAction.CommitSucceeded, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `successful commit with focus writes importFocus to AppSettings`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val focus = CsvImportFocus(occurredAtEpochMs = 1_700_000_000_000L, currencyId = 3L)
            repo.seedCommitFocus(focus)
            val settingsRepo = FakeAppSettingsRepository()
            val vm = buildViewModel(repo, settingsRepo)
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            assertEquals(focus.occurredAtEpochMs, settingsRepo.settings.value.importFocusEpochMs)
            assertEquals(focus.currencyId, settingsRepo.settings.value.importFocusCurrencyId)
        }

    @Test
    fun `successful commit with null focus does not write importFocus to AppSettings`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCommitFocus(null)
            val settingsRepo = FakeAppSettingsRepository()
            val vm = buildViewModel(repo, settingsRepo)
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            assertEquals(0L, settingsRepo.settings.value.importFocusEpochMs)
            assertEquals(-1L, settingsRepo.settings.value.importFocusCurrencyId)
        }

    @Test
    fun `commit failure sets error banner and clears progress`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.simulateCommitFailure()
            val vm = buildViewModel(repo)
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            vm.state.test {
                val state = awaitItem()
                assertEquals(R.string.import_wizard_error, state.errorBannerRes)
                assertFalse(state.inProgress)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `commit failure emits no CommitSucceeded action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.simulateCommitFailure()
            val vm = buildViewModel(repo)
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.DestructiveConfirmed)
                expectNoEvents()
            }
        }

    // ------------------------------------------------------------------ toPlan shape

    @Test
    fun `toPlan with ReplaceAll always uses ReplaceCurrent category strategy`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.Append))

            vm.state.test {
                val plan = awaitItem().toPlan()
                assertEquals(ImportDataStrategy.ReplaceAll, plan.dataStrategy)
                assertEquals(ImportCategoryStrategy.ReplaceCurrent, plan.categoryStrategy)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toPlan with Append preserves the user-selected category strategy`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append))
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))

            vm.state.test {
                val plan = awaitItem().toPlan()
                assertEquals(ImportDataStrategy.Append, plan.dataStrategy)
                assertEquals(ImportCategoryStrategy.ReplaceCurrent, plan.categoryStrategy)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ back navigation

    @Test
    fun `BackClicked from Preview emits Cancel action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.BackClicked)
                assertEquals(ImportWizardAction.Cancel, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from DataStrategy returns to Preview step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.Preview, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from CategoryStrategy returns to DataStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.DataStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from OrphanDecisions returns to CategoryStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)

            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.CategoryStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from Confirm with ReplaceAll returns to DataStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.NextClicked)
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
            vm.onEvent(ImportWizardEvent.NextClicked) // → Confirm
            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.DataStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from Confirm with ReplaceCurrent and orphans returns to OrphanDecisions step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 2),
                ),
            )
            val vm = buildViewModel(repo)
            navigateToOrphanDecisions(vm)
            vm.onEvent(ImportWizardEvent.OrphanDecided("Bills", OrphanDecision.KeepCategory)) // → Confirm

            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.OrphanDecisions, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ DismissError

    @Test
    fun `DismissError clears an existing error banner`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.simulateParseFailure()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DismissError)

            vm.state.test {
                assertNull(awaitItem().errorBannerRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ strategy selection events

    @Test
    fun `DataStrategySelected ReplaceAll is reflected in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))

            vm.state.test {
                assertEquals(ImportDataStrategy.ReplaceAll, awaitItem().dataStrategy)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CategoryStrategySelected ReplaceCurrent is reflected in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val vm = buildViewModel(repo)

            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))

            vm.state.test {
                assertEquals(ImportCategoryStrategy.ReplaceCurrent, awaitItem().categoryStrategy)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ ManualMerge routing

    @Test
    fun `AppendManualMerge with one unmatched import category routes to ManualMerge step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Groceries", kind = CategoryKind.Expense, transactionCount = 0),
                ),
            )
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 3,
                            categories = setOf(PreviewCategory("Transport", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.AppendManualMerge(emptyList())))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.ManualMerge, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `exact name+kind match is excluded from ManualMerge resolver rows`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Groceries", kind = CategoryKind.Expense, transactionCount = 0),
                    ExistingCategorySummary(id = 2L, name = "Transport", kind = CategoryKind.Expense, transactionCount = 0),
                ),
            )
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 3,
                            categories =
                                setOf(
                                    PreviewCategory("Groceries", CategoryKind.Expense), // exact match — excluded
                                    PreviewCategory("Transport", CategoryKind.Expense), // exact match — excluded
                                    PreviewCategory("Dining", CategoryKind.Expense), // unmatched — included
                                ),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.AppendManualMerge(emptyList())))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                val state = awaitItem()
                assertEquals(ImportWizardStep.ManualMerge, state.step)
                assertEquals(1, state.mergeRows.size)
                assertEquals("Dining", state.mergeRows.first().importCategoryName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `default merge row action is CreateNew and toPlan carries AppendManualMerge with CreateNew mapping`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(emptyList())
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Leisure", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.AppendManualMerge(emptyList())))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                val state = awaitItem()
                assertEquals(ImportWizardStep.ManualMerge, state.step)
                assertFalse(state.mergeRows.first().isMergeInto)
                val plan = state.toPlan()
                val strategy = plan.categoryStrategy as ImportCategoryStrategy.AppendManualMerge
                assertEquals(1, strategy.mappings.size)
                val mapping = strategy.mappings.first()
                assertEquals("Leisure", mapping.importCategoryName)
                assertTrue(mapping.action is MergeAction.CreateNew)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `MergeActionSelected with existing category sets targetId and resultName on the row`() =
        runTest {
            val existing = ExistingCategorySummary(id = 7L, name = "Food", kind = CategoryKind.Expense, transactionCount = 0)
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(listOf(existing))
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Dining", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.MergeActionSelected(importCategoryName = "Dining", target = existing))

            vm.state.test {
                val state = awaitItem()
                val row = state.mergeRows.first()
                assertTrue(row.isMergeInto)
                assertEquals(7L, row.targetId)
                assertEquals("Food", row.resultName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toPlan with MergeInto action carries targetId targetCategoryName and resultName`() =
        runTest {
            val existing = ExistingCategorySummary(id = 7L, name = "Food", kind = CategoryKind.Expense, transactionCount = 0)
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(listOf(existing))
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Dining", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.MergeActionSelected(importCategoryName = "Dining", target = existing))
            vm.onEvent(ImportWizardEvent.MergeResultNameChanged(importCategoryName = "Dining", resultName = "Nutrition"))

            vm.state.test {
                val state = awaitItem()
                val plan = state.toPlan()
                val strategy = plan.categoryStrategy as ImportCategoryStrategy.AppendManualMerge
                val mapping = strategy.mappings.first()
                val action = mapping.action as MergeAction.MergeInto
                assertEquals(7L, action.targetId)
                assertEquals("Food", action.targetCategoryName)
                assertEquals("Nutrition", action.resultName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `MergeResultNameChanged updates only the named row leaving others unchanged`() =
        runTest {
            val expExisting = ExistingCategorySummary(id = 1L, name = "Food", kind = CategoryKind.Expense, transactionCount = 0)
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(listOf(expExisting))
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 2,
                            categories =
                                setOf(
                                    PreviewCategory("Dining", CategoryKind.Expense),
                                    PreviewCategory("Fuel", CategoryKind.Expense),
                                ),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.MergeActionSelected(importCategoryName = "Dining", target = expExisting))
            vm.onEvent(ImportWizardEvent.MergeResultNameChanged(importCategoryName = "Dining", resultName = "Eating Out"))

            vm.state.test {
                val state = awaitItem()
                val diningRow = state.mergeRows.first { it.importCategoryName == "Dining" }
                val fuelRow = state.mergeRows.first { it.importCategoryName == "Fuel" }
                assertEquals("Eating Out", diningRow.resultName)
                assertFalse(fuelRow.isMergeInto)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `merge row candidates contain only categories of the same kind as the import category`() =
        runTest {
            val expenseExisting = ExistingCategorySummary(id = 1L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 0)
            val incomeExisting = ExistingCategorySummary(id = 2L, name = "Salary", kind = CategoryKind.Income, transactionCount = 0)
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(listOf(expenseExisting, incomeExisting))
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Utilities", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)

            vm.state.test {
                val state = awaitItem()
                val row = state.mergeRows.first()
                assertEquals(CategoryKind.Expense, row.kind)
                assertEquals(1, row.candidates.size)
                assertEquals("Bills", row.candidates.first().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AppendManualMerge with zero unmatched categories skips ManualMerge and goes to Confirm`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(
                listOf(
                    ExistingCategorySummary(id = 1L, name = "Groceries", kind = CategoryKind.Expense, transactionCount = 0),
                ),
            )
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Groceries", CategoryKind.Expense)), // exact match
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToCategoryStrategy(vm, ImportDataStrategy.Append)
            vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.AppendManualMerge(emptyList())))
            vm.onEvent(ImportWizardEvent.NextClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.Confirm, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from ManualMerge returns to CategoryStrategy step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(emptyList())
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Leisure", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.CategoryStrategy, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from Confirm with AppendManualMerge and non-empty merge rows returns to ManualMerge step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(emptyList())
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Leisure", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.NextClicked) // ManualMerge → Confirm
            vm.onEvent(ImportWizardEvent.BackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.ManualMerge, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `MergeActionSelected with null target resets row to CreateNew`() =
        runTest {
            val existing = ExistingCategorySummary(id = 3L, name = "Bills", kind = CategoryKind.Expense, transactionCount = 0)
            val repo = FakeWizardBackupRepository()
            repo.seedCategories(listOf(existing))
            val staged =
                defaultStaged().copy(
                    preview =
                        ImportPreview(
                            rowCount = 1,
                            categories = setOf(PreviewCategory("Utilities", CategoryKind.Expense)),
                            accounts = emptySet(),
                            dateRange = null,
                        ),
                )
            repo.seedParseResult(staged)
            val vm = buildViewModel(repo)

            navigateToManualMerge(vm)
            vm.onEvent(ImportWizardEvent.MergeActionSelected(importCategoryName = "Utilities", target = existing))
            vm.onEvent(ImportWizardEvent.MergeActionSelected(importCategoryName = "Utilities", target = null))

            vm.state.test {
                val row = awaitItem().mergeRows.first()
                assertFalse(row.isMergeInto)
                assertNull(row.targetId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ ConfigGate: commit now routes to ConfigGate

    @Test
    fun `successful commit routes to ConfigGate step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(id = 1L, name = "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            // ReplaceAll → Confirm in fewest steps
            vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
            vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy → Confirm

            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            vm.state.test {
                assertEquals(ImportWizardStep.ConfigGate, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `configCategories is populated from CategoryRepository after commit`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(id = 1L, name = "Food"), testCategory(id = 2L, name = "Transport")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
            vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
            vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy → Confirm

            vm.onEvent(ImportWizardEvent.DestructiveConfirmed)

            vm.state.test {
                val state = awaitItem()
                assertEquals(2, state.configCategories.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigureLaterClicked from ConfigGate emits Finished action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(id = 1L, name = "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToConfigGate(vm, categoryRepo)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.ConfigureLaterClicked)
                assertEquals(ImportWizardAction.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigureNowClicked with empty categories emits Finished action without entering CategoryConfig`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            // seed empty list — no categories to configure
            categoryRepo.seed(emptyList())
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToConfigGate(vm, categoryRepo)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.ConfigureNowClicked)
                assertEquals(ImportWizardAction.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ CategoryConfig: navigation

    @Test
    fun `ConfigureNowClicked with categories advances to CategoryConfig step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(id = 1L, name = "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToConfigGate(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigureNowClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.CategoryConfig, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `entering CategoryConfig loads first category name into configName`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(id = 1L, name = "Groceries")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.state.test {
                assertEquals("Groceries", awaitItem().configName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `configIndex is zero on first CategoryConfig step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "A"), testCategory(2L, "B")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.state.test {
                assertEquals(0, awaitItem().configIndex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `configPosition is 1 of N on first CategoryConfig step`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"), testCategory(3L, "C"))
            categoryRepo.seed(cats)
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.state.test {
                val state = awaitItem()
                assertEquals(1, state.configPosition)
                assertEquals(3, state.configTotal)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isLastConfigStep is false when not on last category`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "A"), testCategory(2L, "B")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.state.test {
                assertFalse(awaitItem().isLastConfigStep)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isLastConfigStep is true when on last category`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "OnlyOne")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.state.test {
                assertTrue(awaitItem().isLastConfigStep)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ CategoryConfig: field edits

    @Test
    fun `ConfigNameChanged updates configName in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNameChanged("Groceries"))

            vm.state.test {
                assertEquals("Groceries", awaitItem().configName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigIconChanged updates configIconKey in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigIconChanged("shopping_cart"))

            vm.state.test {
                assertEquals("shopping_cart", awaitItem().configIconKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigColorChanged updates configColorHex in state`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigColorChanged("#00FF00"))

            vm.state.test {
                assertEquals("#00FF00", awaitItem().configColorHex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ CategoryConfig: ConfigNextClicked persistence

    @Test
    fun `ConfigNextClicked calls CategoryRepository upsert with edited values`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNameChanged("Groceries"))
            vm.onEvent(ImportWizardEvent.ConfigIconChanged("cart"))
            vm.onEvent(ImportWizardEvent.ConfigColorChanged("#123456"))
            vm.onEvent(ImportWizardEvent.ConfigNextClicked)

            assertEquals(1, categoryRepo.upsertedCategories.size)
            val saved = categoryRepo.upsertedCategories.first()
            assertEquals("Groceries", saved.name)
            assertEquals("cart", saved.iconKey)
            assertEquals("#123456", saved.colorHex)
        }

    @Test
    fun `ConfigNextClicked with blank name falls back to original category name`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNameChanged("   "))
            vm.onEvent(ImportWizardEvent.ConfigNextClicked)

            assertEquals("Food", categoryRepo.upsertedCategories.first().name)
        }

    @Test
    fun `ConfigNextClicked on last category emits Finished action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.ConfigNextClicked)
                assertEquals(ImportWizardAction.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigNextClicked on non-last category advances to next index`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food"), testCategory(2L, "Transport")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNextClicked)

            vm.state.test {
                val state = awaitItem()
                assertEquals(1, state.configIndex)
                assertEquals("Transport", state.configName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigNextClicked persists all categories in sequence`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(
                listOf(
                    testCategory(1L, "Food"),
                    testCategory(2L, "Transport"),
                    testCategory(3L, "Health"),
                ),
            )
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNextClicked) // save Food, move to Transport
            vm.onEvent(ImportWizardEvent.ConfigNextClicked) // save Transport, move to Health
            vm.onEvent(ImportWizardEvent.ConfigNextClicked) // save Health, finish

            assertEquals(3, categoryRepo.upsertedCategories.size)
        }

    @Test
    fun `ConfigNextClicked upsert failure sets error banner and does not advance`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food"), testCategory(2L, "Transport")))
            categoryRepo.simulateUpsertFailure()
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNextClicked)

            vm.state.test {
                val state = awaitItem()
                assertEquals(R.string.import_wizard_error, state.errorBannerRes)
                assertEquals(0, state.configIndex)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ CategoryConfig: ConfigBackClicked

    @Test
    fun `ConfigBackClicked from first CategoryConfig step returns to ConfigGate`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigBackClicked)

            vm.state.test {
                assertEquals(ImportWizardStep.ConfigGate, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ConfigBackClicked from second CategoryConfig step returns to previous index`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food"), testCategory(2L, "Transport")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNextClicked) // Food → Transport
            vm.onEvent(ImportWizardEvent.ConfigBackClicked) // back to Food

            vm.state.test {
                val state = awaitItem()
                assertEquals(0, state.configIndex)
                assertEquals("Food", state.configName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ CloseClicked early exit

    @Test
    fun `CloseClicked from CategoryConfig emits Finished action preserving already-saved edits`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food"), testCategory(2L, "Transport")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToFirstCategoryConfig(vm, categoryRepo)

            vm.onEvent(ImportWizardEvent.ConfigNextClicked) // saves Food, moves to Transport
            assertEquals(1, categoryRepo.upsertedCategories.size)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.CloseClicked)
                assertEquals(ImportWizardAction.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked from ConfigGate step emits Finished action`() =
        runTest {
            val repo = FakeWizardBackupRepository()
            val categoryRepo = FakeCategoryRepository()
            categoryRepo.seed(listOf(testCategory(1L, "Food")))
            val vm = buildViewModel(repo, categoryRepo = categoryRepo)
            navigateToConfigGate(vm, categoryRepo)

            vm.actions.test {
                vm.onEvent(ImportWizardEvent.BackClicked)
                assertEquals(ImportWizardAction.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------------ navigation helpers

    private fun navigateToCategoryStrategy(
        vm: ImportWizardViewModel,
        dataStrategy: ImportDataStrategy,
    ) {
        vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
        vm.onEvent(ImportWizardEvent.DataStrategySelected(dataStrategy))
        vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy → CategoryStrategy
    }

    private fun navigateToOrphanDecisions(vm: ImportWizardViewModel) {
        vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
        vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append))
        vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy → CategoryStrategy
        vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent))
        vm.onEvent(ImportWizardEvent.NextClicked) // CategoryStrategy → OrphanDecisions
    }

    private fun navigateToManualMerge(vm: ImportWizardViewModel) {
        vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
        vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append))
        vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy → CategoryStrategy
        vm.onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.AppendManualMerge(emptyList())))
        vm.onEvent(ImportWizardEvent.NextClicked) // CategoryStrategy → ManualMerge
    }

    /**
     * Navigate all the way to ConfigGate (commit done).
     * Uses ReplaceAll to skip CategoryStrategy and reach Confirm in the fewest steps.
     * Categories must be seeded in [categoryRepo] before calling.
     */
    private fun navigateToConfigGate(
        vm: ImportWizardViewModel,
        @Suppress("UNUSED_PARAMETER") categoryRepo: FakeCategoryRepository,
    ) {
        vm.onEvent(ImportWizardEvent.NextClicked) // Preview → DataStrategy
        vm.onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll))
        vm.onEvent(ImportWizardEvent.NextClicked) // DataStrategy (ReplaceAll) → Confirm
        vm.onEvent(ImportWizardEvent.DestructiveConfirmed) // commit → ConfigGate
    }

    /** Navigate to first CategoryConfig step. Categories must be seeded before calling. */
    private fun navigateToFirstCategoryConfig(
        vm: ImportWizardViewModel,
        categoryRepo: FakeCategoryRepository,
    ) {
        navigateToConfigGate(vm, categoryRepo)
        vm.onEvent(ImportWizardEvent.ConfigureNowClicked) // ConfigGate → CategoryConfig
    }
}
