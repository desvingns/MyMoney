# Redesign to Monefy v1.0 visual fidelity — epic overview
Epic: redesign-monefy-fidelity
Order: 00 (index)
Status: backlog
Date: 2026-05-30

## Goal
Bring the whole app's design to the Monefy v1.0 reference screenshots
(`D:\Pet\TDD_creater\MyMoney\input\screenshots` 01–10), especially the expense/income **category
buttons**. The colour theme is already APK-accurate (`core/ui/.../theme/Color.kt`); the real gaps
are the per-category **icons** (one generic glyph is used everywhere) and component chrome.

User decisions (2026-05-30):
- Icons = **hybrid**: Material Icons Extended *Outlined* where faithful; custom stroke-only vector
  drawables where Material has no good match (hygiene/toothbrush, pets/cat, health/thermometer,
  clothing/t-shirt).
- Scope = **broad pass** across the app, delivered as the ordered SPECs below.

## Ordered SPECs
| # | File | Slice | Depends-on | Status |
|---|------|-------|-----------|--------|
| 01 | `-01-category-icons.md` | Icon registry + category-picker grid (S09/S10) | — | ✅ **done** (→ `done/`) |
| 02 | `-02-donut-ring.md` | Donut perimeter icons (S01/S05) | 01 | backlog |
| 03 | `-03-form-chrome.md` | Add/transfer form chrome (S03/S06/S07) | — (icons help) | backlog |
| 04 | `-04-dashboard-chrome.md` | Top bar + balance pill + ± FABs (S01) | — | backlog |
| 05 | `-05-drawers.md` | Left/right drawers (S02/S04) | 01 (icons) | backlog |
| 06 | `-06-embed-grid.md` | Embed grid into add-form, drop picker route (S06/S07) | 01 | 🚧 **active** (→ `active/`) |

Recommended order: 01 → 02 → 03 → 04 → 05. SPEC 02 hard-depends on SPEC 01's registry; 03/04/05
are independent of each other. **SPEC 06** was promoted from divergence #3 below once the embed work
went in flight; it depends on 01 and supersedes 01's separate-route choice (reusing 01's registry).

> Status as of 2026-05-31: **01 shipped & pushed** (`078a269`/`25bb66e`/`73ab68d`); **06 in progress**
> (uncommitted working tree); **02–05 not started** (their target components still match each SPEC's gap).

## Cross-cutting divergences (behavioural, NOT pure design — flagged, do NOT change silently)
1. **Left drawer content** — reference shows currency + period-type; MyMoney shows accounts. SPEC 05
   restyles only; a content swap is a separate TDD/nav decision.
2. **Period strip** — reference is a swipeable prev/current/next date strip with period TYPE in the
   left drawer; MyMoney uses Today/Week/Month/Year/All FilterChips. A full match is a behavioural
   change, out of this design pass.
3. **Category picker route** — reference embeds the grid into the add-transaction screen (replaces
   the keypad); MyMoney shipped SPEC 01 with a separate route. **Now in progress as SPEC 06** — the
   embed/nav-refactor is no longer "optional, later"; it removes the separate route and moves the
   keypad into a modal sheet (uncommitted as of 2026-05-31).

## Reference
Screenshots 01–10 (Monefy v1.0); TDD `TDD\MyMoney\pipeline\03_style.md` (esp. L124–177).
