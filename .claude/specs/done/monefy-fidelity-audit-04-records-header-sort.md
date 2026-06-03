# Records header and sort affordance (S12/S13)
Epic: monefy-fidelity-audit
Order: 04 of 04
Status: done
Depends-on: -
Date: 2026-06-02

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Restyle the records screen header to match `12.jpg` and `13.jpg`: the balance/sort strip
should read as the primary Monefy records header, with the sort affordance integrated beside the
balance strip instead of living as a generic Material TopAppBar action. Affected surface: S12/S13
records list.
LAYERS: presentation
CHANGED_HINT: feature/transactionslist/.../list/TransactionsListScreen.kt (`TopAppBar` at line 119,
sort `IconButton`/`SwapVert` at lines 131-135, `BalanceBar` call at line 155, and `BalanceBar`
implementation at line 205 are restyled/repositioned); update/add Compose UI tests for header
structure, sort button semantics, category expanded/collapsed state, and empty state; screenshot
evidence must use `12.jpg` and `13.jpg`.
TEST_TYPES: unit compose-ui screenshot-manual
CONSTRAINTS:
  - Preserve shipped records behavior from `monefy-behavioral-fidelity-08b-records-screen`: category
    grouping, expand/collapse, total sort toggle, pre-expanded category navigation, swipe delete/undo,
    and item tap to detail.
  - Do not alter transactions data queries unless SPEC 02 changes the account filter contract and
    this screen must consume the new all-accounts route.
  - Keep header controls accessible and localized; do not hardcode strings/colors.
  - The visual balance strip should stay stable with long currency symbols and large amounts.
  - Manual screenshot verification is required because the gap is primarily placement, density, and
    visual hierarchy.
=== END SPEC ===

## Evidence
- Reference screenshot IDs: `12.jpg`, `13.jpg`.
- Affected surfaces: S12 collapsed records groups, S13 expanded category group.
- Current evidence source: `feature\transactionslist\src\main\java\com\kshavrin\mymoney\feature\transactionslist\list\TransactionsListScreen.kt:119`
  renders a standard `TopAppBar`; lines 131-135 put `SwapVert` in the app bar; line 155 renders
  `BalanceBar` as a separate content element.
- Prior shipped SPEC check: `monefy-behavioral-fidelity-08a-records-data` and `08b-records-screen`
  shipped the data/grouping/expand/sort behavior. This SPEC is a residual header/affordance fidelity
  pass and must not duplicate the completed records rewrite.

## Implementation links
- commit: cce0837, 8b569f4, 9b989ba, 354cc87
- files:
  - `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt`
  - `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Shape.kt`
  - `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt`
  - `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt`
  - `feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListScreen.kt`
  - `app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/list/TransactionsListContentUiTest.kt`

## Verification
- `mp-reviewer-android` re-review: pass, no violations.
- `.\gradlew.bat --no-daemon :feature:transactionslist:compileDebugKotlin --console=plain`: BUILD SUCCESSFUL.
- `.\gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin --console=plain`: BUILD SUCCESSFUL.
- `mp-runner-instrumented-android`: `TransactionsListContentUiTest` passed 12/12 on `Pixel_5_API_34`
  (`emulator-5554`, SDK 34), 0 failures / 0 errors / 0 skipped.
- Manual screenshot capture against `12.jpg` / `13.jpg` was attempted under `build/fidelity-audit/`,
  but the app launch stayed on onboarding/splash and did not reach S12/S13. Those captures are not
  valid records-header evidence; fresh S12/S13 screenshots remain a pre-push manual checklist item.
