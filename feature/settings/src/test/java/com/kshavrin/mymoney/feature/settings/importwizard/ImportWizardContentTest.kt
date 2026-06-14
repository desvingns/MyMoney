package com.kshavrin.mymoney.feature.settings.importwizard

import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportPreview
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [ImportWizardContent] (import-wizard step machine).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:settings`'s offline test classpath does NOT have `androidx.compose.ui:ui-test-junit4`,
 * `ui-test-manifest`, or Robolectric — mirroring the deliberate deferral already documented in
 * [BackupRestoreContentTest], [ThemeSettingsContentTest], and [LanguageContentTest]. Full Compose-UI
 * tests land in PHASE_15 once those dependencies are wired in.
 *
 * Until then, every user-visible decision that [ImportWizardContent] derives from [ImportWizardState]
 * is tested here via pure Kotlin mirrors of the same expressions, so the state machine is completely
 * pinned at the JVM level.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class ImportWizardContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: ImportWizardState, onEvent: (ImportWizardEvent) -> Unit = {}) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { ImportWizardContent(state = state, onEvent = onEvent) }
 *         }
 *     }
 *
 *     @Test fun `shows title`() {
 *         setContent(ImportWizardState())
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_title)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `loading spinner visible while inProgress`() {
 *         setContent(ImportWizardState(inProgress = true))
 *         composeTestRule.onNode(hasProgressBarRangeInfo()).assertIsDisplayed()
 *     }
 *
 *     @Test fun `Next button disabled while inProgress`() {
 *         setContent(ImportWizardState(inProgress = true, preview = null))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_next)).assertIsNotEnabled()
 *     }
 *
 *     @Test fun `Next button disabled when preview is null`() {
 *         setContent(ImportWizardState(inProgress = false, preview = null))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_next)).assertIsNotEnabled()
 *     }
 *
 *     @Test fun `Next button enabled when preview is available`() {
 *         setContent(ImportWizardState(
 *             inProgress = false,
 *             preview = ImportPreview(rowCount = 5, categories = emptySet(), accounts = emptySet(), dateRange = null)
 *         ))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_next)).assertIsEnabled()
 *     }
 *
 *     @Test fun `error banner is shown when errorBannerRes is set`() {
 *         setContent(ImportWizardState(errorBannerRes = R.string.import_wizard_error))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_error)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `destructive confirmation dialog is shown when destructiveConfirmationVisible`() {
 *         setContent(ImportWizardState(destructiveConfirmationVisible = true))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_destructive_title)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `Finish button shown on Confirm step`() {
 *         setContent(ImportWizardState(step = ImportWizardStep.Confirm, preview = defaultPreview()))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_finish)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `nav buttons hidden on OrphanDecisions step`() {
 *         setContent(ImportWizardState(step = ImportWizardStep.OrphanDecisions))
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_next)).assertDoesNotExist()
 *         composeTestRule.onNodeWithText(context.getString(R.string.import_wizard_finish)).assertDoesNotExist()
 *     }
 * }
 * ```
 */
class ImportWizardContentTest {
    // ------------------------------------------------------------------ state helpers

    private fun defaultPreview() =
        ImportPreview(
            rowCount = 5,
            categories = emptySet(),
            accounts = emptySet(),
            dateRange = null,
        )

    /** Mirror of WizardNavButton's `enabled = !state.inProgress && state.preview != null`. */
    private fun nextButtonEnabled(state: ImportWizardState): Boolean =
        !state.inProgress && state.preview != null

    /** Mirror of WizardNavButton: Finish button shown only on Confirm step. */
    private fun finishButtonVisible(state: ImportWizardState): Boolean =
        state.step == ImportWizardStep.Confirm

    /** Mirror of `state.step != ImportWizardStep.OrphanDecisions` nav-button guard. */
    private fun navButtonVisible(state: ImportWizardState): Boolean =
        state.step != ImportWizardStep.OrphanDecisions

    /** Mirror of merge row result-name field: visible only when [MergeRow.isMergeInto]. */
    private fun mergeResultNameVisible(row: MergeRow): Boolean = row.isMergeInto

    /** Mirror of error Text `state.errorBannerRes?.let { ... }`. */
    private fun errorBannerVisible(state: ImportWizardState): Boolean =
        state.errorBannerRes != null

    /** Mirror of ConfirmStep's destructive note visibility. */
    private fun destructiveNoteVisible(state: ImportWizardState): Boolean =
        state.step == ImportWizardStep.Confirm && state.isDestructive

    /** Mirror of ConfirmStep's category row guard. */
    private fun categoryConfirmRowVisible(state: ImportWizardState): Boolean =
        state.step == ImportWizardStep.Confirm && state.dataStrategy != ImportDataStrategy.ReplaceAll

    // ------------------------------------------------------------------ Next button

    @Test
    fun `Next button is disabled while parse is in progress`() {
        val state = ImportWizardState(inProgress = true, preview = null)
        assertFalse(nextButtonEnabled(state))
    }

    @Test
    fun `Next button is disabled when preview is null`() {
        val state = ImportWizardState(inProgress = false, preview = null)
        assertFalse(nextButtonEnabled(state))
    }

    @Test
    fun `Next button is enabled when idle with a loaded preview`() {
        val state = ImportWizardState(inProgress = false, preview = defaultPreview())
        assertTrue(nextButtonEnabled(state))
    }

    // ------------------------------------------------------------------ Finish / nav button visibility

    @Test
    fun `Finish button is visible on Confirm step`() {
        val state = ImportWizardState(step = ImportWizardStep.Confirm)
        assertTrue(finishButtonVisible(state))
    }

    @Test
    fun `Finish button is not visible on non-Confirm steps`() {
        for (step in listOf(
            ImportWizardStep.Preview,
            ImportWizardStep.DataStrategy,
            ImportWizardStep.CategoryStrategy,
            ImportWizardStep.OrphanDecisions,
            ImportWizardStep.ManualMerge,
        )) {
            assertFalse("Expected finishButtonVisible=false for step $step", finishButtonVisible(ImportWizardState(step = step)))
        }
    }

    @Test
    fun `nav buttons are hidden on OrphanDecisions step`() {
        val state = ImportWizardState(step = ImportWizardStep.OrphanDecisions)
        assertFalse(navButtonVisible(state))
    }

    @Test
    fun `nav buttons are visible on all other steps`() {
        for (step in listOf(
            ImportWizardStep.Preview,
            ImportWizardStep.DataStrategy,
            ImportWizardStep.CategoryStrategy,
            ImportWizardStep.ManualMerge,
            ImportWizardStep.Confirm,
        )) {
            assertTrue("Expected navButtonVisible=true for step $step", navButtonVisible(ImportWizardState(step = step)))
        }
    }

    // ------------------------------------------------------------------ error banner

    @Test
    fun `error banner is hidden when errorBannerRes is null`() {
        val state = ImportWizardState(errorBannerRes = null)
        assertFalse(errorBannerVisible(state))
        assertNull(state.errorBannerRes)
    }

    @Test
    fun `error banner is shown when errorBannerRes is set`() {
        val state = ImportWizardState(errorBannerRes = R.string.import_wizard_error)
        assertTrue(errorBannerVisible(state))
        assertEquals(R.string.import_wizard_error, state.errorBannerRes)
    }

    // ------------------------------------------------------------------ destructive confirmation

    @Test
    fun `destructive confirmation dialog hidden by default`() {
        val state = ImportWizardState()
        assertFalse(state.destructiveConfirmationVisible)
    }

    @Test
    fun `destructive confirmation dialog flag set when requested`() {
        val state = ImportWizardState(destructiveConfirmationVisible = true)
        assertTrue(state.destructiveConfirmationVisible)
    }

    // ------------------------------------------------------------------ isDestructive

    @Test
    fun `isDestructive is false for Append strategy with no orphan deletions`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                orphanDecisions = emptyMap(),
            )
        assertFalse(state.isDestructive)
    }

    @Test
    fun `isDestructive is true for ReplaceAll strategy`() {
        val state = ImportWizardState(dataStrategy = ImportDataStrategy.ReplaceAll)
        assertTrue(state.isDestructive)
    }

    @Test
    fun `isDestructive is true when any orphan decision is DeleteTransactions`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                orphanDecisions =
                    mapOf(
                        "Bills" to OrphanDecision.KeepCategory,
                        "Food" to OrphanDecision.DeleteTransactions,
                    ),
            )
        assertTrue(state.isDestructive)
    }

    @Test
    fun `isDestructive is false when all orphan decisions are KeepCategory`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                orphanDecisions =
                    mapOf(
                        "Bills" to OrphanDecision.KeepCategory,
                        "Food" to OrphanDecision.KeepCategory,
                    ),
            )
        assertFalse(state.isDestructive)
    }

    // ------------------------------------------------------------------ ConfirmStep visibility

    @Test
    fun `destructive note is visible on Confirm step when isDestructive`() {
        val state =
            ImportWizardState(
                step = ImportWizardStep.Confirm,
                dataStrategy = ImportDataStrategy.ReplaceAll,
            )
        assertTrue(destructiveNoteVisible(state))
    }

    @Test
    fun `destructive note is hidden on Confirm step when not destructive`() {
        val state =
            ImportWizardState(
                step = ImportWizardStep.Confirm,
                dataStrategy = ImportDataStrategy.Append,
            )
        assertFalse(destructiveNoteVisible(state))
    }

    @Test
    fun `category confirm row is visible on Confirm step when not ReplaceAll`() {
        val state =
            ImportWizardState(
                step = ImportWizardStep.Confirm,
                dataStrategy = ImportDataStrategy.Append,
            )
        assertTrue(categoryConfirmRowVisible(state))
    }

    @Test
    fun `category confirm row is hidden on Confirm step when ReplaceAll`() {
        val state =
            ImportWizardState(
                step = ImportWizardStep.Confirm,
                dataStrategy = ImportDataStrategy.ReplaceAll,
            )
        assertFalse(categoryConfirmRowVisible(state))
    }

    // ------------------------------------------------------------------ toPlan

    @Test
    fun `toPlan with ReplaceAll always produces ReplaceCurrent category strategy`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.ReplaceAll,
                categoryStrategy = ImportCategoryStrategy.Append,
            )
        val plan = state.toPlan()
        assertEquals(ImportDataStrategy.ReplaceAll, plan.dataStrategy)
        assertEquals(ImportCategoryStrategy.ReplaceCurrent, plan.categoryStrategy)
    }

    @Test
    fun `toPlan with Append preserves the selected category strategy`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                categoryStrategy = ImportCategoryStrategy.ReplaceCurrent,
            )
        val plan = state.toPlan()
        assertEquals(ImportDataStrategy.Append, plan.dataStrategy)
        assertEquals(ImportCategoryStrategy.ReplaceCurrent, plan.categoryStrategy)
    }

    @Test
    fun `toPlan carries orphan decisions from state`() {
        val decisions =
            mapOf(
                "Bills" to OrphanDecision.KeepCategory,
                "Food" to OrphanDecision.DeleteTransactions,
            )
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                orphanDecisions = decisions,
            )
        assertEquals(decisions, state.toPlan().orphanDecisions)
    }

    // ------------------------------------------------------------------ default state

    @Test
    fun `default state starts on Preview step with no error and no progress`() {
        val state = ImportWizardState()
        assertEquals(ImportWizardStep.Preview, state.step)
        assertFalse(state.inProgress)
        assertNull(state.errorBannerRes)
        assertNull(state.preview)
        assertFalse(state.destructiveConfirmationVisible)
    }

    @Test
    fun `default data strategy is Append`() {
        assertEquals(ImportDataStrategy.Append, ImportWizardState().dataStrategy)
    }

    @Test
    fun `default category strategy is Append`() {
        assertEquals(ImportCategoryStrategy.Append, ImportWizardState().categoryStrategy)
    }

    // ------------------------------------------------------------------ ManualMerge step visibility

    @Test
    fun `ManualMerge step is shown when step equals ManualMerge`() {
        val state = ImportWizardState(step = ImportWizardStep.ManualMerge)
        assertEquals(ImportWizardStep.ManualMerge, state.step)
    }

    @Test
    fun `merge result name field is hidden when row is CreateNew`() {
        val row =
            MergeRow(
                importCategoryName = "Dining",
                kind = CategoryKind.Expense,
                candidates = emptyList(),
                targetId = null,
                resultName = "",
            )
        assertFalse(mergeResultNameVisible(row))
    }

    @Test
    fun `merge result name field is visible when row is MergeInto`() {
        val candidate = ExistingCategorySummary(id = 1L, name = "Food", kind = CategoryKind.Expense, transactionCount = 0)
        val row =
            MergeRow(
                importCategoryName = "Dining",
                kind = CategoryKind.Expense,
                candidates = listOf(candidate),
                targetId = 1L,
                resultName = "Food",
            )
        assertTrue(mergeResultNameVisible(row))
    }

    @Test
    fun `ManualMerge step does not show Finish button`() {
        val state = ImportWizardState(step = ImportWizardStep.ManualMerge)
        assertFalse(finishButtonVisible(state))
    }

    @Test
    fun `ManualMerge step shows the Next nav button`() {
        val state = ImportWizardState(step = ImportWizardStep.ManualMerge)
        assertTrue(navButtonVisible(state))
    }

    @Test
    fun `MergeRow isMergeInto is false when targetId is null`() {
        val row = MergeRow(importCategoryName = "A", kind = CategoryKind.Expense, candidates = emptyList())
        assertFalse(row.isMergeInto)
    }

    @Test
    fun `MergeRow isMergeInto is true when targetId is set`() {
        val row =
            MergeRow(
                importCategoryName = "A",
                kind = CategoryKind.Expense,
                candidates = emptyList(),
                targetId = 42L,
                resultName = "Existing",
            )
        assertTrue(row.isMergeInto)
    }

    @Test
    fun `toPlan with AppendManualMerge and empty merge rows produces empty mappings`() {
        val state =
            ImportWizardState(
                dataStrategy = ImportDataStrategy.Append,
                categoryStrategy = ImportCategoryStrategy.AppendManualMerge(emptyList()),
                mergeRows = emptyList(),
            )
        val plan = state.toPlan()
        val strategy = plan.categoryStrategy as ImportCategoryStrategy.AppendManualMerge
        assertTrue(strategy.mappings.isEmpty())
    }

    @Test
    fun `category confirm row shows manual merge label when AppendManualMerge strategy is used`() {
        val state =
            ImportWizardState(
                step = ImportWizardStep.Confirm,
                dataStrategy = ImportDataStrategy.Append,
                categoryStrategy = ImportCategoryStrategy.AppendManualMerge(emptyList()),
            )
        assertTrue(categoryConfirmRowVisible(state))
    }
}
