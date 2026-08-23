# Общий PlusSubscriptionCoordinator: вынести catalog/purchase/reconciliation из PaywallViewModel

Epic: support-plus-inline-purchase
Order: 01 of 02
Status: active
Depends-on: —
Date: 2026-08-23
Risk-signals: state_or_concurrency, cross_module
Acceptance-matrix: surface=catalog-loading,purchase-flow,entitlement-reconciliation,paywall-behavior-preserved

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Вынести catalog-loading/purchase/entitlement-reconciliation логику подписки Plus из
`PaywallViewModel` в новый общий singleton-координатор `PlusSubscriptionCoordinator`
(interface в `:core:domain`, impl в `:core:sync` — по образцу уже существующего
`SupportPurchaseReconciliationCoordinator`), чтобы её мог использовать не только `PaywallViewModel`,
но и новый `SupportPlusViewModel` (SPEC 02) без дублирования кода. Поведение экрана Paywall не
меняется — чистый behavior-preserving рефакторинг.
LAYERS: [domain] [data]
CHANGED_HINT:
  - Новый файл `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/billing/PlusSubscriptionCoordinator.kt` —
    interface по образцу `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupportPurchaseReconciliationCoordinator.kt`:
    `val state: StateFlow<PlusSubscriptionState>`, `suspend fun refreshCatalog()`,
    `suspend fun purchase(planId: PaywallPlanId): PlusPurchaseOutcome` (или аналог — важно, чтобы
    вызывающий `ViewModel` мог узнать, что покупка завершилась успехом и нужно запросить
    notification permission, как сейчас делает `PaywallViewModel.handlePurchaseOutcome` через
    `PaywallAction.RequestNotificationPermission`; способ сигнализации — на усмотрение реализации,
    но не через побочный `SharedFlow` на самом координаторе, который бы молча терялся, если ни
    один ViewModel не подписан в момент эмита — вызывающий `purchase()` корутины должен получить
    сигнал напрямую как часть завершения suspend-вызова или через собственный наблюдаемый `state`).
    `PlusSubscriptionState` переносит форму `PaywallState`/`PaywallCatalogState`/`PaywallPurchaseState`
    из `feature/support/.../paywall/PaywallState.kt` (catalog-состояние, purchase-состояние,
    entitlement) — presentation-слой (`PaywallViewModel`, будущий `SupportPlusViewModel`) не
    должен знать деталей billing-каталога/reconciliation, только состояние для рендера.
    **`PaywallPlanId` и `PaywallPlan` остаются presentation-типами** в
    `feature/support/.../paywall/PaywallState.kt` (не переезжают в domain) — SPEC 02 переиспользует
    существующие composable'ы `PaywallPlans`/`PaywallPlanColumn` (сейчас `private` в
    `PaywallScreen.kt:349-424`, станут `internal`) на новом Support-экране без дублирования кода, и
    им нужны эти два presentation-типа как параметры; координатор может возвращать цены как
    `Map<String productId, String formattedPrice>` или домен-версией плана — маппинг в
    `PaywallPlan`/`PaywallPlanId` остаётся на стороне ViewModel, как сейчас в `loadCatalog()`.
  - Новый файл `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/supporter/PlusSubscriptionCoordinatorImpl.kt`
    (или другой подходящий пакет в `:core:sync`) — `@Singleton`, конструктор в духе
    `SupportPurchaseReconciliationCoordinatorImpl.kt` (`BillingGateway` + `@IoDispatcher` +
    доступ к entitlement — через `ObserveEntitlementUseCase`/`EntitlementRepository`, смотреть, что
    уже доступно в `:core:sync`); переносит ровно логику из `PaywallViewModel.kt:105-263`
    (`refreshCatalog`/`loadCatalog`/`applyAvailability`/`purchase`/`handlePurchaseOutcome`/
    `reconcileEntitlement`/`completeReconciliationIfEntitled`/`refreshEntitlementForConfirmation`),
    включая мьютекс на конкурентный purchase (`purchaseMutex` в оригинале) и таймауты/задержки
    reconciliation (`ENTITLEMENT_RECONCILIATION_DELAYS_MILLIS`, `ENTITLEMENT_REFRESH_TIMEOUT_MILLIS`).
  - `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/di/SyncModule.kt` — добавить Hilt-биндинг
    нового координатора (по образцу существующего биндинга `SupportPurchaseReconciliationCoordinator`).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallViewModel.kt` —
    рефакторить на использование нового `PlusSubscriptionCoordinator` вместо прямых вызовов
    `billingGateway.querySubscriptions()`/`launchSubscriptionFlow()`/`observeEntitlement`
    (`ObserveEntitlementUseCase` в конструкторе `PaywallViewModel` может остаться, если ещё нужен
    для чего-то, кроме уже перенесённой в координатор логики, иначе убрать неиспользуемую
    зависимость). `onEvent`/навигационные события (`BackClicked`) и `PaywallAction`
    (`NavigateBack`, `RequestNotificationPermission`) остаются на месте — это presentation-слой,
    не переносится.
TEST_TYPES: unit
CONSTRAINTS:
  - **Поведение экрана Paywall не меняется ни на бит** — это чистый рефакторинг. Все существующие
    сценарии `PaywallViewModelTest.kt` (catalog states, purchase states, entitlement
    reconciliation, retry) должны продолжать проходить после адаптации под новую архитектуру
    (адаптация самих тестов — задача Tester'а, не Developer'а).
  - Не менять `PaywallScreen.kt` (presentation/UI) вообще — это SPEC только про domain/data слой.
  - Не менять `SupportViewModel.kt`/`SupportScreen.kt` — новый `SupportPlusViewModel` появится в
    SPEC 02, эта SPEC только готовит для него общий координатор.
  - Мьютекс на конкурентный `purchase()` обязателен (нельзя допустить одновременный вызов из
    Paywall и будущего Support-слота — координатор один на всё приложение через `@Singleton`).
  - Fakes only в тестах (project convention) — никаких моков.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Общий координатор подписки Plus

  Scenario: Paywall продолжает работать как раньше после рефакторинга
    Given каталог подписок доступен, координатор возвращает Monthly/Yearly с ценами
    When пользователь открывает экран Paywall
    Then он видит те же цены и состояния, что и до рефакторинга

  Scenario: Покупка через координатор завершается запросом notification permission
    Given координатор в состоянии "готов к покупке"
    When вызывается purchase(Yearly) и billing возвращает Purchased
    Then вызывающий ViewModel получает сигнал о необходимости запросить notification permission
    And entitlement реконсилируется с той же логикой retry/timeout, что и раньше

  Scenario: Конкурентные вызовы purchase() не пересекаются
    Given координатор уже выполняет purchase(Monthly)
    When второй вызывающий пытается purchase(Yearly) одновременно
    Then второй вызов не выполняется, пока первый не завершится (или явно отклоняется)
```

## Gap / context
Это SPEC 01 из эпика `support-plus-inline-purchase` — переделка после провального feedback (1/5)
по `support-paywall-visual-polish` SPEC 02: пользователь хотел кнопки Monthly/Yearly прямо на
экране Support, а не отдельный экран Paywall. Чтобы не дублировать subscription-flow логику в
`SupportViewModel`/новом `SupportPlusViewModel`, она сначала выносится в общий domain-координатор
(эта SPEC), затем SPEC 02 строит inline-карточку на Support поверх него.
