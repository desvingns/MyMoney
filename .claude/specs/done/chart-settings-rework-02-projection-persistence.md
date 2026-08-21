# Реворк настроек графика — персистентность тумблера проекций
Epic: chart-settings-rework
Order: 02 of 06
Status: done
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: setting=missing_key,explicit_true
Risk-signals: persistence

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Добавить в `AppSettings` поле `chartShowProjection: Boolean = false` (D5: default off)
с полным fan-out по конвенции проекта: ключ DataStore, чтение с `?: false`, запись,
round-trip тесты. UI/mapping — в SPEC 04/05; здесь только data-слой.
LAYERS: data
CHANGED_HINT:
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:32-40 — поле `chartShowProjection: Boolean = false` рядом с chart-полями (D1, D9).
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt:34-42 — `CHART_SHOW_PROJECTION = booleanPreferencesKey("chart_show_projection")` (D3).
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:100-108,148-156 — чтение `?: false` в toAppSettings + запись в writeTo (D3).
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt:130-163 — round-trip с non-default значением + тест «prefs без ключа → false» (D9).
TEST_TYPES: unit
CONSTRAINTS:
  - Guard'ы `firstPositiveSeen`/`supporterBadgeEarned` в update() (`AppSettingsRepositoryImpl.kt:35-49`) НЕ трогать (D4, F13).
  - `FakeAppSettingsRepository` (core:testing) — data-class copy, новых полей не требует; `FakeAppSettingsRepositoryContractTest` должен остаться зелёным (D10).
  - Обратная совместимость через `?: default` — миграций не нужно (D3).
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Дефолт при пустых prefs
  Given DataStore без ключа chart_show_projection
  When читаются настройки
  Then chartShowProjection = false

Scenario: Round-trip не-дефолтного значения
  Given update { copy(chartShowProjection = true) }
  When настройки перечитаны
  Then chartShowProjection = true
```

## Gap / context
Даёт persistence для тумблера «Проекции» (D1/D5) — единственное новое persisted-поле эпика.

## Implementation links
- commit: d0e754da, a2158dc5
- files: core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt, core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt, core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
