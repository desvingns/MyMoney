# Drawer menu cleanup — epic overview
Epic: drawer-menu-cleanup
Order: 00 of 02
Status: done
Depends-on: —
Date: 2026-08-20
Closed: 2026-08-22

## Цель

Правый drawer дашборда (кнопка «три точки» в топ-баре) очищается от дублей: пункт About уходит
(он и так вёл на тот же экран Settings — G6), пункт «Настройки графиков» переезжает в
Settings → Appearance. После чистки в drawer остаётся 7 пунктов, которые помещаются на экран;
verticalScroll остаётся как страховка для низких экранов и крупного fontScale (D4). Тап по
«Настройки графиков» в Settings переводит на дашборд и открывает существующий ChartSettingsSheet
поверх графика — пользователь сразу видит результат изменений (D1).

Вне скоупа: новый экран настроек графиков в feature:settings, перенос ChartConfig в core,
архивация ChartSettingsSheet.kt, изменение размеров рядов drawer, хранение CHART_* (DataStore).

## Заблокированные решения (из grill)

- D1: Пункт «Настройки графиков» живёт в Settings → Appearance; тап → дашборд + авто-открытие
  ChartSettingsSheet поверх графика. [confirmed]
- D2: ChartSettingsSheet остаётся в feature:dashboard без изменений; новый подэкран в
  feature:settings НЕ строится; ChartConfig/ChartConfigMapping НЕ переезжают в core. [confirmed]
- D3: About удаляется из drawer; цепочка AboutClicked → NavigateAbout → ветка NavHost →
  строки/теги вычищается рефакторингом; секция About внутри Settings не трогается. [confirmed]
- D4: verticalScroll в drawer остаётся; 7 пунктов; размеры рядов (Box 56dp / иконка 44dp /
  Spacing.l) не меняются. Осознанный разворот исходного «без скролла» — цель достигается
  сокращением до 7 пунктов. [confirmed]
- D5: Support остаётся — единственный путь к Destinations.Support (G13). [confirmed by scope]
- (assumption) O1: механизм авто-открытия — optional Boolean nav-arg `openChartSettings=false`
  на `Destinations.Dashboard`, потребляется один раз; back возвращает в Settings.

## SPEC'и (запускать через /mp --feature --next в порядке Order)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `drawer-menu-cleanup-01-prune-right-drawer.md` | — | presentation | Удалить ChartSettings и About из drawer (7 пунктов, скролл остаётся), вычистить dead-цепочку AboutClicked→NavigateAbout, строки/теги EN/RU, обновить прибитые UI-тесты |
| 02 | `drawer-menu-cleanup-02-settings-chart-settings-entry.md` | 01 | presentation | Пункт «Настройки графиков» в Settings → Appearance → навигация на дашборд с openChartSettings=true → шторка открывается один раз поверх графика |

## Почему такой порядок

01 удаляет старые точки входа, 02 добавляет новую — если 02 поставить первым, пункт в drawer и
пункт в Settings временно продублируются. Оба SPEC-а трогают `MyMoneyNavHost.kt` и
`DashboardViewModel.kt` (same-file clash) → строго последовательно, без параллельных правок.

## Ключевые факты (verified, из grounding)

- G2: 9 пунктов drawer и их строки — `feature/dashboard/.../components/RightDrawerContent.kt:55-146`
  (ChartSettings :85-90, About :97-103).
- G3: «Настройки графиков» — ModalBottomSheet внутри дашборда (`ChartSettingsSheet`,
  `DashboardScreen.kt:292-298`), отдельного route нет; событие `ChartSettingsClicked`,
  флаг `chartSettingsSheetOpen` (`DashboardViewModel.kt:1149-1154`).
- G5: Drawer-пункт About маппится на ТОТ ЖЕ `Destinations.Settings` (`MyMoneyNavHost.kt:89-90`),
  что и пункт Settings — чистый дубль.
- G9: Скролл добавлен сознательно (комментарий `RightDrawerContent.kt:46-48`) из-за переполнения
  8+ пунктов и ATF-флага обрезанного ряда; после чистки — 7 пунктов.
- G10: UI-тесты пинят состав/порядок drawer — `DashboardDrawerContentUiTest.kt:40-71`,
  `DashboardContentUiTest.kt:682-739,1702-1712`; `DestinationsTest.kt:145-146` source-сканит
  NavHost на ветки `DashboardAction.*`.
- G11: Строки EN/RU правятся парой (`L10nParityTest`, lint MissingTranslation = error):
  drawer `feature/dashboard/src/main/res/values*/strings.xml:77-85`.
- G13: About-экран — выделенный, в feature:settings (`AboutHelpRoute`, `Destinations.SettingsAbout`);
  Support — единственный путь к `Destinations.Support` (`MyMoneyNavHost.kt:248-267`).
- G15: Unit-гейт НЕ компилирует src/androidTest — Compose-тесты drawer проверяются только
  connected/compile-AndroidTest задачами.
- G16: Visual-change device gate: Pixel 5 API 34, подключённый и загруженный, обязателен до
  верификации (оба SPEC-а — визуальные).

## Чеклист для человека (НЕ для агента)

1. Открыть drawer → видно ровно 7 пунктов, все помещаются на экран, скролл не нужен (но не сломан).
2. Пунктов «Настройки графиков» и About в drawer нет.
3. Settings → Appearance → «Настройки графиков» → дашборд, шторка открыта поверх графика.
4. Изменить настройку в шторке → график под шторкой реагирует; закрыть шторку → авто-открытия нет.
5. Back из дашборда → возврат в Settings.
6. Settings → «О приложении» → About & Help работает как раньше.

## Implementation links
- commit: —
- files: —
