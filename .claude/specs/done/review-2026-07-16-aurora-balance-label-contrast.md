# Aurora balance label contrast
Epic: review-2026-07
Order: follow-up to 16
Status: done
Depends-on: review-2026-07-16-a11y-manual-pass
Date: 2026-07-15

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Raise the Aurora `FREE BALANCE` label contrast to WCAG AA 4.5:1 against the actual dark panel fill while preserving the approved Aurora visual hierarchy and the DashboardAuroraInnerPanelFill alpha 0.045 token. Verify the result with a fresh Pixel 5 API 34 screenshot and a measured ratio.
LAYERS: [presentation]
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt; core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt
TEST_TYPES: [compose-ui]
CONSTRAINTS: do not change balance semantics, chart semantics, or AS-12/AS-14 behavior; no hardcoded user-facing strings; preserve the Aurora layout; prove the ratio from an actual device screenshot rather than source-color inference alone
=== END SPEC ===

## Evidence

The manual pass captured `build/visual-check/a11y-2026-07-15-manual-pass/05-dashboard-first-launch.png` at 1080x2340. Dominant core text pixels for `FREE BALANCE` were `#98B4B3` over adjacent panel pixels `#1A5956`; WCAG ratio = **3.65:1**, below the normal-text AA threshold 4.5:1. Other sampled candidates passed. Full report: `docs/a11y/2026-07-15-manual-accessibility-pass.md`.

## Runner evidence disposition

The originating manual-a11y run completed Gradle with exit code 0 and lint/detekt green. This follow-up is a docs/evidence record; it has no authored test files and therefore does not require a test summary or JaCoCo output. The repository's coverage provider is Kover, not JaCoCo; the parser messages `no parseable output` and `jacoco report missing` are runner-contract mismatches and do not change this observed defect or its acceptance criteria.

## Post-fix verification (2026-07-16)

- Device: local `emulator-5554` (Pixel 5 / API 34 / boot complete), default font scale.
- Evidence: `build/visual-check/aurora-balance-label-contrast-2026-07-16/dashboard.png`; its UI dump records `FREE BALANCE` at `[418,384][662,419]`.
- Screenshot pixels: label foreground `#E8EAF0`, adjacent Aurora inner-panel fill `#1A5956`; measured WCAG contrast ratio **6.70:1** (AA threshold: 4.5:1).
- Static/JVM runner: 12 suites passed with 0 failures/errors; detekt passed; lint reported 0 errors (136 warnings).
- The scoped `AuroraBalanceCardUiTest` class ran as 26 passed / 3 failed. The failures are pre-existing, unrelated assertions (two `126dp` vs `132dp` chart-height expectations and one `26sp` vs `36sp` balance-type expectation); they were retained unchanged.

## Acceptance

```gherkin
Feature: Aurora balance label meets WCAG AA

  Scenario: Free balance label is readable on the Aurora panel
    When the dashboard is rendered on Pixel 5 API 34 at the default font scale
    Then the measured screenshot ratio for FREE BALANCE against its panel background is at least 4.5:1
    And the balance value, pills, chart summary semantics, and layout remain unchanged
```

## Implementation links

- Commit: `32dc598b` (`fix: raise Aurora balance label contrast`)
- Files: `app/build.gradle.kts`; `feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt`
- Verification: Pixel 5 screenshot evidence above; measured contrast **6.70:1**.
