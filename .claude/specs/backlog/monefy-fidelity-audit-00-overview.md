# Monefy fidelity screenshot audit - epic overview
Epic: monefy-fidelity-audit
Order: 00 (index)
Status: draft
Depends-on: -
Date: 2026-06-02

## Goal
Audit the Monefy reference screenshots `01.jpg` through `14.jpg` at
`D:\Pet\TDD_creater\MyMoney\input\screenshots` against the current app and package only new,
non-duplicative fidelity gaps for later implementation. This is a grooming epic: no app source,
tests, `PROGRESS.md`, or archived implementation-plan files were changed to create it.

## Audit inputs
- Reference evidence: `D:\Pet\TDD_creater\MyMoney\input\screenshots\01.jpg` through `14.jpg`;
  temporary contact sheet: `build\fidelity-audit\reference-contact-sheet.jpg`.
- Current evidence: source inspection of the affected Compose screens and shipped SPECs under
  `.claude\specs\done\`.
- Device evidence: `adb devices -l` reported no attached `Pixel_5_API_34`, so no current app
  screenshots were captured in this pass. Any later device capture must first run
  `scripts\preflight_device_health.ps1` and save evidence under `build\fidelity-audit\`.
- Shipped fidelity epics treated as complete: `redesign-monefy-fidelity` and
  `monefy-behavioral-fidelity`.

## Ordered SPECs
| # | File | Slice | Reference screenshots | Layers | Depends-on |
|---|------|-------|-----------------------|--------|------------|
| 01 | `monefy-fidelity-audit-01-search-overlay.md` | Search overlay chrome | `08.jpg` | presentation | - |
| 02 | `monefy-fidelity-audit-02-left-drawer-all-accounts.md` | Selectable all-accounts dashboard mode | `02.jpg`, `14.jpg` | domain, data, presentation | - |
| 03 | `monefy-fidelity-audit-03-transfer-selector-stack.md` | Transfer account selector stack | `03.jpg` | presentation | - |
| 04 | `monefy-fidelity-audit-04-records-header-sort.md` | Records header and sort affordance | `12.jpg`, `13.jpg` | presentation | - |

Recommended order: 01 -> 02 -> 03 -> 04. SPEC 02 is the only behavior/data slice; the others are
presentation fidelity refinements.

## Non-gaps
- AS-12 is preserved: "Pick a date" opens a two-date range picker, not Monefy's single-day picker.
- AS-14 is preserved: donut percentage labels appear for slices `>=3%`, not Monefy's older `>=5%`.
- The app brand remains `MyMoney`; screenshot text reading `Monefy` is reference evidence, not a
  requirement to rename the product.
- The right drawer keeps the shipped app navigation set, including About, because
  `redesign-monefy-fidelity-05-drawers` explicitly retained current entries and events.
- Dashboard donut, balance plate placement, empty-state ring/icons, transaction keypad-first flow,
  category-grid embedding, drawer width, period selector, and grouped records behavior are already
  covered by shipped specs unless a later visual verification finds a new residual gap.

## Later implementation rule
Every implementation SPEC in this epic must declare any internal type, route, use-case, or data
contract change in `CHANGED_HINT` before implementation. No public app API/type/interface change is
implied by this planning run itself.

