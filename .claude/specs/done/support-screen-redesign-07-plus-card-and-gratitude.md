# Плашка Plus и карточка благодарности с тремя счётчиками
Epic: support-screen-redesign
Order: 07 of 07
Status: done
Depends-on: support-screen-redesign-01, support-screen-redesign-03, support-screen-redesign-04, support-screen-redesign-05
Date: 2026-08-20
Acceptance-matrix: supporter=prospect,supporter; counters=zeros,mixed
Risk-signals: visual, navigation

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Две последние секции экрана. Плашка `MyMoney Plus` приводится к общей стилистике плашек
(иллюстрация-звезда, заголовок, описание, кнопка) и по-прежнему открывает существующий экран
Paywall (D8) — цен подписки на плашке нет. Карточка благодарности перестаёт быть условной и
показывается ВСЕГДА (D9): аватар 84dp, заголовок и текст в двух вариантах — для уже поддержавшего
и для ещё не поддержавшего, чип «Supporter» (только для поддержавшего) и три счётчика: реклама,
мл. кофе, бл. кофе. Отдельный `SupporterBadge` схлопывается в чип внутри карточки.
LAYERS: presentation
CHANGED_HINT:
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt:153-185 — `PaywallSupportEntry`: перевести на `Shapes.supportPanel` + `supportPanelContainer` + `BorderStroke(1.dp, supportPanelOutline)`, добавить иллюстрацию `R.drawable.support_neon_plus` размером `Spacing.supportPanelIconSize` с contentDescription `support_image_plus_description`, кнопку — формой `Shapes.supportPrimaryAction`, высотой `Spacing.supportActionMinHeight`; `onOpenPaywall` и навигация не меняются (G13, G4, D8)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:99-102 — убрать условие `if (state.supporterState.badgeEarned)`: карточка благодарности рендерится всегда; отдельный вызов `SupporterBadge()` убрать (G9, D9)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:231-252 — `SupporterGratitude` переписать: `Row` из аватара (`support_neon_avatar`, `Spacing.supportAvatarSize`, круглая обрезка) и колонки с заголовком/текстом (`support_gratitude_title_supporter`/`_prospect`, `support_gratitude_body_supporter`/`_prospect` по `badgeEarned`), чипом Supporter (`Shapes.supporterChip`, `supporterChipContainer/Outline/Content`, `Spacing.supporterChipHeight`, только при `badgeEarned`) и рядом из трёх счётчиков
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt — новый приватный composable счётчика (значение стилем `supportCounterValue`, подпись стилем `supportCounterLabel`); три экземпляра: `state.adsWatchedTotal` + `support_counter_ads_label`, `state.supporterState.smallCoffeeCount` + `support_counter_coffee_small_label`, `state.supporterState.largeCoffeeCount` + `support_counter_coffee_large_label` (SPEC-02, SPEC-04)
  - feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:215-228 — удалить composable `SupporterBadge` вместе с его единственным использованием (заменён чипом внутри карточки)
  - feature/support/src/main/res/values/strings.xml + values-ru/strings.xml — вывести из употребления `support_gratitude` и `support_gratitude_count` после перевода карточки на новые ключи; `support_badge` переиспользуется как текст чипа (G23)
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt — сценарии: карточка видима при `badgeEarned = false` с текстом для «ещё не поддержал» и без чипа; при `badgeEarned = true` — с чипом и текстом благодарности; три счётчика показывают значения из состояния; плашка Plus кликается и отдаёт колбэк открытия Paywall (G27)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Кнопка плашки Plus ведёт ТОЛЬКО на существующий `Destinations.Paywall(SupportSection)` через
    имеющийся колбэк `onOpenPaywall` (G3, G4, D8). Ни покупки подписки на месте, ни цен плана на
    плашке — это явный out-of-scope эпика.
  - Экран Paywall (`PaywallRoute`, `PaywallScreen` кроме `PaywallSupportEntry`) не редизайнится.
  - Счётчики читаются из `SupportState` (SPEC-04) — плашка не должна получать `RewardedAdViewModel`
    или свой ViewModel (D13).
  - Тексты благодарности выбираются по `supporterState.badgeEarned`, а не по `purchaseCount > 0`:
    флаг монотонен и уже является признаком «поддержал» (G35).
  - Чип «Supporter» показывается только при `badgeEarned` — «Спасибо за поддержку» без поддержки
    выглядит фальшиво (H обсуждено в grill).
  - Сырых dp/цветов/строк нет — только токены SPEC-03 и `stringResource` (G19, G23).
  - Файл `SupportScreen.kt` уже правил SPEC-05 (clash) — этот SPEC запускать строго после него,
    иначе правки разъедутся.
  - `SupportScreenContentTest` переписывается, а не ослабляется; Robolectric читает реальные
    ресурсы (G29) — строки брать из `R.string`.
  - Визуальная работа → visual device gate на Pixel 5 API 34 (AGENTS.md).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Плашка Plus и карточка благодарности

  Scenario: Плашка Plus в общей стилистике
    Given экран поддержки открыт
    Then плашка Plus имеет ту же форму, обводку и фон, что и плашки рекламы и кофе
    And в ней есть иллюстрация с описанием для скринридера

  Scenario: Плашка Plus открывает экран планов
    Given экран поддержки открыт
    When пользователь нажимает кнопку на плашке Plus
    Then приложение открывает экран Plus с планами

  Scenario: Карточка благодарности видна до первой поддержки
    Given пользователь ещё не совершал покупок
    Then карточка благодарности отображается с приглашающим текстом
    And чип сторонника не отображается

  Scenario: Карточка благодарности после поддержки
    Given пользователь уже поддержал приложение
    Then карточка отображает благодарность и чип сторонника

  Scenario: Три счётчика показывают реальные значения
    Given просмотрено двенадцать роликов, куплено два маленьких и один большой кофе
    Then карточка показывает счётчик рекламы со значением двенадцать
    And счётчик маленького кофе со значением два
    And счётчик большого кофе со значением один

  Scenario: Нулевые счётчики отображаются, а не скрываются
    Given у пользователя нет ни просмотров, ни покупок
    Then все три счётчика отображаются с нулевыми значениями
```

## Источник дизайна
`docs/design/support-screen-1A.html` — размеры, отступы и цвета брать оттуда; иллюстрации
в макете — заглушки, реальные ассеты приходят из SPEC-01.

## Gap / context
Сейчас благодарность и бейдж показываются только после первой покупки (G9), а вход в Plus —
карточка `secondaryContainer`, не совпадающая с новыми плашками (G13). Макет делает карточку
благодарности постоянным элементом с аватаром и чипом; пользователь дополнительно потребовал
три раздельных счётчика вместо одной строки «Покупок кофе: N» (D9). Данные под это подготовлены
в SPEC-02 и SPEC-04.

## Implementation links
- commits: f0175b98, 455ea25a, 0d5037b9
- files: feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt; feature/support/src/main/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreen.kt; feature/support/src/main/res/values/strings.xml; feature/support/src/main/res/values-ru/strings.xml; feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt; feature/support/src/test/java/com/kshavrin/mymoney/feature/support/paywall/PaywallScreenContentTest.kt
