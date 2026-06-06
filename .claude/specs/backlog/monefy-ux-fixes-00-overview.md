# Эпик: monefy-ux-fixes — 6 UX-правок по ревью пользователя
Epic: monefy-ux-fixes
Order: 00 of 07 (overview)
Status: draft
Depends-on: —
Date: 2026-06-06

## Цель

6 замечаний пользователя по ревью MyMoney (Android, Monefy-клон), оформленных как бэклог-эпик.
Собираются по одному через `/mp --feature --next` в порядке 01→07. Все правки визуальные →
обязателен device-гейт `Pixel_5_API_34` (health-check `scripts/preflight_device_health.ps1` перед
инструментальными прогонами).

Ключевое решение (подтверждено пользователем): **полная унификация** формы транзакции — общий
domain-free компонент `TransactionFormContent` в `:core:designsystem`, который рендерят и «Новый
расход/доход», и «Изменить расход/доход». Поэтому SPEC-01 — включающий рефактор-фундамент, на нём
держатся 02–04.

## Упорядоченный список SPEC'ов → замечание пользователя

| SPEC | Замечание | Модули (LAYERS=presentation) | Depends-on |
|------|-----------|------------------------------|------------|
| 01 — extract-transaction-form | (фундамент для п.1–3) | `:core:designsystem` + `:feature:transaction` | — |
| 02 — form-layout-keypad-up | **п.1** циферблат вверх, кнопка «ВЫБОР КАТЕГОРИИ» крупнее | `:core:designsystem` | 01 |
| 03 — single-date-picker | **п.2** одиночный выбор даты вместо диапазона | `:core:designsystem` | 01 |
| 04 — edit-uses-shared-form | **п.3** «Изменить» = «Новый» | `:core:designsystem` + `:feature:transactionslist` | 01, 02, 03 |
| 05 — dashboard-negative-balance | **п.4** отрицательный баланс красным | `:core:ui` + `:feature:dashboard` | — |
| 06 — donut-icon-tap | **п.5** тап по иконке у пончика = тап по сектору | `:core:designsystem` | — |
| 07 — categories-scroll | **п.6** экран «Категории» скроллится | `:feature:dictionaries` | — |

05 / 06 / 07 независимы и могут собираться в любом порядке.

## Сквозные ограничения

- **Архивировать, не удалять** (правило проекта): никаких `rm`. SPEC-01 промоутит `CategoryGrid`/`DateHeader`
  в `:core:designsystem` — старые файлы либо `git mv`, либо дубликат уезжает в `archive/` репо-корня с
  отчётом пользователю.
- **Слои:** `:feature:* ⊥ :feature:*`; `:core:designsystem` остаётся **domain-free** (UI-модели по образцу
  `CategorySlice` в `donut/`, не доменные типы).
- **Переиспользовать существующие общие компоненты** (`:core:designsystem`): `MonefyAmountInput`,
  `MonefyKeypad`, `KeypadEvent`/`CalculatorEngine` (BR-7: одна точка на операнд),
  `AmountFieldSection`/`AmountFieldState`, `categoryIcon(iconKey)`, `CategorySlice`.
- **Заблокированные решения:** AS-12 (dashboard range picker) — НЕ трогать (SPEC-03 — про другой экран,
  форму транзакции); AS-14 (метки ≥3%) — вне scope.
- Строки EN+RU без хардкода; английские идентификаторы; комментарии только при неочевидном WHY.
- Доска отдельна от 15-фазного плана (`docs/implementation_plan/`) — PROGRESS.md не трогаем.

## Implementation links
- commit: (pending)
- files: (pending)
