# Monefy-импорт: счёт резолвится по имени И валюте
Epic: audit7-forms-hardening
Order: 01 of 04
Status: active
Depends-on: audit4-records-02
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Строка Monefy-CSV со счётом «Наличные» в USD не должна прикрепляться к существующему счёту «Наличные» (RUB). resolveAccountId учитывает валюту: имя совпало и валюта совпала → существующий счёт; имя совпало, валюта НЕТ → создаётся новый счёт «Наличные (USD)» (суффикс — код валюты строки); повторные строки той же пары имя+валюта попадают в один и тот же счёт.
LAYERS: data
CHANGED_HINT:
  - core/database/.../repository/BackupRepositoryImpl.kt:276-294 — resolveAccountId: сравнение account.currencyId с currencyId строки (зеркало MyMoney-пути :184-186); ветка mismatch → создание счёта с суффиксом « (<CODE>)» и кешем в map импорта (G1)
  - unit/инструментальный тест импорта: фикстура с «Наличные» RUB в базе + CSV «Наличные» USD → два счёта, балансы не смешаны; повторный прогон строк USD → счёт один
TEST_TYPES: unit, dao
CONSTRAINTS:
  - NFC-нормализация имён (существующее поведение merge из `1a777ec6`) сохраняется — сравнение к нормализованному имени + валюте.
  - Суффиксованное имя тоже нормализуется и участвует в последующих совпадениях (идемпотентность повторного импорта по именам не ломать — дубль строк при повторном импорте остаётся известным документированным поведением).
  - `BackupRepositoryImpl.kt` — после audit4-records-02; его же затем правит audit9-sync-hardening-03.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Импорт не смешивает валюты в одном счёте

  Scenario: Конфликт валют создаёт отдельный счёт
    Given в приложении есть счёт "Наличные" в RUB
    When импортируется Monefy-CSV со строками счёта "Наличные" в USD
    Then создаётся счёт "Наличные (USD)" с валютой USD
    And баланс рублёвых "Наличные" не изменился от USD-строк

  Scenario: Совпадение имени и валюты переиспользует счёт
    Given в приложении есть счёт "Карта" в RUB
    When импортируются строки счёта "Карта" в RUB
    Then новые счета не создаются

  Scenario: Несколько строк одной новой пары
    When импортируются три строки "Наличные"/USD
    Then счёт "Наличные (USD)" создан ровно один раз
```

## Gap / context
Баг M12 аудита (G1): Monefy-путь резолвит счёт только по имени — 100 USD + 100 RUB суммируются
в «200» на одном счёте. MyMoney-путь уже делает проверку валюты — переносим паттерн.

## Implementation links
- commit: 63291630 (fix) + f20222e6 (ktlint-format new test) — pushed to main 2026-06-13
- files: `core/database/.../repository/BackupRepositoryImpl.kt`, `core/database/src/androidTest/.../MonefyCsvImportE2ETest.kt`
- notes: resolveAccountId import-map re-keyed `name → (normalizedName, currencyId)`; currency mismatch on a name match creates `"<Name> (<CODE>)"` (suffix normalized + cached → repeat rows reuse, created once); MyMoney path untouched; NFC merge preserved. MonefyCsvImportE2ETest 8/8 on emulator-5554 (4 new currency scenarios). JVM runner false-fail (probes :app:jacoco; Kover project) → verified-manual JVM gates green.
- ⚠ `BackupRepositoryImpl.kt` edit #2 of 3 in this drain (after audit4-records-02); audit9-sync-hardening-03 edits it next.
