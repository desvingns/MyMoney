# Audit and fix locale-bypassing number/date formatting
Epic: review-2026-07
Order: 06 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Sweep the codebase for user-visible values formatted without locale awareness — starting from the known BigDecimal.toString() in CurrencyRateViewModel:60 — and route every user-facing amount/rate/date through the shared MoneyFormatter / localized date formatters; internal parsing/serialization paths (Room, JSON, CSV) explicitly stay locale-independent.
LAYERS: [presentation] [domain]
CHANGED_HINT: feature/transaction/**/CurrencyRateViewModel.kt:60, grep for .toString() on BigDecimal / String.format without Locale / SimpleDateFormat across feature modules; :core:common MoneyFormatter
TEST_TYPES: unit
CONSTRAINTS: distinguish display formatting (locale-aware) from data formatting (invariant — CSV/JSON/Room must NOT become locale-dependent); add a unit test per fixed call-site with a comma-decimal locale (ru-RU)
=== END SPEC ===

## Gap / context
At least one user-visible value bypasses MoneyFormatter; ru-RU users see wrong
decimal separators. Source: project review 2026-07-06, item 38 (P2/S).

## Implementation links
- commit: (pending)
- files: (pending)
