# Курс валют: редактирование существующей пары реально сохраняется
Epic: audit2-save-integrity
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Сохранение курса на S27 для УЖЕ существующей пары валют должно обновлять запись. Сейчас VM всегда шлёт `CurrencyRate(id = 0L)`; insert бьётся об unique(from,to), fallback-UPDATE идёт по id=0 → 0 строк, экран рапортует успех, переводы считаются по старому курсу. Фикс: перед сохранением `findRate(from,to)` и переиспользование `existing.id` (паттерн detail-VM).
LAYERS: presentation
CHANGED_HINT:
  - feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateViewModel.kt:96-105 — в save(): `val existing = currencyRateRepository.findRate(fromId, toId)`; `CurrencyRate(id = existing?.id ?: 0L, …)` (G1, G4; зеркало G3)
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateViewModelTest.kt — НОВЫЙ (закрывает G10): fake-репозиторий; кейсы «новая пара → insert», «существующая пара → upsert с её id», «ошибка парсинга → errorMessage»
TEST_TYPES: unit
CONSTRAINTS:
  - DAO/Entity/Repository НЕ менять — фикс только в VM (минимальный дифф, контракт G4 уже существует).
  - Модульно-локальный fake для CurrencyRateRepository (конвенция module-local fakes).
  - `CurrencyRateViewModel.kt` затем минорно правится в audit7-forms-hardening-02 (запятая) — этот SPEC первый.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Редактирование курса валют

  Scenario: Обновление существующего курса
    Given курс USD→EUR уже сохранён со значением 0.90
    When пользователь открывает экран курса, вводит 0.95 и сохраняет
    Then сохранённый курс USD→EUR равен 0.95
    And последующий перевод конвертируется по 0.95

  Scenario: Первый курс пары
    Given курс USD→EUR ещё не задан
    When пользователь вводит 0.90 и сохраняет
    Then создаётся новая запись курса USD→EUR = 0.90
```

## Gap / context
Баг H1 аудита, найден двумя независимыми проверками и подтверждён по сгенерированному DAO (G2).
Первый ввод курса для пары работает; любое последующее изменение — silent no-op.

## Implementation links
- commit: 2da61c02, 749e21f6
- files: feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateViewModel.kt; feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/rate/CurrencyRateViewModelTest.kt
