# Left drawer: account dropdown + period selector; remove dashboard period strip (S01/S02)
Epic: monefy-behavioral-fidelity
Order: 06 of 09
Status: draft
Depends-on: —
Date: 2026-06-01

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Rework the left drawer to the Monefy layout: a tappable currency/account header that expands a dropdown of accounts including "Все счета" (14.jpg), and below it the period selector as outlined rounded-rect buttons — День / Неделя / Месяц / Год / Все / "Выбор даты" — with the current period highlighted (02.jpg). Remove the PeriodStrip chips from the dashboard and show the current period as a static label at the top of the dashboard instead.
LAYERS: presentation
CHANGED_HINT: feature/dashboard/.../components/LeftDrawerContent.kt (header row becomes clickable -> expandable account dropdown listing accounts + "Все счета"; AccountChanged on select; period buttons -> DashboardEvent.PeriodChanged(period); "Выбор даты" -> range picker -> Period.CustomRange, AS-12); feature/dashboard/.../components/PeriodStrip.kt (remove from the dashboard; its period logic moves into the drawer); feature/dashboard/.../DashboardScreen.kt (remove the PeriodStrip usage L181-188; add a static current-period label in its place); feature/dashboard/.../components/PeriodLabel.kt (NEW — Period -> localized label: Day "EEEE, d MMMM", Week range, Month "LLLL yyyy", Year "yyyy", All R.string.period_all, CustomRange "d MMM – d MMM"); screenshots 02.jpg / 14.jpg; picks up done/redesign-monefy-fidelity-05-drawers.md divergence #1 + overview divergence #2
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Left drawer top = a currency/account header (icon + currency name + code + dropdown chevron), TAPPABLE; tapping expands a dropdown listing all accounts (icon + name + code) + an "Все счета" entry, exactly like 14.jpg; selecting an account emits AccountChanged and collapses.
  - "Все счета" aggregate: render the entry, but if a cross-account aggregate-balance mode does NOT exist in domain, flag its selection as follow-up (do NOT silently sum multi-currency); keep this SPEC's scope to per-account switching.
  - Below the header: period buttons (outlined rounded-rect, selected = primaryContainer fill) for День / Неделя / Месяц / Год / Все + "Выбор даты" (calendar icon -> range picker -> Period.CustomRange, AS-12), emitting DashboardEvent.PeriodChanged; keep the existing period-change sound/haptic. (Monefy's "Интервал" has no domain equivalent — omit it or alias to the range picker; note the choice in the run report.)
  - Remove PeriodStrip from the dashboard; show the current period via the static PeriodLabel (SPEC-07 makes it swipeable). Drop the left-drawer "Manage accounts" row (account management stays via the right-drawer "Счета").
  - Reuse the existing drawer row styling (outlined rounded-rect, primaryContainer selected state). No new nav routes; no hardcoded strings (EN+RU) / colours; English ids; no comments unless WHY.
=== END SPEC ===

## Gap / context
User notes #1 (content) and #2. LeftDrawerContent currently shows a persistent accounts list + a
"Manage accounts" row (SPEC-05 restyle only), and the period selector lives on the dashboard as
FilterChips (PeriodStrip). The reference puts the period selector in the drawer (02.jpg) and the
accounts behind a tappable currency dropdown (14.jpg). This is the prior epic's deferred divergences
#1 and #2 (`done/redesign-monefy-fidelity-00-overview.md` lines 40-44).

## Implementation links
(pending — fill commit + changed files after `/cmp --feature --next`)
