# Inline-подписка на Plus прямо на экране Support (переделка после feedback 1/5)

Epic: support-plus-inline-purchase
Order: 00 of 02 (overview)
Status: backlog
Depends-on: —
Date: 2026-08-23

## Goal
Исправление после провального feedback (1/5) по эпику `support-paywall-visual-polish` SPEC 02.
Пользователь не хотел отдельный экран Paywall («Support MyMoney With Plus») с кнопкой
«View Plus plans» — вместо этого кнопки Monthly/Yearly должны быть **прямо на экране Support**,
той же двухколоночной карточкой, что уже сделана (по образцу `CoffeePurchaseCard`), без перехода
на отдельный экран. Также: инфо-иконка "i" на карточке `MyMoney Plus` с кратким тултипом про
Supabase-бэкенд, и цвет back-row иконки/лейбла «Back» — та же проблема контраста, что была у
«Support the app» / «Help MyMoney», но её тогда пропустили.

## Locked decisions (grilled with user 2026-08-23)
- **Domain reuse**: логика каталога подписок/покупки/reconciliation-с-сервером (сейчас целиком в
  `PaywallViewModel`: `refreshCatalog()`/`loadCatalog()`/`purchase()`/`reconcileEntitlement()`)
  выносится в общий domain-слой координатор — по образцу уже существующего
  `SupportPurchaseReconciliationCoordinator` (`core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/supporter/SupportPurchaseReconciliationCoordinator.kt`,
  impl `core/sync/.../SupportPurchaseReconciliationCoordinatorImpl.kt`) — interface в `:core:domain`,
  impl (`@Singleton`) в `:core:sync`, тот же стиль конструктора (`BillingGateway` + `@IoDispatcher`).
  И `PaywallViewModel`, и новый слот-ViewModel на Support (см. ниже) используют этот координатор —
  **без дублирования** бизнес-логики.
- **Кто рендерит inline-карточку на Support**: **не** заводить логику покупки прямо в
  `SupportViewModel` (это раздуло бы его несвязанной ответственностью). Вместо этого — новый
  тонкий `SupportPlusViewModel` + composable `SupportPlusEntry`, **точно по образцу уже
  существующей пары `RewardedAdViewModel`/`RewardedAdSupportEntry`** (свой Hilt-`ViewModel` на
  bounded context, подключается через уже существующий слот `plusSlot` в `SupportContent`
  (`SupportScreen.kt:77,105`) и в `MyMoneyNavHost.kt:258-266`, ровно как `adSlot` подключает
  `RewardedAdSupportEntry`). `SupportPlusViewModel` потребляет `PlusSubscriptionCoordinator.state`
  и не содержит собственной catalog/purchase-логики — тонкий presentation-маппинг, как у
  `RewardedAdViewModel` поверх ad-координатора.
- **Экран Paywall не удаляется**: он всё ещё нужен для (a) shared-sync гейта
  (`PaywallEntryPoint.SharedSyncGate`, там benefits/free-forever/workspace-payer карточки,
  которых нет на Support) и (b) deep-link входа из `DecisionRouter`
  (`MyMoneyNavHost.kt:359-361`, `navController.navigate(Destinations.Paywall(PaywallEntryPoint.SupportSection))`
  при `openPaywall=true` — вероятно из push-уведомления/шортката, отдельный UX-момент вне экрана
  Support). **Оба** эти пути продолжают использовать `PaywallEntryPoint.SupportSection` —
  **enum-кейс не удаляется**. Убирается только composable `PaywallSupportEntry`
  (`PaywallScreen.kt:169-225`) и его подключение через `plusSlot` в `MyMoneyNavHost.kt:258-266` —
  на Support-экране `plusSlot` теперь рендерит `SupportPlusEntry()` вместо
  `PaywallSupportEntry(onOpenPaywall = ...)`, без навигации вообще.
- **Инфо-бабл**: текст — "Plus keeps a shared workspace in sync for your team or family." (лёгкая
  правка формулировки пользователя "Plus keeps your shared workspace for your team or family" —
  без упоминания Supabase по имени, пользователь сознательно убрал техническое название бэкенда
  из финального варианта). Использовать `TooltipBox`/`PlainTooltip` (Compose Material3 BoM
  2024.10+, `@OptIn(ExperimentalMaterial3Api::class)` уже используется в `PaywallScreen.kt`) —
  первое использование тултипа в проекте, паттерна для копирования нет.
- **Back-row контраст**: та же причина, что у `support_title`/`support_headline_lead` в
  `support-paywall-visual-polish-01` — иконка стрелки назад вообще без явного цвета (наследует
  произвольный `LocalContentColor`), лейбл «Back» использует приглушённый `supportBackLabel`
  (`onSurfaceVariant`) вместо контрастного `onBackground`, который уже применён к «Support the
  app» рядом. Выровнять оба под `onBackground`.
- **Уже подписан (entitled)**: карточка `MyMoney Plus` на Support-экране при активном Plus должна
  показывать статус (аналог `PlusStatusCard` с `PaywallScreen.kt:426-443`: `entitlement.statusRes`
  + дата продления/окончания), а не кнопки Monthly/Yearly — то же условие
  `state.entitlement.hasActivePlus()`, что уже есть в `PaywallContent`.
- Вне scope: любые правки carousel/coffee-карточки, gratitude-карточки, ad-блока (уже сделаны и
  приняты), сам `PaywallScreen.kt`/`PaywallViewModel` UI для shared-sync входа — там всё остаётся
  как есть, кроме внутреннего рефакторинга на общий координатор (behavior-preserving).

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-plus-inline-purchase-01-shared-subscription-coordinator.md` | — | domain, data | вынести catalog/purchase/reconciliation в общий `PlusSubscriptionCoordinator`, `PaywallViewModel` рефакторится на него (behavior-preserving) |
| 02 | `support-plus-inline-purchase-02-support-inline-plus-card.md` | 01 | presentation | новый `SupportPlusViewModel`/`SupportPlusEntry` (по образцу `RewardedAdViewModel`): inline Monthly/Yearly карточка + info-тултип + PlusStatusCard-аналог; + back-row контраст на `SupportScreen.kt`; убрать `PaywallSupportEntry`-вход в `plusSlot` |

## Why this ordering
SPEC 02 не может быть реализован без общего координатора из SPEC 01 (иначе снова придётся
дублировать subscription-flow логику в `SupportViewModel`, что и было явно отвергнуто при гриле).

## Key facts (verified)
- `PaywallViewModel.kt` — вся catalog/purchase/reconciliation логика: `refreshCatalog()` (:105),
  `loadCatalog()` (:141), `purchase()` (:169), `handlePurchaseOutcome()` (:197),
  `reconcileEntitlement()` (:218) — целиком мигрирует в координатор.
  `PaywallEvent.BackClicked`/`RetryClicked` остаются во ViewModel (навигационные, не доменные).
- `SupportViewModel.kt` уже инжектит `billingGateway`, `observeEntitlementUseCase` (nullable),
  `analyticsGateway`, `@IoDispatcher` — те же зависимости, что нужны новому координатору; уже
  использует однотипный координатор-паттерн (`supportPurchaseReconciliationCoordinator`) для
  кофе-покупок — прямой прецедент для повторения.
- `SupportPurchaseReconciliationCoordinator` (interface, `:core:domain/.../supporter/`) +
  `SupportPurchaseReconciliationCoordinatorImpl` (`:core:sync/.../supporter/`, `@Singleton`,
  конструктор `BillingGateway` + `SupporterRepository` + `@IoDispatcher`) — эталонный паттерн для
  нового координатора; DI-биндинг — `core/sync/.../di/SyncModule.kt`.
- `BillingGateway` interface — `core/domain/.../billing/BillingGateway.kt`; impl — `:core:billing`
  module (не подключается напрямую как project-dependency в `:core:sync`, DI биндится на уровне
  `:app`).
- `PlusStatusCard` (образец для entitled-состояния) — `PaywallScreen.kt:426-443`.
- `PaywallSupportEntry` (кнопка «View Plus plans», удаляемый вход) — `PaywallScreen.kt:169-225`;
  вызывающий код на Support-экране — искать в `SupportScreen.kt`/`SupportRoute.kt`/навигационном
  графе (`plusSlot` параметр `SupportContent`).
- Back-row — `SupportScreen.kt:110-140` (после уже смерженных правок SPEC `support-paywall-visual-polish-01`).
- Нет ни одного использования `TooltipBox`/`PlainTooltip` в проекте — первый прецедент, следовать
  официальному M3 API без кастомного popup.
