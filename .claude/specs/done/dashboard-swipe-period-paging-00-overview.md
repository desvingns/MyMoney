# Dashboard period swipe — full-screen paging/peek effect (S01) — epic overview
Epic: dashboard-swipe-period-paging
Order: 00 of 02
Status: done
Depends-on: —
Date: 2026-06-22

## Goal
Сейчас свайп влево/вправо на dashboard (S01) мгновенно подменяет период (`detectHorizontalDragGestures`, порог 56.dp — добавлено в `monefy-behavioral-fidelity-07`). Цель — заменить это на **полноэкранный paging/peek-эффект как в Monefy**: при плавном перетаскивании всё тело dashboard соседнего (prev/next) периода едет за пальцем и видно реальным контентом; на отпускании страница «защёлкивается» к соседу (фиксируя смену периода) или возвращается назад. Входная точка — само тело dashboard на S01. Вне области: другие экраны, арифметика периодов (`Period.next()/previous()` уже есть), правки repository/Room.

## Locked decisions
- D1: пик показывает РЕАЛЬНЫЕ данные соседа — ViewModel в фоне досчитывает и кэширует `DashboardState` prev/next на каждом settle периода (явное требование пользователя + дорогой per-period Room-расчёт, G9–G11).
- D2: перелистывается только тело dashboard; верхняя панель (☰ ⇄ 🔍 ⋮ + «‹ период ›») и 3 FAB зафиксированы.
- D3: верхний 3-up `PeriodLabel` следит за drag-оффсетом страницы (сдвигается вместе с контентом).
- D4: бесконечное непрерывное перелистывание (пейджер ре-центрируется на settle); `Period.All` — paging отключён (нет соседей); `CustomRange` сдвигается на длину диапазона (уже в `Period`).
- D5: сохранить направление left→Next / right→Prev; `SoundKey.SWIPE`+`HapticKind.SOFT` на settle; left `ModalNavigationDrawer gesturesEnabled=false`.
- D6: заменить `detectHorizontalDragGestures` на `HorizontalPager` (зеркалить онбординг, G6), не наслаивать.
- (assumption) O1: тактильный/звуковой фидбэк — только на settle (как сейчас), без «тика» в середине drag.
- (assumption) H2: если сосед не досчитан при быстром свайпе — страница-сосед показывает dashboard loading-state до прихода своего фонового результата.

## SPECs (run via `/mp --feature --next` in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `dashboard-swipe-period-paging-01-neighbor-period-state-cache.md` | — | presentation | ViewModel предрасчитывает+кэширует `DashboardState` prev/next на каждом settle; отдаёт состояние соседа в UI (loading пока не готов) |
| 02 | `dashboard-swipe-period-paging-02-content-pager-peek.md` | 01 | presentation | 3-страничный `HorizontalPager` из `DashboardContent` на кэшированных состояниях; top bar+FAB фиксированы; `PeriodLabel` следит за drag; settle фиксирует период + ре-центр; All — paging off |

## Why this ordering
Foundation-first: 01 готовит данные соседних периодов (юнит-тестируется через `DashboardViewModelTest`, шиппится без видимых изменений), 02 строит пейджер поверх них. Оба трогают `DashboardState.kt` (01 добавляет поля кэша соседей, 02 их читает) → **same-file clash, обязательная последовательность 01→02**.

## Key facts (verified)
- G1: `DashboardContent(state: DashboardState, onEvent)` — чистая composable, рендерит один период; можно инстанцировать 3× в пейджере — `feature/dashboard/.../DashboardScreen.kt:121-124`
- G2: текущий свайп — `detectHorizontalDragGestures`, порог 56.dp, onDragEnd → Prev/Next — `feature/dashboard/.../DashboardScreen.kt:174-201`
- G4: обработка Prev/Next → `period.previous()/next()` + `recomputeBalance()` + persist + clear importFocus — `feature/dashboard/.../DashboardViewModel.kt:709-724`
- G6 (MIRROR): `HorizontalPager`+`rememberPagerState`+`animateScrollToPage` уже в онбординге — `feature/onboarding/.../OnboardingScreen.kt:16-18,54,99-107`; в compose-bom без отдельной зависимости
- G7: `PeriodLabel` уже 3-up, но статичен — `feature/dashboard/.../components/PeriodLabel.kt:50-173`
- G8: поля `DashboardState` для полной отрисовки — period, balanceSnapshot, currencyCards, periodNet, ringFraction, ringIsExpense, trendPoints, slices, expenseTiles, isLoading, isSeparateMode — `feature/dashboard/.../DashboardState.kt:16-62`
- G9: `recomputeBalance()` — async `viewModelScope.launch{}`, отменяет предыдущий job; per-period Room-чтения, кэша всех транзакций в памяти НЕТ — `feature/dashboard/.../DashboardViewModel.kt:357-416`
- G12: dashboard Compose UI тесты — `app/src/androidTest/.../dashboard/DashboardContentUiTest.kt` (инструментальные, не Robolectric); теги `DASHBOARD_*_TAG`; запуск `:app:connectedDebugAndroidTest`
- G13: `Period.All` prev/next = no-op; `CustomRange` сдвиг на длину; ktlint-гейт на изменённых модулях

## Implementation links
- 01: 2081b96e (prod) + a1aeeea0 (tests)
- 02: 003e7218 (prod) + 2ec6c2be (tests)
- Epic COMPLETE 2026-06-23, pushed to main.
