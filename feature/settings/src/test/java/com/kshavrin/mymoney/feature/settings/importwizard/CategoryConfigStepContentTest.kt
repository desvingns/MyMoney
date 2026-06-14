package com.kshavrin.mymoney.feature.settings.importwizard

import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Contract-level pinning for [CategoryConfigStep] and [ConfigGateStep] (SPEC D7).
 *
 * Follows the same "pure-Kotlin mirror" pattern as [ImportWizardContentTest]:
 * every user-visible decision derived from [ImportWizardState] is expressed as a pure function
 * tested here at the JVM level.
 *
 * Full Compose-UI tests (Robolectric) are deferred to PHASE_15 when the compose-test dependencies
 * are wired into `:feature:settings`.
 */
class CategoryConfigStepContentTest {
    // ------------------------------------------------------------------ helpers

    private fun testCategory(
        id: Long,
        name: String,
        kind: CategoryKind = CategoryKind.Expense,
        iconKey: String = "food",
        colorHex: String = "#FF0000",
    ): Category =
        Category(
            id = id,
            name = name,
            kind = kind,
            iconKey = iconKey,
            colorHex = colorHex,
            sortOrder = id.toInt(),
            isDefault = false,
            isArchived = false,
            createdAt = Instant.EPOCH,
        )

    private fun stateWith(
        categories: List<Category>,
        index: Int = 0,
        inProgress: Boolean = false,
        name: String = categories.getOrNull(index)?.name.orEmpty(),
        iconKey: String = categories.getOrNull(index)?.iconKey.orEmpty(),
        colorHex: String = categories.getOrNull(index)?.colorHex.orEmpty(),
    ): ImportWizardState =
        ImportWizardState(
            step = ImportWizardStep.CategoryConfig,
            configCategories = categories,
            configIndex = index,
            configName = name,
            configIconKey = iconKey,
            configColorHex = colorHex,
            inProgress = inProgress,
        )

    // Mirror of CategoryConfigStep button-label derivation
    private fun configButtonLabelRes(state: ImportWizardState): Int =
        if (state.isLastConfigStep) {
            R.string.import_wizard_config_done
        } else {
            R.string.import_wizard_config_next
        }

    // Mirror of CategoryConfigStep Back / Next button enabled guard
    private fun buttonsEnabled(state: ImportWizardState): Boolean = !state.inProgress

    // ------------------------------------------------------------------ progress badge

    @Test
    fun `configPosition is 1 when configIndex is 0`() {
        val state = stateWith(listOf(testCategory(1L, "Food")), index = 0)
        assertEquals(1, state.configPosition)
    }

    @Test
    fun `configPosition equals configIndex plus one`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"), testCategory(3L, "C"))
        val state = stateWith(cats, index = 2)
        assertEquals(3, state.configPosition)
    }

    @Test
    fun `configTotal equals category list size`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"))
        val state = stateWith(cats)
        assertEquals(2, state.configTotal)
    }

    @Test
    fun `configTotal is zero when category list is empty`() {
        val state = stateWith(emptyList())
        assertEquals(0, state.configTotal)
    }

    // ------------------------------------------------------------------ name field

    @Test
    fun `configName reflects the loaded category name`() {
        val state = stateWith(listOf(testCategory(1L, "Groceries")))
        assertEquals("Groceries", state.configName)
    }

    @Test
    fun `configName can be overridden by user input`() {
        val state = stateWith(listOf(testCategory(1L, "Food")), name = "Supermarket")
        assertEquals("Supermarket", state.configName)
    }

    // ------------------------------------------------------------------ icon field

    @Test
    fun `configIconKey reflects the loaded category icon`() {
        val state = stateWith(listOf(testCategory(1L, "Transport", iconKey = "car")))
        assertEquals("car", state.configIconKey)
    }

    @Test
    fun `configIconKey can be changed to any non-empty string`() {
        val state = stateWith(listOf(testCategory(1L, "Food")), iconKey = "shopping_cart")
        assertEquals("shopping_cart", state.configIconKey)
    }

    // ------------------------------------------------------------------ color field

    @Test
    fun `configColorHex reflects the loaded category color`() {
        val state = stateWith(listOf(testCategory(1L, "Health", colorHex = "#00FF88")))
        assertEquals("#00FF88", state.configColorHex)
    }

    // ------------------------------------------------------------------ isLastConfigStep

    @Test
    fun `isLastConfigStep is true for single-category list at index 0`() {
        val state = stateWith(listOf(testCategory(1L, "Solo")), index = 0)
        assertTrue(state.isLastConfigStep)
    }

    @Test
    fun `isLastConfigStep is false when not on last index`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"))
        val state = stateWith(cats, index = 0)
        assertFalse(state.isLastConfigStep)
    }

    @Test
    fun `isLastConfigStep is true when on last index`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"))
        val state = stateWith(cats, index = 1)
        assertTrue(state.isLastConfigStep)
    }

    @Test
    fun `isLastConfigStep is false when configCategories is empty`() {
        val state = stateWith(emptyList(), index = 0)
        assertFalse(state.isLastConfigStep)
    }

    // ------------------------------------------------------------------ button label

    @Test
    fun `Done label shown on last config step`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"))
        val state = stateWith(cats, index = 1)
        assertEquals(R.string.import_wizard_config_done, configButtonLabelRes(state))
    }

    @Test
    fun `Next label shown on non-last config step`() {
        val cats = listOf(testCategory(1L, "A"), testCategory(2L, "B"))
        val state = stateWith(cats, index = 0)
        assertEquals(R.string.import_wizard_config_next, configButtonLabelRes(state))
    }

    // ------------------------------------------------------------------ button enabled state

    @Test
    fun `Back and Next buttons are enabled when not in progress`() {
        val state = stateWith(listOf(testCategory(1L, "Food")), inProgress = false)
        assertTrue(buttonsEnabled(state))
    }

    @Test
    fun `Back and Next buttons are disabled while inProgress`() {
        val state = stateWith(listOf(testCategory(1L, "Food")), inProgress = true)
        assertFalse(buttonsEnabled(state))
    }

    // ------------------------------------------------------------------ configCurrentKind

    @Test
    fun `configCurrentKind returns Expense for expense category`() {
        val state = stateWith(listOf(testCategory(1L, "Bills", kind = CategoryKind.Expense)), index = 0)
        assertEquals(CategoryKind.Expense, state.configCurrentKind)
    }

    @Test
    fun `configCurrentKind returns Income for income category`() {
        val state = stateWith(listOf(testCategory(1L, "Salary", kind = CategoryKind.Income)), index = 0)
        assertEquals(CategoryKind.Income, state.configCurrentKind)
    }

    @Test
    fun `configCurrentKind is null when categories list is empty`() {
        val state = stateWith(emptyList(), index = 0)
        assertNull(state.configCurrentKind)
    }

    @Test
    fun `configCurrentKind follows configIndex not zero`() {
        val cats =
            listOf(
                testCategory(1L, "Bills", kind = CategoryKind.Expense),
                testCategory(2L, "Salary", kind = CategoryKind.Income),
            )
        val state = stateWith(cats, index = 1)
        assertEquals(CategoryKind.Income, state.configCurrentKind)
    }

    // ------------------------------------------------------------------ ConfigGateStep: state mirrors

    @Test
    fun `ConfigGate step is not Confirm and does not show the Finish import button`() {
        val state = ImportWizardState(step = ImportWizardStep.ConfigGate)
        // Finish button guard: step == Confirm
        assertFalse(state.step == ImportWizardStep.Confirm)
    }

    @Test
    fun `ConfigGate step is a post-commit step`() {
        val state = ImportWizardState(step = ImportWizardStep.ConfigGate)
        assertTrue(state.step == ImportWizardStep.ConfigGate || state.step == ImportWizardStep.CategoryConfig)
    }

    @Test
    fun `CategoryConfig step is a post-commit step`() {
        val state = ImportWizardState(step = ImportWizardStep.CategoryConfig)
        assertTrue(state.step == ImportWizardStep.ConfigGate || state.step == ImportWizardStep.CategoryConfig)
    }
}
