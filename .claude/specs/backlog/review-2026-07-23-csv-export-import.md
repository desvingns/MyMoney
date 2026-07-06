# CSV export/import (deferred TDD §4.17 AC4)
Epic: review-2026-07
Order: 23 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Implement the CSV export/import deferred from PHASE_12: export all transactions to CSV via SAF (column set and format per TDD §4.17 AC4 — re-read the anchor lines before implementing), and wire CSV import through the existing Monefy-CSV import path where formats align; surface both in the Backup/Restore settings screen alongside the .db export.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: :feature:settings BackupRestoreScreen/ViewModel, :core:domain CSV use cases (import side exists — MonefyCsvImportE2ETest), :core:database export queries
TEST_TYPES: unit [dao] [compose-ui]
CONSTRAINTS: CSV format is TDD-governed — cite §4.17 lines in the implementation, never invent columns; CSV is locale-INVARIANT (dot decimals, ISO dates) regardless of UI locale (ties to SPEC 06); SAF only, no raw file paths; strings EN+RU
=== END SPEC ===

## Gap / context
Deferred TDD scope + user data portability; import path already half-exists.
Source: review item 6 (P2/M), first half.

## Implementation links
- commit: (pending)
- files: (pending)
