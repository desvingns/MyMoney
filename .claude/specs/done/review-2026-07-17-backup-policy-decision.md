# ADR: unencrypted DB in cloud backup + destructive-migration invariant
Epic: review-2026-07
Order: 17 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Decide and document the cloud-backup posture for financial data as docs/DECISIONS/ADR-0001: today monefy.db goes unencrypted into Google cloud backup (backup_rules.xml includes database + datastore, secure prefs excluded) — options: keep (restore convenience), restrict DB to device-transfer only (cloud excluded), or encrypt-before-backup; implement the chosen option in backup_rules.xml/data_extraction_rules.xml if it changes. Additionally add the missing invariant comment at DatabaseModule fallbackToDestructiveMigrationFrom(99) explaining why 99 and why production schemas (1..8) can never hit it.
LAYERS: [data]
CHANGED_HINT: app/src/main/res/xml/backup_rules.xml, app/src/main/res/xml/data_extraction_rules.xml, core/database/**/DatabaseModule.kt:49, new docs/DECISIONS/ADR-0001-*.md
TEST_TYPES: unit
CONSTRAINTS: decision needs an explicit user gate before any rules change (privacy trade-off is the user's call); the ADR records rejected options and why; PIN/tokens must stay excluded from backup in every option
=== END SPEC ===

## Gap / context
Financial DB in unencrypted cloud backup is a deliberate-looking but undocumented
choice; the migration fallback constant reads like a bomb without a comment.
Source: review items 44+46 (P2/S + P3/S).

## Implementation links
- commits: f1a12bfd, fd12462c, a9282036, 2bc83921, cb6c7fc5
- files: app/src/main/res/xml/backup_rules.xml, app/src/main/res/xml/data_extraction_rules.xml, core/database/src/main/java/com/kshavrin/mymoney/core/database/di/DatabaseModule.kt, docs/DECISIONS/ADR-0001-system-backup-financial-data.md, app/src/test/java/com/kshavrin/mymoney/BackupRulesTest.kt, app/src/test/java/com/kshavrin/mymoney/DataExtractionRulesTest.kt
