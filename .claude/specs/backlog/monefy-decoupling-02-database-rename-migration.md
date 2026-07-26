# Rename monefy.db → a neutral filename, with a real migration
Epic: monefy-decoupling
Order: 02 of 02
Status: draft
Depends-on: —
Date: 2026-07-26

## SPEC
=== SPEC ===
TASK: feature
WHAT: Rename the on-disk Room database file from `monefy.db` to a neutral name (e.g. `mymoney.db`) WITHOUT orphaning data on existing installs. Requires a startup migration that detects a legacy `monefy.db` (plus its `-shm` / `-wal` siblings) in the databases dir and renames all three before Room opens the database, exactly once, idempotently. All five call sites must move together: core/database/di/DatabaseModule.kt:40 (databaseBuilder name), core/database/repository/BackupRepositoryImpl.kt:1132 (DATABASE_NAME), feature/settings/backup/BackupRestoreViewModel.kt:172 (DATABASE_NAME), app/src/main/res/xml/data_extraction_rules.xml:10-12 (three include paths), app/src/test/.../DataExtractionRulesTest.kt:34-36 (expected rule entries).
LAYERS: [data]
CHANGED_HINT: core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt, core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt, feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/backup/BackupRestoreViewModel.kt, app/src/main/res/xml/data_extraction_rules.xml, app/src/test/java/com/kshavrin/mymoney/DataExtractionRulesTest.kt
TEST_TYPES: unit dao
CONSTRAINTS: DATA LOSS RISK — this is the only reason the SPEC exists separately. A build that simply changes the filename silently strands every existing user's data behind an empty new database; such a diff must be rejected. Required coverage: (a) fresh install creates the new name; (b) legacy install with monefy.db + -shm + -wal is renamed and ALL rows survive; (c) migration is idempotent across restarts; (d) a restored backup written under the old name still opens; (e) both cloud-sync and local backup/restore round-trip after the rename. Auto-backup continuity: data_extraction_rules.xml must keep the OLD paths alongside the new ones, or a device restore from a pre-rename backup loses the database. Verify on a real device with pre-existing data, not only in tests.
=== END SPEC ===

## Gap / context

`monefy.db` is the last functional Monefy artifact — a hardcoded filename, not a label. It is
harmless as-is: users never see it, and the app works. This SPEC is therefore **optional
cosmetics with real downside**, filed so the decision is explicit rather than forgotten.

If the migration cannot be verified on a device carrying real pre-existing data, the correct
outcome is to close this SPEC as "won't do" and keep the filename.

## Implementation links
- commit: (pending)
- files: (pending)
