# Empty-state donut: gray ring + expense-category icons + 0/0 center (S01)
Epic: monefy-behavioral-fidelity
Order: 03 of 09
Status: draft
Depends-on: 02 (center renderer + isExpense)
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: When the selected period has NO transactions, still render the donut (11.jpg): a neutral GRAY ring + every expense category icon (in its own colour) evenly placed around the perimeter + a center showing income/expense "0,00 ₽" (green) over "0,00 ₽" (red). Today an empty period draws nothing in the chart area.
LAYERS: domain presentation
CHANGED_HINT: feature/dashboard/.../DashboardViewModel.kt (inject CategoryRepository; observe observeByKind(CategoryKind.Expense); build expenseCategoryPlaceholders: List<CategorySlice> with fraction=0, colour=parseHexColor(colorHex), iconKey, label; sort by sortOrder); feature/dashboard/.../DashboardState.kt (+ expenseCategoryPlaceholders field); feature/dashboard/.../DashboardScreen.kt L230 (pass emptyStateIcons = state.expenseCategoryPlaceholders); core/designsystem/.../donut/MonefyDonutChart.kt (+ param `emptyStateIcons: List<CategorySlice> = emptyList()`; when slices.isEmpty() draw a gray ring + evenly-spaced icons + the 0/0 center; otherwise unchanged); core/designsystem/.../donut/DonutGeometry.kt (+ pure `evenAngles(count): List<Float>`); feature/dashboard/.../DashboardViewModelTest.kt (update buildViewModel() + add a FakeDashboardCategoryRepository); screenshot 11.jpg
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Empty-state rendering activates ONLY when slices.isEmpty(); the populated arc/label/icon/badge path MUST stay byte-for-byte unchanged.
  - REQUIRED: add `categoryRepository: CategoryRepository` to the DashboardViewModel constructor AND update DashboardViewModelTest.buildViewModel() + add a FakeDashboardCategoryRepository (mirror the existing fakes). Do NOT thread categories through BalanceSnapshot (SPEC-02 deliberately avoided this VM dependency; we now accept the constructor change — it is expected, not a workaround target).
  - Empty ring stroke = MaterialTheme.colorScheme.outline (single neutral), same strokeWidth/radius as the populated ring. Empty icons are FULL-COLOUR per category (11.jpg) — NOT muted. Center = the SPEC-02 two-line renderer with income=0 / expense=0 (two stacked lines, "0,00 ₽" green over "0,00 ₽" red — 11.jpg shows two lines).
  - `DonutGeometry.evenAngles(count)` is PURE (start -90°, step 360/count) with unit tests; the Canvas reuses the existing icon + leader-line drawing via a shared helper — do NOT duplicate the geometry inline.
  - Empty-state icon taps are a NO-OP in this SPEC (an optional `onEmptyCategoryClick: ((CategorySlice) -> Unit)? = null` hook may be added but left unwired; do NOT modify the add-expense route contract). Icon source = observeByKind(CategoryKind.Expense); keys are ic_cat_* resolving via the existing categoryIcon(). English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User note #3. DonutGeometry.computeSliceArcs returns empty for empty input, so when there are no
transactions the chart draws nothing. The reference (11.jpg) shows the full expense-category icon
ring + a gray ring + the 0/0 center even with no records, inviting the first entry. The dashboard VM
does not currently know the category catalog (it has no CategoryRepository) — this SPEC injects it.

## Implementation links
(pending — fill commit + changed files after `/cmp --feature --next`)
