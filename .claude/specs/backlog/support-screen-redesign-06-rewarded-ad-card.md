# Плашка рекламы в стилистике плашки кофе
Epic: support-screen-redesign
Order: 06 of 07
Status: backlog
Depends-on: support-screen-redesign-01, support-screen-redesign-03, support-screen-redesign-05
Date: 2026-08-20
Acceptance-matrix: ad_status=loading,unauthenticated,ready,no_fill,region_unavailable,offline,awaiting_confirmation,confirmation_timeout,rearming,unavailable; plus=active,inactive
Risk-signals: visual, ads

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Rewarded-блок перерисовывается в ту же плашку, что и кофе из SPEC-05: `Surface` формы
`Shapes.supportPanel` с мятной обводкой, внутри — иллюстрация `support_neon_ads`, заголовок,
детерминированный прогресс-бар «просмотрено N из M» и одна основная кнопка. Все десять состояний
`RewardedAdStatus` (G16) остаются ВНУТРИ плашки строкой под кнопкой (D6); для `Unauthenticated`
основная кнопка превращается в «Войти» и по-прежнему открывает `GoogleSignInDialog`. Логика
`RewardedAdViewModel` и `AdGateway` не трогается — SPEC чисто вёрсточный.
LAYERS: presentation
CHANGED_HINT:
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:92-130 — `RewardedAdContent`: заменить `Surface(shape = supportCard, color = secondaryContainer)` на плашку `Shapes.supportPanel` + `supportPanelContainer` + `BorderStroke(1.dp, supportPanelOutline)`, добавить иллюстрацию `R.drawable.support_neon_ads` размером `Spacing.supportPanelIconSize` с contentDescription `support_image_ads_description` (G12, SPEC-01, SPEC-03)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:118-128 — вместо ветвления на три разных «тела» (Loading / Unauthenticated / Authenticated) собрать единый каркас плашки: заголовок + правило + прогресс + одна кнопка + строка статуса; ветвление оставить только на содержимое кнопки и текст статуса (D6)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt — прогресс-бар: детерминированный `LinearProgressIndicator` с `rewardAdProgressIndicator`/`rewardAdProgressTrack` (G38), значение `reward.progress / reward.required`; при `reward == null` показывать прогресс нулевым, а не скрывать плашку
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:151-165 — `UnauthenticatedBody` схлопнуть: та же основная кнопка, но с текстом `support_ads_sign_in_action` и текстом-пояснением `support_ads_sign_in_required` в строке статуса (D6)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:167-200 — `AuthenticatedBody`: сообщения `no_fill` / `region_unavailable` / `offline` / `awaiting_confirmation` / `confirmation_timeout` / `unavailable` / `plus_active` вывести в единую строку статуса под кнопкой; кнопка «Смотреть» доступна только в `Ready`, повтор — в состояниях, где он есть сейчас (G16)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:44-71 — `RewardedAdSupportEntry` (точка входа со слота `adSlot`) не меняет сигнатуру и логику показа `GoogleSignInDialog` (G12, G3)
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreenContentTest.kt — переписать под единый каркас: по одному сценарию на каждый статус (видимость прогресса, текст кнопки, текст статуса), тесты на доступность кнопки в `Ready` и её недоступность в `NoFill` (G28)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - `RewardedAdViewModel`, `AdGateway`, `ObserveAdRewardStateUseCase` и `RewardedAdState` НЕ менять
    (G16, G36): SPEC меняет только вёрстку. Новых статусов не вводить, существующие не схлопывать.
  - Плашка рекламы видима всегда, включая `Loading` — она первая на экране (D2), и её исчезновение
    вызывает скачок раскладки. Индикатор загрузки живёт внутри плашки.
  - Существующие строки `support_ads_*` не переименовывать: их паритет EN/RU и содержание
    проверяет `PaywallStringsTest` (G37), включая запрет слов credited/charged в
    `support_ads_awaiting_confirmation`.
  - `TotalAdsWatchedBadge` уже удалён в SPEC-05 (D4) — не воскрешать, счётчик роликов показывает
    карточка благодарности из SPEC-07.
  - Сырые dp/цвета запрещены: цвета прогресс-бара берутся из существующих алиасов (G38), остальное —
    из токенов SPEC-03 (G19).
  - Кнопка — высотой не меньше `Spacing.supportActionMinHeight` (48dp), форма
    `Shapes.supportPrimaryAction` — визуально идентична кнопкам плашки кофе (H4).
  - `RewardedAdScreenContentTest` переписывается, а не ослабляется: `@Ignore` и удаление проверок
    статусов запрещены.
  - Clash: `RewardedAdScreen.kt` уже правил SPEC-05 (удаление `TotalAdsWatchedBadge`) —
    этот SPEC запускать строго после него.
  - Визуальная работа → visual device gate на Pixel 5 API 34 (AGENTS.md).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Плашка рекламы на экране поддержки

  Scenario: Плашка выглядит как плашка кофе
    Given экран поддержки открыт
    Then плашка рекламы имеет ту же форму, обводку и фон, что и плашка кофе
    And в ней отображается иллюстрация с описанием для скринридера

  Scenario: Прогресс показывает путь к награде
    Given пользователь просмотрел два ролика из пяти
    Then в плашке отображается прогресс два из пяти

  Scenario: Неавторизованный пользователь видит вход
    Given пользователь не вошёл в аккаунт
    Then основная кнопка плашки предлагает войти
    And под кнопкой поясняется, зачем нужен вход
    When пользователь нажимает кнопку
    Then открывается диалог входа

  Scenario: Реклама готова к показу
    Given рекламный блок загружен
    Then основная кнопка предлагает посмотреть ролик и доступна для нажатия

  Scenario: Реклама недоступна
    Given подходящей рекламы сейчас нет
    Then основная кнопка недоступна
    And под ней отображается объяснение, что рекламы нет

  Scenario: Плашка не исчезает во время загрузки
    Given состояние рекламного блока ещё загружается
    Then плашка остаётся на экране и показывает индикатор загрузки внутри себя

  Scenario: Активный Plus объясняется внутри плашки
    Given у пользователя уже активен Plus
    Then под кнопкой отображается пояснение, что просмотр всё равно считается поддержкой
```

## Источник дизайна
`docs/design/support-screen-1A.html` — размеры, отступы и цвета брать оттуда; иллюстрации
в макете — заглушки, реальные ассеты приходят из SPEC-01.

## Gap / context
Сейчас rewarded-блок — карточка `secondaryContainer` с тремя разными «телами» и собственной
композицией (G12), которая после SPEC-05 будет визуально выпадать из ряда плашек. Макет рисовал
этот блок узкой строкой-навигацией, но по решению D1/D6 экранов не добавляем — блок остаётся на
месте и приводится к общей стилистике с добавлением прогресс-бара.

## Implementation links
- commit: —
- files: —
