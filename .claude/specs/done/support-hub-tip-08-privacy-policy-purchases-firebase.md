# Политика конфиденциальности: покупки и сервисы Firebase
Epic: support-hub-tip
Order: 08 of 08
Status: done
Depends-on: 03, 06
Date: 2026-08-12
Acceptance-matrix: locale=en,ru; block=purchases,firebase-variant-b,last-updated-date
Risk-signals: —

## SPEC
=== SPEC ===
TASK: docs
PLATFORM: android
WHAT: Опубликованная политика перестаёт описывать приложение, которого больше нет. В обе локали добавляется готовый блок «Purchases (Google Play Billing)» из черновика, а абзац «Services and feature flags», сегодня утверждающий «Firebase Remote Config is not enabled in this release», заменяется честным описанием двух реально работающих SDK — Remote Config и Analytics. Дата «Last updated» на строке 21 обоих файлов двигается на дату релиза. Оба файла публикуются на GitHub Pages, поэтому это правка публичного юридического документа, а не строки в приложении.
LAYERS: docs, legal
CHANGED_HINT:
  - app/src/main/assets/privacy_policy_en.html — вставить блок «Purchases (Google Play Billing)» как новый `<h3>` в секцию «Information sent over the network» после подсекции Dropbox/Google Drive; текст взять дословно из `docs/legal/privacy-policy-monetization-draft.md:36-45` (G31, G33)
  - app/src/main/assets/privacy_policy_ru.html — то же, RU-текст из `docs/legal/privacy-policy-monetization-draft.md:50-58` (G31, G33)
  - app/src/main/assets/privacy_policy_en.html:55 — заменить абзац «Firebase Remote Config is not enabled in this release…» на **вариант B** из `docs/legal/privacy-policy-monetization-draft.md:94-103` (G30, G31, D9)
  - app/src/main/assets/privacy_policy_ru.html:55 — RU-вариант B из `docs/legal/privacy-policy-monetization-draft.md:126-136` (G30, G31, D9)
  - app/src/main/assets/privacy_policy_en.html:21 и privacy_policy_ru.html:21 — обновить `Last updated` на дату релиза (G29)
TEST_TYPES: none
CONSTRAINTS:
  - **Вариант B, не A.** Черновик велит брать вариант A, пока `firebase-analytics` реально не в сборке (`privacy-policy-monetization-draft.md:68-78`); SPEC-06 его добавляет, поэтому к моменту этой правки истинен вариант B. Если SPEC-06 по какой-то причине не поехал в тот же релиз — брать вариант A и не описывать Analytics.
  - **Не приписывать Remote Config управление Shared workspace.** `sharedSyncEnabled()` (`core/sync/.../RemoteConfigRepositoryImpl.kt:55-59`) читает только BuildConfig-флаги; константы `KEY_SHARED_SYNC`/`DEFAULT_SHARED_SYNC` объявлены, но не читаются. Прямой запрет — `privacy-policy-monetization-draft.md:106-109` и ADR-0010 §D1.
  - **Блоки 3 и 4 черновика не трогать.** Block 3 (формулировки про Shared workspace) принадлежит эпику `plus-subscription-gating`, Block 4 (AdMob, рекламный идентификатор) — эпику `support-rewarded-ads`. Объявить сбор рекламного ID, которого в сборке нет, — та же ложь, что умолчать о реальном сборе.
  - Правило последовательности: блок применяется **в том релизе, который везёт описанное поведение, и никогда раньше** (`privacy-policy-monetization-draft.md:15-20`). Поэтому SPEC зависит от 03 и 06 и стоит последним в эпике.
  - EN и RU — переводы одного документа: расходиться по смыслу они не имеют права, правятся только вместе.
  - **Форма Play Data Safety этой правкой не покрывается** и обновляется отдельно в Play Console: текущий листинг заявляет отсутствие покупок (ADR-0010:194-195, `privacy-policy-monetization-draft.md:217-219`). Отметить факт обновления в Implementation links.
  - Структуру HTML (стили, `<header>`, футер, canonical-ссылки) не переписывать — только вставка блока, замена абзаца и дата.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Политика описывает реальное поведение релиза

  Scenario: Покупки объявлены в обеих локалях
    Given опубликованную политику после правки
    When читатель открывает секцию про передачу данных по сети
    Then там есть раздел про покупки через Google Play Billing
    And он присутствует и в английской, и в русской версии

  Scenario: Ложное утверждение про Firebase убрано
    Given опубликованную политику после правки
    When читатель ищет фразу о том, что Remote Config не включён
    Then такой фразы в документе нет
    And вместо неё описаны Remote Config и Analytics

  Scenario: Remote Config не выдаётся за переключатель общего доступа
    Given опубликованную политику после правки
    When читатель читает раздел про сервисы Firebase
    Then Remote Config не описан как управляющий общими рабочими пространствами

  Scenario: Реклама не объявлена раньше времени
    Given релиз без рекламного SDK
    When читатель ищет раздел про рекламу и рекламный идентификатор
    Then такого раздела в документе нет

  Scenario: Дата обновлена
    Given опубликованную политику после правки
    When читатель смотрит строку «Last updated»
    Then там стоит дата этого релиза
```

## Gap / context
Строка 55 обоих файлов сегодня утверждает «Firebase Remote Config is not enabled in this
release» (G30). Добавление `google-services.json` ради Analytics включает **оба** SDK разом —
они сидят на одном `BuildConfig.HAS_FIREBASE` (G16, G17), — поэтому одного нового абзаца про
покупки недостаточно: без замены строки 55 опубликованный документ станет ложным ровно в тот
момент, когда релиз выйдет.

## Implementation links
- commit: d6dd1eb6, 81bf0462, 87d30326, 5c6ed25e
- files: app/src/main/assets/privacy_policy_en.html, app/src/main/assets/privacy_policy_ru.html,
  privacy-policy/en/index.html, privacy-policy/ru/index.html,
  app/src/test/java/com/kshavrin/mymoney/PrivacyPolicyAdvertisingContractTest.kt
- Play Data Safety form: NOT updated by this SPEC (out of scope per CONSTRAINTS) — remains a
  separate manual Play Console step (current listing still claims no purchases).
- Repair notes: one semantic-review repair cycle (SCOPE-001 — GitHub Pages mirror files were
  initially missed, then synced in 81bf0462); one runner auto-fix retry (test-precision fix for a
  hard-wrapped-newline false negative in the RU Firebase assertion, 5c6ed25e — no content change).
  Independent critic + final verifier both passed clean.
