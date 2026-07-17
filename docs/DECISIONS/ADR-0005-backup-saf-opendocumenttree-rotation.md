# ADR-0005: Backup export uses SAF OpenDocumentTree, not CreateDocument

- Status: Accepted
- Date: 2026-05-24

## Context

Retrospective record (2026-07-17) from PROGRESS.md decision-log lines 70–71,
log/2026-05.md line 68, PHASE_12 Notes, commit 003ec46.

TDD §4.17 specifies OQ-8: after every export the local backup directory keeps the three
most recent backups, deleting the oldest excess file automatically (lines 1019–1022).
`BackupRotationWorker` is invoked synchronously by `BackupRepository.exportDb()` and
operates on the SAF tree (TDD line 1991). OQ-8 is resolved in §14.1 at line 2749 as
fixed N = 3, not user-settable.

The phase file specified `CreateDocument` as the SAF intent for export. `CreateDocument`
returns a single-file URI; it cannot enumerate sibling files in the same directory. There
is no way to implement keep-newest-3 rotation using only a `CreateDocument` URI.

A separate architectural question arose: the `BackupRepository` interface contains
`ContentResolver` usage and direct access to the Room database file; placing its interface
in `:feature:settings` (as the phase file stated) would violate the layer rule prohibiting
feature modules from closing or reopening `MoneyDatabase` or touching data internals.

## Decision

Export uses `ACTION_OPEN_DOCUMENT_TREE` to obtain a persistent tree URI. This lets
`DocumentFile.fromTreeUri(...).listFiles()` enumerate siblings for the keep-newest-3
rotation in a pure, unit-testable `backupsToDelete()` function.

`BackupRepository` interface is placed in `:core:domain` (URIs are passed as `String` to
keep the module pure-JVM). The implementation lives in `:core:database`, which already
owns `MoneyDatabase` and `ContentResolver`. The `:feature:settings` ViewModel injects the
domain interface only. This follows the PHASE_06 precedent of placing `RepositoryImpl`
classes in `:core:database`.

## Rejected alternatives

- `CreateDocument` (phase file's stated SAF intent): rejected because the returned
  single-file URI cannot enumerate directory siblings, making keep-newest-3 rotation
  impossible without a separate tree grant.
- `BackupRepository` interface in `:feature:settings`: rejected because a feature module
  must not hold or close the Room database or use `ContentResolver` directly.

## Consequences

- The user grants access to a folder once via the system directory picker; subsequent
  exports and rotations operate within that tree URI without additional prompts.
- The keep-newest-3 selection (`backupsToDelete()`) is tested as a pure unit function with
  no SAF or Android dependency.
- CSV export and destructive factory reset (TDD §4.17 AC4/AC5) were deferred beyond
  PHASE_12 and are not affected by this decision.
