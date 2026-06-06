# Dashboard donut — 4 точечные правки (фон иконок, группировка <2%→Other, белый текст шапки, иконки по контуру)
Epic: dashboard-donut-refinement
Order: 00 of 03 (overview)
Status: backlog
Date: 2026-06-06

## Goal
4 правки экрана S01 по запросу пользователя: (1) убрать круглый фон под иконками категорий у
пончика; (2) группировать категории расходов <2% в один display-only слайс «Other»; (3) в светлой
теме сделать текст «MyMoney» и название валюты белым; (4) разместить иконки категорий на внешнем
контуре пончика, привязав к углу своего сектора. #1/#3/#4 — шаг к
`docs/design/dashboard-redesign/dashboard-final-38-spec.md` (поведение не меняется); #2 — НОВОЕ
правило (в референсе 38 ещё есть слайс 1%).

## Ordered SPECs
1. **01-header-text-white** (#3) — :feature:dashboard `DashboardScreen`: title + валюта → `onPrimary`
   (белый в обеих темах). Тривиально, без зависимостей.
2. **02-other-grouping** (#2) — :feature:dashboard `DashboardViewModel.snapshotToSlices` + строки +
   гард `SliceClicked`. Variant A: всегда сворачивать <2% в один честный «Other». domain/presentation.
3. **03-icons-contour-no-disc** (#1+#4) — :core:designsystem `MonefyDonutChart`/`DonutGeometry`:
   убрать диск-фон, разместить иконки на внешнем контуре по mid-angle сектора, % — радиально наружу.
   presentation. (самый объёмный/рискованный)

## Cross-cutting notes / flags
- **НОВОЕ решение (AS-стиль):** категории расходов с долей <2% объединяются в один display-only слайс
  «Other» (Variant A — всегда, даже одну; пончик = 100%; % у «Other» честный = сумма мелких).
  Не навигируется, не персистится. Порог = новая именованная константа `OTHER_GROUP_MAX_FRACTION = 0.02f`,
  обратимо. **ОТДЕЛЬНА** от AS-14 / `DEFAULT_LABEL_MIN_FRACTION = 0.03f` (тот скрывает только ПОДПИСЬ
  % на мелком слайсе, не фильтрует). Не смешивать пороги.
- **Scope:** только эти 4 правки. Полный редизайн 38 (3D-extrude тюнинг, перенос balance-панели,
  подписи категорий, подписи у FAB, центральный divider) — ВНЕ scope.
- **Soft-dep:** 02 уменьшает число мелких слайсов → меньше коллизий иконок в 03; реализовать 02 до
  девайс-сверки 03 (не жёсткая зависимость).
- **Visual task → device-gate** (booted Pixel_5_API_34) перед runner — правило проекта для
  визуальных изменений dashboard.
- Watch `CategoryIconsTest` (захардкоженные счётчики иконок): «Other» переиспользует существующий
  fallback-икон `Icons.Outlined.Category` (новый вектор НЕ добавляем) → счётчик не меняется; проверить.

## Implementation links
- commit: — (filled when done)
- files: —
