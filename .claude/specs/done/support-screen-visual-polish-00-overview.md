# Фон и заголовок экрана поддержки

Epic: support-screen-visual-polish
Order: 00 of 01 (overview)
Status: done
Depends-on: —
Date: 2026-08-23

## Goal
Экран поддержки (Support) сейчас выбивается из остального приложения: корневой `Column` в
`SupportContent` не задаёт фон, поэтому под системным окном виден белый, а не темизированный фон.
Заголовок «Help MyMoney / grow» текстуально центрирован сам в себе, но блок с ним не растянут на
всю ширину экрана, поэтому визуально прижат к левому краю. Подзаголовок «MyMoney is made
independently. A coffee helps keep it growing.» убирается по просьбе пользователя.

Экран становится визуально консистентным с остальным приложением: тот же фон-токен, реально
центрированный заголовок, без лишнего подзаголовка. Бизнес-логика покупок, рекламы, Paywall и
навигация не затрагиваются.

## Locked decisions
- Фон: `Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)` на корневом
  `Column` в `SupportContent` — тот же токен и та же конструкция, что уже используется в
  `OnboardingScreen` и других full-screen экранах приложения.
- Центрирование: `Column` внутри `SupportHeadline()` получает `Modifier.fillMaxWidth()` (или
  эквивалент), чтобы существующие `TextAlign.Center` / `horizontalAlignment = CenterHorizontally`
  центрировали блок относительно экрана, а не относительно самого себя.
- Подзаголовок: `Text`, рендерящий `support_description`, удаляется из `SupportHeadline()`; больше
  не используемые строки `support_description` убираются из `values/strings.xml` и
  `values-ru/strings.xml` (это правка существующего файла, а не удаление файла — не противоречит
  archive-only политике).
- Вне scope: копирайтинг заголовка, любые другие блоки экрана (карточка покупки, карточка
  благодарности, ad/plus слоты), back-row, навигация.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `support-screen-visual-polish-01-headline-and-background.md` | — | presentation | фон под общий токен, реальное центрирование заголовка, удаление подзаголовка |

## Why this ordering
Единственный SPEC: все три правки — presentation-слой одного файла (`SupportScreen.kt`) плюс два
`strings.xml`, без доменной/data-логики и без пересечений с другими активными SPEC на доске.
Разбивать на несколько SPEC избыточно.

## Key facts (verified)
- G1/G2: `SupportContent` — голый `Column` без фона внутри `Box { MyMoneyNavHost(...) }` без
  корневого `Surface` — `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:81-86`;
  `app/src/main/java/com/kshavrin/mymoney/MainActivity.kt:114-124`.
- G3: конвенция полноэкранного фона `Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)`
  подтверждена в `feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/OnboardingScreen.kt:83-87`.
- G4: `SupportHeadline()` уже центрирует текст внутри себя, но не растянут на всю ширину —
  `SupportScreen.kt:143-182`.
- G5/G6: заголовок — `support_headline_lead`/`support_headline_accent`; подзаголовок —
  `support_description` — `feature/support/src/main/res/values/strings.xml:4,18-19` (RU: `values-ru/strings.xml:4,18-19`).
- G7: `SupportScreenContentTest.kt` фиксирует текущий текст/layout и потребует обновления.
- G9: применяется Visual-change device gate (`AGENTS.md`) — Pixel 5 API 34 перед подтверждением визуальной верификации.
