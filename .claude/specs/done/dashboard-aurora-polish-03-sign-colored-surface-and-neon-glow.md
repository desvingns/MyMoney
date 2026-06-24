# Sign-colored Aurora surface + neon glow (card + FABs)
Epic: dashboard-aurora-polish
Order: 03 of 03
Status: done
Depends-on: 02
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Перекрашивать фон, рамку и неон-свечение Aurora-карточки по знаку остатка за период: net≥0 → зелёный (NeonMint), net<0 → красный (NeonRed). Плюс добавить лёгкое неон-свечение под нижними FAB-кнопками (− ⇄ +), каждая в своём цвете. Касается главной и per-currency карточек (знак — по net каждой карточки).
LAYERS: presentation
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt (G9: добавить `ColorScheme.dashboardAuroraAccentForSign(positive: Boolean)` → `NeonMint 0xFF5BE3B0` / `NeonRed 0xFFE63950` (уже есть L15–19); текущий `dashboardAuroraAccent` L372 оставить дефолтом); feature/dashboard/.../components/AuroraCardCommon.kt (G2: у `AuroraCardSurface` добавить параметр `accent: Color` (дефолт = `dashboardAuroraAccent`) вместо жёсткого чтения L50 — от `accent` уже зависят градиент L70–82, рамка L83–86, свечение `.shadow` L64–69); feature/dashboard/.../components/AuroraBalanceCard.kt (G1: принять знак/accent и передать в `AuroraCardSurface`); feature/dashboard/.../components/CurrencyBalanceCardList.kt (G6: accent по знаку `card.snapshot.net` для каждой карточки); feature/dashboard/.../DashboardScreen.kt (G3/G11: передать знак `state.periodNet.amount.signum()` в главную карточку); feature/dashboard/.../components/ThreeFabLayout.kt (G7: под каждой `NeonOutlineFab` L74 добавить лёгкое свечение её цвета — coral/cyan/mint, низкая alpha); app/src/androidTest/.../AuroraBalanceCardUiTest.kt + DashboardContentUiTest.kt — кейсы знака (зел/красн).
TEST_TYPES: compose-ui
CONSTRAINTS: Один параметр `accent` управляет фоном (радиальный градиент @0.20→white@0.02), рамкой (@0.28) и свечением (`.shadow` ambient/spot) — менять ТОЛЬКО его, структуру `AuroraCardSurface` не переписывать (это и реализует «фон + подсветка + рамка» пункта 7 единым цветом). net = 0 → ПОЛОЖИТЕЛЬНЫЙ/зелёный (`signum() >= 0`, D5). Линия/заливка trend-графика по знаку НЕ перекрашивается (остаётся по `chartConfig.colorRule`, H4) — иначе конфликт с настраиваемым стилем графика. Свечение под FAB — «очень лёгкое»: низкая alpha + небольшой elevation/blur; цвет = цвет самой кнопки (`dashboardActionExpense`/`dashboardActionTransfer`/`dashboardActionIncome`), НЕ по знаку остатка (D6). Per-currency: каждая карточка зел/красн по своему `snapshot.net` (D2). Свечение карточки = тот же `accent`, что фон/рамка (пункты 2 и 7 — единый accent). НЕ трогать форматирование строк (SPEC 01) и размеры/типографику (SPEC 02).
=== END SPEC ===

## Gap / context
Сейчас accent карточки фиксированный бирюзовый `dashboardAuroraAccent` (#37E1C0, G9) — фон, рамка и
свечение не зависят от знака остатка. FAB-ряд (G7) — без свечения под кнопками. Нужно визуально
сигналить знак периода (зелёный/красный) на всей плашке и добавить лёгкое неон-свечение под кнопками.

## Implementation links
- commit: 1f254ede (token) + 08654b66 (impl) + a04cf3c0 (tests), pushed to main
- files:  core/ui/.../theme/Color.kt (dashboardAuroraAccentForSign); feature/dashboard/.../components/AuroraCardCommon.kt (accent param on AuroraCardSurface), AuroraBalanceCard.kt, CurrencyBalanceCardList.kt, ThreeFabLayout.kt (per-FAB glow); feature/dashboard/.../DashboardScreen.kt (periodNet sign); app/src/androidTest/.../dashboard/AuroraSignColorUiTest.kt (new) + AuroraBalanceCardUiTest.kt (sign cases). 29/29 instrumented green on emulator-5554.
