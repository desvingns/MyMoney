# Wide compact Aurora card — ~95% width, −5% height, no period label, smaller balance
Epic: dashboard-aurora-polish
Order: 02 of 03
Status: done
Depends-on: 01
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Расширить Aurora-карточку до ≈95% ширины экрана (сейчас лимит 245dp — отсюда узкая секция и узкий встроенный график), уменьшить её высоту ~5%, убрать подпись «Остаток за …» у главной карточки и уменьшить шрифт значения остатка на ~5% (36→34sp). Касается главной и per-currency карточек (общий контейнер).
LAYERS: presentation
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt (G8: `dashboardBalancePanelMaxWidth=245dp` L19 больше не использовать как лимит Aurora; G11: уменьшить вертикальные паддинги/межблочные отступы L164–188 и `dashboardAuroraChartHeight=116dp` L174 на ~5%); core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt (G10: `dashboardAuroraBalanceValue` L321–328 — 36→34sp, lineHeight 40→38); feature/dashboard/.../components/AuroraCardCommon.kt (G2: `AuroraCardSurface` L60 — заменить `.widthIn(max=245dp)` на ширину ≈95%); feature/dashboard/.../components/AuroraBalanceCard.kt (G1: убрать label Text L49–56 + его Spacer L57 + параметр `label` + константу `DASHBOARD_AURORA_LABEL_TAG` L113); feature/dashboard/.../DashboardScreen.kt (G3: убрать аргумент `label=` L242 у главной карточки + локальную `periodLabel`, если станет неиспользуемой; G11: боковой отступ карточки L258 и списка per-currency L230 под ≈95%); app/src/androidTest/.../AuroraBalanceCardUiTest.kt + DashboardContentUiTest.kt (G12: убрать проверки по `DASHBOARD_AURORA_LABEL_TAG`, поправить хардкод ширины/высоты).
TEST_TYPES: compose-ui unit
CONSTRAINTS: ≈95% ширины экрана = «почти вся ширина» минус ~5% суммарного бокового отступа (≈2.5%/сторона; точное значение подобрать на устройстве — O1). Высота −5% — суммарно через паддинги (`dashboardAuroraCardPaddingTop/Bottom`, `dashboardAuroraValueBottomMargin`, `dashboardAuroraPillBottomMargin`) и/или высоту графика; пропорции макета (центрирование, порядок label→value→pills→chart) не ломать. Шрифт остатка 34sp (36×0.95, округлено). Убрать «Остаток за …» (`R.string.dashboard_balance_for_period`) ТОЛЬКО у главной карточки (D3); per-currency сохраняет label = `currency.code` (G6) — это другой текст, НЕ удалять. Период остаётся виден в верхней панели (PeriodLabel) — контекст не теряется. Per-currency карточки расширяются/сжимаются автоматически (общий `AuroraCardSurface`, D2). НЕ трогать форматирование строк (это SPEC 01) и цвета/свечение (SPEC 03); высоту графика менять только как часть −5%, не его стиль. Обновить устаревшие тесты (удалённый label-tag, новые размеры). Если `dashboardBalancePanelMaxWidth` после правки нигде не используется — оставить токен (легаси) или удалить, проверив отсутствие ссылок.
DESIGN_TOKENS: spacing.dashboardAuroraHostHorizontalPaddingWide, spacing.dashboardAuroraCardPaddingTopCompact, spacing.dashboardAuroraCardPaddingBottomCompact, spacing.dashboardAuroraChartHeightCompact, spacing.dashboardAuroraPillBottomMarginCompact, typography.dashboardAuroraBalanceValueCompact
=== END SPEC ===

## Gap / context
Карточка и встроенный график визуально узкие: `AuroraCardSurface` ограничен `widthIn(max=245dp)`
(G2/G8) — это и есть незакрытый follow-up эпика `dashboard-final-redesign` («график слишком узкий»).
Плюс пользователь просит компактнее по высоте, без избыточной подписи «Остаток за …» (дублирует
период из топ-бара) и чуть меньший шрифт суммы остатка.

## Implementation links
- commit: 87bd1a29, ad46ed17, cf8f1400, e0e85ddd, d3fce5b5, d569a2c7
- files:  core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt; core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraCardCommon.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/AuroraBalanceCardUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardListUiTest.kt; app/src/main/res/xml/backup_rules.xml; app/src/main/res/xml/data_extraction_rules.xml; app/src/test/java/com/kshavrin/mymoney/BackupRulesTest.kt; app/src/test/java/com/kshavrin/mymoney/DataExtractionRulesTest.kt
