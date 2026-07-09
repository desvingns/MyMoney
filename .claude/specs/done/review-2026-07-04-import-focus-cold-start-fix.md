# Fix the known red ImportFocusColdStartRegressionTest
Epic: review-2026-07
Order: 04 of 35
Status: done
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
- commit: 733b233b, fe5258c6, 42218b96, 0fa8f392
- files:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/ImportFocusColdStartRegressionTest.kt
