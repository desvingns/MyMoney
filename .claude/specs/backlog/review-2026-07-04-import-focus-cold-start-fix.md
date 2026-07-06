# Fix the known red ImportFocusColdStartRegressionTest
Epic: review-2026-07
Order: 04 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Diagnose and fix the pre-existing red androidTest ImportFocusColdStartRegressionTest.importedRowsSurviveColdStartAndShowOnDashboard (confirmed failing at baseline fc0710a4, noted 2026-06-25 in PROGRESS.md as "needs a separate fix"; bbaf3bfb later touched only its runBlocking) — first re-run to confirm current status, then root-cause: is imported data actually lost on cold start (real defect) or is the test's cold-start simulation flawed (test defect)?
LAYERS: [data] [presentation]
CHANGED_HINT: app/src/androidTest/**/ImportFocusColdStartRegressionTest.kt, CSV import path in :core:database / :feature:settings import wizard
TEST_TYPES: unit [dao] [compose-ui]
CONSTRAINTS: RUNTIME_BUG: true — reproduce on device before fixing and re-verify on device after; never weaken, @Ignore, or delete the test to go green; a real data-loss defect outranks the test fix
=== END SPEC ===

## Gap / context
A permanently red test in the suite normalizes "red is fine" and hides regressions.
Source: project review 2026-07-06, item 7 (P1/S).

## Implementation links
- commit: (pending)
- files: (pending)
