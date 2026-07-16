# Manual accessibility pass: contrast and TalkBack journeys

Date: 2026-07-16
SPEC: `review-2026-07-16-a11y-manual-pass`
Scope: Android presentation surfaces after review SPECs 13–15
Result: one WCAG AA contrast failure confirmed; TalkBack spoken focus/order/activation remain unverified in this ADB-only environment

## Device and method

- Device: local `emulator-5554`, AVD `Pixel_5`, SDK 34, `sys.boot_completed=1`.
- Package: `com.kshavrin.mymoney`, installed `versionName=1.0.11`.
- Capture metadata: `build/visual-check/a11y-2026-07-16-manual-pass/device-metadata.txt`.
- TalkBack state at capture: `accessibility_enabled=1`; enabled service `com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`. See `build/visual-check/a11y-2026-07-16-manual-pass/talkback-state.txt`.
- Screenshot resolution: 1080x2340. Fresh screenshot evidence is under `build/visual-check/a11y-2026-07-16-manual-pass/`; the prior 2026-07-15 evidence remains under `build/visual-check/a11y-2026-07-15-manual-pass/` and was not deleted.
- The app journeys used direct ADB taps for deterministic state changes. ADB taps are app-interaction evidence, not proof of TalkBack touch-exploration behavior.
- Contrast was measured from PNG pixels with `System.Drawing.Bitmap`. WCAG relative luminance was computed from sRGB RGB values using `(L_lighter + 0.05) / (L_darker + 0.05)`. The 4.5:1 threshold is for normal text.
- The source token is `core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt:402`: `DashboardAuroraInnerPanelFill = Color.Black.copy(alpha = 0.045f)`. PNG pixels are already composited and all sampled pixels had `A=FF`; the 0.045 alpha is therefore recorded as a source-token assumption, not applied a second time during measurement.

## Contrast measurements

Source screenshots: `build/visual-check/a11y-2026-07-16-manual-pass/05-dashboard-first-launch.png` for the dashboard candidates and `build/visual-check/a11y-2026-07-16-manual-pass/01-onboarding-page1.png` for onboarding text. Foreground values are dominant solid text pixels in the XML bounds; panel/background values are dominant or local adjacent composited screenshot samples. The screenshot bounds are device pixels.

| Candidate | Screenshot bounds / sample | Foreground | Background | Ratio | AA 4.5:1 | Evidence / disposition |
|---|---|---:|---:|---:|:---:|---|
| Aurora `FREE BALANCE` label | `[418,384][662,419]`; dominant counts: `#98B4B3` 352 px, `#1A5956` 525 px | `#98B4B3` | `#1A5956` | **3.65:1** | **FAIL** | Confirmed defect; use existing follow-up SPEC `review-2026-07-16-aurora-balance-label-contrast.md`. |
| Aurora balance value | `[474,425][606,541]`; local adjacent background sample at `[474,430]` | `#FFFFFF` | `#123439` | 13.32:1 | PASS | `05-dashboard-first-launch.png`. |
| Income pill | `[249,574][508,647]`; dominant counts: `#79F8AF` 999 px, `#163F3C` 2391 px | `#79F8AF` | `#163F3C` | 8.76:1 | PASS | `05-dashboard-first-launch.png`. |
| Expense pill | `[536,574][832,647]`; dominant counts: `#FFA3AE` 1245 px, `#2E323C` 2479 px | `#FFA3AE` | `#2E323C` | 6.78:1 | PASS | `05-dashboard-first-launch.png`. |
| Empty-period copy | `[338,1148][743,1193]`; dominant counts: `#7C8290` 2332 px, `#0A0E1C` 13539 px | `#7C8290` | `#0A0E1C` | 4.99:1 | PASS | `05-dashboard-first-launch.png`. |
| Onboarding headline | `[301,1318][779,1389]`; dominant counts: `#E8EAF0` 4666 px, `#0A0E1C` 26670 px | `#E8EAF0` | `#0A0E1C` | 15.98:1 | PASS | `01-onboarding-page1.png`. |
| Onboarding body | `[88,1422][992,1540]`; dominant counts: `#E8EAF0` 9396 px, `#0A0E1C` 89318 px | `#E8EAF0` | `#0A0E1C` | 15.98:1 | PASS | `01-onboarding-page1.png`. |

The Aurora label is the only sampled normal-text AA failure. This SPEC does not alter production code or the approved `0.045f` panel token.

## TalkBack walkthrough checklist

Legend: `Observed (ADB app path)` means the app state or action was reached with direct ADB input. `Semantics exposed` means the UI hierarchy contained the stated text/content description. `UNVERIFIED (TalkBack)` means that spoken announcement, TalkBack accessibility-focus order, or TalkBack double-tap activation was not established and is not claimed as a pass.

### Journey 1 — onboarding to first launch

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Page 1 renders | UNVERIFIED (TalkBack). UIAutomator tree order observed: `Skip`, headline/body, page indicator, `Next`; this is not TalkBack focus order. | UNVERIFIED (utterance text unavailable) | `Skip` and `Next` are visible text on clickable parents; `Onboarding page indicator` is exposed as content description. | `01-onboarding-page1.png`, `01-onboarding-page1.xml` |
| Next through pages 2–3 | UNVERIFIED (TalkBack) | UNVERIFIED | Direct ADB taps advanced to page 2 (`See where money flows`), page 3 (`Stay within budget`), with `Next` exposed on each page. | `02-onboarding-page2.png/.xml`, `03-onboarding-page3.png/.xml` |
| Page 4 / Get started | UNVERIFIED (TalkBack) | UNVERIFIED | `Get started` is visible text on a clickable parent. Direct ADB tap reached dashboard. | `04-onboarding-page4.png`, `04-onboarding-page4.xml`, `05-dashboard-first-launch.png`, `05-dashboard-first-launch.xml` |
| Keyboard side probe | Not a TalkBack focus result. `KEYCODE_TAB` set `focused=true` on `Skip`; `KEYCODE_ENTER` reached dashboard. | No spoken text observed | The keyboard path activated `Skip`; this does not prove TalkBack touch exploration or TalkBack action handling. | `14-onboarding-after-tab.xml`, `15-onboarding-after-enter.xml` |

### Journey 2 — add expense via calculator

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Dashboard expense affordance | UNVERIFIED (TalkBack) | UNVERIFIED | `Expense` content description is exposed; direct ADB tap opened `New expense`. | `05-dashboard-first-launch.xml`, `06-add-expense-calculator-initial.png`, `06-add-expense-calculator-initial.xml` |
| Calculator amount entry | UNVERIFIED (TalkBack) | UNVERIFIED | Visible digit keys are exposed as text on clickable parent nodes. `Backspace`, `Plus`, `Minus`, `Multiply`, `Divide`, `Equals`, `Back`, and `Swap` content descriptions are exposed. Direct taps produced visible `12.5`. | `07-calculator-12-5.png`, `07-calculator-12-5.xml` |
| Choose category | UNVERIFIED (TalkBack) | UNVERIFIED | `Open category: Food` and the other category labels are exposed. Direct ADB tap on Food selected the category and returned to dashboard. | `08-category-picker.png`, `08-category-picker.xml` |
| Saved expense appears | UNVERIFIED (TalkBack) | UNVERIFIED | Dashboard exposes `Open Food, 13 $` and `Spending share: 100%`; trend exposes a full summary including `Start: 0, end: -12.5, Direction: decreasing.` | `09-dashboard-after-expense.png`, `09-dashboard-after-expense.xml` |
| Keyboard side probe | Not a TalkBack focus result. `KEYCODE_TAB` focused the disabled `CHOOSE CATEGORY` node; `KEYCODE_ENTER` returned to dashboard. | No spoken text observed | This was not treated as a successful TalkBack calculator activation. | `17-calculator-after-tab.xml`, `18-calculator-after-enter.xml` |

### Journey 3 — dashboard reading (trend and donut scope)

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Dashboard top-bar controls | UNVERIFIED (TalkBack). A keyboard probe focused `Open menu`, but that is not accessibility focus. | UNVERIFIED | Hierarchy exposes `Open menu`, `Previous period`, `Pick a date`, `Next period`, and `More options`. `KEYCODE_ENTER` opened the menu after the keyboard probe. | `10-talkback-swipe-dashboard.xml`, `11-talkback-tab-dashboard.xml`, `12-talkback-enter-dashboard.xml` |
| Balance and trend reading | UNVERIFIED (TalkBack) | UNVERIFIED (utterance text unavailable) | Hierarchy exposes `FREE BALANCE`, the value, and `Balance trend. Metric: Cumulative. Period: selected period. Start: No data, end: No data. Direction: unchanged.` | `05-dashboard-first-launch.png`, `05-dashboard-first-launch.xml` |
| Dashboard after expense | UNVERIFIED (TalkBack) | UNVERIFIED | The post-expense hierarchy exposes the same chart semantics with `Start: 0, end: -12.5, Direction: decreasing.` plus the Food row/share labels. | `09-dashboard-after-expense.png`, `09-dashboard-after-expense.xml` |
| Donut reading | N/A — no donut is rendered in the current dashboard design. | N/A | No donut content description or donut action was present to test. The 2026-07-15 chart-semantics decision keeps the dashboard `BalanceTrendChart` design and does not wire a Monefy donut; no donut defect is filed. | `05-dashboard-first-launch.png`, `09-dashboard-after-expense.png` |

## TalkBack limitation and evidence

- TalkBack was enabled throughout the fresh capture. `build/visual-check/a11y-2026-07-16-manual-pass/talkback-log.txt` contains Google TTS `Synthesis request` entries, but the captured log does not expose the utterance text for the app nodes.
- The one-finger ADB swipe in `10-talkback-swipe-dashboard.png` changed the visible period to August; it behaved as an ordinary app swipe and did not provide a reproducible TalkBack focus step.
- `KEYCODE_TAB`/`KEYCODE_ENTER` provided a repeatable hardware-keyboard path (`Open menu` became `focused=true`, then the menu opened), but this is not evidence of TalkBack touch-exploration focus or spoken output.
- Therefore the report does not claim that focus order, announcements, or TalkBack activation passed. A physical/GUI TalkBack run with observable audio or a harness exposing accessibility-focus events remains manual follow-up.

## Existing coverage and defect routing

- Existing relevant tests/reports were inspected, including `OnboardingContentUiTest.kt`, `AddExpenseScreenUiTest.kt`, `AuroraBalanceCardUiTest.kt`, `MonefyKeypadA11yUiTest.kt`, `BalanceTrendChartUiTest.kt`, the prior manual report, and the existing content-description/a11y SPECs.
- No tests were written and no Gradle/instrumented test result is claimed for this docs/evidence SPEC.
- The confirmed Aurora contrast defect is already represented by `.claude/specs/backlog/review-2026-07-16-aurora-balance-label-contrast.md`. No duplicate follow-up SPEC was created.
- No production Kotlin/Compose/Gradle/application behavior was changed.

## Fresh screenshot paths

- Onboarding: `build/visual-check/a11y-2026-07-16-manual-pass/01-onboarding-page1.png`, `02-onboarding-page2.png`, `03-onboarding-page3.png`, `04-onboarding-page4.png`.
- First launch dashboard: `build/visual-check/a11y-2026-07-16-manual-pass/05-dashboard-first-launch.png`.
- Expense flow: `06-add-expense-calculator-initial.png`, `07-calculator-12-5.png`, `08-category-picker.png`, `09-dashboard-after-expense.png` in the same directory.
- TalkBack/keyboard probes: `10-talkback-swipe-dashboard.png`, `11-talkback-tab-dashboard.png`, `12-talkback-enter-dashboard.png`, `13-onboarding-before-tab.png`, `14-onboarding-after-tab.png`, `15-onboarding-after-enter.png`, `16-calculator-before-tab.png`, `17-calculator-after-tab.png`, `18-calculator-after-enter.png` in the same directory. Matching `.xml` UI hierarchy dumps are beside each PNG.
