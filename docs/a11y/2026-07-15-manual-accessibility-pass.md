# Manual accessibility pass: contrast and TalkBack journeys

Date: 2026-07-15
SPEC: `review-2026-07-16-a11y-manual-pass`
Scope: Android presentation surfaces after review SPECs 13–15
Result: contrast pass recorded; TalkBack pass partially verifiable in this ADB environment

## Device and method

- Device: local `emulator-5554`, AVD `Pixel_5`, SDK 34, `sys.boot_completed=1`.
- Package: `com.kshavrin.mymoney`, installed `versionName=1.0.11`.
- TalkBack: `com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`, enabled; `accessibility_enabled=1`.
- Screenshots and UI hierarchy dumps were captured from the device at 1080x2340. Evidence is under `build/visual-check/a11y-2026-07-15-manual-pass/`; earlier same-session journey captures remain under `build/visual-check/a11y-2026-07-15/`.
- Contrast uses WCAG relative luminance and `(L lighter + 0.05) / (L darker + 0.05)`. The reported colors are dominant core pixels from the screenshot region, paired with the adjacent sampled background; the 4.5:1 AA threshold is applied to normal text.

## Contrast measurements

Source screenshot: `build/visual-check/a11y-2026-07-15-manual-pass/05-dashboard-first-launch.png`.

| Candidate | Screenshot sample | Ratio | AA 4.5:1 | Evidence / disposition |
|---|---|---:|:---:|---|
| Aurora `FREE BALANCE` label over the dark translucent panel | foreground `#98B4B3`, background `#1A5956`; UI bounds `[418,384][662,419]` | **3.65:1** | **FAIL** | Confirmed defect; follow-up SPEC `review-2026-07-16-aurora-balance-label-contrast.md`. |
| Aurora balance value | `#FFFFFF` over `#122B33` | 14.80:1 | PASS | Same dashboard screenshot. |
| Income pill | `#79F8AF` over `#163F3C` | 8.76:1 | PASS | Same dashboard screenshot. |
| Expense pill | `#FFA3AE` over `#2E323C` | 6.78:1 | PASS | Same dashboard screenshot. |
| Empty-period copy | `#7C8290` over `#0A0E1C` | 4.99:1 | PASS | `No expenses this period` in the same screenshot. |
| Onboarding primary text | `#E8EAF0` over `#0A0E1C` | 15.98:1 | PASS | `build/visual-check/a11y-2026-07-15-manual-pass/02-onboarding-after-splash.png`. |

The failing label is the only measured AA failure in the sampled candidates. The panel token remains the documented black fill at alpha 0.045; this pass does not change production code.

## TalkBack walkthrough checklist

Legend: `PASS (device evidence)` means the screen/control was observed or its exposed UI hierarchy was captured. `UNVERIFIED` means a spoken announcement, TalkBack focus order, or TalkBack activation was not claimed.

### Journey 1 — onboarding to first launch

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Splash settles on onboarding page 1 | UNVERIFIED | UNVERIFIED | UNVERIFIED | `02-onboarding-after-splash.png`, `.xml` |
| Page 1 title/body and `Next` | UNVERIFIED | UNVERIFIED | UNVERIFIED | `02-onboarding-after-splash.xml` exposes title/body and clickable `Next` bounds `[44,2164][1036,2296]`. |
| Pages 2–4 | UNVERIFIED | UNVERIFIED | UNVERIFIED | `03-onboarding-next-attempt.png`, `04-onboarding-page4.png` and XML dumps; direct injected input changed pages. |
| `Get Started` to dashboard | UNVERIFIED | UNVERIFIED | UNVERIFIED | `05-dashboard-first-launch.png`, `.xml`; route was observed through direct injected input only. |

### Journey 2 — add expense via calculator

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Dashboard expense affordance | UNVERIFIED | UNVERIFIED | UNVERIFIED | `08-add-expense-enter-attempt.png` shows TalkBack focus still on the menu after an ADB tap/Enter attempt; no calculator route was claimed. |
| Calculator amount entry | UNVERIFIED | UNVERIFIED | UNVERIFIED | Existing device capture `build/visual-check/a11y-2026-07-15/14-calculator-after-1.png` and `14-calculator-after-1.xml` show the calculator surface and operator keys. |
| Choose category and save | UNVERIFIED | UNVERIFIED | UNVERIFIED | Existing device captures `add-expense-category.png` and `dashboard-after-expense.png`; TalkBack speech/focus was not observable. |

### Journey 3 — dashboard reading

| Step | Focus order | Announcement | Actionable label / activation | Evidence |
|---|---|---|---|---|
| Top-bar controls | UNVERIFIED | UNVERIFIED | Semantics exposed | `05-dashboard-first-launch.xml` contains `Open menu`, `Previous period`, `Pick a date`, `Next period`, and `More options`. |
| Balance/trend reading | UNVERIFIED | UNVERIFIED | Semantics exposed | The same XML contains `Balance trend. Metric: Cumulative. Period: selected period. Start: No data, end: No data. Direction: unchanged.` |
| Donut reading | N/A | N/A | N/A | Product decision recorded in `PROGRESS.md` on 2026-07-15 keeps the dashboard's `BalanceTrendChart` design and does not wire a Monefy donut. No donut defect is filed. |

## TalkBack limitation and evidence

TalkBack was running and produced real TTS activity, but this environment did not provide a reliable way to drive its accessibility-focus gestures and observe utterance text:

1. A one-finger ADB swipe changed the onboarding pager as an ordinary app gesture (`talkback-swipe-left.png`), rather than yielding a reproducible TalkBack focus step.
2. ADB `input tap` / double-tap / Enter attempts left the dashboard menu as the visible focused target (`talkback-tab-01.png`, `08-add-expense-enter-attempt.png`) and did not establish a repeatable TalkBack activation path.
3. `talkback-tts-log.txt` records TalkBack and Google TTS synthesis events, including `Synthesis request`, but does not expose the spoken utterance text. The log also records one `SpeechControllerImpl: TTS is not ready` event during TalkBack startup.

Therefore this report does not claim that focus order, announcements, or activation passed. The UI XML and screenshots are evidence of exposed labels and visual state only. A follow-up manual run with physical/GUI TalkBack gestures or an audio-observation path is required for a complete spoken-UX verdict.

## Files and scope

No production Kotlin/Compose files and no tests were written for this SPEC. The only confirmed product defect is the Aurora label contrast failure; it is queued as a separate backlog SPEC.
