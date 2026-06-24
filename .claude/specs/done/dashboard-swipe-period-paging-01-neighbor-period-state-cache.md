# Precompute & cache adjacent-period dashboard state (S01)
Epic: dashboard-swipe-period-paging
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: After every period settle on the dashboard, the ViewModel precomputes the FULL DashboardState for both the previous and the next period in the background and caches them, so the upcoming pager (SPEC 02) can render the adjacent page with real data instead of a placeholder. Expose the cached neighbor states (each independently nullable / loading until its background job lands) on DashboardState. No visible UI change yet — this is the data foundation for the paging peek.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardViewModel.kt — after the current period's recomputeBalance() completes (the path that handles PreviousPeriod/NextPeriod and the initial load), launch two background jobs computing the snapshot/currencyCards/trend for period.previous() and period.next() reusing the SAME compute helpers (computeSnapshot/computeCurrencyCards/trend) used for the current period (G9 `:357-416`, G4 `:709-724`). Cache the two resulting DashboardState-shaped values; cancel & recompute them whenever the committed period changes (mirror the existing `recomputeJob?.cancel()` pattern, G9). Skip neighbor computation when `period is Period.All` (no neighbors, G13). (assumption: neighbor jobs are best-effort — if not finished when read, the neighbor is exposed as still-loading.)
  - feature/dashboard/.../DashboardState.kt — add two fields holding the precomputed neighbor render-state, e.g. `previousPeriodState: DashboardState? = null` / `nextPeriodState: DashboardState? = null` (or a lighter dedicated `PeriodPageState` carrying exactly the fields DashboardContent needs: balanceSnapshot, currencyCards, periodNet, ringFraction, ringIsExpense, trendPoints, slices, expenseTiles, isLoading, period — G8). Default null. (assumption on exact field shape — pick the lightest type that lets DashboardContent render a neighbor page.)
TEST_TYPES: unit
CONSTRAINTS:
  - SHARES feature/dashboard/.../DashboardState.kt with SPEC 02 → MUST be implemented and merged before 02 (same-file clash, see overview).
  - Reuse the existing per-period compute helpers; do NOT add new repository/DAO/Room methods — neighbor state is computed the same way as the current period (G9–G10). No repository/data-layer edits.
  - Avoid recursion / infinite expansion: the cached neighbor states must NOT themselves carry their own neighbor caches (only the committed center period precomputes neighbors). Guard against the field type recursing forever — prefer a dedicated lightweight page-state type over reusing the whole DashboardState if that risks it.
  - Cancel in-flight neighbor jobs on every committed period change and on account/selection change (reuse the recomputeJob cancel discipline, G9) so stale neighbors never surface.
  - `Period.All` → no neighbor computation; neighbor fields stay null. `CustomRange` neighbors use the existing range-length shift (G13).
  - No hardcoded strings (EN+RU), English ids, no comments unless WHY. Run `:feature:dashboard` ktlintFormat before commit (tester emits unformatted Kotlin, G13).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Adjacent-period dashboard state is ready ahead of the swipe
  Covers the data foundation for the dashboard paging peek (S01).

  Background:
    Given the user is on the dashboard viewing a bounded period (e.g. a month) with transactions

  Scenario: Neighbors are precomputed after the period settles
    When the dashboard finishes loading the current period
    Then the dashboard state also holds the fully computed balance and chart data for the previous period
    And the dashboard state holds the fully computed balance and chart data for the next period

  Scenario: Committing a new period refreshes the neighbor cache
    When the user moves to the next period
    Then the next period becomes the current period
    And the neighbor cache is recomputed for the new previous and next periods
    And any stale in-flight neighbor computation is discarded

  @edge
  Scenario: The All period has no neighbors
    Given the selected period is "All"
    When the dashboard loads
    Then no previous or next period state is computed
    And both neighbor states remain absent
```

## Gap / context
Соседний период дорого считать (per-period Room-чтения, кэша всех транзакций в памяти нет — G9–G11), поэтому синхронно отрендерить соседа в пейджере нельзя. Этот SPEC заранее (в фоне) готовит реальные состояния prev/next, чтобы SPEC 02 показывал «полный экран соседнего периода» сразу при свайпе.

## Implementation links
- commit: 2081b96e (prod), a1aeeea0 (tests)
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt, feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt, feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
