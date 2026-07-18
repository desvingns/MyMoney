# CSV export/import (deferred TDD §4.17 AC4)
Epic: review-2026-07
Order: 23 of 35
Status: done
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

Closed 2026-07-18 as **already delivered** — CSV export/import shipped incrementally before
this SPEC was consumed, so no pipeline run was performed. Verified live: export writes CSV via
SAF, import routes through the Monefy-CSV parse/commit path, both surfaced in the Backup/Restore
screen alongside .db, EN+RU strings present, on-device round-trip tests green.

- commits (key):
  - `003ec467` feat: S18 Backup & Restore via SAF (screen + CSV buttons/launchers)
  - `d80718a8` fix: auto-detect and import Monefy CSV exports (import path)
  - `f1f210ad` feat: split CSV import into parse and commit with data strategies
  - `29ed27c8` fix: include transfers in CSV export/import round-trip (11-col format)
- files:
  - `core/database/.../repository/BackupRepositoryImpl.kt` (exportTransactionsCsv, parseImport, commitImport, CSV_HEADER)
  - `core/domain/.../domain/csv/*` (MonefyCsvImportParser, ImportPreview, ImportStrategy, TransactionDedup, StagedImport)
  - `feature/settings/.../backup/BackupRestoreViewModel.kt` + `BackupRestoreScreen.kt` (CSV export/import events, launchers, buttons)
  - `feature/settings/src/main/res/values{,-ru}/strings.xml` (backup_export_csv / backup_import_csv + success/error)
  - tests: `core/database/.../BackupCsvTransferTest.kt`, `MonefyCsvImportE2ETest.kt`, `core/domain/.../csv/*Test.kt`
- deviation: CSV export uses an 11-column superset (`+to_account,to_amount`) of TDD §4.17 AC4's
  9 columns, to round-trip transfers — recorded as an intended deviation in
  `docs/DECISIONS/ADR-0009-csv-transfer-columns-supersets-tdd-ac4.md`.
