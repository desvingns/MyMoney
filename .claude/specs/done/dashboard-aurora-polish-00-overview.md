# Dashboard Aurora section polish — epic overview
Epic: dashboard-aurora-polish
Order: 00 of 03
Status: done
Depends-on: dashboard-final-redesign (done)
Date: 2026-06-22

## Goal
Доработать секцию «остаток + доход/расход + график» на S01 (Aurora hero card из эпика
`dashboard-final-redesign`) по 7 визуальным/форматным правкам пользователя. Поведение не меняется.
Закрывает и открытый follow-up прошлого эпика («внутрикарточный график слишком узкий») — пункт 1.

7 правок (→ в каком SPEC):
1. Карточку расширить до ≈95% ширины экрана + уменьшить высоту ~5% (сейчас лимит 245dp → узко). → **02**
2. Лёгкое неон-свечение под карточкой и под нижними кнопками (− ⇄ +), каждая FAB в своём цвете. → **03**
3. Убрать подпись «Остаток за …» у главной карточки. → **02**
4. Шрифт значения остатка −5% (36→34sp). → **02**
5. В пилюлях доход/расход валюта ПОСЛЕ числа (а не перед). → **01**
6. В пилюлях и в значении остатка — только целые (отсекать дробную часть). → **01**
7. Фон/подсветка/рамка карточки по знаку остатка: ≥0 зелёный, <0 красный. → **03**

## Ordered SPECs
- **01 — integer-amounts-and-currency-after** (presentation, foundation): пункты 5, 6. Валюта после
  числа в пилюлях главной карточки (`formatMoney` +AFTER) + отсечение дробной части (целые) в
  остатке и пилюлях главной и per-currency карточек через локальный `RoundingMode.DOWN`.
- **02 — wide-compact-aurora-no-label** (presentation, **зависит от 01**): пункты 1, 3, 4. Ширина
  ≈95% (убрать лимит 245dp) + высота −5% + убрать label «Остаток за…» + шрифт остатка 36→34sp.
- **03 — sign-colored-surface-and-neon-glow** (presentation, **зависит от 02**): пункты 2, 7. accent
  карточки по знаку net (NeonMint/NeonRed) → фон+рамка+свечение; свечение под каждой FAB в её цвете.

## Cross-cutting notes
- **Линейный порядок 01→02→03 ВЫНУЖДЕН** общими файлами: `DashboardScreen.kt` — во всех трёх;
  `AuroraCardCommon.kt` / `AuroraBalanceCard.kt` / `CurrencyBalanceCardList.kt` — в 02 и 03.
  Параллелить нельзя.
- Правки применяются и к **per-currency** карточкам режима «Все счета → раздельно» (grill D2): они
  делят `AuroraCardSurface` + `IncomeExpensePills` + типографику с главной. Зел/красн — по знаку net
  каждой карточки.
- Зафиксированные цвета знака: net≥0 → `NeonMint 0xFF5BE3B0`, net<0 → `NeonRed 0xFFE63950` (оба уже в
  палитре, `Color.kt:15–19`). net = 0 → положительный (зелёный).
- Отсечение дробной части — **ЛОКАЛЬНО** в `:feature:dashboard` (pre-truncate
  `setScale(0, RoundingMode.DOWN)`); общий `MoneyFormatter` НЕ менять (он HALF_UP/BEFORE и используется
  во всём приложении).
- Линия/заливка trend-графика по знаку **НЕ** перекрашивается (остаётся по `chartConfig.colorRule`).
- Точные dp для ≈95%/−5% подбираются на устройстве — визуальный pre-flight каждого SPEC
  (`emulator-5554`); числом в SPEC не фиксируем. (assumption O1)
- Заземление: `~/AppSpecs/dashboard-aurora-polish/pipeline/grounding.md` (факты G1–G12), решения
  grill — `…/grill.md` (D1–D8). Образец house-format: `done/dashboard-final-redesign-02-aurora-hero-card.md`.

## Status
- [x] 01-integer-amounts-and-currency-after
- [x] 02-wide-compact-aurora-no-label
- [x] 03-sign-colored-surface-and-neon-glow
