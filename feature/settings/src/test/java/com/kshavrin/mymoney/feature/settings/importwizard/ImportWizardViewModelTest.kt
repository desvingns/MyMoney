package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.csv.CsvImportFormat
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPlan
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.csv.StagedImport
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CsvImportFocus
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.settings.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportWizardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ------------------------------------------------------------------ fakes

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
        uri: String = "content://doc/import.csv",
    ): ImportWizardViewModel =
        ImportWizardViewModel(
            backupRepository = repo,
            appSettingsRepository = settings,
            savedStateHandle = SavedStateHandle(mapOf("uri" to uri)),
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
}
