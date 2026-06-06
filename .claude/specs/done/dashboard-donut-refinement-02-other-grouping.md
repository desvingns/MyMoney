# Donut — группировка категорий <2% в псевдо-слайс «Other» (только для пончика)
Epic: dashboard-donut-refinement
Order: 02 of 03
Status: done
Depends-on: —
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: On the dashboard donut, aggregate every expense category whose share of total expense is below 2% into a single display-only pseudo-slice "Other" (Variant A — ALWAYS fold, even a lone sub-2% category). The donut still sums to 100% and the "Other" slice percentage equals the summed share of the folded categories. "Other" is display-only: not a DB category, non-navigable. Categories >= 2% are unchanged.
LAYERS: domain presentation
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt snapshotToSlices() (~L188-206 — after the existing per-category .map(), partition by fraction and synthesize the "Other" slice) + the SliceClicked handler (ignore the Other sentinel id); feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml (new `category_other` = "Other"/"Другое"); core/designsystem/.../icon/CategoryIcons.kt (confirm an "other"/fallback iconKey resolves to Icons.Outlined.Category). core/designsystem/.../donut/CategorySlice.kt reused unchanged.
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Variant A (LOCKED): ALWAYS fold sub-2% expense slices into ONE "Other" slice — including a single sub-2% category. Never leave a sub-2% category as its own slice; never leave a donut gap.
  - Threshold = new named constant OTHER_GROUP_MAX_FRACTION = 0.02f (fraction 0..1). This is a GROUPING/filter threshold, INDEPENDENT of MonefyDonutChart's DEFAULT_LABEL_MIN_FRACTION = 0.03f (label-only). Do NOT merge/conflate; a 2-3% slice stays its own slice with no % label (AS-14 unchanged).
  - "Other" slice: sentinel categoryId = OTHER_CATEGORY_ID = -1L (real Room ids are >= 0); fraction = (1f - sum(major fractions)).coerceAtLeast(0f) to absorb float drift (== sum of folded fractions); color = neutral gray (e.g. Color(0xFF9E9E9E)); iconKey resolving to the generic Category icon; hasBudgetAlert = false. Append LAST in the slice list.
  - Guard: when totalExpense.signum()==0 do NOT synthesize an "Other" slice (no 0% Other); keep current empty/zero behavior.
  - Localized label: the grouping math lives in snapshotToSlices and MUST be unit-tested; the "Other" text MUST be a localized resource (category_other), NOT hardcoded. The VM has no Context — wire per the project's existing VM->UI string convention (e.g. pass the resolved label into snapshotToSlices, or relabel the sentinel slice at the Compose boundary in DashboardScreen via stringResource). Pick whichever matches existing patterns.
  - SliceClicked: tapping "Other" (categoryId == OTHER_CATEGORY_ID) is a no-op — emit NO navigation Action. Real category taps still navigate. Ring stays tappable (hit-test unchanged); only the handler guards the sentinel.
  - "Other" never inherits budget alerts (sentinel id can't be in budgetAlertCategoryIds — verify applyBudgetAlerts leaves it untouched).
  - a11y: the chart contentDescription is built from the slice list, so "Other" is announced via the existing donut_chart_slice template — pass the localized label, not a placeholder. Do NOT add separate Other/center semantics.
  - Tests: unit-test snapshotToSlices for all >=2% (no Other, sum~1), exactly one <2% (one Other == that fraction, sum~1), several <2% (one Other == sum of minors, sum~1), all <2% (single ~100% Other), empty/zero-expense (no Other). Unit-test the SliceClicked guard (Other id -> no nav; real id -> nav). Update existing DashboardViewModelTest fixtures whose sub-2% categories now fold. Keep MonefyDonutChartUiTest green.
  - English ids; no hardcoded strings; zero comments unless non-obvious WHY. Keep grouping in the :feature:dashboard VM, not in :core:designsystem.
=== END SPEC ===

## Gap / context
`snapshotToSlices` сейчас маппит каждую расходную категорию в слайс без группировки, поэтому мелкие
категории захламляют пончик и дают слайвера без подписи. Нужно свернуть <2% в один честный
display-only «Other». Намеренно расходится с `dashboard-final-38.png` (там есть слайс 1%) — это новое правило.

## Implementation links
- commit: 7ad5b1a, 685c6d6, c25aa91
- files: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt; feature/dashboard/src/main/res/values/strings.xml; feature/dashboard/src/main/res/values-ru/strings.xml; feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
