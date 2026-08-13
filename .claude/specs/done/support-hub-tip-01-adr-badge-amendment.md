# ADR-0010: бейдж Supporter выдаётся за кофе, а не за Plus
Epic: support-hub-tip
Order: 01 of 08
Status: done
Depends-on: —
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: docs
PLATFORM: android
WHAT: ADR-0010 перестаёт противоречить реализуемой модели. Строка «Supporter badge» уходит из таблицы возможностей Plus (D2); вместо неё в D4 фиксируется, что первая покупка любого из consumable-товаров «кофе» навсегда выдаёт косметический бейдж Supporter, который не является entitlement, не открывает Shared workspace и не влияет на Plus. Открытый вопрос ADR «косметический бейдж или виден другим участникам workspace» закрывается: бейдж виден только владельцу в его приложении. Никакого кода этот SPEC не трогает — он делает запись решения истинной до того, как по ней начнут писать код.
LAYERS: docs
CHANGED_HINT:
  - docs/DECISIONS/ADR-0010-monetization.md:64 — убрать строку «Supporter badge | no | yes» из таблицы D2 (G35)
  - docs/DECISIONS/ADR-0010-monetization.md:81-90 — в D4 добавить абзац: первая покупка любого coffee-SKU навсегда выдаёт косметический бейдж Supporter; бейдж не entitlement, Plus его не выдаёт и не скрывает (G34, G35)
  - docs/DECISIONS/ADR-0010-monetization.md:222-227 — из «Open items» убрать вопрос о видимости бейджа, перенеся ответ в D4 (G35)
  - docs/DECISIONS/ADR-0010-monetization.md — в конце добавить раздел «Amended 2026-08-12» с одной строкой обоснования и ссылкой на эпик support-hub-tip (assumption: проект не имеет отдельной конвенции для поправок к принятому ADR)
  - `TDD/MyMoney/MyMoney_TDD.md:2113` — в сравнительной таблице §14 строка `| Permissions | 12 (APK) | 4 (decision) |`: заменить `4` на `6`. TDD §8.2 (`:2040`) уже говорит «Final count: **6** manifest permissions» после добавления `com.android.vending.BILLING` и `com.google.android.gms.permission.AD_ID` — сейчас документ противоречит сам себе в двух своих же местах
  - `TDD/MyMoney/MyMoney_TDD.md:2773` — correct the stale §14.4 kickoff statement that Plus sells the Supporter badge; it must state that the badge is awarded for the first coffee purchase, is cosmetic, and is not an entitlement. This is the semantic-review blocker that expands the approved docs scope.
TEST_TYPES: none
CONSTRAINTS:
  - The original one-digit TDD correction at line 2113 remains in scope; the additional line-2773 correction above is explicitly authorized by the user's scope-expansion instruction and is limited to resolving the documented ADR contradiction.
  - Статус ADR остаётся `Accepted`; новый ADR не заводится — это уточнение внутри уже принятого решения, а не его отмена.
  - Ничего, кроме таблицы D2, раздела D4 и списка Open items, не трогать. В частности, D5 (rewarded ads) и регионные ограничения остаются дословно как есть — они принадлежат другим эпикам.
  - Правки TDD ограничены **одной цифрой** на строке 2113. Строки Q-B3 (`:24`, `:2110`) и таблица разрешений §8.2 уже приведены в соответствие с ADR-0010 более ранней работой — их не трогать. `POST_NOTIFICATIONS` на строке 2032 остаётся `REMOVED`: разрешение появляется только вместе с воркером предупреждений, и его переворачивает `plus-subscription-gating-07`, а не этот SPEC.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Запись решения о бейдже соответствует реализации

  Scenario: Бейдж больше не числится возможностью Plus
    Given ADR-0010 после правки
    When читатель смотрит таблицу возможностей D2
    Then строки «Supporter badge» в ней нет

  Scenario: Бейдж описан как награда за кофе
    Given ADR-0010 после правки
    When читатель смотрит раздел D4 про consumable-товары
    Then там сказано, что первая покупка кофе навсегда выдаёт косметический бейдж
    And сказано, что бейдж не является entitlement

  Scenario: Открытый вопрос закрыт
    Given ADR-0010 после правки
    When читатель смотрит список Open items
    Then вопроса о видимости бейджа другим участникам workspace там нет

  Scenario: TDD перестаёт противоречить сам себе по числу разрешений
    Given TDD после правки
    When читатель сравнивает счётчик разрешений в разделе про манифест и в итоговой таблице
    Then обе цифры равны шести
```

## Gap / context
ADR-0010 D2 (строка 64) относит Supporter badge к возможностям Plus, а D4 (строки 81-90) прямо
говорит, что кофе не даёт никаких прав. Реализуемая модель выдаёт вечный бейдж именно за кофе.
Если начать с кода, эпик будет писаться против записи решения, которая утверждает обратное, —
и следующий читатель ADR получит ложную картину продукта.

## Implementation links
- commit: 45e8103a, 6852733c
- files: docs/DECISIONS/ADR-0010-monetization.md, TDD/MyMoney/MyMoney_TDD.md
