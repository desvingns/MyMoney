# contentDescription sweep across all actionable UI
Epic: review-2026-07
Order: 14 of 35
Status: active
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Audit every Icon/IconButton/clickable image across the 8 feature modules and :core:designsystem (only 79 contentDescription instances exist for ~30 screens): give every ACTIONABLE element a meaningful localized contentDescription (EN + RU string resources), mark every decorative element with explicit contentDescription = null, and record the per-screen result table in the SPEC report.
LAYERS: [presentation]
CHANGED_HINT: feature/*/src/main (Icon( / IconButton( / painterResource call sites), per-module strings.xml + values-ru/strings.xml
TEST_TYPES: [compose-ui]
CONSTRAINTS: descriptions come from string resources, never literals; RU translations required (pairs with the SPEC 05 lint gate); do not add descriptions to decorative art (that worsens TalkBack); keep the donut/trend charts for SPEC 15
=== END SPEC ===

## Gap / context
79 contentDescriptions across a 27-screen app means most icon buttons are silent in
TalkBack. Source: review item 30 (P2/M).

## Per-screen result

| Screen / shared surface | Result |
| --- | --- |
| Dashboard: top bar, period controls, FAB, and drawer | Pass — the seven period rows are mutually exclusive; Interval expands inline and emits `Interval` without closing the drawer, while Pick a date dismisses the drawer and opens the two-date picker that emits `CustomRange`; actionable controls have localized descriptions. |
| Dashboard: balance, trend, category tiles, and inline records | Pass for non-chart actions — trend-chart accessibility remains deferred to SPEC 15. |
| Add expense and add income forms | Pass — shared date, category, amount, and keypad controls are covered. |
| Transfer form | Pass — source and destination account selectors are localized. |
| Transactions list | Pass — transaction rows expose localized record summaries. |
| Transactions search | Pass — search result rows expose localized record summaries. |
| Transaction detail | Pass — existing action controls retain localized descriptions. |
| Settings root | Pass — navigable settings rows have descriptions. |
| Theme settings | Pass — selectable theme rows have localized descriptions. |
| Language settings | Pass — selectable language rows have localized descriptions. |
| Biometric lock setup | Pass — system-settings action and idle-timeout choices are described. |
| Cloud sync | Pass — existing sync actions retain localized descriptions. |
| Backup and restore | Pass — existing navigation and action descriptions retained. |
| About, help, and licences | Pass — web/help links have localized descriptions. |
| Import wizard category configuration | Pass — icon and color choices expose localized selection semantics. |
| Accounts list and account editor | Pass — account rows and picker choices are described. |
| Categories list and category editor | Pass — category rows and picker choices are described. |
| Currencies list and currency editor | Pass — currency rows and picker choices are described. |
| Goals list and goal editor | Pass — goal rows and icon choices are described. |
| Onboarding | Pass — hero artwork is explicitly decorative; actionable controls retain descriptions. |
| Splash | Pass — branded artwork is explicitly decorative; loading state is not announced as an action. |
| Shared amount input and keypad | Pass — clear/backspace actions use localized resources. |
| Shared date header | Pass — date action is described once at the row level. |
| Shared balance bar and category grid | Pass — actionable categories and summary rows are localized. |
| Shared color picker and donut chart | Pass for the color picker — donut-chart accessibility remains deferred to SPEC 15. |

## Implementation links
- commit: scoped audit implementation
- files: scoped source, resource, and audit-report files across the implementation and repair commits
