# Localize seeded built-in categories + default account to the locale
Epic: monefy-import-visibility-l10n
Order: 02 of 03
Status: draft
Depends-on: —
Date: 2026-06-08

## SPEC
=== SPEC ===
TASK: feature
WHAT: Seed built-in categories and the default account in the device/app locale — Russian names when the locale language is Russian (aligned to Monefy's default labels), English otherwise.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/seed/InitialDataSeeder.kt (seedIfNeeded already receives `locale: Locale`; the seed account name "Cash" and EXPENSE_CATEGORY_SEEDS / DEFAULT_INCOME_CATEGORIES names are hard-coded English)
  - core/domain/src/test/kotlin/.../seed/InitialDataSeederTest.kt (if present) — extend for locale branching
TEST_TYPES: unit
CONSTRAINTS:
  - Decision: SEED-IN-LOCALE (static at first launch), NOT resolve-at-render. Keep iconKey and colorHex unchanged; only the `name` strings branch on locale.
  - Add a bilingual name table in the seeder keyed by `locale.language == "ru"`. RU names MUST match Monefy's default category labels so a RU Monefy CSV merges (SPEC 03 relies on this). Default account RU name = "Наличные" (Monefy's default), EN stays "Cash".
  - RU category names (expense) aligned to Monefy defaults — map each existing English seed to its Monefy RU label, e.g.: Food→"Продукты", Transport→"Транспорт", Taxi→"Такси", Car→"Автомобиль", Health→"Здоровье", Sport→"Спорт", Pets→"Питомцы", Gifts→"Подарки", Phone→"Телефон", Bills→"Счета", Housing→"Жильё", Clothing→"Одежда", Entertainment→"Развлечения", Cafe→"Кафе и рестораны", Hygiene→"Личная гигиена". Income: Salary→"Зарплата", Other→"Прочее". (Developer: verify each label against the real Monefy RU defaults / the user's CSV category column; the CSV in this repo's import test shows `Зарплата`. Where a Monefy label is uncertain, keep the count and ordering and pick the closest standard RU term — note any (assumption).)
  - Preserve sortOrder, isDefault=true, kind, and the existing category COUNT and ordering exactly (CategoryIconsTest / any hard-coded count tests must still pass — do not add or drop categories here).
  - Seed remains one-shot: only runs when the currency table is empty (existing guard). No behaviour change for already-seeded installs.
  - No hard-coded user-facing strings leak elsewhere; this is seed data, the canonical name table lives in the seeder. Money BigDecimal in domain; no Room schema change.
  - Unit tests: seedIfNeeded with `Locale("ru")` seeds the RU account/category names; with `Locale.ENGLISH` (or any non-ru) seeds the English names; category count + ordering identical across locales.
=== END SPEC ===

## Gap / context
Built-in entities are hard-coded English, so a Russian Monefy export cannot match them and
duplicates every category/account. Localizing the seed to the locale gives a fresh RU install
built-in names that match Monefy's defaults — the precondition for SPEC 03's no-duplicate merge.

## Implementation links
- commit: <hash>
- files: <changed files>
