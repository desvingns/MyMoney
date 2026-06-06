# Financial Goals menu entry + navigation + goals list screen (S28)
Epic: financial-goals
Order: 04 of 06
Status: done
Depends-on: 02, 03
Date: 2026-06-05

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Add the entry point and the goals list. Insert a "Финансовые цели" button in the dashboard right
(⋮) drawer **between "Счета" and "Валюты"**, wire it through the dashboard UDF + nav graph to a new
**S28 goals list** screen, and build that screen (empty state + goal rows with `goalIcon` + a FAB → create).
Per FG-6 the screen lives **inside `:feature:dictionaries`** (`…/dictionaries/goals/`), reusing that
module's patterns and deps — NO new Gradle module. Register both the list route and a minimal goal-edit
route placeholder (the edit screen is built in SPEC 05/06) so the graph compiles and the FAB works.
LAYERS: presentation
CHANGED_HINT:
  - app/src/main/java/com/kshavrin/mymoney/navigation/Destinations.kt — after `ACCOUNT_EDIT` (L25), before
    `CURRENCIES_LIST` (L26), add `const val FINANCIAL_GOALS = "dictionaries/goals"` and
    `const val FINANCIAL_GOAL_EDIT = "dictionaries/goals/edit"`.
  - feature/dashboard/.../components/RightDrawerContent.kt — insert a `RightDrawerItem` BETWEEN the Accounts
    item (L49-54) and the Currencies item (L55-60): `label = stringResource(R.string.right_drawer_financial_goals)`,
    `icon = Icons.Outlined.Flag` (resilience: substitute `Savings`/`TrackChanges`/`EmojiEvents` if `Flag`
    reads poorly), `onClick = { onEvent(DashboardEvent.FinancialGoalsClicked) }`,
    `testTag = RIGHT_DRAWER_FINANCIAL_GOALS_TAG`. Add the import for the chosen icon and, in the const block
    (L111-115), `const val RIGHT_DRAWER_FINANCIAL_GOALS_TAG = "right_drawer_financial_goals"`.
  - feature/dashboard/.../DashboardState.kt — add `data object FinancialGoalsClicked : DashboardEvent`
    after `AccountsClicked` (L57).
  - feature/dashboard/.../DashboardAction.kt — add `data object NavigateFinancialGoals : DashboardAction`
    after `NavigateAccounts` (L10).
  - feature/dashboard/.../DashboardViewModel.kt — in `onEvent`, after the `AccountsClicked` branch (L284-287),
    add `DashboardEvent.FinancialGoalsClicked -> { closeDrawers(); emit(DashboardAction.NavigateFinancialGoals) }`.
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt — in the dashboard `onAction`
    when-block, after `NavigateAccounts` (L72-73), add
    `com.kshavrin.mymoney.feature.dashboard.DashboardAction.NavigateFinancialGoals -> navController.navigate(Destinations.FINANCIAL_GOALS)`.
    After the ACCOUNT_EDIT composable block (ends L201), before `CURRENCIES_LIST` (L202), add:
    `composable(Destinations.FINANCIAL_GOALS) { com.kshavrin.mymoney.feature.dictionaries.goals.GoalsListRoute(onAdd = { navController.navigate("${Destinations.FINANCIAL_GOAL_EDIT}/-1") }, onEdit = { id -> navController.navigate("${Destinations.FINANCIAL_GOAL_EDIT}/$id") }, onBack = { navController.popBackStack() }) }`
    and `composable(route = "${Destinations.FINANCIAL_GOAL_EDIT}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { com.kshavrin.mymoney.feature.dictionaries.goals.GoalEditRoute(onBack = { navController.popBackStack() }) }` (mirror the Accounts block at L187-201).
  - NEW feature/dictionaries/.../goals/GoalsListScreen.kt — `GoalsListRoute(onAdd, onEdit, onBack, viewModel = hiltViewModel())`
    (collect state + actions → callbacks, mirror `AccountsListScreen.kt`) + `GoalsListContent(state, onEvent)`:
    Scaffold + TopAppBar (back arrow → BackClicked) + FAB (add → AddClicked); empty state (icon + text) when
    `rows` empty; else LazyColumn of rows — each a coloured circle (`parseHexColor(colorHex)`) rendering
    `Icon(imageVector = goalIcon(goal.iconKey), …)` + goal name + target amount + a variant chip
    (savings/credit). Row click → ItemClicked(id).
  - NEW feature/dictionaries/.../goals/GoalsListViewModel.kt — `@HiltViewModel`, inject `GoalRepository`;
    `StateFlow<GoalsListState>` from `goalRepository.observeActive()`; `SharedFlow<GoalsListAction>`
    (extraBufferCapacity = 4). `GoalsListEvent { AddClicked; ItemClicked(id); BackClicked }` →
    `GoalsListAction { NavigateAdd; NavigateEdit(id); NavigateBack }`. Mirror `AccountsListViewModel.kt`.
  - NEW feature/dictionaries/.../goals/GoalEditScreen.kt — MINIMAL placeholder `GoalEditRoute(onBack)` +
    empty `Scaffold` with a back arrow (so the route compiles + FAB navigates). SPEC 05 replaces the body.
  - feature/dashboard/src/main/res/values/strings.xml + values-ru/strings.xml — add
    `right_drawer_financial_goals` (EN "Financial goals" / RU "Финансовые цели").
  - feature/dictionaries/src/main/res/values/strings.xml + values-ru/strings.xml — add goals-list strings
    (`goals_title`, `goals_empty`, `goals_add`, `goals_variant_savings`, `goals_variant_credit`).
  - NEW feature/dictionaries/src/test/.../goals/fake/FakeGoalRepository.kt — in-memory `GoalRepository`
    (seedable list, MutableStateFlow) for ViewModel + compose tests.
  - NEW feature/dictionaries/src/test/.../goals/GoalsListViewModelTest.kt (unit, Turbine) — observe seeds
    rows; AddClicked→NavigateAdd; ItemClicked→NavigateEdit; BackClicked→NavigateBack.
  - NEW feature/dictionaries/src/test/.../goals/GoalsListContentTest.kt (compose-ui / Robolectric) — empty
    state shows when no rows; rows render name + a `goalIcon` Icon node + variant chip.
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt — add an assertion that the right drawer
    now shows the Financial Goals item (by `RIGHT_DRAWER_FINANCIAL_GOALS_TAG`) and that clicking it emits
    the right action; PRESERVE all existing `RIGHT_DRAWER_*` assertions/tags. Compile + run the affected
    androidTest in the same pass (the runner compiles androidTest).
TEST_TYPES: compose-ui instrumented unit
CONSTRAINTS:
  - **FG-6:** host goals in `:feature:dictionaries` — NO new module, NO `settings.gradle.kts` / `build.gradle.kts`
    change. `:feature:dictionaries` already depends on `:core:{designsystem,domain,…}`, so `goalIcon` +
    `GoalRepository` are reachable. Do NOT add a `:feature:* → :feature:*` dependency.
  - This SPEC's list row needs ONLY `GoalRepository` (SPEC 02) + `goalIcon` (SPEC 03); it does NOT compute
    the projected date (that needs SPEC 01) — keep rows to icon/name/target/variant. The projected
    date/summary appears on the edit screen (SPEC 05/06).
  - `GoalEditRoute` here is a compile-only placeholder; SPEC 05 fully implements it. Keep its signature
    `GoalEditRoute(onBack: () -> Unit, viewModel: GoalEditViewModel = hiltViewModel())` so SPEC 05 only
    fills the body (no nav rewrite).
  - Preserve existing right-drawer behaviour/tags; the new item must not reorder or retag the others.
  - English ids; no hardcoded user-facing strings (EN default + RU); no comments unless WHY.
=== END SPEC ===

## Gap / context
There is no Financial Goals entry point or screen. This SPEC adds the menu button (between Accounts and
Currencies), the dashboard event→action→nav plumbing (mirroring the existing Accounts/Currencies path),
and the S28 list inside `:feature:dictionaries` so it reuses that module's deps and patterns without a
forbidden cross-feature dependency. The edit screen is stubbed for the next SPECs.

## Implementation links
- commit (production): c6b39e3 — feat: add financial goals entry point and S28 list
- commit (tests): a8c20a1 — test: cover financial goals entry, S28 list, and goals list VM/UI
- verified: GoalsListViewModelTest 8/8 pass; :feature:dictionaries + :feature:dashboard unit tests green; :app:compileDebugAndroidTestKotlin compiles (GoalsListContentUiTest + DashboardContentUiTest); Verifier static checks all ok (nav/Hilt/strings/tests).
- push: PENDING — autonomous run, awaiting interactive `y` at the pre-push gate (commits c6b39e3 + a8c20a1 are local on main).
- files:
  - app/src/main/java/com/kshavrin/mymoney/navigation/Destinations.kt
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt
  - feature/dashboard/.../DashboardAction.kt, DashboardState.kt, DashboardViewModel.kt, components/RightDrawerContent.kt
  - feature/dashboard/src/main/res/values{,-ru}/strings.xml
  - feature/dictionaries/.../goals/GoalsListScreen.kt, GoalsListViewModel.kt, GoalEditScreen.kt
  - feature/dictionaries/src/main/res/values{,-ru}/strings.xml
  - feature/dictionaries/src/test/.../goals/GoalsListViewModelTest.kt, fake/FakeGoalRepository.kt
  - app/src/androidTest/.../dictionaries/goals/GoalsListContentUiTest.kt
  - app/src/androidTest/.../dashboard/DashboardContentUiTest.kt
