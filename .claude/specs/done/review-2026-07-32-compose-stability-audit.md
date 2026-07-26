# Compose stability audit: compiler metrics + immutable collections
Epic: review-2026-07
Order: 32 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Run the Compose compiler metrics/reports once across all Compose modules, review every UNSTABLE class/parameter that participates in hot recomposition paths (dashboard state, list rows), and fix the flagged ones — kotlinx.collections.immutable (ImmutableList) for collection-bearing UiState fields and/or @Immutable annotations where structural immutability is real but not inferred; commit the metrics summary and the fix list as the SPEC report.
LAYERS: [presentation]
CHANGED_HINT: root/module build.gradle.kts (compiler metrics flags, temporary), feature/*/**/ *UiState/ *State classes, gradle/libs.versions.toml (kotlinx-collections-immutable)
TEST_TYPES: unit
CONSTRAINTS: evidence-driven — fix ONLY what the metrics flag on real recomposition paths, no blanket annotation spraying; kotlinx.collections.immutable is a runtime dep addition → one-line user ack at implement time; existing tests updated per stale-test rule
=== END SPEC ===

## Gap / context
Stability rests on compiler inference today; one metrics pass either proves it fine
or pinpoints the recomposition leaks. Source: review items 41+50 (P3/S).

## Implementation links
- commit: see repository commit for this completed SPEC
- files: `gradle/libs.versions.toml`, `feature/dashboard`, `feature/transactionslist`

## Compose stability audit report

- Metrics pass: generated debug Compose compiler reports for all 11 Compose modules with a temporary root reporting hook and `:app:assembleDebug --rerun-tasks`; the hook was removed after the pass. Focused post-fix reports were then generated for `:feature:dashboard` and `:feature:transactionslist`.
- Reviewed hot paths: `DashboardContent` / `DashboardBodyPager` / `DashboardPage`, the dashboard category-tile rendering loop, and `TransactionsListContent` / `TransactionsListRows` / `TransactionListRow`.
- Result: the dashboard root, page state, and category-tile rendering loop are stable. The transactions-list state, rows, locale wrapper, and base row are stable in the post-fix reports.
- Deliberate non-fixes: compiler-unknown `Locale` and cross-module domain leaf values remain at individual dashboard boundaries; no broad configuration or annotation was added. The public `CategoryTilesList(List<CategoryTileItem>)` remains for test compatibility and forwards directly to the stable internal rendering function.
- Verification: focused post-fix metrics compile and normal compile both passed for `:feature:dashboard:compileDebugKotlin` and `:feature:transactionslist:compileDebugKotlin`. A scoped ktlint check found one new `TransactionsListViewModel` line-break violation, which was corrected before closeout; no full-repository suite was run.

## Fix list

- Added `kotlinx.collections.immutable:0.3.8` only to `:feature:transactionslist` after metrics identified the collection-bearing transaction-list UiState as a hot-path instability. The requested implementation authorizes this runtime dependency addition.
- Replaced the transaction-list UiState list with `ImmutableList`, removed map fields from that UiState, and introduced an immutable UI read model containing each row's resolved display data.
- Applied `@Immutable` only to the directly reported dashboard UDF state/projection classes and the transaction-list UDF state/read model whose value structure is immutable.
- Isolated the dashboard category-tile loop behind an immutable wrapper so the actual rendering function receives a stable parameter without changing its public List-compatible adapter.
