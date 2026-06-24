# Dashboard content paging/peek via HorizontalPager (S01)
Epic: dashboard-swipe-period-paging
Order: 02 of 02
Status: done
Depends-on: 01 (neighbor states cached on DashboardState)
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Replace the dashboard's instant-swap swipe with a Monefy-style full-screen paging/peek effect. The dashboard body (Aurora card + donut/tiles/currency cards — everything DashboardContent draws) becomes a 3-page HorizontalPager [previous | current | next], each page rendered by DashboardContent using the period's render-state (current from DashboardState, neighbors from the cache added in SPEC 01). As the user drags, the adjacent period's real content slides into view following the finger; on release the page settles to a neighbor (committing the period change) or snaps back. The top bar (☰ ⇄ 🔍 ⋮ + the «‹ period ›» row) and the 3 FABs stay fixed; the 3-up PeriodLabel tracks the pager's drag offset. Infinite paging via re-centering on settle. Period.All disables paging.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt — REMOVE the `detectHorizontalDragGestures` block + 56.dp threshold logic (G2 `:174-201`). Wrap the dashboard BODY (the `DashboardContent(state,onEvent)` call, G1 `:121-124`) in a `HorizontalPager` with `rememberPagerState`, mirroring the onboarding usage (G6 `OnboardingScreen.kt:16-18,54,99-107`). 3 pages [prev|current|next]; page i renders `DashboardContent` fed the matching render-state (center = current state; prev/next = the cached neighbor states from SPEC 01, falling back to a loading page if a neighbor isn't ready yet — assumption H2). Keep top bar + ThreeFabLayout OUTSIDE the pager (D2). On settle to a neighbor page, emit the existing `DashboardEvent.PreviousPeriod` / `NextPeriod` (G3/G4) so the period commit + recompute + neighbor refresh fire, then re-center the pager to the middle page WITHOUT animation (infinite paging, D4). Keep `SoundKey.SWIPE`+`HapticKind.SOFT` on settle (G5). Keep left `ModalNavigationDrawer gesturesEnabled=false` (G5).
  - feature/dashboard/.../components/PeriodLabel.kt — drive the 3-up label from the pager's current page + fractional offset so it slides/tracks the drag (G7 `:50-173`), instead of being static. (assumption: animate via the pager offset value passed down from DashboardScreen.)
  - feature/dashboard/.../DashboardState.kt — read-only consumer of the neighbor fields added in SPEC 01 (no new fields here unless a small `canPage`/derived flag is cleaner, e.g. `false` when `period is Period.All`, G13).
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - SHARES feature/dashboard/.../DashboardState.kt with SPEC 01 → implement AFTER 01 is merged (same-file clash, see overview).
  - Dashboard Compose UI tests are INSTRUMENTED in `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt` (NOT Robolectric); run on a connected device via `:app:connectedDebugAndroidTest`. Update existing swipe tests (the old `detectHorizontalDragGestures` assertions become pager-swipe assertions). Use `performTouchInput { swipeLeft()/swipeRight() }`; assert period change via the existing `DASHBOARD_*_TAG` / period-label semantics, not internals (G12).
  - Direction unchanged: a LEFT swipe → next period, a RIGHT swipe → previous period (D5; confirm against the prior behavior). A settle that doesn't cross the threshold snaps back to the current period with no period change.
  - `Period.All` → pager has a single page / paging disabled (no neighbors, D4/G13). `CustomRange` pages use the existing range-length shift.
  - Do NOT regress: hamburger still opens the left drawer; vertical scroll inside a page still works; taps aren't swallowed (the pager must not capture vertical gestures meant for scrolling content).
  - No hardcoded strings (EN+RU), English ids, no comments unless WHY. Run `:feature:dashboard` ktlintFormat before commit (G13). Clean-assemble + on-device smoke (this is an interactive gesture change — semantics tests miss draw/gesture occlusion per project memory).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Full-screen paging between dashboard periods
  Covers the Monefy-style period peek on S01.

  Background:
    Given the user is on the dashboard viewing a bounded period with transactions
    And the previous and next periods have been precomputed

  Scenario: Peek the next period mid-drag
    When the user drags the dashboard body to the left without releasing
    Then the next period's full dashboard content slides into view following the finger
    And the period label slides toward the next period

  Scenario: Commit the next period on release
    When the user swipes the dashboard body left past the settle threshold and releases
    Then the dashboard settles on the next period
    And the next period becomes the current period
    And a swipe sound and soft haptic are emitted

  @snap-back
  Scenario: Small drag snaps back
    When the user drags the dashboard body a little and releases before the settle threshold
    Then the dashboard returns to the current period
    And the period does not change

  @edge
  Scenario: The All period cannot be paged
    Given the selected period is "All"
    When the user swipes the dashboard body horizontally
    Then the period does not change
    And no adjacent period is shown

  @no-regression
  Scenario: Horizontal swipe no longer opens the menu
    When the user swipes right on the dashboard body
    Then the previous period is shown
    And the left navigation drawer does not open
    But tapping the hamburger button still opens the left drawer
```

## Gap / context
Сегодня свайп мгновенно подменяет период без визуального перехода. Этот SPEC превращает тело dashboard в `HorizontalPager`, где соседние страницы рендерятся реальным контентом (из кэша SPEC 01), давая плавный peek-эффект как в Monefy; верхняя панель и FAB остаются на месте, ярлык периода следит за свайпом.

## Implementation links
- commit: 003e7218 (prod), 2ec6c2be (tests)
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt, feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodLabel.kt, app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardBodyPagerSwipeUiTest.kt, app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt, feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardPagerStateTest.kt
