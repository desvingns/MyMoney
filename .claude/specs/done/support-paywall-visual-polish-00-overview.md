# Мелкая визуальная полировка Support + двухколоночная карточка Paywall

Epic: support-paywall-visual-polish
Order: 00 of 02 (overview)
Status: done
Depends-on: —
Date: 2026-08-23
Completed: 2026-08-23 — both SPECs shipped, verified on Pixel 5/API 34, epic closed

## Goal
Четыре точечные визуальные правки на экранах Support и Paywall (`feature/support`):
объединение строк рекламного блока в одну жирную строку, удаление текстового счётчика
просмотров (графический счётчик из сегментов остаётся), контрастный цвет шрифта у
«Support the app» / «Help MyMoney», и перестройка блока выбора тарифа Paywall в
двухколоночную карточку (месяц слева / год справа), визуально идентичную
`CoffeePurchaseCard`. Вся работа — presentation-слой, без изменений domain/data/billing-логики.

## Locked decisions
- Рекламный блок (`RewardedAdContent`): убрать строку `support_ads_title` ("Watch ads for
  temporary Plus"), оставить только `support_ads_rule` ("Watch %d ads to unlock 24 hours of
  Plus."), присвоить ей стиль `MaterialTheme.typography.supportPanelTitle` (тот же bold-вес,
  что был у убираемой строки).
- Счётчик просмотров (`RewardProgressRow`): удалить видимый `Text(progressText)`; accessibility
  `contentDescription = progressText` на родительском `Column` (уже есть через
  `clearAndSetSemantics`) не трогать — графический ряд сегментов остаётся единственным
  визуальным индикатором.
- Цвет шрифта: `support_title` («Support the app», back-row) и `support_headline_lead`
  («Help MyMoney») получают явный `color = MaterialTheme.colorScheme.onBackground` — та же
  конвенция полноэкранного текста на themed-фоне, что уже используется в `OnboardingScreen`
  (`OnboardingScreen.kt:158,165`). Без этого текст наследует произвольный `LocalContentColor` и
  сливается с фоном, добавленным в SPEC `support-screen-visual-polish-01`.
- Карточка тарифа Paywall: `PaywallPlans`/`PaywallPlanCard` заменяются на двухколоночную
  карточку **визуально идентичную `CoffeePurchaseCard`** — один `Surface` с
  `shape = MaterialTheme.shapes.supportPanel`, `color = supportPanelContainer`,
  `border = supportPanelOutline` (вместо текущего `supportCard`/`surfaceVariant`), `Row` из двух
  колонок с `weight(1f)` (Monthly слева, Yearly справа), вертикальный `Box`-делитель
  `supportPanelDivider` между ними — по образцу `CoffeePurchaseCard`/`CoffeeProductColumn`
  (`SupportScreen.kt:243-296`, `298-348`). Иконка `Icons.Outlined.CreditCard` остаётся сверху
  каждой колонки (готовых neon-иллюстраций для monthly/yearly нет). Кнопка выбора плана,
  текст цены/trial-подписи и вся `PaywallPurchaseState`-логика под карточкой не меняются.
- Вне scope: тексты/копирайтинг (кроме удаляемой строки `support_ads_title`), billing/paywall
  ViewModel-логика, состояние `PaywallCatalogState`, навигация, все остальные блоки экранов
  Support/Paywall.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-paywall-visual-polish-01-rewarded-ad-and-headline.md` | — | presentation | объединение строк рекламного блока + удаление текстового счётчика + контрастный цвет заголовков |
| 02 | `support-paywall-visual-polish-02-paywall-monthly-yearly-card.md` | — | presentation | двухколоночная карточка Paywall (месяц/год) по образцу CoffeePurchaseCard |

## Why this ordering
Два независимо поставляемых среза: SPEC 01 — три точечные правки в `feature/support` (два файла
экрана Support), SPEC 02 — отдельный экран Paywall с более заметной структурной перестройкой.
Пересечений по файлам нет (`RewardedAdScreen.kt`+`SupportScreen.kt` vs `PaywallScreen.kt`),
зависимостей друг от друга нет — порядок произвольный, оставлен по возрастанию сложности.

## Key facts (verified)
- `support_ads_title`/`support_ads_rule` — `RewardedAdScreen.kt:154-161`; `support_ads_progress`
  видимый Text — `RewardedAdScreen.kt:191-194`, семантика на родительском `Column` —
  `RewardedAdScreen.kt:188`.
- `support_title` без цвета — `SupportScreen.kt:135-138`; `support_headline_lead` без цвета —
  `SupportScreen.kt:151-155`.
- Конвенция `onBackground` на themed full-screen фоне — `OnboardingScreen.kt:86,158,165`.
- `CoffeePurchaseCard`/`CoffeeProductColumn` — эталонный двухколоночный паттерн,
  `SupportScreen.kt:234-348`.
- `PaywallPlans`/`PaywallPlanCard`/`PaywallCard` — текущая вертикальная реализация,
  `PaywallScreen.kt:339-424`, `462-480`. `PaywallPlanId` всегда ровно 2 значения
  (Monthly, Yearly) — `PaywallState.kt:6-11,20`.
- Существующее покрытие ссылается на `support_ads_title` как видимый текст
  (`RewardedAdScreenContentTest.kt:62`) и на `support_ads_progress` только через
  `onNodeWithContentDescription` (не как видимый текст) — Tester должен обновить строку 62 по
  Stale-Test Update Rule, остальные a11y-проверки прогресса не затронуты.
