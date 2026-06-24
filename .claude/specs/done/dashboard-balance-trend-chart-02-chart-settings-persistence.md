# Настройки графика — хранение в AppSettings
Epic: dashboard-balance-trend-chart
Order: 02 of 07
Status: done
Depends-on: —
Date: 2026-06-21

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Расширить `AppSettings` конфигом графика и провести его через DataStore (чтение/запись/round-trip). Поля (примитивы, в стиле плоского `AppSettings`): `chartVisible: Boolean = true`, `chartStyle: String = "neon_line"`, `chartPeriodType: String = "follow"` (follow = повторять период дашборда; иначе day/week/month/year), `chartPointCount: Int = 5`, `chartMetric: String = "cumulative"` (cumulative/period_net/income_expense), `chartShowGridlines: Boolean = true`, `chartShowLabels: Boolean = true`, `chartColorRule: String = "by_sign"` (always_green/by_sign/by_trend).
LAYERS: data
CHANGED_HINT:
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt:3 — добавить 8 полей с дефолтами (G15).
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:41 `toAppSettings()` и :65 `writeTo()` — чтение/запись новых ключей с дефолтами (G15).
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt — добавить `Preferences.Key` для каждого поля (G15 — файл уже хранит все ключи, повторить паттерн).
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt — round-trip новых полей + дефолты при отсутствии ключей (G15).
TEST_TYPES: unit
CONSTRAINTS:
  - НЕ задевать монотонный guard `firstPositiveSeen` в `update()` (`AppSettingsRepositoryImpl.kt:28`) — новые поля независимы (G15).
  - Дефолты обязаны давать обратную совместимость: старый prefs без новых ключей → значения по умолчанию.
  - Значения строковых «enum» — стабильные английские id (НЕ локализованные строки); локализация ярлыков — на UI (06).
  - English-идентификаторы; zero comments кроме неочевидного WHY.
  - Это ЕДИНСТВЕННЫЙ SPEC, меняющий схему `AppSettings`; SPEC 06 только читает/пишет через `update {}` — без правок маппинга.
=== END SPEC ===

## Acceptance
```gherkin
Scenario: Round-trip конфига графика
  Given пользователь сохранил chartStyle="bars", chartMetric="period_net", chartVisible=false
  When AppSettings читается заново из DataStore
  Then значения равны сохранённым

Scenario: Дефолты для старой установки
  Given prefs без ключей графика (обновление с прошлой версии)
  When читается AppSettings
  Then chartVisible=true, chartStyle="neon_line", chartPointCount=5, chartMetric="cumulative", chartColorRule="by_sign"
```

## Gap / context
Настройки графика должны переживать перезапуск (D13). `AppSettings` — единственный persistence для пользовательских
предпочтений дашборда; добавляем конфиг туда тем же паттерном, что и `dashboardPeriodEpochMs`.

## Implementation links
- commit: d66d76a9 (prod) + 589dd8a5 (tests)
- files: AppSettings.kt, AppSettingsKeys.kt, AppSettingsRepositoryImpl.kt, AppSettingsRepositoryTest.kt (core/datastore)
