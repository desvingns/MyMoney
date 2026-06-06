# Тап по иконке у пончика = тап по сектору (п.5)
Epic: monefy-ux-fixes
Order: 06 of 07
Status: draft
Depends-on: —
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На dashboard в populated-состоянии пончика тап по иконке категории (диск, нарисованный на Canvas рядом с кольцом) должен срабатывать так же, как тап по соответствующему сектору пончика — то есть вызывать onSliceClick для слайса этой иконки. Сейчас иконки не имеют собственного hit-test: тап засчитывается только при попадании по геометрии кольца.
LAYERS: presentation
CHANGED_HINT: core/designsystem/.../donut/MonefyDonutChart.kt — в ветке тап-хендлера для populated-состояния (~158-202) добавить hit-test по дискам иконок из списка placed (~295-328) перед/вместе с проверкой геометрии кольца; при попадании вызвать onSliceClick(slice) соответствующей иконке
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Переиспользовать существующий паттерн disc hit-test из empty-state (~175-184): hypot(offset.x - slot.x, offset.y - slot.y) <= discRadius.
  - Маппинг иконка→слайс из списка placed; контракт onSliceClick не менять.
  - Не сломать empty-state hit-test (onEmptyCategoryClick) и существующую обработку тапа по кольцу.
  - Идентификаторы английские; комментарии только при неочевидном WHY.
=== END SPEC ===

## Gap / context
Секторы пончика кликаются через detectTapGestures + DonutGeometry.hitTest() → onSliceClick → DashboardEvent.SliceClicked
→ переход к транзакциям категории. Иконки рисуются на Canvas (drawIconDisc ~619-642) в позициях placed, но в
populated-состоянии собственного hit-test у них нет (есть только для empty-state). Пользователь ожидает, что
тап по иконке = тап по сектору. Замечание пользователя №5.

## Implementation links
- commit: (pending)
- files: (pending)
