# Доменные мелочи: атомарный сидер, ошибки Splash, HALF_UP, ÷0
Epic: audit7-forms-hardening
Order: 04 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Три маленьких, но реальных дефекта: (1) первичное сидирование выполняется атомарно (общая транзакция) — краш между валютами и категориями больше не оставляет базу полузасеянной навсегда, а SplashViewModel оборачивает сидер в обработку ошибок (ошибка → Sentry + узнаваемое состояние с retry, не uncaught crash); (2) MoneyFormatter получает roundingMode = HALF_UP — отображение совпадает с доменным округлением на граничных «.005»; (3) калькулятор при делении на ноль сохраняет левый операнд и сбрасывает pending-операцию вместо тихого нуля (O2).
LAYERS: domain
CHANGED_HINT:
  - core/domain/.../seed/InitialDataSeeder.kt:29-33 — выполнить сидирование внутри транзакционного раннера (assumption: интерфейс TransactionRunner в :core:domain + impl на database.withTransaction в :core:database — паттерн Decision 3/PHASE_12; если audit9-sync-hardening-01 уже ввёл его — переиспользовать) (G6)
  - feature/onboarding/.../SplashViewModel.kt:24-26 — try/catch вокруг сидера: ошибка → Sentry + state с возможностью повторить (G6)
  - core/common/.../money/MoneyFormatter.kt:19-23 — `roundingMode = RoundingMode.HALF_UP` (G7)
  - core/common/.../calculator/CalculatorEngine.kt:155-159 — ÷0: вернуть левый операнд, сбросить pending (G8, O2)
  - тесты: сидер-атомарность (fake падает на категориях → валюты не закоммичены), MoneyFormatter «0.125»→«0.13», CalculatorEngine «100 ÷ 0 =» → 100
TEST_TYPES: unit
CONSTRAINTS:
  - Идемпотентность сидера (проверка по валютам) сохраняется; локле-зависимый SEED (`53ad39d2`) не трогать.
  - BR-7 и лимит 16 цифр калькулятора не трогать.
  - Если SplashViewModelTest уже создан (audit6-04) — дополнить его, не дублировать.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Надёжный фундамент домена

  Scenario: Сидер атомарен
    Given сидирование падает на середине
    When приложение стартует снова
    Then база либо полностью засеяна, либо пуста — и сидер успешно отрабатывает повторно

  Scenario: Отображение совпадает с доменом
    Given сумма 0.125 при двух знаках валюты
    Then форматтер показывает 0.13

  Scenario: Деление на ноль в калькуляторе
    Given пользователь набрал 100 ÷ 0
    When нажимает =
    Then на экране остаётся 100 и операция сброшена
```

## Gap / context
Баги L1/L6/L2 аудита (G6, G7, G8): полузасеянная база неисправима без очистки данных; HALF_EVEN
расходится с доменным HALF_UP; ÷0 тихо даёт 0.

## Implementation links
- commit: 03943e6e, a7b9573b, 2f5e19a5
- files:
  - core/common/src/main/kotlin/com/kshavrin/mymoney/core/common/calculator/CalculatorEngine.kt
  - core/common/src/main/kotlin/com/kshavrin/mymoney/core/common/money/MoneyFormatter.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/di/RepositoryBindingsModule.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/transaction/RoomTransactionRunner.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/seed/InitialDataSeeder.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/transaction/TransactionRunner.kt
  - feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/SplashScreen.kt
  - feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/SplashViewModel.kt
  - feature/onboarding/src/main/res/values/strings.xml
  - feature/onboarding/src/main/res/values-ru/strings.xml
  - core/common/src/test/kotlin/com/kshavrin/mymoney/core/common/calculator/CalculatorEngineTest.kt
  - core/common/src/test/kotlin/com/kshavrin/mymoney/core/common/money/MoneyFormatterTest.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/seed/InitialDataSeederTest.kt
  - feature/onboarding/src/test/java/com/kshavrin/mymoney/feature/onboarding/SplashViewModelTest.kt
