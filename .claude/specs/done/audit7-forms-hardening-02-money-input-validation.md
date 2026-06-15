# Денежный ввод форм: ошибка вместо тихого нуля, запятая, createdAt
Epic: audit7-forms-hardening
Order: 02 of 04
Status: done
Depends-on: audit2-save-integrity-02
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) Форма целей перестаёт молча превращать нечисловой ввод в 0: parseMoney → нормализация запятой в точку + toBigDecimalOrNull(); null → ошибка валидации у поля, сохранение блокируется (паттерн AccountEdit). (2) Запятая принимается как десятичный разделитель также в AccountEdit (initial balance) и CurrencyRate (курс). (3) createdAt существующей сущности сохраняется при редактировании (Goal + Account) — затирается только при создании.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditViewModel.kt:316-317 — parseMoney: `replace(',', '.')` + ошибка вместо `?: BigDecimal.ZERO` (G2; зеркало G3); :305 — `createdAt = existing?.createdAt ?: now` (G4)
  - feature/dictionaries/.../accounts/AccountEditViewModel.kt:102-105 — нормализация запятой перед существующей валидацией (G3); :121 — createdAt как выше (G4)
  - feature/transaction/.../rate/CurrencyRateViewModel.kt:75 — нормализация запятой перед toDoubleOrNull (G9)
  - строки ошибок: переиспользовать существующие validation-ключи модулей; новые — EN+RU
  - тесты: GoalEdit «10000,50» → сохранено 10000.50; «abc» → ошибка, не сохранено; createdAt неизменен при редактировании
TEST_TYPES: unit
CONSTRAINTS:
  - Общие файлы: GoalEdit/AccountEdit — после audit2-save-integrity-02; CurrencyRateViewModel — после audit2-save-integrity-01.
  - Существующее поведение точки как разделителя не меняется; пробелы/NBSP в вводе — trim (полноценный locale-парсинг вне scope).
  - Деньги остаются BigDecimal в домене (конвенция).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Денежный ввод не теряет данные молча

  Scenario: Запятая принимается
    When пользователь вводит цель "10000,50" и сохраняет
    Then цель сохранена со значением 10000.50

  Scenario: Мусорный ввод блокируется
    When пользователь вводит "10 000р" в сумму цели
    Then у поля показана ошибка валидации
    And цель не сохранена со значением 0

  Scenario: createdAt стабилен
    Given цель создана 1 июня
    When пользователь редактирует её 10 июня
    Then createdAt остаётся 1 июня
```

## Gap / context
Баги M14/L4 аудита (G2, G4): RU-ввод с запятой давал цель с target=0 без какой-либо ошибки;
каждое редактирование переписывало дату создания.

## Implementation links
- commit: 15a32338 (feat) + 8695932e (auto-fix: blank optional credit fields stay 0, error only on non-blank garbage)
- files:
  - feature/dictionaries/.../goals/GoalEditViewModel.kt (parseMoneyOrNull comma-norm; blank→0, non-blank-garbage→amount_format error; createdAt = existing ?: now)
  - feature/dictionaries/.../goals/GoalEditScreen.kt (surface amount_format error)
  - feature/dictionaries/.../accounts/AccountEditViewModel.kt (comma in initial balance; createdAt preserved on edit)
  - feature/transaction/.../rate/CurrencyRateViewModel.kt (comma in rate before toDoubleOrNull)
  - feature/dictionaries/src/main/res/values{,-ru}/strings.xml (validation error strings EN+RU)
- tests: GoalEditViewModelTest (+comma/garbage/createdAt), AccountEditViewModelTest, CurrencyRateViewModelTest — :feature:dictionaries 275/0, :feature:transaction 82/0 (JVM)
- verified: 2026-06-13; runner script gives known :app: detekt/jacoco false-negative — modules verified directly
