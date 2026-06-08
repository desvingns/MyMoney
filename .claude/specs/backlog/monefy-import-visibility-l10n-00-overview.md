# Monefy CSV import — make imported data visible + localized built-in entities
Epic: monefy-import-visibility-l10n
Status: draft
Date: 2026-06-08

## Goal
Fix two compounding problems reported after a real Monefy import
(`Monefy.Data.07.06.2026.csv` — account `Наличные`, RU categories `Зарплата`/…,
currency RUB, dates from 29/09/2018 onward):

1. **Imported transactions are invisible on the dashboard.** Root causes:
   - The RU CSV account/categories do not match the English seed (`Cash`, `Food`, …),
     so a *new* `Наличные` account is created; the dashboard stays pinned to the seeded
     `Cash` account. `DashboardViewModel.observeAccountsAndCurrencies()` reads the account
     list once via `.first()`, so newly-imported accounts never enter the balance
     computation — even "All accounts" mode filters a stale list
     (`feature/dashboard/.../DashboardViewModel.kt:86-106,160-186`).
   - The dashboard defaults to the current month (`Period.Month(YearMonth.now())`,
     `DashboardState.kt:12`); every imported row is from 2018+, so the screen is empty even
     when the account matches. Newly-added transactions show only because they are *today* on
     the *default* account.

2. **Built-in categories/account are hard-coded English**, so a Russian Monefy export
   duplicates them instead of merging ("полное совпадение с CSV").

## Decisions (grilled 2026-06-08)
- **Localization = seed-in-locale** (not resolve-at-render): at first launch seed
  category/account names in the device/app locale (RU → Russian matching Monefy defaults,
  else English). Static — does not re-localize on later language change. Only affects fresh
  installs (seed runs once when the currency table is empty).
- **Period after import = jump to the period that contains the data** (month of the latest
  imported transaction), and refresh the account list + selection so the imported data is
  shown.
- **Shape = backlog epic, critical visibility fix first.**

## Ordered SPECs
| Order | File | Depends-on | Summary |
|------|------|-----------|---------|
| 01 | `monefy-import-visibility-l10n-01-dashboard-surfaces-import.md` | — | **Critfix.** Dashboard reactively refreshes accounts/currencies (imported accounts appear; "All accounts" stops using a stale list) + after import jumps the period to the latest imported transaction and selects an account/currency that contains it. Solves the user's immediate "no history" pain on its own. |
| 02 | `monefy-import-visibility-l10n-02-localize-seed.md` | — | Seed built-in categories + default account in the locale language (RU↔EN), RU names aligned to Monefy's default labels. |
| 03 | `monefy-import-visibility-l10n-03-import-match-localized.md` | 02 | Monefy import matches existing (localized) categories/accounts by normalized name → no duplicate `Наличные`/`Зарплата`/… on a RU install. |

## Cross-cutting notes
- SPEC 01 ships first and alone restores visibility regardless of localization (even the
  duplicate `Наличные` account becomes visible and the period jumps to 2018–2026 data).
- SPECs 02→03 deliver requirement #1 ("полное совпадение с CSV", no duplicates) for fresh
  RU installs. Existing installs already seeded in English are unaffected by 02 (seed is
  one-shot); SPEC 03's matching still merges anything that *does* match.
- Money stays `BigDecimal` in domain / `Double` in Room; occurredAt `Instant` ↔ epoch-millis
  (existing TypeConverters). No schema-version bump expected for 01 unless a new DataStore
  `AppSettings` field is added (DataStore, not Room — no Room migration).
