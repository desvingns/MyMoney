# Dashboard Final redesign — epic overview
Epic: dashboard-final-redesign
Order: 00 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## Goal
Make the S01 dashboard look **1:1 with the "MyMoney Dashboard Final" mockup**
(`docs/design/dashboard-final/MyMoney Dashboard Final (standalone).html`) while keeping all
current behaviour. Dark-neon palette already matches; the deltas are layout/structure:

1. **Single-row top bar** — `☰ · ‹month + mint underline› · ⋮` (was two rows). Transfer (⇄)
   moves to a FAB, search (🔍) moves into the ⋮ overflow.
2. **Three neon-ring FABs** — `− expense (coral) · ⇄ transfer (cyan) · + income (mint)` (was two).
3. **"Aurora" hero card** — one centered card merging balance label + big balance + income/expense
   pills + the trend chart (was: a standalone trend card + two separate income/expense panels).
4. **Trend chart** — stays configurable (tap → settings sheet + right-menu entry preserved);
   default style switched to the neon **wave**, wave style refined to match the mockup.
5. **Separate ("All accounts → separately") mode** — per-currency cards restyled to the same
   neon-aurora look (still one card per currency, no conversion).

## Ordered SPECs
- **01 — top-bar-and-fabs** (presentation, independent): single-row top bar + 3-FAB row;
  transfer toolbar→FAB, search→overflow.
- **02 — aurora-hero-card** (presentation + data): centered hero card (label + balance + pills +
  embedded wave chart); default chart style → wave + wave-style refinement; remove the two
  standalone income/expense panels. Headline slice.
- **03 — separate-mode-neon-cards** (presentation, **depends on 02**): restyle per-currency
  balance cards to the neon-aurora look (container + pills + wave mini-chart), no conversion.

## Cross-cutting notes
- Authoritative visual reference: the mockup React source extracted to scratchpad
  (`04_fourth.jsx` = RealTopBar/RealCategoryTile/RealFabs/ScreenDashboardFinal,
  `03_third.jsx` = SecAurora, `02_second.jsx` = ChartWave/neon palette, `01_first.jsx` = data/icons).
- Reused existing tokens (already match the mockup): `NeonBackground 0xFF0A0E1C`,
  `NeonSurface 0xFF111A2E`, `NeonMint 0xFF5BE3B0`, `NeonCoral 0xFFFF8A80`, `NeonCyan 0xFF46B6E6`;
  `dashboardFabSize 94dp`, `dashboardFabOutlineWidth 3.6dp`, `dashboardTileHeight 76dp`,
  `dashboardTileIconChipSize 44dp`, `dashboardTileProgressBarHeight 4dp`.
- New tokens introduced in 02 (reused by 03): `dashboardAuroraAccent 0xFF37E1C0`,
  income pill `0xFF3DF59B`, expense pill `0xFFFF8A9B`.
- Decisions (grilled 2026-06-22): keep configurable chart (default→wave); search→⋮ overflow;
  separate mode also restyled.
- Visual epic → each SPEC runs the Android device visual pre-flight; `emulator-5554` connected.
- Category tiles + FAB ring style already match the mockup — no dedicated tile SPEC.

## Status
- [x] 01-top-bar-and-fabs
- [x] 02-aurora-hero-card
- [x] 03-separate-mode-neon-cards

## Completed
2026-06-22 — all 3 SPECs shipped to main (01 20520782, 02 94b4809d, 03 3ed4eb58). Dashboard now matches the "MyMoney Dashboard Final" mockup; all behaviour preserved.
