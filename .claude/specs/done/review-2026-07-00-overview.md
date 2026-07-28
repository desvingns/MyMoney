# Epic: review-2026-07 — improvements from the 2026-07-06 project review
Epic: review-2026-07
Order: 00 (index)
Status: done (completed 2026-07-28)
Date: 2026-07-06

## Goal

Work through the approved 2026-07-06 full-project review (56 findings across
architecture, tests, CI, a11y, l10n, performance, security, docs). This epic files
every actionable item EXCEPT review section D (UX, items 22–29 — excluded by user)
and item 56 (free-tier memory rule — already done, commit `ec62caca`).

## Cross-cutting constraints (apply to every SPEC in this epic)

- **Free tier only** (user directive, see `.ai/memory/MEMORY.md`): Sentry Developer
  errors-only, GitHub Actions free minutes (heavy emulator jobs → nightly /
  `workflow_dispatch` if the repo is private), Firebase Spark, personal
  Dropbox/GDrive quotas. Never wire anything that needs a paid plan.
- Locked decisions stay locked: AS-12, AS-14, Clean Architecture verbosity,
  fakes-only tests, TDD-pinned stack versions (a `[TDD-revision]` SPEC only
  produces a proposal/ADR, never a silent bump).
- Never delete files — move to `archive/` and report for manual deletion.
- Visual/instrumented SPECs obey the `Pixel_5_API_34` device gate.

## Ordered SPEC list

Wave 1 — release blockers (P1):
- 01 sentry-dsn-free-tier — errors-only Sentry on the free Developer plan (OQ-1)
- 02 slice5-release-qa — manual QA + minified walk + macrobenchmark (Slice 5)
- 03 ci-signed-aab — signed release AAB from CI secrets (OQ-9)
- 04 import-focus-cold-start-fix — fix the known red androidTest
- 05 missing-translation-gate — MissingTranslation=error + EN/RU parity + RU plurals
- 06 locale-formatting-audit — kill locale-bypassing formatting paths
- 07 cold-start-budget — cold start ~5.5s vs TDD budget + macrobenchmark cadence
- 08 progress-md-restructure — 263KB PROGRESS.md → small header + monthly archives

Wave 2 — high value (P2):
- 09 ci-connected-modules — run :core:sync/:core:network/:feature:lockscreen androidTests in CI
- 10 kover-thresholds — real coverage floors + HTML report artifact
- 11 fakes-dedup-contract-tests — repository contract suite + shared fakes
- 12 roborazzi-screenshots — previews + screenshot regression suite
- 13 a11y-automated-checks — ATF checks, touch targets, fontScale tests
- 14 content-description-audit — actionable-icon a11y sweep
- 15 chart-semantics — TalkBack semantics for donut/trend Canvas charts
- 16 a11y-manual-pass — contrast check + manual TalkBack journeys (after 13–15)
- 17 backup-policy-decision — ADR: unencrypted DB in cloud backup + migration comment
- 18 security-crypto-migration-plan — ADR: leave deprecated security-crypto [TDD-revision]
- 19 type-safe-navigation — @Serializable routes instead of string concatenation
- 20 retro-adrs — backfill docs/DECISIONS/ADR-*.md (currently zero)
- 34 flag-secure-recents — FLAG_SECURE on lock surfaces + hide-in-Recents option

Wave 3 — nice to have (P2/P3):
- 21 oq-service-decisions — ADR: OQ-10 (Sentry vs Crashlytics) + OQ-5 (Remote Config)
- 22 cloud-creds-setup — Dropbox app + GDrive OAuth (free quotas) + one real round-trip
- 23 csv-export-import — deferred TDD §4.17 AC4
- 24 factory-reset — deferred TDD §4.17 AC5
- 25 two-device-merge-e2e — LWW merge integration scenario
- 26 property-based-tests — invariants for pure merge/trend/backup logic
- 27 gradle-config-cache — configuration cache + heap bump
- 28 convention-plugins — build-logic/ + unified jvmToolchain
- 29 renovate-radar — dependency radar, no auto-bumps [TDD-revision]
- 30 versioning-changelog — tag-based versionCode/Name + CHANGELOG.md
- 31 chart-frame-rate — drawWithCache/path caching for donut & trend
- 32 compose-stability-audit — compiler metrics + immutable collections where flagged
- 33 dashboard-vm-decomposition — sub-coordinators; only with the next dashboard epic
- 35 repo-hygiene — untracked strays, root log litter, stray "@ " commit prefix

## Dependencies

- 09 builds on the CI job layout touched by 03 (run 03 first).
- 16 (manual a11y pass) is meaningful after 13/14/15 land.
- 12 reuses the @Preview foundation it itself creates first (single SPEC).
- 21 should precede 22 only if the crash-reporting choice affects Firebase wiring; otherwise independent.

## Source traceability

Each SPEC's "Gap / context" cites its review item number (project review 2026-07-06,
approved plan `calm-moseying-dusk`). Review sections: A=release, B=tests, C=CI/build,
E=a11y, F=l10n, G=perf, H=security, I=architecture, J=docs/process.

## Epic completion review (2026-07-28, clean)

- 34 of 35 ordered SPECs are in `done/` with commits + files filled (shipped slices include
  sub-SPECs 02a–d, 13a–c, 16-aurora; final slice 35 = e7b9d1b5 + 30ed4fe1). Every cross-cutting
  constraint above (free tier only, locked AS decisions, never-delete archive policy,
  Pixel_5_API_34 device gate) was honoured per-SPEC.
- SPEC 33 (dashboard-vm-decomposition) is NOT a gap: its own CONSTRAINTS defer it to the next
  substantial dashboard epic, and it is preserved (never deleted) at
  `archive/review-2026-07-33-dashboard-vm-decomposition.deferred-no-next-dashboard-feature.md`
  for that trigger.
- Goal met: all actionable findings of the 2026-07-06 review are shipped except review section D
  (excluded by user) and item 56 (already done pre-epic, commit ec62caca).
