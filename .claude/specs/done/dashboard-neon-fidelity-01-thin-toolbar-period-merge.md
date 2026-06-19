# Тонкий неон-тулбар одной строкой + период внутри тулбара
Epic: dashboard-neon-fidelity
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-19

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Верхняя область S01 = ОДНА тонкая строка-тулбар на плоском неон-фоне: слева ☰ (меню) и ⇄ (перевод), по центру переключатель периода (‹ метка-месяца ›, занимает доступную ширину), справа 🔍 (поиск) и ⋮ (ещё). Убрать курсивный логотип «MyMoney» и подпись валюты «Serbian Dinar». Отдельной строки `PeriodLabel` под баром больше нет — её содержимое переезжает в строку тулбара. Высота бара уменьшается (≈64dp→≈44dp). ☰ по-прежнему открывает левый drawer; цели навигации поиск/перевод/ещё не меняются.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardScreen.kt `DashboardTopBar` (G3, :287-340) — заменить вызов `MoneyHeroAppBar` (логотип+subtitle) на компактный неон-тулбар-Row: iconButton(menu)+iconButton(swap) слева, период-навигация по центру (`Modifier.weight(1f)`), iconButton(search)+iconButton(more) справа; без title/subtitle
  - feature/dashboard/.../DashboardScreen.kt `DashboardContent` (G2, :89-284) — убрать отдельную строку `PeriodLabel` из Column (она теперь в тулбаре)
  - feature/dashboard/.../components/PeriodLabel.kt (G4, :38-107) — переиспользовать chevron-prev/next + метку периода как встраиваемый в тулбар компонент (без высоты 80dp/underline-строки); либо извлечь общий `PeriodSwitcher` и вставить в тулбар-Row
  - core/designsystem/.../appbar/MoneyHeroAppBar.kt (G3, :33-75) — НЕ ломая других потребителей: либо завести новый компактный `NeonTopBar`, либо параметризовать существующий слотами без title/subtitle (assumption — проверить call-sites)
  - core/ui/.../theme/Spacing.kt (G8) — уменьшить `heroAppBarHeight`; обнулить/удалить `dashboardPeriodRowHeight` (строки периода больше нет)
  - app/src/androidTest/.../DashboardContentUiTest.kt (G5, G17) — обновить тесты: тегов `DASHBOARD_TOP_BAR_TITLE_TAG`/`…_SUBTITLE_TAG` больше нет; добавить тег периода в тулбаре; проверить, что меню/поиск/перевод/ещё кликабельны и период prev/next работает
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - Теги title/subtitle (`DashboardScreen.kt:427-430`, G5) исчезают или меняют смысл — обновить тесты В ТОЙ ЖЕ правке, иначе компиляция androidTest падает (G18: runner компилирует androidTest).
  - `MoneyHeroAppBar` может использоваться другими экранами — при правке общего компонента проверить ВСЕ call-sites и не сломать их; безопаснее новый `NeonTopBar` только для дашборда (R3, assumption).
  - ☰ сохраняет открытие левого drawer (`RightDrawerToggled`/события из G6, D3); search/swap/more — прежние `DashboardEvent`/навигация (G6), поведение не менять.
  - Свайп-навигация периода (если присутствует) не ломается переносом метки в тулбар.
  - Same-file clash: `DashboardScreen.kt` + `Spacing.kt` + `DashboardContentUiTest.kt` правятся также в 02/03/04 — этот SPEC идёт ПЕРВЫМ, остальные ребейзятся на него.
  - ktlintFormat перед коммитом; дашборд-тесты прогнать на устройстве `./gradlew :app:connectedDebugAndroidTest` (G18).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Тонкий тулбар дашборда

  Scenario: Тулбар одной строкой без логотипа и валюты
    Given открыт главный экран S01
    Then в верхней строке нет курсивного логотипа «MyMoney» и нет подписи валюты
    And видны иконки меню, перевода, поиска и «ещё», а по центру — переключатель периода

  Scenario: Период переехал в тулбар
    Given открыт главный экран
    Then отдельной строки периода под баром нет
    And метка текущего месяца и стрелки ‹ › находятся в строке тулбара

  Scenario: Переключение периода из тулбара
    Given в тулбаре показан «июнь»
    When нажать стрелку «вперёд»
    Then выбранный период меняется на следующий месяц (поведение как прежде)

  Scenario: Меню открывает левый drawer
    When нажать ☰ в тулбаре
    Then открывается левый drawer (как до редизайна)
```

## Gap / context
Сейчас сверху — высокий hero-бар с курсивным логотипом «MyMoney» и подписью валюты, а период вынесен
в отдельную строку 80dp. Мокап показывает одну тонкую строку иконок с переключателем месяца по центру.
SPEC сводит две области в один тонкий тулбар и убирает логотип/валюту.

## Implementation links
- commit: 25748bd5, 653678bb, 95aab200
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodLabel.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityLaunchTest.kt