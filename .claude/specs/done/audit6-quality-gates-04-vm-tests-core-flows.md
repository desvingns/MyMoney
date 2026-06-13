# Недостающие VM-тесты: Onboarding, Splash, Transfer
Epic: audit6-quality-gates
Order: 04 of 05
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Закрыть верифицированные дыры покрытия ядра пользовательских флоу: :feature:onboarding получает ПЕРВЫЕ тесты (OnboardingViewModel: персист onboardingCompletedAt + роутинг; SplashViewModel: запуск сидера + переход), :feature:transaction получает TransferViewModelTest (валидация суммы, кросс-валютный путь AS-6/AS-7 через fake-репозитории, эмиссия NavigateBack).
LAYERS: test
CHANGED_HINT:
  - feature/onboarding/src/test/.../OnboardingViewModelTest.kt — НОВЫЙ + module-local fakes (G5, G6)
  - feature/onboarding/src/test/.../SplashViewModelTest.kt — НОВЫЙ: happy + сидер бросает → поведение текущего кода зафиксировать (G5; уточнение поведения — audit7-forms-hardening-04)
  - feature/transaction/src/test/.../transfer/TransferViewModelTest.kt — НОВЫЙ (G5): same-currency и cross-currency сценарии, isSaving-флоу
  - feature/onboarding/build.gradle.kts — (assumption) добавить недостающие test-зависимости по образцу feature/transaction
TEST_TYPES: unit
CONSTRAINTS:
  - Fakes-only, без мок-фреймворков; фейки module-local (G6).
  - Turbine + kotlinx-coroutines-test по стеку проекта (TDD §12).
  - Тесты пишутся под ТЕКУЩЕЕ поведение (характеризация); найденные при написании дефекты — в новые SPEC-и, не чинить тут.
  - Если audit2-save-integrity-02 уже выполнен — покрыть и double-tap guard Transfer.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Ядровые флоу под тестами

  Scenario: Онбординг завершается корректно
    When пользователь проходит онбординг до конца
    Then onboardingCompletedAt персистится и эмитится переход на дашборд

  Scenario: Сплэш сеет данные один раз
    Given база пуста
    When SplashViewModel инициализируется
    Then сидер вызван и эмитится переход дальше

  Scenario: Кросс-валютный перевод
    Given счета в RUB и USD и сохранённый курс
    When пользователь сохраняет перевод
    Then суммы источника/получателя соответствуют курсу (AS-6/AS-7)

  Scenario: Сбой сидера зафиксирован
    Given сидер бросает исключение при инициализации
    When SplashViewModel инициализируется
    Then текущее поведение зафиксировано тестом-характеризацией (до фикса audit7-forms-hardening-04)
```

## Gap / context
Аудит §3 (G5): :feature:onboarding — ноль тестов при том, что Splash запускает сидер без
обработки ошибок; TransferViewModel не покрыт вовсе (только screen-contract).

## Implementation links
- commit: 9f243319
- files:
  - feature/onboarding/build.gradle.kts
  - feature/onboarding/src/test/java/com/kshavrin/mymoney/feature/onboarding/OnboardingViewModelTest.kt
  - feature/onboarding/src/test/java/com/kshavrin/mymoney/feature/onboarding/SplashViewModelTest.kt
  - feature/onboarding/src/test/java/com/kshavrin/mymoney/feature/onboarding/util/MainDispatcherRule.kt
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/transfer/TransferViewModelTest.kt
