# Атомарный AppSettings.update внутри dataStore.edit
Epic: audit2-save-integrity
Order: 03 of 04
Status: done
Depends-on: audit1-timezone-03
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Конкурентные вызовы `AppSettingsRepository.update` не должны терять изменения друг друга. Сейчас транзформ читает снапшот через `settings.first()` СНАРУЖИ `dataStore.edit`, затем перезаписывает все 16 ключей — параллельная запись воскрешает стёртые поля (наблюдаемо: выбор счёта после CSV-импорта возвращает importFocus и откатывает выбор пользователя). Фикс: transform выполняется внутри `dataStore.edit { prefs -> transform(prefs.toAppSettings()).writeTo(prefs) }`.
LAYERS: data
CHANGED_HINT:
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt:23-31 — убрать `settings.first()`; читать текущее состояние из `prefs` внутри edit-лямбды и применять transform там же (G7)
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt — добавить concurrency-кейс: два параллельных update (разные поля) → оба изменения сохранены; update+clearImportFocus параллельно → importFocus не воскресает
TEST_TYPES: unit
CONSTRAINTS:
  - Сигнатура `update(transform: (AppSettings) -> AppSettings)` НЕ меняется — 13 вызовов в 9 файлах продолжают работать без правок (G8).
  - transform обязан остаться чистой функцией (он уже не suspend) — выполнять внутри edit безопасно.
  - Файл также правится в audit1-timezone-03 (новое поле) — выполняется ПОСЛЕ него.
  - Семантика монотонного firstPositiveSeen (валидация в update) сохраняется.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Конкурентные обновления настроек не теряются

  Scenario: Параллельные изменения разных полей
    Given текущие настройки по умолчанию
    When параллельно выполняются update{defaultAccountId=5} и update{themeMode=DARK}
    Then итоговые настройки содержат и defaultAccountId=5, и themeMode=DARK

  Scenario: Очистка import-focus не откатывается
    Given importFocusEpochMs установлен после CSV-импорта
    When пользователь выбирает счёт (параллельные clearImportFocus и запись defaultAccountId)
    Then importFocusEpochMs остаётся очищенным
    And выбранный счёт сохранён
```

## Gap / context
Баг H2 аудита (G7, G8): классический lost-update. Прямо угрожает фиксу import-focus `26dc71ac` —
дашборд после гонки снова форсит месяц/валюту импорта, молча откатывая навигацию пользователя.

## Implementation links
- commit: 589b596d, b52d76cb
- files:
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
