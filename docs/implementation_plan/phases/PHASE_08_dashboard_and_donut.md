# PHASE 08 — Dashboard (S01/S05) + drawers (S02/S04) + custom MonefyDonutChart

## Goal

Build the centerpiece of the app: the main dashboard (S01 day-period, S05 year-period — same Composable parameterised by period), the two side drawers (S02 period + account, S04 settings-entry), and the custom `MonefyDonutChart` Composable (pure Compose Canvas, 2-tier: outer ring split income/expense, inner segments by category, surrounding icons connected by 1 dp spokes per §6.5). Per AS-12 the "Pick a date" entry opens a **two-date range picker**. Per AS-14 percentage labels render on slices `>= 3 %`.

## TDD anchors

- §4.2 S01 Main dashboard (day) — lines 520–601 (the longest screen spec; covers layout, two FABs, balance pill, donut, category strip, swipe gestures)
- §4.3 S05 Main dashboard (year) — lines 602–613 (deltas vs S01: long period, positive balance)
- §4.4 S02 Period & account drawer (left) — lines 614–645
- §4.5 S04 Settings entry drawer (right) — lines 646–665
- §6.5 Components — lines 1380–1424 (donut signature, spokes, balance pill, confetti)
- §6.7 Motion — lines 1433–1445 (donut grow `spring(0.7, 300)`, balance colour swap `tween(400)`)
- §5 Business rules BR-1 … BR-5 (period defaults, donut math, threshold per AS-14) — lines 1172–1207
- AS-12, AS-14 — §14.1 lines 2727–2750
- BR-21, BR-27 — §5 (transfer icon → S03 per AS-1; milestone confetti per AS-10)

## Prerequisites

- PHASE_06 — done (BalanceCalculator + repos)
- PHASE_03 — done (theme + designsystem stubs)

## Deliverables (in `:feature:dashboard`)

- `feature/dashboard/build.gradle.kts` — compose + lifecycle-runtime-compose + hilt-navigation-compose + `:core:designsystem` + `:core:domain`.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt` — `@Composable fun DashboardRoute(vm: DashboardViewModel = hiltViewModel(), onNavigate: (Route) -> Unit)`. Hoists state; renders `DashboardScreen(state, onEvent)`. The Composable is reused for S01 + S05 — the only difference is the `Period` it shows.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt` — `@HiltViewModel`. State (per §2.3 UDF) holds `period`, `account`, `balanceSnapshot`, `slices`, `isLoading`, `showConfetti`. Events: `PeriodChanged`, `AccountChanged`, `LeftDrawerToggled`, `RightDrawerToggled`, `MinusFabClicked`, `PlusFabClicked`, `TransferClicked`, `SearchClicked`, `BalanceCardClicked`, `SliceClicked(categoryId)`. Actions: navigation effects.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/state/DashboardState.kt` — data class.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodStrip.kt` — chip row showing Today/Week/Month/Year/All + a "Pick a date" chip. Per AS-12 the "Pick a date" chip opens a `DateRangePicker` (M3 `DateRangePickerDialog`) and emits `Period.CustomRange(start, end)`.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/BalancePill.kt` — wraps `MonefyBalancePill` from `:core:designsystem`. Colour reflects sign per §6.5: positive → `primary`, negative → `tertiary`. Tap → invokes `onEvent(BalanceCardClicked)`.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt` — period chips at top, account list below (one row per `account`), "Manage accounts" footer.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt` — entries: Settings, Categories, Accounts, Currencies, Search, About.
- `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/TwoFabLayout.kt` — non-standard FAB layout per §4.2: `-` FAB on left (tertiary ring), `+` FAB on right (primary ring), small transfer icon between them. Custom positioning via `Box` + `Modifier.align`.

## Deliverables (in `:core:designsystem`) — finalise PHASE_03 stubs

- `core/designsystem/.../donut/MonefyDonutChart.kt` — full implementation per §6.5 lines 1402–1423:
  - `Canvas { drawArc(...) }` outer ring split 50/50 income/expense.
  - Each half subdivided by category proportions.
  - Inner labels: stacked income above, expense below (`drawText`).
  - Surrounding category icons orbiting on an invisible circle ~16–24 dp larger radius; thin 1 dp `drawLine` spokes from icon centre to corresponding slice midpoint.
  - On first composition, segments grow from 0 % over 600 ms via `Animatable` + `spring(dampingRatio = 0.7f, stiffness = 300f)`.
  - **`private const val LABEL_THRESHOLD = 0.03f`** per AS-14 — labels only on slices `>= 3 %`.
  - `onSliceClick: ((CategorySlice) -> Unit)?` callback. Hit-testing via polar coords.
- `core/designsystem/.../pill/MonefyBalancePill.kt` — full implementation.
- `core/designsystem/.../confetti/MonefyConfetti.kt` — one-shot ~1500 ms Lottie or Canvas particle. Triggered by parent via `LaunchedEffect(state.showConfetti)`.

## Task checklist

- [ ] Re-read TDD anchors. Hand-draw the dashboard composition: TopAppBar (with hamburger left, transfer icon centre, search icon right), period strip below, balance pill, donut chart, category strip at bottom, two-FAB row.
- [ ] Set up the `ModalNavigationDrawer` at the `:app` `MyMoneyNavHost` level so it wraps the dashboard route. Actually no — per §4.2 the drawer is per-screen. Implement two `ModalNavigationDrawer`s: left drawer wraps the content, right is implemented as a second `ModalNavigationDrawer` with `drawerContent` on the right via Mirror modifier. Or use `androidx.compose.material3.DismissibleNavigationDrawer` for both. Pick the M3 idiom — note per §4.4 + §4.5 the drawers carry independent state (`leftOpen`, `rightOpen`).
- [ ] Wire `DashboardViewModel`. Observe `accountRepository.observeActive()`, `currencyRepository.observeActive()`, derive `currentAccount` from `AppSettings.defaultAccountId`. Run `BalanceCalculator(account, period)` whenever `(account, period)` changes — use `combine(...).flatMapLatest { ... }`.
- [ ] Implement `PeriodStrip`. Chips: Today, Week, Month, Year, All, "Pick a date". Tapping "Pick a date" opens `DateRangePickerDialog` (M3). On confirm: emit `Period.CustomRange(start, end)`.
- [ ] Implement `BalancePill`. Animate colour change between sign flips: `animateColorAsState(targetValue = if (balance >= 0) primary else tertiary, animationSpec = tween(400))`.
- [ ] Implement the two-FAB layout. The "−" FAB invokes `MinusFabClicked` → navigation effect to `"add_expense"`. "+" FAB → `"add_income"`. Transfer icon → `"transfer"` per AS-1.
- [ ] Implement the **donut chart** — the hardest single component in this phase:
  - Compute slice geometry from `slices: List<CategorySlice>`. Outer ring is 2 arcs (income half, expense half). Each half is subdivided into per-category sub-arcs proportional to that category's total.
  - Animate from `fraction = 0` to `fraction = slice.fraction` on first composition. Use `Animatable<Float>` per slice.
  - Render percentage label centred on each slice's arc midpoint when `slice.fraction >= LABEL_THRESHOLD` (3 %).
  - Render orbital icons: for each slice with an icon, compute the icon's centre `(cx + r_outer * cos(theta_mid), cy + r_outer * sin(theta_mid))` where `r_outer = chartRadius + 24.dp`. Draw 1 dp line from icon centre to slice midpoint at `chartRadius - strokeWidth/2`.
  - Hit-testing: on `Modifier.pointerInput(slices) { detectTapGestures { offset -> findSlice(offset)?.let(onSliceClick) } }`.
- [ ] Implement `MonefyBalancePill` + `MonefyConfetti` for AS-10 lifetime trigger. Pull `firstPositiveSeen` from `AppSettings`; on first render with `balanceSnapshot.net.amount >= BigDecimal.ZERO`, fire confetti once and `appSettingsRepository.update { it.copy(firstPositiveSeen = true) }`.
- [ ] Tap-handling:
  - `BalanceCardClicked` → nav to `"transactions?accountId=<id>"` (S12 unfiltered per AS-2). Routing wires in PHASE_11; for now this is a navigation Action that PHASE_11 picks up.
  - `SliceClicked(categoryId)` → nav to `"transactions?accountId=<id>&categoryId=<categoryId>"` per AS-3.
  - `TransferClicked` → nav to `"transfer"` per AS-1.
- [ ] Wire the dashboard into `MyMoneyNavHost`: replace the PHASE_07 placeholder with the real `DashboardRoute`.
- [ ] Install and walk the dashboard with seeded data. Confirm:
  - Donut grows in on first render.
  - Drawers slide both directions.
  - Period strip switches between periods correctly (Today vs Year — S01 vs S05).
  - "Pick a date" opens **two-date range picker** (AS-12).
  - Tapping the balance pill triggers a Toast (placeholder routing) until PHASE_11.
  - Tapping a slice triggers a Toast with the slice's category id.
- [ ] Visual QA: compare against `D:\Pet\TDD_creater\MyMoney\input\screenshots\01.jpg` and `05.jpg`. Mint background, mint primary, red negative, donut with category icons orbiting.
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:dashboard:assembleDebug` succeeds.
- App launches into the dashboard (after onboarding). Donut renders correctly with seeded data + a few test transactions. Animations smooth.
- All BR-1 … BR-5 + AS-12 + AS-14 behaviours observable manually.
- A unit test for `MonefyDonutChart` slice-geometry (pure-math helper extracted from the Composable) exists and passes.

## Verification commands

```powershell
cd D:\Pet\TDD_creater\MyMoney_app
.\gradlew.bat :feature:dashboard:assembleDebug
.\gradlew.bat :feature:dashboard:test
.\gradlew.bat :app:installDebug
adb shell am force-stop com.kshavrin.mymoney.debug
adb shell am start -n com.kshavrin.mymoney.debug/com.kshavrin.mymoney.MainActivity
```

## Notes for next session

(empty — fill at end of session. Especially: any donut-rendering edge cases at small fractions, dark-theme polish gaps.)
