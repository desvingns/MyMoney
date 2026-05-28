# MyMoney — On-Device Test Coverage — Runbook for Claude Sonnet 4.6

> **What this is.** A step-by-step runbook for finishing the on-device (instrumented) test coverage
> of MyMoney, written so a less-powerful model can execute it mechanically. It **supersedes
> `docs/DEVICE_VERIFICATION_PLAN_FOR_CODEX.md`** for execution (that doc was written for Codex GPT-5.5
> and its coverage numbers are stale). The **single source of progress truth is
> `docs/DEVICE_VERIFICATION_PROGRESS.md`** — read it first, update it after every green test.
>
> **The loop, in one line:** pick the next un-green control → write **one** test → run it on
> `Pixel_5_API_34` → read the report → if green, update the tracker → **STOP**. Repeat.

---

## 0. Before you touch anything

1. Open `docs/DEVICE_VERIFICATION_PROGRESS.md`. Find the first row whose status is **Pending** or
   **In progress** in the *Delivery Order* table, then the matching rows in the *Screen Matrix*.
2. Open this runbook's **slice card** for that screen (§8).
3. Do exactly one control/test, following the card. Do not batch. Do not skip ahead.
4. Everything you need is in this file. If something here disagrees with the Codex doc, **this file
   wins**; if either disagrees with the TDD (`TDD/MyMoney/MyMoney_TDD.md`), **the TDD wins**.

Already green (do **not** redo): S01/S05, S02, S03, S06, S07, S08, S09, S11, S12, S13, S14, S15,
S16, S17, S18, S19, S20, S27, plus baseline `:core:*` suites. **Remaining:** Slice 0 infra, S00,
S21–S26, three Pattern A E2E journeys, Worker instrumentation.

---

## 1. The six rules (always — re-read before every slice)

1. **One test at a time.** Write → run on AVD → read report → green → update tracker → **STOP**.
   Never write a batch of tests "blind". This is the whole point of the runbook.
2. **Never weaken a test.** No `@Ignore`, no deleted assertions, no `assertTrue(true)`, no
   catch-and-swallow. If a test exposes a real product defect, fix the **production** bug with the
   smallest correct change and record it in the tracker. If you cannot fix it safely, leave the test
   out and write the gap in the tracker "notes" column — never hide it.
3. **Missing-seam policy (the most important rule for you).** If a control has no testable hook:
   - You **may** add ONLY one of: a `Modifier.testTag("…")`, a `contentDescription`, or change a
     `*Content` composable's visibility to `public`. Nothing else.
   - You **must not** invent UI, events, ViewModel methods, or features to make a test possible.
   - If the control or state genuinely **does not exist** in production (e.g. an error-retry button
     the screen never renders), **SKIP it**, write one line in the tracker notes
     (`<control> — no production seam, escalated`), and move to the next control.
4. **Keep Sentry / Firebase / cloud sync OFF.** They are off by default — do not enable them in any
   test.
5. **Determinism.** Wrap content in `MyMoneyTheme { … }`. Assert only after the frame settles —
   use `composeTestRule.runOnIdle { … }` or `waitUntil { … }`. Never `Thread.sleep`, never assert
   mid-animation. Seed dates relative to `LocalDate.now()`, never hard calendar dates.
6. **Strings via resources.** Look up every user-facing string with
   `InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.…)` (the
   `targetString(...)` helper in the template). Never hard-code an English or Russian literal — the
   app ships both EN and RU.

---

## 2. Two ways to run a slice (pick one; both end in a green report + tracker update)

- **By hand (default).** You (the main session) write the test with the Edit/Write tools, then run
  the device step (§4) yourself with the **PowerShell tool**, read the report, and edit the tracker.
- **Orchestrated via `/cmp --device <Sxx>`.** The cmp orchestrator runs the full slice for one
  control: (optional) `cmp-developer-android` adds a seam → `cmp-reviewer-android` checks it →
  `cmp-tester-android` writes one test → `cmp-runner-instrumented-android` runs it on the AVD and
  parses the report → on green it commits, ticks the tracker row, and stops. Use this when you want
  the guardrails enforced for you.

Both paths use the **same device-run step (§4)** and the **same template (§5)**.

---

## 3. Environment preflight (run once per session; copy-paste, PowerShell)

**A connected, booted test device is mandatory for every device run — there is no dry run.** The
verified connection is recorded in the `mymoney-device-connection` memory memo (host AVD
`Pixel_5_API_34` via `adb connect 10.0.2.2:5555`); use whatever that memo says. Run the block below
to confirm it.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"
$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
$device = '10.0.2.2:5555'
& $adb kill-server; & $adb start-server; & $adb connect $device
& $adb -s $device shell getprop ro.boot.qemu.avd_name   # must print: Pixel_5_API_34
& $adb -s $device shell getprop sys.boot_completed       # must print: 1
& $adb -s $device shell input keyevent 82                # dismiss keyguard
```

If `adb devices` does not list the device, the AVD is wrong, or the connection was lost: **STOP and
ask the user where/how the test device is connected now** (address / serial / connection method),
then **update the `mymoney-device-connection` memo** with their answer so you never ask again while it
keeps working, and rerun this preflight. Never "fix" a missing device by retrying tests in a loop, and
never proceed to write/run a test without a confirmed connection.

---

## 4. The device-run step — run ONE test class on the AVD, then read the report

**Do not use plain `./gradlew connectedDebugAndroidTest`.** AGP 8.7.3 UTP cannot handle the remote
serial (`10.0.2.2:5555`) on Windows. Always go through the helper, which proxies a safe serial and
waits 60 s after the run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_connected_test_on_host_avd.ps1 `
  -Tasks ':app:connectedDebugAndroidTest' `
  -TestClass 'com.kshavrin.mymoney.feature.<pkg>.<TestClass>'
```
(From the cmp Bash-only agent path the same call is `powershell.exe -NoProfile -ExecutionPolicy
Bypass -File scripts/run_connected_test_on_host_avd.ps1 -TestClass '…'`.)

**Trust the report, not "BUILD SUCCESSFUL".** After the run, read the JUnit XML:
`app/build/outputs/androidTest-results/connected/debug/TEST-emulator-5554*.xml` — the
`<testsuite … tests="N" failures="M" skipped="K">` attributes are authoritative. **Green = the
class you targeted ran AND `failures="0"` AND `skipped="0"`.** The human-readable report is
`app/build/reports/androidTests/connected/debug/index.html`. If the suite reports `tests="0"` →
your class name/filter is wrong; fix it and rerun.

---

## 5. The canonical Pattern B test template (copy this exactly)

This is the proven style — copied from the already-green
`app/src/androidTest/.../dashboard/DashboardContentUiTest.kt`. Fill the `<…>` blanks.

```kotlin
package com.kshavrin.mymoney.feature.<pkg>

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class <Screen>ContentUiTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `<behaviour described in backticks>`() {
        val capturedEvents = mutableListOf<<Screen>Event>()
        composeTestRule.setContent {
            MyMoneyTheme {
                <Screen>Content(
                    state = <Screen>State(/* set only the fields this test needs */),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.<id>))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(<Screen>Event.<Expected>), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
```

**Matcher cheat-sheet** (all used in the existing green tests):
- text node that is clickable: `composeTestRule.onNode(hasText(targetString(R.string.x)) and hasClickAction())`
- by content-description: `onNodeWithContentDescription(targetString(R.string.x))`
- N identical nodes: `onAllNodesWithContentDescription(...)[i]` + `.assertCountEquals(2)`
- type into a field: `.performTextInput("12.50")`
- assert state: `.assertIsDisplayed()`, `.assertIsEnabled()`, `.assertIsNotEnabled()`
- build state objects from `com.kshavrin.mymoney.core.domain.model.*` (`Category`, `CategoryKind`,
  `Account`, `AccountType`, `Currency`, `Money`, …) — same imports the dashboard test uses.

**Per screen, TDD §12.4 wants three tests** (skip a variant only if the screen has no such state —
log the skip): **happy** (primary gesture → expected event/state), **empty** (no data → empty
semantics visible), **error** (failure in state → banner/inline error visible).

---

## 6. Decision tables (look up instead of deciding)

| Situation | Do this |
|---|---|
| Report shows `failures>0`, cause is a **compile error in the test** | Fix the test code, rerun. |
| Report shows `failures>0`, cause is a **real product defect** | Minimal production fix (Rule 2), rerun, log the bug in the tracker. |
| Report shows `failures>0`, cause is the flaky `HardwareRenderer` teardown watchdog in a *previously green* test | Rerun the **unchanged** class once (documented precedent). Still red → STOP + escalate. |
| Report shows `tests="0"` | Wrong `-TestClass` FQN or wrong module task. Fix and rerun. |
| A control maps to `Unit` / has no event / no state field | Missing-seam policy (Rule 3): SKIP + log. |
| You think a control "should" have a retry/loading/error UI but it doesn't | Do **not** add it. SKIP + log as escalated. |
| The string differs EN vs RU | Use `targetString(R.string.…)`, never a literal. |
| `adb devices` empty | Rerun §3 preflight. Never sleep-loop on tests. |
| The screen's back-arrow emits `SaveClicked` (S22/S24/S26) | Known pre-existing quirk — see the shared note in §8. Cover the unambiguous controls first; treat the back-arrow as an escalation, do not assert it as correct. |

---

## 7. Tracker update mechanics (do this only after a green report)

In `docs/DEVICE_VERIFICATION_PROGRESS.md`:
1. Flip the screen's *Screen Matrix* cell(s) from `Pending` to `Green: <what> N/N`.
2. Update the *Delivery Order* slice status if the whole slice is now done.
3. Append one dated line to the *Session Log*, e.g.:
   `- 2026-05-DD — S21 Categories direct controls green, N/N — CategoriesListContentUiTest covers tabs/Add/Item/Back on Pixel_5_API_34; <commit>.`
Keep the wording in the same terse style as the existing log entries.

---

## 8. Slice cards (do them in this order)

> **Shared note for S22 / S24 / S26 (edit screens):** the TopAppBar back-arrow currently calls
> `onEvent(<Screen>Event.SaveClicked)` instead of a back/cancel event (CategoryEditScreen.kt:103,
> AccountEditScreen.kt:86, CurrencyEditScreen.kt:73). This is the pre-existing quirk recorded in
> memory `mymoney-edit-screen-backarrow-quirk.md`. **Do not** write a test that asserts "back-arrow =
> SaveClicked is correct." Cover the unambiguous controls (fields, save, delete, dialogs) and log the
> back-arrow as an escalation for a separate `/cmp --bugfix` decision.

### Slice 0 — Pattern A infrastructure (BLOCKING — do before S00 and any E2E)

Pattern B (the cards below) needs none of this. Pattern A (E2E through real Hilt + Room) needs all
of it. Build it once, prove the gate, then stop.

1. **`gradle/libs.versions.toml`** — add libraries (versions per `CLAUDE.md`): `hilt-android-testing`
   = `com.google.dagger:hilt-android-testing:2.52`; `androidx-navigation-testing` =
   `androidx.navigation:navigation-testing:2.8.4`; `work-testing` =
   `androidx.work:work-testing:2.10.x`. (`room-testing`, `compose-ui-test-junit4`,
   `compose-ui-test-manifest`, `androidx-test-junit`, `turbine`, `coroutines-test` already exist.)
2. **`app/build.gradle.kts`** — change line ~47 to
   `testInstrumentationRunner = "com.kshavrin.mymoney.HiltTestRunner"`; in `dependencies { }` add
   `androidTestImplementation(libs.hilt.android.testing)`, `kspAndroidTest(libs.hilt.compiler)`,
   `androidTestImplementation(libs.androidx.navigation.testing)`,
   `androidTestImplementation(libs.androidx.work.testing)`,
   `androidTestImplementation(project(":core:database"))`, `(":core:datastore"))`, `(":core:domain"))`.
3. **`app/src/androidTest/java/com/kshavrin/mymoney/HiltTestRunner.kt`**
   ```kotlin
   class HiltTestRunner : AndroidJUnitRunner() {
       override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?) =
           super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
   }
   ```
4. **`TestDatabaseModule`** — `@TestInstallIn(components=[SingletonComponent::class],
   replaces=[DatabaseModule::class])` providing an in-memory `MoneyDatabase`
   (`Room.inMemoryDatabaseBuilder(ctx, MoneyDatabase::class.java).allowMainThreadQueries().build()`)
   plus every DAO accessor the real `:core:database` `DatabaseModule` exposes (open the real module
   and mirror its `@Provides` exactly).
5. **`TestDataStoreModule`** — replace the real DataStore module with one backed by a unique temp file
   per run (so `onboardingCompletedAt`/settings don't leak between tests), or clear the prefs file in
   `@Before`.
6. **GATE:** one `@HiltAndroidTest` using `createAndroidComposeRule<MainActivity>()` that launches the
   app on a fresh in-memory DB and asserts the Splash/Onboarding root renders. Run it via §4. **Must
   be green before any E2E card.** Mark Slice 0 done in the tracker.

### S21 — Categories list · TDD §4.20 (lines 1058–1078)
- **Content:** `CategoriesListContent(state, onEvent)` — `feature/dictionaries/.../categories/CategoriesListScreen.kt:76` (public).
- **State:** `CategoriesListState(expense: List<Category>, income: List<Category>)`.
- **Events:** `AddClicked`, `ItemClicked(id)`, `BackClicked`, `Reordered(kind, newOrder)` — `…/CategoriesListViewModel.kt:73`.
- **Cover:** *happy* — FAB (`R.string.dictionaries_add` content-desc) → `AddClicked`; a category row tap → `ItemClicked(id)`; back-arrow (`dictionaries_back`) → `BackClicked`; tab switch shows expense vs income list. *empty* — `expense=emptyList(), income=emptyList()` renders without crash, FAB still enabled.
- **Seams/skips:** Archive/Unarchive (TDD §4.20 AC4) is **not** in `CategoriesListEvent` → missing seam → SKIP + log. Drag-`Reordered` is hard to drive deterministically in Pattern B → cover via the existing JVM `CategoriesListViewModel` reorder tests; if you attempt it on device, keep it a separate single test or SKIP + log.
- **Test file:** `app/src/androidTest/java/com/kshavrin/mymoney/feature/dictionaries/categories/CategoriesListContentUiTest.kt` · **FQN:** `com.kshavrin.mymoney.feature.dictionaries.categories.CategoriesListContentUiTest`.

### S22 — Category edit · TDD §4.21 (lines 1079–1100)
- **Content:** `CategoryEditContent(state, onEvent)` — `…/categories/CategoryEditScreen.kt:85`.
- **State:** `CategoryEditState(isCreateMode, name, kind, iconKey, colorHex, sortOrder, isDefault, blockedDeleteCount, errorMessage)`.
- **Events:** `NameChanged(v)`, `KindChanged(v)`, `IconChanged(v)`, `ColorChanged(v)`, `SaveClicked`, `DeleteClicked`, `BlockedDeleteDismissed` — `CategoryEditViewModel.kt:137`.
- **Cover:** *happy* — type in name field → `NameChanged`; kind radio → `KindChanged`; icon pick → `IconChanged`; colour pick → `ColorChanged`; Save FAB → `SaveClicked`. *error* — `errorMessage="…"` renders an inline error; `blockedDeleteCount=3` → blocked-delete dialog visible, OK → `BlockedDeleteDismissed`.
- **Seams/skips:** back-arrow quirk (shared note) — escalate. Save-disabled-when-name-blank (AC1) lives in the VM; assert the disabled state only if it's exposed in `CategoryEditState`, else cover in JVM.
- **FQN:** `com.kshavrin.mymoney.feature.dictionaries.categories.CategoryEditContentUiTest`.

### S23 — Accounts list · TDD §4.22 (lines 1101–1121)
- **Content:** `AccountsListContent(state, onEvent)` — `…/accounts/AccountsListScreen.kt:67`.
- **State:** `AccountsListState(rows: List<AccountRow>)`; `AccountRow(account: Account, balance: BigDecimal, currency: Currency?)`.
- **Events:** `AddClicked`, `ItemClicked(id)`, `BackClicked` — `AccountsListViewModel.kt:80`.
- **Cover:** *happy* — FAB → `AddClicked`; a row tap → `ItemClicked(id)`; back → `BackClicked`; a populated `rows` shows name + balance + currency code. *empty* — `rows=emptyList()` renders, FAB enabled.
- **Seams/skips:** **AS-13 (delete-blocked dialog) is NOT on this list** — `AccountsListEvent` has no delete/archive. AS-13 is testable on **S24** (AccountEdit). Default-account chip / set-default is also not in `AccountsListEvent` → SKIP + log here.
- **FQN:** `com.kshavrin.mymoney.feature.dictionaries.accounts.AccountsListContentUiTest`.

### S24 — Account edit · TDD §4.23 (lines 1122–1131) · **AS-13**
- **Content:** `AccountEditContent(state, onEvent)` — `…/accounts/AccountEditScreen.kt:67`.
- **State:** `AccountEditState(isCreateMode, name, currencyId, initialBalanceText, type, colorHex, iconKey, isDefault, sortOrder, availableCurrencies, blockedDeleteCount, errorMessage)`.
- **Events:** `NameChanged`, `CurrencyChanged(id)`, `InitialBalanceChanged`, `TypeChanged`, `ColorChanged`, `IconChanged`, `IsDefaultChanged`, `SaveClicked`, `DeleteClicked`, `BlockedDeleteDismissed` — `AccountEditViewModel.kt:163`.
- **Cover:** *happy* — name → `NameChanged`; currency dropdown (seed `availableCurrencies`) → `CurrencyChanged(id)`; initial-balance field → `InitialBalanceChanged`; type radio → `TypeChanged`; default toggle → `IsDefaultChanged`; Save FAB → `SaveClicked`. *error* — **AS-13:** `blockedDeleteCount=2` → blocked-delete dialog with the count, OK → `BlockedDeleteDismissed`, and assert no `DeleteClicked` re-fires; `errorMessage` (e.g. currency-locked, AC3) renders inline.
- **Seams/skips:** back-arrow quirk (shared note) — escalate.
- **FQN:** `com.kshavrin.mymoney.feature.dictionaries.accounts.AccountEditContentUiTest`.

### S25 — Currencies list · TDD §4.24 (lines 1132–1142)
- **Content:** `CurrenciesListContent(state, onEvent)` — `…/currencies/CurrenciesListScreen.kt:59`.
- **State:** `CurrenciesListState(currencies: List<Currency>)`.
- **Events:** `AddClicked`, `ItemClicked(id)`, `ActiveToggled(id, active)`, `BackClicked` — `CurrenciesListViewModel.kt:54`.
- **Cover:** *happy* — FAB → `AddClicked`; row tap → `ItemClicked(id)`; the active switch → `ActiveToggled(id, active)`; back → `BackClicked`; a seeded `currencies` list shows code/symbol/name. *empty* — `currencies=emptyList()` renders.
- **Seams/skips:** "currency in use cannot be disabled" (AC2) is VM logic; `CurrenciesListState` carries only `List<Currency>`, so the locked-toggle may have no Content seam → verify; if absent, SKIP + log (cover in JVM VM test).
- **FQN:** `com.kshavrin.mymoney.feature.dictionaries.currencies.CurrenciesListContentUiTest`.

### S26 — Currency edit · TDD §4.25 (lines 1143–1152)
- **Content:** `CurrencyEditContent(state, onEvent)` — `…/currencies/CurrencyEditScreen.kt:57`.
- **State:** `CurrencyEditState(isCreateMode, code, symbol, name, decimalDigitsText, isActive, sortOrder, blockedDeleteCount, errorMessage, dependentAccountCount, isCodeLocked)`.
- **Events:** `CodeChanged`, `SymbolChanged`, `NameChanged`, `DecimalDigitsChanged`, `IsActiveChanged`, `SaveClicked`, `DeleteClicked`, `BlockedDeleteDismissed` — `CurrencyEditViewModel.kt:147`.
- **Cover:** *happy* — code field → `CodeChanged`; symbol → `SymbolChanged`; name → `NameChanged`; decimal digits → `DecimalDigitsChanged`; active toggle → `IsActiveChanged`; Save → `SaveClicked`. *locked* — `isCodeLocked=true` (deps exist) → code field is disabled/read-only (TDD §4.25 + PHASE_09 code-lock). *error* — `errorMessage` (invalid `^[A-Z]{3}$`, AC1) renders inline; `blockedDeleteCount` → blocked dialog + `BlockedDeleteDismissed`.
- **Seams/skips:** back-arrow quirk (shared note) — escalate.
- **FQN:** `com.kshavrin.mymoney.feature.dictionaries.currencies.CurrencyEditContentUiTest`.

### S00 — Splash · TDD §4.0
- Pattern B on `SplashContent` for what it renders (logo/progress). The actual **routing** decision
  (Onboarding vs Dashboard) is proven by Pattern A journey **J1** (needs Slice 0), not here. Cover
  the content render; log routing as "covered by J1".

### Pattern A E2E (needs Slice 0 green) — `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>()`
- **J1 (highest value) — onboarding → dashboard → add-expense → balance.** Fresh in-memory DB +
  cleared DataStore ⇒ app starts at Splash → Onboarding. Tap through pager / Get Started → Dashboard.
  Tap `−` FAB → keypad `1 2 + 3 =` → Choose Category → pick a seeded category → Save → pops to
  Dashboard → **assert the balance pill reflects the new expense** (AS-2, AS-4, TDD §4.6 AC6).
- **J2 — cross-currency transfer (AS-6/AS-7).** Two accounts of different currencies, no stored rate
  → Transfer auto-navigates to S27 → save rate → returns to S03 → save → **assert one transfer row
  and the dashboard reflects both legs** (single-row storage, AS-7).
- **J3 — category-picker return (AS-4).** From a form, S09 `+ ADD` → S22 → save → returns past S09
  with the new category pre-selected and the amount preserved.

### Workers — instrumentation (needs `work-testing` from Slice 0 step 1, + `:core:sync` androidTest infra)
- Use `WorkManagerTestInitHelper.initializeTestWorkManager(ctx)` with a `SynchronousExecutor`, build
  each worker via `TestListenableWorkerBuilder<W>` + a `HiltWorkerFactory`, enqueue, assert
  `Result.success()` and the expected Room side-effect. Cover `RecurringWorker` (silent, AS-11),
  `PruneDeletedWorker` (30-day), `BackupRotationWorker`, `SyncWorker` (gated-off ⇒ no-op path).

---

## 9. Per-slice STOP checklist (run through this every time)

- [ ] Picked exactly **one** un-green control from the tracker.
- [ ] Wrote **one** `@Test` using the §5 template; strings via `targetString`.
- [ ] If a seam was needed, added **only** testTag/contentDescription/`public` (Rule 3).
- [ ] Ran it on `Pixel_5_API_34` via the §4 helper.
- [ ] Read the JUnit XML: targeted class ran, `failures="0"`, `skipped="0"`.
- [ ] Updated `DEVICE_VERIFICATION_PROGRESS.md` (matrix cell + session log).
- [ ] Committed (`test: cover <screen> <control>`); production seam, if any, in its own `feat/fix:` commit.
- [ ] **STOP.** Do not start the next control in the same step.

---

## 10. Deferred — out of scope for this runbook (do after device coverage is complete)

These are **not** part of the automated-test work and are intentionally left for later:
`docs/MANUAL_QA_CHECKLIST.md` authoring + execution (TalkBack walk, WCAG-AA contrast, font-scale
1.3×/1.5×, haptic/sound feel, confetti on first positive balance), the R8 minified-release
walkthrough on a clean emulator, macrobenchmark + Baseline Profile generation, and a green hosted CI
run. Track them in `docs/implementation_plan/PROGRESS.md` PHASE_15, not here.
