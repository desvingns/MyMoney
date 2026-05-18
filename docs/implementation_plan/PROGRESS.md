# Implementation progress

> Always read this file first in a new session. It points to the active phase.
> Update it at the end of every session before stopping.

---

## Current state

- **Active phase:** `PHASE_01` — Multi-module Gradle scaffolding (not yet started)
- **Last session:** 2026-05-18 — implementation plan created (phase files + tracker bootstrapped, no app code yet)
- **Next action:** Open `phases/PHASE_01_scaffolding.md`, read its TDD anchors, work through the task list.
- **Blockers:** none

---

## Phase completion

| #  | Phase                                                 | Status        | Session date  | Outcome / link to "Notes for next session" |
|----|-------------------------------------------------------|---------------|---------------|--------------------------------------------|
| 00 | Overview / orientation                                | done          | 2026-05-18    | Plan + checklist files created. Start with PHASE_01. |
| 01 | Multi-module Gradle scaffolding                       | **active**    | —             | — |
| 02 | DI + App shell + Sentry skeleton                      | not started   | —             | — |
| 03 | Design system (`:core:ui` + designsystem skeleton)    | not started   | —             | — |
| 04 | Database layer (`:core:database`)                     | not started   | —             | — |
| 05 | DataStore + secure storage (`:core:datastore`)        | not started   | —             | — |
| 06 | Domain layer + seeding (`:core:domain`, `:core:common`) | not started | —             | — |
| 07 | Splash + onboarding (S00, S11) + nav root             | not started   | —             | — |
| 08 | Dashboard + donut chart (S01/S05 + S02/S04)            | not started  | —             | — |
| 09 | Dictionaries CRUD (S21–S26)                            | not started  | —             | — |
| 10 | Transaction forms (S03, S06, S07, S09, S27)            | not started  | —             | — |
| 11 | List + search + detail (S08, S12, S13)                 | not started  | —             | — |
| 12 | Settings hierarchy (S14, S15, S18, S19, S20)            | not started | —             | — |
| 13 | Cloud sync + Sentry + Remote Config (S17)              | not started  | —             | — |
| 14 | Biometric + WorkManager (S16, recurring, budget)       | not started  | —             | — |
| 15 | Polish + gamification + l10n + tests + release         | not started  | —             | — |

Legend: `not started` → `active` → `in progress` → `done` (use `blocked` if a phase is paused on external work; cite OQ-id).

---

## Decisions log

Append a one-line entry whenever a non-obvious decision is made during a session. Format: `YYYY-MM-DD — <decision>. Cross-ref: <phase or TDD §>.`

- 2026-05-18 — Implementation plan: 15 phases, English, located under `docs/implementation_plan/`. Cross-ref: this file's existence.
- 2026-05-18 — Application namespace finalised as `com.kshavrin.mymoney` (replaces template `com.example.mymoney`). Cross-ref: README §4 conventions, TDD OQ-3.
- 2026-05-18 — All TDD §14.1 resolved decisions (AS-1 … AS-15) are pre-locked. See TDD lines 2727–2750. Do not re-litigate during implementation; cite the AS-id instead.

---

## Deferred work — DevOps prerequisites (TDD §14.2)

These need real external accounts. They block PHASE_13 (cloud sync) but do NOT block PHASE_01 … PHASE_12 or PHASE_14 / PHASE_15. Track here when picked up.

- [ ] **OQ-1** — Sentry: project created for the re-impl; fresh DSN collected. **DO NOT reuse Monefy's DSN.**
- [ ] **OQ-2** — Dropbox: app registered (Scoped access — App folder); app key + debug/release SHA-1 fingerprints collected.
- [ ] **OQ-3** — Google Cloud: project + Drive API + OAuth consent screen (`drive.appdata` scope), SHA-1 fingerprints, package name `com.kshavrin.mymoney`.
- [ ] **OQ-5** — Firebase Remote Config: initial `min_supported_version_code` value chosen (suggest `1`).
- [ ] **OQ-9** — CI: secret-injection path for `google-services.json` decided.
- [ ] **OQ-10** — Crash reporting: Sentry-only (recommended) vs Sentry + Crashlytics — picked.

OQ-4 (Privacy Policy) was resolved as AS-15 → bundled HTML. OQ-6 (live FX provider) deferred to v1.1, not blocking. OQ-7 (auto-sync interval) and OQ-8 (backup rotation N) resolved in §14.1.

---

## Open questions (non-DevOps)

Append any clarification needed mid-implementation. Format: `YYYY-MM-DD — <question>. Affects: <phase>. Status: <open|answered (date)>.`

- (empty at start)

---

## Session log

Append one entry per session below this line. Keep it short — date, phase, one sentence.

```
2026-05-18 — PHASE_00 — Created docs/implementation_plan/ scaffolding (README, PROGRESS, 00_overview, 15 phase files). No Android code touched.
```
