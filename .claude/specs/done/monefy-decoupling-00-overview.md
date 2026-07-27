# Epic: monefy-decoupling — drop the last Monefy naming from a standalone app
Epic: monefy-decoupling
Order: 00 (index)
Status: done
Date: 2026-07-26

## Goal

MyMoney was bootstrapped from Monefy v1.0 via `/mp-spec` clone mode. That was a **foundation
step, not a standing dependency**, and the product is now standalone. The clone source material
(screenshots, decompiled APK, `/mp-spec` analysis bundle) was untracked and moved to
`archive/monefy-clone-source/` on 2026-07-26 for manual deletion.

What remains are Monefy *names* inside our own code. This epic removes them — carefully, because
the two remaining groups have very different blast radii.

## Explicitly out of scope

- **`MonefyCsvImportParser`** and its ~65 references stay. Importing a user's data **out of**
  Monefy is a real migration feature, not clone coupling. Renaming the *class* is allowed as part
  of SPEC 01 only if the CSV format detection and its tests are untouched; the capability itself
  is never removed.
- **AS-12 / AS-14** stay locked. They were written up as "intentional Monefy deviations" but are
  now simply how MyMoney behaves. Do not restore Monefy behaviour.
- The TDD (`TDD/MyMoney/MyMoney_TDD.md`) stays in git as the authoritative spec.

## Ordered SPEC list

- 01 ui-component-rename — cosmetic, no runtime behaviour change (do first)
- 02 database-rename-migration — **data-loss risk**, needs a real migration (do second, or never)

01 and 02 are independent; 02 is deliberately ordered last so a purely cosmetic change is never
bundled with a persistence change in one push.

## Closure

Both ordered slices are complete. SPEC 01 closed the UI-component naming residue; SPEC 02
closed the database filename migration with device evidence and backup/restore coverage.
