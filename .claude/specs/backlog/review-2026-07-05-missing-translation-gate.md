# Enforce EN/RU string parity: MissingTranslation as error + RU plurals
Epic: review-2026-07
Order: 05 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Make Russian localization completeness a build gate: enable Android lint MissingTranslation (and ExtraTranslation) as errors across all modules, run a one-shot EN↔RU key-parity sweep filling every missing RU string, and verify all plurals resources carry correct Russian quantity branches (one/few/many/other — e.g. transactions_in_period).
LAYERS: [presentation]
CHANGED_HINT: per-module res/values/strings.xml + res/values-ru/strings.xml, res/values/plurals.xml (+ru), root or convention lint config in build.gradle.kts
TEST_TYPES: unit
CONSTRAINTS: RU was deferred in PHASE_12/15 — expect real gaps, translate them (RU translations in the TDD string tables win when present; cite TDD lines); UI strings default language stays EN (config uiLang: en); no hardcoded user-facing strings may be introduced
=== END SPEC ===

## Gap / context
RU parity was deferred and nothing guards it — English fallbacks can ship silently.
Source: project review 2026-07-06, items 36+37 (P1/S + P2/S).

## Implementation links
- commit: (pending)
- files: (pending)
