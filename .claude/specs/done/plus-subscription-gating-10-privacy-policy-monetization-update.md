# Privacy policy: Block 3 — формулировки про Shared workspace и хранение entitlement
Epic: plus-subscription-gating
Order: 10 of 10
Status: done
Depends-on: plus-subscription-gating-09
Date: 2026-08-12
Acceptance-matrix: locale=en,ru; surface=app_asset,pages_site; block=shared-workspace-wording,shared-entitlement-paragraph,last-updated-date
Risk-signals: —

## SPEC
=== SPEC ===
TASK: docs
PLATFORM: android
WHAT: Применить **Block 3** черновика `docs/legal/privacy-policy-monetization-draft.md:141-174` — он
  закреплён именно за этим эпиком и обязан выйти в том же релизе, что флип
  `PLAY_RELEASE_SYNC_ENABLED` (SPEC 09, ADR-0010 §D1). Снять оговорки «compatible builds» в обеих
  локалях, привести формулировки к уже корректной строке 49 EN и добавить абзац о том, что доступ к
  общему воркспейсу требует активного права Plus, которое хранится в бэкенде Supabase и привязано к
  аккаунту, а не к устройству.
LAYERS: docs, legal
CHANGED_HINT:
  - `app/src/main/assets/privacy_policy_en.html:33` (G21) — «…are stored in the encrypted secure
    store **if that feature is enabled in a compatible build**» → «…are stored in the encrypted
    secure store **when you sign in to a Shared workspace**» (текст дословно из
    `privacy-policy-monetization-draft.md:154`).
  - `app/src/main/assets/privacy_policy_en.html:48` (G21) — «**Compatible builds may include** an
    optional Shared workspace feature. When that feature is enabled, users can sign in…» → «**The
    release build includes** an optional Shared workspace feature. Users can sign in…»
    (`…draft.md:155`).
  - `app/src/main/assets/privacy_policy_ru.html:33` и `:48` (G21 — RU-зеркало существует) — RU-замены
    из `…draft.md:156-157`.
  - Оба файла, подсекция Shared workspace (EN рядом с `:49`, RU симметрично) — вставить готовый
    абзац про право доступа Plus в Supabase: EN из `…draft.md:161-164`, RU из `…draft.md:168-172`.
  - `app/src/main/assets/privacy_policy_{en,ru}.html:21` (G21) — `Last updated` на дату релиза, если
    `support-hub-tip-08` не сдвинул её раньше в этом же релизе.
  - Опубликованная копия на GitHub Pages (`https://desvingns.github.io/MyMoney/privacy-policy/`,
    ссылка стоит в `privacy_policy_en.html:21` — G21) — обновить тем же содержимым.
TEST_TYPES: none
CONSTRAINTS:
  - **Только Block 3.** Block 1 (покупки) и Block 2 (сервисы Firebase, строка 55) принадлежат
    `support-hub-tip-08`; Block 4 (AdMob, рекламный идентификатор) — `support-rewarded-ads`
    (`privacy-policy-monetization-draft.md:22-29`). Не трогать строку 55 и не описывать здесь ни
    покупки, ни Analytics, ни рекламу: дублирование абзацев в юридическом документе хуже, чем их
    отсутствие.
  - **Тот же релиз, что SPEC 09** — черновик задаёт правило «блок применяется в релизе, который
    везёт описанное поведение, и никогда раньше» (`…draft.md:15-20`). До флипа флага формулировка
    «release build includes» была бы ложной.
  - Не приписывать Remote Config управление Shared workspace **в старом смысле**: после SPEC 09
    `sharedSyncEnabled()` действительно читает Remote Config, поэтому запрет из
    `…draft.md:106-109` (написанный до killswitch) больше не актуален — но описание роли Remote
    Config это зона Block 2 / `support-hub-tip-08`, а не этого SPEC-а. Если `support-hub-tip-08` уже
    выехал с формулировкой «Remote Config не управляет Shared», её придётся поправить — отметить
    это в Implementation links.
  - Тексты берутся из черновика **дословно**, не переписываются: он уже сверен по обеим локалям.
  - Структуру HTML (стили, `<header>`, футер, canonical-ссылки) не менять — только замена
    формулировок и вставка одного абзаца.
  - EN и RU правятся только вместе; расхождение по составу разделов недопустимо.
  - Форма Play Data Safety этим SPEC-ом не покрывается (`…draft.md:215-219`). *(assumption, O3)*
=== END SPEC ===

## Acceptance

```gherkin
Feature: Политика описывает общий воркспейс релизной сборки

  Scenario: Оговорки о «совместимых сборках» сняты
    Given пользователь открывает политику конфиденциальности
    Then описание общего воркспейса сформулировано утвердительно
    And оговорки о том, что возможность доступна лишь в совместимых сборках, отсутствуют

  Scenario: Хранение права доступа описано
    Given пользователь читает подраздел про общий воркспейс
    Then там сказано, что доступ требует активного права Plus
    And что право хранится в бэкенде проекта и привязано к аккаунту, а не к устройству

  Scenario: Обе локали синхронны
    Given пользователь переключает язык приложения на русский
    When он открывает политику конфиденциальности
    Then состав разделов и смысл формулировок совпадают с английской версией

  Scenario: Чужие блоки не тронуты
    Given политика после этой правки
    Then раздел про покупки остаётся в том виде, в каком его оставил эпик поддержки
    And реклама в политике не упоминается

  Scenario: Дата обновлена
    Given пользователь открывает политику
    Then дата последнего обновления совпадает с датой релиза
```

## Gap / context

Три места политики утверждают, что Shared доступен лишь в «совместимых сборках» (G21 — строки 33 и
48 в обеих локалях), а после флипа флага в SPEC 09 это перестаёт быть правдой для всех пользователей.
Одновременно появляется новый факт, которого в тексте нет вовсе: доступ к общему воркспейсу теперь
требует права Plus, и это право хранится на сервере проекта. Формулировки уже подготовлены в
`docs/legal/privacy-policy-monetization-draft.md` и закреплены за этим эпиком.

## Implementation links
- commit: `3cd4107b` (Block 3 wording, all 4 policy files + draft status header), `e55a843e`
  (Stale-Test Update Rule reconciliation of `PrivacyPolicyAdvertisingContractTest`)
- files: `app/src/main/assets/privacy_policy_en.html`, `app/src/main/assets/privacy_policy_ru.html`,
  `privacy-policy/en/index.html`, `privacy-policy/ru/index.html`,
  `docs/legal/privacy-policy-monetization-draft.md`,
  `app/src/test/java/com/kshavrin/mymoney/PrivacyPolicyAdvertisingContractTest.kt`
- Actual line numbers at implementation time differed from the SPEC's original CHANGED_HINT guesses
  (`:33`/`:48` → actually `:33`/`:57-58` EN, `:33`/`:58-59` RU) due to insertions by
  `support-hub-tip-08` and `support-rewarded-ads-06` earlier the same day; verified against current
  file state before dispatch, not blindly trusted.
- Semantic review + independent critic both passed clean at 12/12 acceptance-matrix coverage, risk
  standard. Full runner: 2301 passed / 0 failed / 0 skipped, detekt/lint ok.
