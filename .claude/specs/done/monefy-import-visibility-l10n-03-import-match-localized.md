# Monefy import matches existing localized categories/accounts (no duplicates)
Epic: monefy-import-visibility-l10n
Order: 03 of 03
Status: done
Depends-on: 02
Date: 2026-06-08

## SPEC
=== SPEC ===
TASK: feature
WHAT: A Monefy CSV import reuses existing categories and accounts whose name matches (case- and whitespace-normalized) instead of creating duplicates, so a RU export merges into the seeded RU built-in entities.
LAYERS: data
CHANGED_HINT:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt → importMonefyCsv (resolveAccountId / resolveCategoryId already key on `name.trim().lowercase(Locale.ROOT)` — harden the normalization and confirm seeded RU defaults are matched, not duplicated)
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt (extend)
TEST_TYPES: dao unit
CONSTRAINTS:
  - Normalize both the CSV name and the existing-entity name identically before comparison: trim, Unicode-normalize (NFC), collapse internal runs of whitespace to a single space, then `lowercase(Locale.ROOT)`. Apply to BOTH accounts and categories (the existing code lowercases+trims only — add NFC + inner-whitespace collapse so "Кафе и  рестораны" vs "Кафе и рестораны" still match).
  - Matching stays kind-aware for categories (name + CategoryKind), as today. Account match is by normalized name.
  - When a normalized match exists → reuse its id (no new row). Only create a new account/category when there is genuinely no match (e.g. a user's custom Monefy category) — keep the existing AUTO_PALETTE / AUTO_*_ICON path for those.
  - Do NOT change the additive import semantics or transaction creation; this SPEC only affects entity de-duplication. Money BigDecimal in domain / Double in Room; occurredAt epoch-millis via existing TypeConverters; import stays inside one `withTransaction`.
  - Tests (instrumented dao E2E with real in-memory/on-device Room, fakes only at repo boundary — no mocks): seed a RU install (or pre-insert RU built-ins), import a Monefy CSV whose account is "Наличные" and categories are RU defaults (e.g. "Зарплата", "Продукты") → assert the account count and category count are UNCHANGED (merged, not duplicated) and the imported transactions reference the existing seeded ids. A CSV category absent from the seed ("My custom") still creates exactly one new category.
  - NOTE for the runner: this is Room orchestration — JVM unit tests cannot cover the DAO path; the E2E instrumented test (MonefyCsvImportE2ETest) is the real gate and must be verified on a connected device per the project's device protocol.
=== END SPEC ===

## Gap / context
Closes requirement #1 ("полное совпадение с CSV, без дубликатов") on a fresh RU install:
with the localized seed from SPEC 02 in place, the importer must actually reuse those built-ins.
Hardens the existing name normalization (NFC + inner-whitespace) so near-identical RU labels match.

## Implementation links
- commit: 1a777ec6 (normalizeName helper + BackupRepositoryImpl matching), 074308d1 (extra JVM + E2E tests)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/csv/MonefyCsvImportParser.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/csv/MonefyCsvImportParserTest.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt
  - core/database/src/androidTest/java/com/kshavrin/mymoney/core/database/MonefyCsvImportE2ETest.kt
- verification: JVM `:core:domain:test --tests *MonefyCsvImportParserTest` green; `:core:database` main + androidTest compile clean; dao E2E `:core:database:connectedDebugAndroidTest` on emulator-5554 (Pixel_5_API_34) = tests 4, failures 0, errors 0, skipped 0 (incl. merges_into_seeded_RU_entities_without_duplicating). Runner JVM-script false-negative bypassed per project policy.
