# Формы: вывод денег через MoneyFormatter + округление ввода до ≤2
Epic: money-decimal-precision
Order: 03 of 05
Status: backlog
Depends-on: money-decimal-precision-01
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) Экран «Финансовые цели» выводит «Сейчас на счету» и «Не хватает» сырым `BigDecimal` (видно `-1182337.0799999996 ₽`) — пропустить эти строки через существующий `MoneyFormatter.format(...)` (символ валюты, разряды, ≤decimalDigits). (2) Защита-в-глубину на вводе: `parseMoneyField()` применяет `toMoneyScale(currency)` (политика 01), чтобы пользовательский ввод вроде `10.005`/`12.3456` не уходил в БД с >2 знаками — в Goal/Account/CurrencyRate VM.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditViewModel.kt:397-402 — `parseMoneyField()`: после `toBigDecimalOrNull()` применить `.toMoneyScale(currency)` (G13, политика 01); строки баланса «на счету»/«не хватает» форматировать через `MoneyFormatter` (G6), а не `toPlainString()`
  - feature/dictionaries/.../goals/GoalEditScreen.kt — отображать уже отформатированную строку (баланс/«не хватает»)
  - feature/dictionaries/.../accounts/AccountEditViewModel.kt:107-109 — `toMoneyScale` после парсинга initial balance (G13)
  - feature/transaction/.../rate/CurrencyRateViewModel.kt:78 — округление введённого значения до ≤2 (G13); курс — это не «деньги», но потолок 2 знака запрошен — применить ту же политику (см. CONSTRAINTS)
  - тесты: GoalEdit «12.3456» → сохранено 12.35; строка баланса не содержит >2 знаков
TEST_TYPES: unit
CONSTRAINTS:
  - Общие файлы с audit7-02 (`parseMoneyField` в тех же трёх VM) — менять рядом с уже существующей логикой запятой/валидации, не ломая её.
  - `MoneyFormatter` уже существует (G6) — переиспользовать, новый форматтер не писать. `decimalDigits`/символ валюты брать из валюты счёта.
  - После SPEC-02 артефакт со скрина уже исчезает; этот SPEC добавляет корректное форматирование (разряды) и не даёт >2 знакам попасть в стор с форм.
  - CurrencyRate: если курс должен поддерживать >2 знаков (точность конвертации) — НЕ округлять курс, а ограничить только денежные поля; решить при реализации, по умолчанию применяем потолок 2 (assumption). Зафиксировать выбор в коммите.
  - :feature:* тесты runner пропускает (G14) — проверять модули напрямую; ktlintFormat перед коммитом (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Деньги на формах не показывают и не сохраняют более 2 знаков

  Scenario: Баланс на экране целей отформатирован
    Given счёт с балансом -1182337.08
    When открыт экран «Финансовые цели»
    Then «Сейчас на счету» показано через MoneyFormatter с не более чем 2 знаками и символом валюты

  Scenario: Ввод с >2 знаками округляется при сохранении
    When пользователь вводит стартовый капитал "12.3456" и сохраняет
    Then значение сохранено как 12.35

  Scenario: Существующая валидация запятой не сломана
    When пользователь вводит "10000,50"
    Then значение принято как 10000.50
```

## Gap / context
Экран целей печатает сырой `BigDecimal` (отсюда `-1182337.0799999996 ₽` на скрине) и формы не ограничивают дробную часть ввода. Форматирование через `MoneyFormatter` + `toMoneyScale` на вводе закрывают presentation-арм проблемы.

## Implementation links
- commit: <hash>
- files:  <changed files>
