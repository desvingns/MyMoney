# Enforce EN/RU string parity: MissingTranslation as error + RU plurals
Epic: review-2026-07
Order: 05 of 35
Status: done
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
- commit: 8ff5afeb (feat: gate build on Russian translation completeness) + 657bec12 (test: EN/RU parity regression guard)
- files: lint.xml, build.gradle.kts, app/build.gradle.kts, app/src/test/java/com/kshavrin/mymoney/L10nParityTest.kt
- result: EN↔RU parity was already complete (0 missing keys across 10 modules); deliverable is the enforced lint gate (MissingTranslation + ExtraTranslation = error on every android application/library module) + a pure-JVM parity/plurals regression test. Verified negatively (deleting one RU string fails the build) and positively (:feature:onboarding:lintDebug green).
- side-fixes (pre-existing blockers cleared in their own commits so the whole-project gate could go green, unrelated to l10n): 12e645f0 (chore: clear pre-existing detekt debt in DecisionRouter), 278edd18 (test: wire currencyRateRepository into SplashViewModelTest seeder).
