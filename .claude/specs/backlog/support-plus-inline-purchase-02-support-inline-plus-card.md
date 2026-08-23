# Inline Monthly/Yearly карточка Plus на экране Support + инфо-тултип + back-row контраст

Epic: support-plus-inline-purchase
Order: 02 of 02
Status: backlog
Depends-on: support-plus-inline-purchase-01-shared-subscription-coordinator.md
Date: 2026-08-23
Risk-signals: visual, state_or_concurrency
Acceptance-matrix: surface=inline-purchase-card,entitled-status,info-tooltip,back-row-contrast,navigation-removed

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На экране Support карточка «MyMoney Plus» вместо кнопки «View Plus plans» (переход на
отдельный экран) показывает прямо на месте двухколоночную карточку Monthly/Yearly (переиспользуя
уже готовый `PaywallPlans`/`PaywallPlanColumn`), инфо-иконку "i" с тултипом про Supabase-бэкенд, и
статус подписки вместо кнопок, если Plus уже активен. Back-row иконка стрелки и лейбл «Back»
получают тот же контрастный `onBackground`, что уже есть у «Support the app».
LAYERS: [presentation]
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt` —
    сделать `PaywallPlans` (:350), `PaywallPlanColumn` (внутренняя, вызывается из `PaywallPlans`),
    `planSelectHandler`, `PlusStatusCard` (:487) `internal` вместо `private`, чтобы их мог
    переиспользовать новый Support-composable в том же модуле `:feature:support` — саму логику/
    визуал этих composable не менять.
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt:180-233` —
    удалить `PaywallSupportEntry` (composable для входа с Support, кнопка «View Plus plans») —
    он больше никем не вызывается после этой SPEC (см. правку `MyMoneyNavHost.kt` ниже). Не
    трогать `PaywallRoute`/`PaywallScreen`/`PaywallContent`/остальные composable — они всё ещё
    нужны для `PaywallEntryPoint.SharedSyncGate` (CloudSync) и deep-link входа из `DecisionRouter`
    (`MyMoneyNavHost.kt:359-361`, `PaywallEntryPoint.SupportSection` из `openPaywall=true` —
    **не удалять** сам enum-кейс `SupportSection`, он там используется).
  - Новый файл `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/plus/SupportPlusViewModel.kt` —
    `@HiltViewModel`, по образцу `RewardedAdViewModel.kt` (тонкий presentation-маппинг поверх
    domain-состояния, без собственной billing-логики): инжектит новый
    `PlusSubscriptionCoordinator` (из SPEC 01) + `AnalyticsGateway`, экспонирует
    `StateFlow<SupportPlusState>` (переиспользовать форму `PaywallCatalogState`/`PaywallPlan`/
    `PaywallPurchaseState` из `paywall/PaywallState.kt` — не создавать параллельные типы), методы
    `onPlanSelected(planId: PaywallPlanId)` (делегирует `coordinator.purchase(planId)`) и
    `onRetryClicked()` (делегирует `coordinator.refreshCatalog()`). Если покупка требует запроса
    notification permission (см. `PlusSubscriptionCoordinator.purchase()` из SPEC 01) —
    прокинуть это дальше как one-shot action, аналогично `PaywallAction.RequestNotificationPermission`
    (переиспользовать существующий `PaywallAction`/`CollectActions`-паттерн из
    `feature/support/paywall/PaywallAction.kt` вместо нового типа, если это не создаёт
    presentation↔presentation зависимость между независимыми фичами — иначе завести свой минимальный
    sealed action).
  - Новый файл `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/plus/SupportPlusEntry.kt` —
    composable по образцу `RewardedAdSupportEntry`/`RewardedAdContent`
    (`feature/support/rewardedad/RewardedAdScreen.kt:57-177`): получает `SupportPlusViewModel = hiltViewModel()`,
    рендерит карточку «MyMoney Plus» — тот же контейнер/токены, что были у `PaywallSupportEntry`
    (`Surface` с `supportPanel`/`supportPanelContainer`/`supportPanelOutline`, картинка
    `support_neon_plus` + заголовок `paywall_support_entry_title` + описание
    `paywall_support_entry_description`), плюс:
    - инфо-иконка "i" (`Icons.Outlined.Info` или `Icons.AutoMirrored...` — уточнить, что доступно
      без нового icon-набора) в правом верхнем углу карточки (`Box` + `Modifier.align(Alignment.TopEnd)`
      вокруг существующего `Column`), обёрнутая в `TooltipBox`/`PlainTooltip`
      (`@OptIn(ExperimentalMaterial3Api::class)`, Compose Material3 BoM 2024.10+ — первое
      использование тултипа в проекте, паттерна для копирования нет, следовать официальному M3 API
      без кастомного popup) с текстом нового строкового ресурса `support_plus_info_tooltip` =
      "Plus keeps a shared workspace in sync for your team or family." (EN) + RU-перевод.
    - если `state.entitlement` имеет активный Plus — под заголовком/описанием рендерить
      переиспользуемый `PlusStatusCard(entitlement)` вместо кнопок (то же условие
      `hasActivePlus()`, что и в `PaywallContent`).
    - иначе — переиспользуемый `PaywallPlans(plans = state.plans, purchaseState = state.purchaseState,
      onEvent = { event -> when (event) { is PaywallEvent.PlanSelected -> viewModel.onPlanSelected(event.planId);
      PaywallEvent.RetryClicked -> viewModel.onRetryClicked(); PaywallEvent.BackClicked -> Unit } })`
      — та же двухколоночная карточка Monthly/Yearly, что уже принята пользователем визуально на
      Paywall-экране, теперь встроенная прямо в Support.
    - `PaywallCatalogState.Loading`/`UnavailableInRegion`/`Unavailable`/`Error` — то же поведение
      (спиннер/сообщения), что в `PaywallContent`/`PaywallCatalog` (`PaywallScreen.kt:279-347`) —
      переиспользовать эти composable (сделать `internal`, если нужно) вместо копирования.
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:110-140`
    (`SupportBackRow`) — у `Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, ...)` добавить
    `tint = MaterialTheme.colorScheme.onBackground`; у `Text(stringResource(R.string.support_back_label))`
    заменить `color = MaterialTheme.colorScheme.supportBackLabel` на
    `color = MaterialTheme.colorScheme.onBackground` (тот же токен, что уже применён к
    `support_title` рядом, для визуальной консистентности back-row).
  - `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:258-266` — заменить
    `plusSlot = { PaywallSupportEntry(onOpenPaywall = { navController.navigate(...) }) }` на
    `plusSlot = { com.kshavrin.mymoney.feature.support.plus.SupportPlusEntry() }` — без навигации,
    без `onOpenPaywall`.
  - `feature/support/src/main/res/values/strings.xml` и `values-ru/strings.xml` — добавить
    `support_plus_info_tooltip`; если `paywall_support_entry_action` (текст кнопки «View Plus
    plans») становится нигде не используемым после удаления `PaywallSupportEntry` — убрать его
    (проверить остальные ссылки перед удалением).
TEST_TYPES: [compose-ui, visual-device]
CONSTRAINTS:
  - `supportBackLabel` ColorScheme-токен (`onSurfaceVariant`) может остаться неиспользуемым нигде
    больше в проекте после этой правки — не удалять сам токен (может использоваться где-то ещё,
    grep перед удалением; если действительно не используется нигде — можно убрать как часть
    неиспользуемого кода, но это не обязательное условие приёмки).
  - `PaywallEntryPoint.SupportSection` enum-кейс **не удаляется** — используется deep-link входом
    из `DecisionRouter`.
  - `PaywallScreen.kt`/`PaywallRoute`/`PaywallContent`/`PaywallCatalog`/каталог-состояния для
    `PaywallEntryPoint.SharedSyncGate` — поведение не меняется, кроме перевода нескольких
    composable в `internal` (без изменения тела).
  - Двухколоночная карточка на Support обязана использовать переиспользованный `PaywallPlans` —
    не копировать её код повторно.
  - Не удалять файлы физически (project-wide archive-only policy) — `PaywallSupportEntry`
    убирается как правка существующего файла `PaywallScreen.kt`, не удаление файла.
  - Это visual-surface изменение: пройти Pixel 5/API 34 device gate и визуально сверить inline-
    карточку (обе колонки, инфо-тултип, back-row контраст, entitled-статус если применимо) на
    устройстве до подтверждения верификации (AGENTS.md "Visual-change device gate").
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Inline-подписка Plus на экране Support

  Scenario: Карточка MyMoney Plus показывает кнопки прямо на месте
    Given открыт экран Support, каталог подписок доступен, пользователь не подписан
    When экран отрисован
    Then карточка "MyMoney Plus" показывает Monthly и Yearly колонки с ценами и кнопками
    And нет перехода на отдельный экран "Support MyMoney With Plus"

  Scenario: Инфо-тултип объясняет назначение Plus
    Given открыт экран Support
    When пользователь нажимает иконку "i" на карточке MyMoney Plus
    Then появляется бабл с текстом про синхронизацию shared workspace для команды/семьи

  Scenario: Уже активный Plus показывает статус вместо кнопок
    Given у пользователя активна подписка Plus
    When открыт экран Support
    Then карточка MyMoney Plus показывает статус/дату продления вместо кнопок Monthly/Yearly

  Scenario: Back-row контрастен фону
    Given открыт экран Support
    When экран отрисован
    Then иконка стрелки назад и текст "Back" используют тот же контрастный цвет, что "Support the app"

  Scenario: Shared-sync вход в Paywall не затронут
    Given пользователь пытается подключиться к общему workspace без Plus
    When открывается экран Paywall через shared-sync гейт
    Then он видит прежний полный экран с benefits/free-forever/workspace-payer карточками
```

## Gap / context
Прямое продолжение SPEC 01 — использует новый `PlusSubscriptionCoordinator` для inline-карточки
на Support. Переделка после провального feedback (1/5): пользователь явно отверг отдельный экран
Paywall для входа с Support, попросил кнопки прямо на месте (как у карточки кофе), инфо-бабл про
Supabase-доступ, и достроить цветовой фикс back-row, пропущенный в предыдущей SPEC.
