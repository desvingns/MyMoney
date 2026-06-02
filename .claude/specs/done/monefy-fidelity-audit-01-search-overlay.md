# Search overlay chrome (S08)
Epic: monefy-fidelity-audit
Order: 01 of 04
Status: done
Depends-on: -
Date: 2026-06-02

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Rework S08 search to match the Monefy reference overlay in `08.jpg`: a green search toolbar
that opens over the dashboard context, requests focus for typing, and shows result/empty states
without presenting as a separate Material search-card screen. Affected surfaces: S08 search and the
S01 dashboard search entrypoint.
LAYERS: presentation
CHANGED_HINT: feature/transactionslist/.../search/SearchScreen.kt (`SearchBar` at line 144 and
`SearchBarDefaults.InputField` at line 146 become a Monefy-style green top overlay/search app bar);
feature/dashboard/.../DashboardScreen.kt if the search entrypoint needs a transition or route flag;
feature/transactionslist/.../search/SearchViewModel.kt only if focus/initial-query state must be
exposed; update/add Compose UI tests for focused search, query editing, clear/back behavior, empty
state, and result taps; screenshot evidence must use `08.jpg`.
TEST_TYPES: unit compose-ui screenshot-manual
CONSTRAINTS:
  - Keep current search data behavior, result filtering, and `OpenDetail` navigation semantics.
  - The dashboard must remain visually contextual behind/under the search overlay until results take
    over; do not introduce a landing/interstitial search page.
  - Back clears focus or exits search according to existing app navigation rules; query clear remains
    explicit and accessible.
  - Do not hardcode user-facing strings or colors; keep EN/RU parity and Material theme tokens.
  - Manual screenshot verification is required because `08.jpg` includes IME/focus behavior that may
    not be fully asserted in JVM tests.
=== END SPEC ===

## Evidence
- Reference screenshot IDs: `08.jpg`.
- Affected surfaces: S08 search overlay, S01 dashboard entrypoint.
- Current evidence source: `feature\transactionslist\src\main\java\com\kshavrin\mymoney\feature\transactionslist\search\SearchScreen.kt:144`
  uses Material3 `SearchBar`; line 146 uses `SearchBarDefaults.InputField`.
- Prior shipped SPEC check: neither `redesign-monefy-fidelity` nor `monefy-behavioral-fidelity`
  contains an S08/search SPEC, so this does not duplicate completed work.

## Implementation links
- commit: 9c5bceb292cb4e3a2a04d34b0883070c006d0784; 07937502e06380c1538a64f44fefccac9af333d5; fdc0767
- files:
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentUiTest.kt
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentTest.kt
