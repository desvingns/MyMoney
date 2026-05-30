package com.kshavrin.mymoney.feature.transaction.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Compose-UI coverage for the redesigned [CategoryPickerContent] (S09/S10), Pattern B
 * (`docs/DEVICE_VERIFICATION_PLAN_FOR_SONNET.md` §5): render the public stateless
 * `*Content(state, onEvent)` directly inside `MyMoneyTheme`, capture events into a list,
 * and assert with `runOnIdle`.
 *
 * Seams used (from the implementer's contract note):
 *   - each category cell exposes `contentDescription = category.name`;
 *   - the "+ ДОБАВИТЬ" cell carries testTag "category_picker_add_cell" and emits
 *     `CategoryPickerEvent.AddCategoryClicked`;
 *   - tapping a category cell emits `CategoryPickerEvent.CategoryClicked(id)`.
 *
 * Strings are resolved via `targetString(...)` (never literals) because the app ships
 * EN + RU — the CTA is "+ ADD" / "+ ДОБАВИТЬ".
 *
 * NOTE: :feature:transaction has no instrumented (androidTest) wiring yet — this test is
 * device-deferred until the module's `connectedDebugAndroidTest` classpath (compose-ui-test-junit4
 * + androidx.test) is enabled.
 */
@RunWith(AndroidJUnit4::class)
class CategoryPickerContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private val addCellTag = "category_picker_add_cell"

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind = CategoryKind.Expense,
        sortOrder: Int = 0,
    ): Category = Category(
        id = id,
        name = name,
        kind = kind,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = false,
        createdAt = now,
    )

    private fun setContent(
        state: CategoryPickerState,
        onEvent: (CategoryPickerEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                CategoryPickerContent(state = state, onEvent = onEvent)
            }
        }
    }

    @Test
    fun `renders a category cell by its name content description`() {
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(category(id = 1L, name = "Food")),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription("Food")
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a category cell emits CategoryClicked with its id`() {
        val captured = mutableListOf<CategoryPickerEvent>()
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(
                    category(id = 1L, name = "Food", sortOrder = 0),
                    category(id = 2L, name = "Bills", sortOrder = 1),
                ),
            ),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithContentDescription("Bills")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf<CategoryPickerEvent>(CategoryPickerEvent.CategoryClicked(2L)), captured)
        }
    }

    @Test
    fun `add cell is shown by its test tag in a non-empty picker`() {
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(category(id = 1L, name = "Food")),
            ),
        )

        composeTestRule
            .onNodeWithTag(addCellTag)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping the add cell emits AddCategoryClicked`() {
        val captured = mutableListOf<CategoryPickerEvent>()
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(category(id = 1L, name = "Food")),
            ),
            onEvent = { captured += it },
        )

        composeTestRule
            .onNodeWithTag(addCellTag)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf<CategoryPickerEvent>(CategoryPickerEvent.AddCategoryClicked), captured)
        }
    }

    @Test
    fun `add cell shows the localized CTA label`() {
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(category(id = 1L, name = "Food")),
            ),
        )

        composeTestRule
            .onNodeWithTag(addCellTag)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.add_category_cta))
            .assertIsDisplayed()
    }

    @Test
    fun `empty picker shows the empty message`() {
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = emptyList(),
            ),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.category_picker_empty))
            .assertIsDisplayed()
    }

    @Test
    fun `non-null amountPreview is displayed`() {
        setContent(
            CategoryPickerState(
                kind = CategoryKind.Expense,
                categories = listOf(category(id = 1L, name = "Food")),
                amountPreview = "12.50",
            ),
        )

        composeTestRule
            .onNodeWithText("12.50")
            .assertIsDisplayed()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
