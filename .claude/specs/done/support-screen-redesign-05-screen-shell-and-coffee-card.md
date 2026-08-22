# Каркас экрана поддержки и плашка кофе
Epic: support-screen-redesign
Order: 05 of 07
Status: done
Depends-on: support-screen-redesign-01, support-screen-redesign-03
Date: 2026-08-20
Acceptance-matrix: billing=loading,available,pending,network_error,unavailable; purchase=idle,in_progress
Risk-signals: visual, navigation

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Экран поддержки переверстывается по макету: вместо M3 `TopAppBar` — кастомная строка
«‹ Назад» с заголовком экрана (D5), под ней hero-иллюстрация 196dp, крупный заголовок в две
строки с мятным подчёркнутым акцентным словом и подзаголовок. Карточка покупки кофе становится
двухколоночной плашкой (маленький / большой): иллюстрация, название, цена, кнопка «Поддержать»;
статусы биллинга (Loading / Pending / NetworkError / Unavailable) больше не подменяют плашку, а
показываются строкой ПОД ней (D7). Секция «Безопасные платежи» не реализуется (D3), отдельная
строка «Всего просмотрено рекламы» и слот `videosWatchedSlot` удаляются (D4). Порядок секций
задаётся новый — реклама, кофе, Plus, благодарность (D2); плашки рекламы и Plus в этом SPEC
остаются прежними слотами и переверстываются в SPEC-06/07.
LAYERS: presentation
CHANGED_HINT:
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:54-105 — заменить `Scaffold`+`TopAppBar` на кастомную строку возврата высотой `Spacing.supportTopBarHeight`, кнопка возврата не меньше `Spacing.supportBackTouchTarget` (48dp); новый порядок слотов: adSlot → плашка кофе → plusSlot → благодарность; `videosWatchedSlot` удалить из сигнатуры (G5, G6, D2, D4, D5)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt — новые приватные composable: hero-иллюстрация (`R.drawable.support_neon_hero_cup`, `Shapes.supportHeroIllustration`, `Spacing.supportHeroSize`) и заголовок с акцентной второй строкой (подчёркивание — `drawBehind`/`Box` толщиной `Spacing.supportHeadlineUnderlineThickness`, НЕ `TextDecoration.Underline`) (SPEC-01, SPEC-03)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:152-188 — `CoffeePurchaseCard` переписать в двухколоночный `Row` внутри `Surface(shape = Shapes.supportPanel, border = supportPanelOutline)`: колонки разделены вертикальной линией `supportPanelDivider`, в каждой — иллюстрация (`support_neon_coffee_small` 66×82dp / `support_neon_coffee_large` 82dp), название из новых строк, цена `product.formattedPrice` стилем `supportProductPrice`, кнопка `support_purchase_action` формой `Shapes.supportPrimaryAction` высотой `Spacing.supportActionMinHeight`, `enabled = !isPurchaseInProgress` (G8, G10)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:107-150 — `SupportPurchaseSection` перестроить: плашка кофе рендерится ВСЕГДА, а `billingState` даёт строку-статус под ней (Loading — без строки; Pending/NetworkError/Unavailable — соответствующее сообщение, у NetworkError сохраняется `TextButton` «Повторить» → `SupportEvent.RetryClicked`) (G7, D7)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:254-268 — `coffeeLabel(product)` заменить на маппинг productId → название без цены (`support_coffee_small_name` / `support_coffee_large_name` / `support_coffee_generic_name`); цена выводится отдельным Text (G10)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportRoute.kt:14-20,36-43 — убрать параметр `videosWatchedSlot` из `SupportRoute` и из вызова `SupportContent` (D4)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:248-266 — убрать `videosWatchedSlot = { TotalAdsWatchedBadge() }` из вызова `SupportRoute`; `adSlot`/`plusSlot` оставить (G2, G3)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:74-89 — удалить `TotalAdsWatchedBadge`/`TotalAdsWatchedBadgeContent` вместе с их единственным использованием (G11, D4)
  - feature/support/src/main/res/values/strings.xml + values-ru/strings.xml — вывести из употребления `support_coffee_title`, `support_coffee_small`, `support_coffee_large`, `support_coffee_price`, `support_ads_total_watched` (после того как их перестали читать); `support_title`/`support_back` сохранить (D5)
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt:34-263 — переписать под новую вёрстку: заголовок, hero (по contentDescription), обе колонки кофе, статус-строка под плашкой для каждого состояния биллинга, блокировка кнопок при `isPurchaseInProgress` (G27)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Никаких сырых dp/цветов/строк в composable: только токены из SPEC-03 и `stringResource` (G19, G23).
  - Логика ViewModel и события не меняются: `SupportEvent.BackClicked / PurchaseClicked / RetryClicked`
    и `SupportAction.NavigateBack` остаются как есть (G15).
  - Плашка кофе не должна исчезать ни в одном состоянии биллинга (D7) — тест на это обязателен для
    каждого из четырёх состояний.
  - Кнопка возврата: `contentDescription` из `support_back`, видимая метка из `support_back_label`,
    цель касания ≥48dp (H4). Заголовок экрана (`support_title`) остаётся в вёрстке (D5) — тесты
    Robolectric ищут его по тексту (G27).
  - `SupportScreenContentTest` переписывается, а НЕ ослабляется: `@Ignore`, удаление проверок и
    замена ассертов на `assertExists` без содержания запрещены. Robolectric читает реальные
    ресурсы (G29) — строки в тестах брать из `R.string`, не литералами.
  - Clash по файлам: `SupportScreen.kt` также правит SPEC-07, `RewardedAdScreen.kt` — SPEC-06,
    `strings.xml`/`values-ru/strings.xml` — SPEC-03 и SPEC-07. Порядок 03 → 05 → 06 → 07
    обязателен, параллельный запуск этих SPEC запрещён.
  - Плашки рекламы и Plus в этом SPEC не переверстываются: они приходят слотами `adSlot`/`plusSlot`
    и меняются в SPEC-06/07. Порядок вызова слотов, однако, задаётся здесь (D2).
  - Визуальная работа → обязателен visual device gate: приёмка на Pixel 5 API 34 (AGENTS.md).
    Roborazzi для `feature/support` в этом эпике не заводится (O2, G30).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Каркас экрана поддержки и плашка кофе

  Scenario: Экран открывается в новой раскладке
    Given пользователь открыл экран поддержки
    Then сверху отображается строка возврата с заголовком экрана
    And под ней hero-иллюстрация и заголовок с акцентным словом
    And секции про безопасные платежи на экране нет
    And отдельной строки со счётчиком просмотренных роликов на экране нет

  Scenario: Плашка кофе показывает оба продукта
    Given биллинг доступен и вернул маленький и большой кофе
    Then плашка показывает две колонки с названиями, ценами и кнопками поддержки

  Scenario: Ошибка сети не убирает плашку кофе
    Given биллинг вернул сетевую ошибку
    Then плашка кофе остаётся видимой
    And под ней отображается сообщение об ошибке с кнопкой повтора
    When пользователь нажимает повтор
    Then экран отправляет событие повторной попытки

  Scenario: Недоступный биллинг объясняется под плашкой
    Given покупки недоступны в регионе
    Then плашка кофе остаётся видимой
    And под ней отображается причина недоступности

  Scenario: Во время покупки кнопки заблокированы
    Given покупка выполняется
    Then обе кнопки поддержки недоступны для нажатия

  Scenario: Возврат назад работает
    Given пользователь на экране поддержки
    When он нажимает кнопку возврата
    Then экран отправляет событие возврата
```

## Источник дизайна
`docs/design/support-screen-1A.html` — размеры, отступы и цвета брать оттуда; иллюстрации
в макете — заглушки, реальные ассеты приходят из SPEC-01.

## Gap / context
Текущий экран — вертикальный стек M3-карточек с заголовком «Buy me a coffee» и кнопками-строками
(G6, G8), который расходится с утверждённым макетом по всем ключевым элементам: топбар,
hero, заголовок, двухколоночная плашка, поведение статусов. Заблокировано D2/D3/D4/D5/D7.

## Implementation links
- commits: 604a488c, 69fa41b1, fcc072e3, 19168b13
- files: app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt; feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportRoute.kt; feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt; feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt; feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt; feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreenContentTest.kt
