# Drawer search redesign — epic overview
Epic: drawer-search-redesign
Order: 00 of 02
Status: done
Depends-on: —
Date: 2026-08-20
Completed: 2026-08-22

## Цель
Пункт «Поиск» в правом drawer (overflow «три точки» на дашборде) сейчас открывает устаревшую
строку поиска (плоский `Surface` + `BasicTextField`, ни с одним паттерном приложения не
совпадает) и — в отличие от всех соседних пунктов — не закрывает drawer. Эпик приводит бар поиска
к текущему hero-дизайну приложения и чинит закрытие drawer. Логика поиска (поиск по заметкам,
debounce, история, фазы) не меняется.

## Заблокированные решения (grill 2026-08-20)
- D1: новый бар — в hero-стиле `MoneyHeroAppBar`: градиент `dashboardHeroGradientStart/End`,
  высота `Spacing.heroAppBarHeight`, app-типографика (G34/G36).
- D2: mic-кнопка остаётся (пустой запрос → Mic, введённый → Close), только рестайл (G32).
- D3: редизайн общего `SearchContent` обновляет ОБА входа в поиск — drawer-overlay и route из
  TransactionsList (G18/G31).
- D4: drawer-поиск остаётся overlay (`searchOverlayOpen`); унификация на `Destinations.Search`
  route — вне скоупа (G16/G17).
- D5: при нажатии «Поиск» сначала `closeDrawers()`, затем `DashboardAction.NavigateSearch`
  — паттерн соседних пунктов (G13/G14).

## SPECs (run via /mp --feature --next in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `drawer-search-redesign-01-close-drawer-on-search.md` | — | presentation | SearchClicked вызывает closeDrawers() перед NavigateSearch |
| 02 | `drawer-search-redesign-02-hero-search-bar.md` | — | presentation | Редизайн SearchTopBar под hero-стиль MoneyHeroAppBar |

## Почему такой порядок
SPEC'и независимы (разные модули: :feature:dashboard vs :feature:transactionslist, пересечений
по файлам нет — clash-check чист). 01 идёт первым как маленький bugfix с быстрым фидбеком;
02 — визуальная работа с device gate.

## Ключевые факты (verified)
- G13: `SearchClicked` — единственный пункт drawer без `closeDrawers()` — `DashboardViewModel.kt:1108`.
- G14: закрытие drawer — флаги UiState: `closeDrawers()` копирует state с `leftDrawerOpen=false,
  rightDrawerOpen=false` — `DashboardViewModel.kt:1280`.
- G16: поиск из дашборда — overlay `searchOverlayOpen` + `SearchRoute(contextualOverlay=true)`
  в `MyMoneyNavHost.kt:64,99-108`, не навигация.
- G32: старый `SearchTopBar` — плоский `Surface` 64.dp (не `Spacing.heroAppBarHeight`),
  `BasicTextField` headlineSmall, ArrowBack слева, Mic/Close справа, автофокус — `SearchScreen.kt:187-270`.
- G34: текущий top-bar приложения — `MoneyHeroAppBar` (градиент, `Spacing.heroAppBarHeight`,
  слоты leading/title/actions) — `core/designsystem/.../appbar/MoneyHeroAppBar.kt:34`.
- G36: токены темы в `:core:ui` theme (Spacing/Color/Typography/Shape) — `core/ui/.../theme/Spacing.kt:5`
  (`heroAppBarHeight` :16; численно равен старому хардкоду 64.dp — визуальная дельта редизайна =
  градиент/типографика/токены, не высота).
- G20/G59: `DashboardViewModelTest` ассертит только actions — добавление `closeDrawers()` тест
  не ломает, но state-assert надо добавить.
- G38: UI-тесты поиска — instrumented `SearchContentUiTest` (чистый `SearchContent`, ноды по
  contentDescription из ресурсов) + JVM `SearchContentTest`.
- G61: UI-тесты drawer/search живут в app/src/androidTest → `:app:connectedDebugAndroidTest`,
  visual device gate (Pixel 5 API 34).

## Чеклист для человека
- [x] Открыть drawer → «Поиск»: drawer закрывается, поверх дашборда открывается поиск.
- [x] Бар поиска в hero-стиле (градиент, высота как у hero app bar), автофокус + клавиатура.
- [x] Пустой запрос → mic; ввод → кнопка очистки; очистка работает.
- [x] Вход в поиск из списка транзакций показывает тот же новый бар.
- [x] Поиск по заметкам и переход в детали транзакции работают как раньше.

## Implementation links
- commit: 5795aa7d, 8de1f24, 3b8b1188, 47616f0b
- files: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt; feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt; feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentUiTest.kt
