# Эпик: form-sticky-save-bar — закреплённая снизу кнопка «Сохранить» на формах
Epic: form-sticky-save-bar
Order: 00 of 03 (overview)
Status: draft
Depends-on: —
Date: 2026-06-07

## Цель

Замечание пользователя: на экранах-формах кнопка «Сохранить» лежит последним элементом **внутри**
скролл-области, поэтому до неё нужно доскроллить в самый низ. Нужно, чтобы «Сохранить» была всегда
видна снизу экрана — в любом месте скролла можно сохранить. Решение: общий `FormBottomBar` в
`:core:designsystem` рендерится в слоте `Scaffold.bottomBar`, а тело формы остаётся в `verticalScroll`
над ним. Собирается по одному через `/mp --feature --next` в порядке 01→03. Правки визуальные →
обязателен device-гейт `Pixel_5_API_34` (health-check `scripts/preflight_device_health.ps1` перед
инструментальными прогонами).

## Заблокированные решения (из grill)

- **Охват — 5 форм:** Category / Account / Currency / Goal edit (`:feature:dictionaries`) + CurrencyRate
  (`:feature:transaction`). TransactionDetail/Transfer **вне scope** — там Save уже FAB/в тулбаре (G6);
  у `TransactionFormContent` «Save»-кнопки нет, CTA уже прижата (G7); «Настройки» применяются сразу.
- **Механизм:** общий **`FormBottomBar`** в `:core:designsystem` (domain-free), слот `Scaffold.bottomBar`;
  тело формы — `verticalScroll`, получает `innerPadding` (DRY-паттерн как monefy-ux-fixes-01).
- **«Удалить» — только Save закрепляем:** на Category/Account/Currency кнопка «Удалить» ОСТАЁТСЯ
  последним элементом тела формы (в скролле). `FormBottomBar` = Save-only ⇒ API тривиален:
  `(text, enabled, onSave)`.
- **enabled расходится:** Goal Save имеет `enabled = state.canSave` (G4); остальные Save включены всегда.
  `FormBottomBar(enabled: Boolean = true)`, Goal прокидывает `state.canSave`.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `form-sticky-save-bar-01-form-bottom-bar-component.md` | — | presentation · `:core:designsystem` | Общий `FormBottomBar` (Save-only, enabled) + compose-ui тест |
| 02 | `form-sticky-save-bar-02-dictionaries-edit-pinned-save.md` | 01 | presentation · `:feature:dictionaries` | Прокинуть в Category/Account/Currency/Goal edit; Save в bottomBar, тело скроллится, Delete в скролле |
| 03 | `form-sticky-save-bar-03-currency-rate-pinned-save.md` | 01 | presentation · `:feature:transaction` | Прокинуть в CurrencyRate; Save в bottomBar, тело скроллится |

02 и 03 независимы между собой — после 01 собираются в любом порядке.

## Почему такой порядок

01 — фундамент (общий компонент), на нём держатся 02 и 03. Same-file клэшей нет (разные модули/файлы).
`monefy-ux-fixes-07 categories-scroll` правит `CategoriesListContent` (экран-список), НЕ `CategoryEditScreen`
(экран-форма) — разные файлы, клэша нет (H4).

## Ключевые факты (verified, из grounding)

- G1 Category: scroll-`Column` `CategoryEditScreen.kt:114-119`, Save+Delete Row внутри скролла `:175-195`.
- G2 Account: scroll `:102`, Save+Delete Row `:220-236` (`AccountEditScreen.kt`).
- G3 Currency: scroll `:88`, Save+Delete Row `:169-178` (`CurrencyEditScreen.kt`).
- G4 Goal: scroll `:118`, одиночная Save `:255-256`, `enabled = state.canSave` (`GoalEditScreen.kt`).
- G5 CurrencyRate: scroll `:106`, Save `:135-136` (`rate/CurrencyRateScreen.kt`).
- G8 Все формы уже на `Scaffold` c пустым слотом `bottomBar`; контент получает `innerPadding`.
- G9 Слои: `:core:designsystem` domain-free; строки EN+RU без хардкода (`dictionaries_save`, `goal_save`, `currency_rate_save`).
- G10 Раннер компилирует androidTest + тестит затронутые модули — UI-тесты экранов обновить в том же проходе.

## Implementation links
- commit: (pending)
- files: (pending)
