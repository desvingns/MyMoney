# Экран «Категории» вертикально скроллится (п.6)
Epic: monefy-ux-fixes
Order: 07 of 07
Status: draft
Depends-on: —
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Сделать экран «Категории» (просмотр всех категорий) вертикально скроллируемым, чтобы при длинном списке расходов+доходов нижние элементы не обрезались. Рекомендуемый путь: заменить внешний нескроллящийся Column + два LazyVerticalGrid на ОДИН LazyVerticalGrid (GridCells.Fixed(3)) с заголовками секций как full-span items (item span = maxLineSpan): заголовок «Расходы» → элементы расходов → заголовок «Доходы» → элементы доходов.
LAYERS: presentation
CHANGED_HINT: feature/dictionaries/.../categories/CategoriesListScreen.kt — CategoriesListContent (~101-135): внешний Column + два CategoryGrid → один LazyVerticalGrid с full-span заголовками; приватный CategoryGrid (~138-211) объединить/встроить, сохранив per-section drag-reorder
TEST_TYPES: compose-ui
CONSTRAINTS:
  - ГЛАВНЫЙ РИСК: сохранить per-section long-press drag-reorder (detectDragGesturesAfterLongPress ~150-211). После объединения в один грид перенос должен остаться ИЗОЛИРОВАННЫМ внутри своей секции (Reordered(kind, newOrder)), без перетаскивания между расход↔доход. Заложить это в тест.
  - Не использовать Modifier.verticalScroll вокруг LazyVerticalGrid (бросает infinite-height crash) — именно поэтому single-grid-with-spans, а не Column+scroll. Образец рабочего скролла: AccountsListScreen/CurrenciesListScreen (прямой LazyColumn).
  - Не трогать category-picker в форме транзакции (другой компонент — это словарный экран управления).
  - Обновить app/src/androidTest/.../categories/CategoriesListContentUiTest.kt под новую структуру (обе секции присутствуют + проверка скролла к секции «Доходы»).
  - EN+RU; без доменных изменений; идентификаторы английские.
=== END SPEC ===

## Gap / context
CategoriesListContent (CategoriesListScreen.kt:101-135) оборачивает два LazyVerticalGrid в нескроллящийся
Column → контент обрезается при длинном списке. Экраны «Счета»/«Валюты» скроллятся правильно через прямой
LazyColumn. Сложность: у каждого грида есть section-isolated long-press drag-reorder, который нужно сохранить.
Замечание пользователя №6.

## Implementation links
- commit: (pending)
- files: (pending)
