# Dashboard drawers: left (S02) + right (S04)
Epic: redesign-monefy-fidelity
Order: 05 of 05
Status: backlog
Depends-on: 01 (categoryIcon registry for drawer icons)
Date: 2026-05-30

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Restyle the dashboard drawers to S02/S04 — right drawer as a centered outlined-icon-over-label list (Категории/Счета/Валюты/Настройки); left drawer rows as outlined rounded-rect items with a leading icon + selected state. Visual only.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/.../components/RightDrawerContent.kt; feature/dashboard/.../components/LeftDrawerContent.kt; screenshots 02.jpg/04.jpg; TDD §03_style L125
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Right drawer (S04): vertical list, each entry = outlined mint-tinted icon ON TOP + label BELOW, centered, generous vertical spacing (icons e.g. Category / AccountBalanceWallet / AttachMoney-in-circle / Settings). Keep current entries + events (Settings/Categories/Accounts/Currencies/About).
  - Left drawer (S02): KEEP the existing accounts content + behaviour (AccountChanged / manage-accounts) — only restyle rows to outlined rounded-rect with a leading icon + primaryContainer selected state, plus the currency header row if available. DO NOT swap accounts->period-type — that is a TDD/nav decision (see epic overview divergence #1), out of scope here; flag it in the run report.
  - No VM/nav changes; no hardcoded strings (EN+RU)/colours; English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
RightDrawerContent is text-only NavigationDrawerItem rows (no icons; reference is a centered icon-over-label list). LeftDrawerContent shows accounts (reference shows currency + period-type — a behavioural divergence, restyle only).

## Implementation links
- commit: (pending)
- files:  (pending)
