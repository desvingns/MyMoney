# Доменный контракт биллинга: BillingGateway и фейк
Epic: support-hub-tip
Order: 02 of 08
Status: done
Depends-on: 01
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В `:core:domain` появляется интерфейс `BillingGateway` и его доменные модели, полностью свободные от Google: `SupportProduct(id, formattedPrice, title)`, `BillingAvailability` (`Available` / `UnavailableOnDevice` / `UnavailableInRegion` / `DisabledInBuild`), `PurchaseOutcome` (`Purchased(productId, purchaseToken, purchasedAtMillis)` / `Pending` / `Cancelled` / `NetworkError` / `Unavailable(reason)`). В `:core:testing` появляется `FakeBillingGateway`, умеющий проиграть каждое из этих состояний, — он же будет двигателем всех тестов SPEC-04 и SPEC-07. Google Play Billing в этом SPEC не подключается вообще; это чистый Kotlin-контракт, который компилируется и тестируется без единой зависимости от Play.
LAYERS: domain
CHANGED_HINT:
  - core/domain/src/main/java/com/kshavrin/mymoney/core/domain/billing/BillingGateway.kt — новый интерфейс: `availability(): Flow<BillingAvailability>`, `products(): Result<List<SupportProduct>>`, `purchase(productId: String): Flow<PurchaseOutcome>`, `resolvePendingPurchases(): Result<List<PurchaseOutcome>>` (assumption: имена методов; слой и пакетная конвенция — G11)
  - core/domain/src/main/java/com/kshavrin/mymoney/core/domain/billing/SupportProduct.kt, BillingAvailability.kt, PurchaseOutcome.kt — доменные модели без Play-типов; цена хранится уже отформатированной строкой от Play, а не `BigDecimal` (O2; конвенция «деньги в домене — BigDecimal» здесь неприменима: приложение не считает эту цену, а показывает то, что вернул Play — `AGENTS.md`, раздел «Data conventions»)
  - core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeBillingGateway.kt — фейк на `MutableStateFlow` с seed-методами по образцу `FakeCurrencyRepository.kt:9-45` (T1)
TEST_TYPES: unit
CONSTRAINTS:
  - **Граница домена — предмет этого SPEC, а не побочный эффект.** Ни один файл в `:core:domain` не должен импортировать `com.android.billingclient.*`. Это проверяется тестом, а не глазами: добавить unit-тест, который читает исходники пакета `core/domain/.../billing/` и падает на любом импорте, начинающем с `com.android.billingclient`.
  - `SupportProduct.formattedPrice` — строка от Play (`ProductDetails`), а не собранная в приложении из числа и знака валюты (O2). Хардкод «€1»/«€5» в домене запрещён.
  - Фейк — только `interface`-реализация на `StateFlow`, без MockK/Mockito (T2).
  - `:core:testing` также правится в SPEC-04 и SPEC-06 — этот первый.
  - `BillingAvailability.DisabledInBuild` нужен уже здесь: флаг `BILLING_ENABLED` из SPEC-03 отдаёт именно это состояние, и экран в SPEC-07 обязан его отличать от регионального.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Доменный контракт покупок поддержки

  Scenario: Домен не знает про Google
    Given исходники пакета core/domain/.../billing
    When тест границы сканирует их импорты
    Then ни одного импорта com.android.billingclient не найдено

  Scenario: Фейк проигрывает успешную покупку
    Given FakeBillingGateway, засеянный доступным биллингом и двумя товарами
    When вызывается purchase для coffee_small
    Then поток отдаёт Purchased с непустым purchaseToken

  Scenario: Фейк проигрывает отмену пользователем
    Given FakeBillingGateway, засеянный сценарием отмены
    When вызывается purchase
    Then поток отдаёт Cancelled
    And ошибка наверх не пробрасывается

  Scenario: Фейк различает недоступность на устройстве и в регионе
    Given FakeBillingGateway, засеянный UnavailableInRegion
    When читается availability
    Then состояние равно UnavailableInRegion, а не UnavailableOnDevice
```

## Gap / context
Модулей `:core:billing` и `:feature:support` на диске нет (G10), а вместе с ними нет и точки, о
которую можно опереть тесты. Контракт вводится первым и отдельно, чтобы всё, что идёт следом —
состояние поддержки, экран, шесть обязательных состояний, — тестировалось на фейке и не ждало
ни Play Console, ни устройства.

## Implementation links
- commit: 3ee42b7a (domain contract and fake)
- commit: efab650 (unit and boundary tests)
- files: core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/BillingAvailability.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/BillingGateway.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/PurchaseOutcome.kt, core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/SupportProduct.kt, core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeBillingGateway.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/billing/BillingContractsTest.kt, core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/billing/BillingSourceBoundaryTest.kt, core/testing/src/test/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeBillingGatewayTest.kt
