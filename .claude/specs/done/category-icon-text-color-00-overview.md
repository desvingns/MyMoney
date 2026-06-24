# Цвет текста категории из доминирующего цвета иконки — epic overview
Epic: category-icon-text-color
Order: 00 of 04
Status: done
Depends-on: —
Date: 2026-06-22

## Goal
Убрать из настроек категории ручной выбор цвета (пикер) и сделать цвет ТЕКСТА имени
категории производным от доминирующего цвета её иконки (жёлтая иконка такси → жёлтый текст).
Цвет текста применяется ВЕЗДЕ, где показывается имя категории, включая S01 dashboard
(callout-подписи доната, тайлы), список записей и сетку категорий на форме. Источник цвета —
курируемая таблица `categoryIconAccent(iconKey)`, расширенная на все ~82 ключа иконок.
Цвет хранится (новое поле `textColor`) и бэкфиллится Room-миграцией для существующих категорий.
Вне scope: рантайм-извлечение цвета из PNG (Palette), смена типа хранения цвета (остаётся hex String).

## Locked decisions
- D1 — «доминирующий цвет иконки» = `categoryIconAccent(iconKey)` (NeonCategoryIcon.kt:138-193),
  расширенная/курированная на все ~82 ключа реальным доминирующим цветом (не 8-цветный хэш-фолбэк).
  Отклонено: рантайм-извлечение из bitmap (async, новая зависимость, перф). [Q1]
- D2 — render-логику доната/тинта иконки НЕ переписываем: дольки и тинт по-прежнему читают
  `colorHex`. Меняется только ЗНАЧЕНИЕ `colorHex` (теперь производное) и добавляется чтение
  `textColor` в местах отрисовки имени. [Q2 + A1]
- D3 — порог контраста: тёмные цвета иконок осветляются для читаемости текста на тёмном неоновом
  фоне dashboard. Новый чистый ARGB-luminance хелпер (без Compose), считается в момент сохранения. [Q3]
- D4 — `textColor` (hex String) ХРАНИТСЯ в `Category` (domain) + `CategoryEntity` (Room `text_color`);
  авто-заполняется при сохранении и импорте; Room-миграция бэкфиллит по `iconKey`. Не редактируется
  пользователем. [Q4 + storage Q]
- D5 — пикер цвета удаляется из настроек категории (CategoryEditScreen.kt:186-193), вместе с
  событием `ColorChanged` (CategoryEditViewModel.kt:168-170) и пользовательской мутацией
  `state.colorHex` (:75-76). [запрос фичи]
- D6 — производный `textColor` применяется во ВСЕХ местах отрисовки имени категории: callout-подпись
  доли доната (MonefyDonutChart.kt:134,762), тайл dashboard (CategoryTile.kt:99), заголовок группы
  в списке записей (TransactionsListScreen.kt:617), лейбл ячейки сетки категорий (CategoryGrid.kt),
  превью имени на экране редактирования. [запрос «везде, включая dashboard»]
- A1 (resolved) — `colorHex` (долька доната + тинт иконки) тоже выводится из иконки ДЛЯ ВСЕХ:
  при сохранении/импорте авто = доминирующий цвет иконки (без порога контраста — это цветные
  элементы на своём фоне), а миграция пересчитывает `colorHex` существующим категориям из `iconKey`.
  По сути полная унификация источника цвета на иконку; render-логика не трогается (D2). [GATE 1 A1]

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `category-icon-text-color-01-icon-dominant-color-policy.md` | — | domain | Чистая функция `iconKey → доминирующий hex` (все ~82 ключа) + порог контраста в :core:common |
| 02 | `category-icon-text-color-02-textcolor-field-and-migration.md` | 01 | domain, data | Поле `textColor` в Category+Room, авто-заполнение colorHex+textColor при сохранении/импорте, Room-миграция с бэкфиллом по iconKey |
| 03 | `category-icon-text-color-03-apply-textcolor-dashboard-and-list.md` | 02 | presentation | Отрисовка имени категории в `textColor` на донате, тайле dashboard, списке записей, сетке категорий |
| 04 | `category-icon-text-color-04-remove-color-picker-from-settings.md` | 03 | presentation | Удалить пикер цвета из настроек + превью имени в textColor + правка UI-тестов |

## Why this ordering
01 — фундамент: чистая `iconKey→hex` функция + контраст, на неё опираются 02 (авто-заполнение +
миграция), 03 (отрисовка) и 04. Без Compose/Room, чтобы её мог звать и Room-миграция, и репозиторий.
02 — данные: добавляет хранимое поле и реальную Room-миграцию (бэкфилл colorHex+textColor по iconKey),
поэтому идёт до presentation. 03 и 04 — оба presentation, но НЕ пересекаются по файлам (03: dashboard/
designsystem/список; 04: экран редактирования категории) — порядок диктуется только Depends-on.

## Key facts (verified)
- G1/G2/G3/G4: домен `Category.colorHex: String` (Category.kt:10, одно поле на иконку И стиль), Room
  `CategoryEntity.color_hex` (CategoryEntity.kt:12-22), маппер без конвертации (Mappers.kt:193-217),
  `CategoryRepository.upsert(category): Long` (CategoryRepository.kt:14, CategoryRepositoryImpl.kt:34-38).
- G10/G11/G12/G13: иконки — готовые цветные PNG (`category_neon_*`, ~82 ключа, CategoryIconAssets.kt:14),
  рисуются через `Image(painterResource)` (NeonCategoryIcon.kt:42-50); единственный per-icon цвет —
  `categoryIconAccent(iconKey): Color` (26 ключей + 8-цветный фолбэк, NeonCategoryIcon.kt:138-193);
  при наличии colorHex он ОВЕРРАЙДИТ accent (CategoryGrid.kt:79-93); утилит luminance/контраста НЕТ.
- G5/G6/G7/G8: экран редактирования — `CategoryEditContent` (CategoryEditScreen.kt:87-230); пикер
  на :186-193 шлёт `ColorChanged(value)`; `CategoryEditState.colorHex` default `#7A9685`
  (CategoryEditViewModel.kt:75-76,142-170); превью иконки через `parseHexColor(state.colorHex)` (:176-180).
- G15/G16/G17/G18/G19: callout-подпись доли доната — хардкод `dashboardCalloutLabel`
  (MonefyDonutChart.kt:134,762), `CategorySlice` несёт `color`+`label` (CategorySlice.kt:5-12);
  тайл dashboard — `textPrimary` (CategoryTile.kt:99); список записей — `onSurface`
  (TransactionsListScreen.kt:617); цвет доли доната из colorHex через categoryToPlaceholder
  (DashboardViewModel.kt:118).
- G20/G21/G22: доска `.claude/specs/` (README.md:44-78); dashboard UI-тесты в `:app` androidTest
  (НЕ Robolectric, текст на Canvas не ассертится через semantics — проверять визуально/пиксельно,
  DashboardContentUiTest.kt); ktlint-гейт `:app:ktlintCheck` — `:<module>:ktlintFormat` перед коммитом.
- DB-версия: прочитать `MoneyDatabase.SCHEMA_VERSION` (после currency-exchange-rate MIGRATION_5_6 ≈ 6)
  и добавить следующую миграцию `MIGRATION_(N)_(N+1)` + bump версии — точные строки см. SPEC 02.

## Implementation links
- commit: (pending)
- files: (pending)

## Completion
- Closed 2026-06-23. All SPECs 01-04 shipped to main (last: 04 commits e827f8ec + b2028049). Color picker removed; category text/icon color fully derived from iconKey and applied everywhere (donut, tile, list, grid, edit preview), stored as textColor + backfilled by Room migration.
