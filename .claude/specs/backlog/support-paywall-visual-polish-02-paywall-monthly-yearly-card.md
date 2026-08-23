# Двухколоночная карточка тарифов Paywall (месяц слева / год справа)

Epic: support-paywall-visual-polish
Order: 02 of 02
Status: backlog
Depends-on: —
Date: 2026-08-23
Risk-signals: visual
Acceptance-matrix: surface=layout,catalog-state=loading,available,unavailable-region,unavailable,error

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Заменить вертикальный список тарифов Paywall (`PaywallPlans`/`PaywallPlanCard`) на одну
двухколоночную карточку — Monthly слева, Yearly справа — визуально идентичную
`CoffeePurchaseCard` (тот же контейнер, делитель, по кнопке в каждой колонке); поведение выбора
плана и вся `PaywallPurchaseState`-логика под карточкой не меняются.
LAYERS: [presentation]
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt:339-386` —
    заменить `PaywallPlans` (сейчас: `Column` из `plan.forEach { PaywallPlanCard(...) }`) на новую
    `PaywallPlanCard`-по-образцу-кофе функцию: один `Surface` с
    `shape = MaterialTheme.shapes.supportPanel`, `color = MaterialTheme.colorScheme.supportPanelContainer`,
    `contentColor = MaterialTheme.colorScheme.onSurface`,
    `border = BorderStroke(1.dp, MaterialTheme.colorScheme.supportPanelOutline)` — по образцу
    `CoffeePurchaseCard` (`SupportScreen.kt:243-249`); внутри `Row` с
    `horizontalArrangement = Arrangement.spacedBy(Spacing.supportPanelColumnGap)`,
    `verticalAlignment = Alignment.CenterVertically`, `height(IntrinsicSize.Min)`, двумя колонками
    `weight(1f)` (Monthly — `plans[0]`/`plans.first { it.id == PaywallPlanId.Monthly }`, Yearly —
    аналогично для `PaywallPlanId.Yearly`) и вертикальным `Box`-делителем шириной `1.dp`,
    `fillMaxHeight()`, `background(MaterialTheme.colorScheme.supportPanelDivider)` между ними — по
    образцу `SupportScreen.kt:274-280` (G1, G2).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt:388-424` —
    заменить `PaywallPlanCard` на колоночную функцию по образцу `CoffeeProductColumn`
    (`SupportScreen.kt:298-348`): `Icon(Icons.Outlined.CreditCard, contentDescription = ...)`
    сверху (вместо `Image`-иллюстрации — готовых neon-ассетов для monthly/yearly нет), затем
    `Text(plan.id.titleRes)` (стиль `supportProductName`), `Text(plan.formattedPrice ?: fallback)`
    (стиль `supportProductPrice`, цвет `supportPriceValue`), при `PaywallPlanId.Yearly` —
    `Text(paywall_yearly_trial)` под ценой, затем `Button` во всю ширину колонки
    (`heightIn(min = Spacing.supportActionMinHeight)`, `shape = supportPrimaryAction`) с текстом
    `paywall_select_plan`, `enabled`/`onClick` — как у текущего `onSelect` (G1, G3, G4).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt` —
    4 существующих вызова `PaywallPlans(plans = ..., ...)` (в состояниях Available/
    UnavailableInRegion/Unavailable/Error, строки ~302-332) обновить на вызов новой
    двухколоночной функции с теми же параметрами (`plans`, `purchaseState`, `onEvent`) — логика
    disabled-кнопок при `onEvent == null` (для error/unavailable-состояний) не меняется (G5).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt` —
    если после правки `PaywallCard`/старый `PaywallPlanCard` остаются нигде не используемыми в
    этом файле — убрать неиспользуемый код; `PaywallCard` используется и другими карточками
    (`PaywallBenefitsCard`, `FreeForeverCard` и т.д.) — не трогать (G6).
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreenContentTest.kt` —
    обновить существующие проверки layout/выбора плана под новую двухколоночную структуру без
    изменения проверяемого поведения (какие планы кликабельны в каких `PaywallCatalogState`,
    какая цена/trial-подпись показана) (G7).
TEST_TYPES: [compose-ui, visual-device]
CONSTRAINTS:
  - Не менять `PaywallState`/`PaywallViewModel`/`PaywallEvent`, `PaywallCatalogState`-логику,
    строки `paywall_select_plan`/`paywall_yearly_trial`/цены/fallback-тексты, порядок и состав
    планов (`PaywallPlanId.entries`), навигацию, остальные карточки экрана (`PaywallBenefitsCard`,
    `FreeForeverCard`, `WorkspacePayerCard`, `PlusStatusCard`, `PaywallMessageCard`) — только
    layout блока выбора тарифа (G1-G6).
  - Обе колонки обязаны использовать одинаковые токены-контейнера/делителя, что и
    `CoffeePurchaseCard`/`CoffeeProductColumn` (`supportPanel`/`supportPanelContainer`/
    `supportPanelOutline`/`supportPanelDivider`) — не вводить новые цвета/shape/hex (G1, G2).
  - Двухколоночная карточка обязана отображаться во всех 4 состояниях каталога, где сейчас
    вызывается `PaywallPlans` (Available/UnavailableInRegion/Unavailable/Error), включая
    non-interactive режим (`onEvent == null` → обе кнопки `enabled = false` или без `onClick`,
    как сейчас) (G5).
  - Не удалять файлы физически (project-wide archive-only policy).
  - Это visual-surface изменение: пройти Pixel 5/API 34 device gate и визуально сверить
    двухколоночную карточку (обе колонки, делитель, кнопки) на устройстве до подтверждения
    верификации (G8, AGENTS.md "Visual-change device gate").
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Двухколоночная карточка тарифов Paywall

  Scenario: Тарифы отображаются в одной карточке, месяц слева, год справа
    Given открыт экран Paywall, каталог тарифов доступен
    When экран отрисован
    Then в одной карточке слева отображается тариф Monthly, справа — Yearly
    And между колонками есть вертикальный делитель того же токена, что в CoffeePurchaseCard

  Scenario: У каждого тарифа своя кнопка выбора
    Given открыт экран Paywall, каталог тарифов доступен, покупка не идёт
    When пользователь нажимает кнопку в колонке Yearly
    Then срабатывает PaywallEvent.PlanSelected(Yearly.productId)
    And кнопка колонки Monthly остаётся кликабельной независимо

  Scenario: Карточка остаётся не интерактивной при недоступном каталоге
    Given открыт экран Paywall в состоянии PaywallCatalogState.Unavailable
    When экран отрисован
    Then обе кнопки карточки не кликабельны
    And сообщение об недоступности остаётся отображённым как сейчас
```

## Gap / context
Сейчас `PaywallPlans` рендерит тарифы вертикальным списком отдельных карточек
(`PaywallPlanCard`, каждая — свой `Surface` с shape `supportCard`/цветом `surfaceVariant`).
Пользователь просит переструктурировать это в одну карточку с двумя колонками (месяц/год),
визуально и по логике идентичную существующей `CoffeePurchaseCard` на экране Support —
единый контейнер, `weight(1f)`-колонки, вертикальный делитель, по кнопке в каждой колонке.
Domain-модель (`PaywallPlanId` всегда ровно Monthly+Yearly) уже подходит под жёстко
двухколоночный layout без дополнительной логики.
