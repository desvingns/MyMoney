# ADR-0006: MainActivity migrated to AppCompatActivity with Theme.AppCompat parent

- Status: Accepted
- Date: 2026-05-24

## Context

Retrospective record (2026-07-17) from PROGRESS.md decision-log line 72,
log/2026-05.md line 66, PHASE_12 Slice 2 (S19 Language), commit 815d4b7.

TDD §4.18 AC1 (line 1033) requires per-app language selection via
`AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(...))`. The
framework `LocaleManager` API that replaces it is only available from API 33. The app's
`minSdk` is 31 (TDD §8.1), so on API 31 and 32 `LocaleManager` is unavailable and the
framework path silently does nothing.

`AppCompatDelegate.setApplicationLocales` back-ports per-app locale to API 31+, but it
requires the Activity to extend `AppCompatActivity`. `MainActivity` previously extended
`ComponentActivity`. Additionally, `Theme.MyMoney` was parented to a non-AppCompat base
theme; without the AppCompat theme parent, `AppCompatDelegate` locale switching does not
propagate correctly.

A companion `AppLocaleController` wrapper was introduced to hold the
`AppCompatDelegate.setApplicationLocales` call so the ViewModel remains JVM-testable
without an Android instrumentation dependency.

## Decision

Migrate `MainActivity` from `ComponentActivity` to `AppCompatActivity`. Reparent
`Theme.MyMoney` to `Theme.AppCompat.DayNight.NoActionBar`.

These two changes are facets of the same delivery (commit 815d4b7) and are recorded as a
single ADR. `AppCompatActivity` is a subclass of `ComponentActivity`, so Compose
`setContent`, Hilt `@AndroidEntryPoint`, and the splash-screen API all continue to work
without modification.

## Rejected alternatives

- Stay on `ComponentActivity` and use only the framework `LocaleManager`: rejected because
  `LocaleManager` is API 33+, which breaks per-app language on the app's minSdk 31/32
  devices.
- Keep `Theme.MyMoney` with a non-AppCompat parent while switching Activity base class:
  rejected because AppCompat locale switching requires the AppCompat theme delegate, which
  is not active without a `Theme.AppCompat.*` ancestor.

## Consequences

- Per-app language selection works correctly on API 31 and 32 via `AppCompatDelegate`.
- `AppLocaleController` isolates the AppCompat call, keeping `LanguageViewModel` testable
  on the JVM.
- The AppCompat dependency is now a hard runtime requirement; it was already present
  transitively but is now explicit.
