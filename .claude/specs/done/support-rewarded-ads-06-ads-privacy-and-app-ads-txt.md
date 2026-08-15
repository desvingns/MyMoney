# Приватность под рекламу и app-ads.txt в том же релизе, что SDK
Epic: support-rewarded-ads
Order: 06 of 06
Status: done
Depends-on: 02
Date: 2026-08-12
Risk-signals: —
Acceptance-matrix: locale=en,ru; surface=app_asset,pages_site; check=ad_block_present,date_in_sync,app_ads_txt_root,draft_marked_applied

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Блок «Advertising (Google AdMob)» из черновика переезжает в обе действующие политики — в ассеты приложения и в опубликованные на GitHub Pages страницы, — дата «last updated» обновляется в обеих языковых версиях, и рядом с политикой появляется app-ads.txt. Черновик помечается применённым. Отдельно проверяется, что опубликованная версия совпадает с той, что лежит в ассетах, и что app-ads.txt реально доступен по адресу, который обходит краулер AdMob, — иначе файл бесполезен.
LAYERS: docs, release
CHANGED_HINT:
  - app/src/main/assets/privacy_policy_ru.html — добавить раздел про рекламу; обновить «Last updated» (G32: строка 21)
  - app/src/main/assets/privacy_policy_en.html — то же, EN-версия (G32: строка 21)
  - privacy-policy/ru/index.html — та же правка в опубликованной версии (фактическая раскладка Pages: `privacy-policy/{en,ru}/index.html`, а не `privacy_policy_*.html`)
  - privacy-policy/en/index.html — то же
  - privacy-policy/app-ads.txt — НОВЫЙ; строка издателя AdMob вида `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (значение — из AdMob-консоли, человеческий чеклист)
  - .github/workflows/privacy-policy-pages.yml:29-42 — workflow копирует только `privacy-policy/` и `account-deletion/` в одноимённые подпапки gh-pages; чтобы `app-ads.txt` оказался в корне сайта, копирование корневого файла нужно добавить явно (assumption: точная форма правки)
  - docs/legal/privacy-policy-monetization-draft.md:3,176-211 — снять статус «Draft, not published» с блока Advertising, отметить релиз применения (G31)
TEST_TYPES: unit
CONSTRAINTS:
  - **Не мержить раньше SPEC 02.** Политика с рекламным блоком не должна выходить раньше самого SDK,
    но и не позже: ADR-0010 требует один и тот же релиз (G28).
  - Обе языковые версии обязаны получить одинаковую дату и одинаковый по смыслу блок. Расхождение
    между ассетом в APK и опубликованной страницей — это самостоятельный дефект.
  - **Гоча адреса app-ads.txt.** Краулер AdMob запрашивает файл в КОРНЕ домена сайта разработчика
    (`https://<домен>/app-ads.txt`), а не рядом с политикой. Текущий workflow кладёт содержимое в
    подпапку `privacy-policy/`, а сайт — проектный Pages (`/<repo>/`), поэтому файл по нужному
    адресу сам собой не появится. SPEC обязан либо добиться корневого размещения на том домене,
    который указан как сайт разработчика в Play, либо явно зафиксировать в чеклисте, что размещение
    делается в корневом Pages-репозитории вручную. Молча положить файл «куда-нибудь» нельзя.
  - Значение `pub-…` берётся из AdMob-консоли (человеческий чеклист в overview); в репозиторий
    коммитится реальная строка — она публична по определению.
  - Флаг «Содержит рекламу» и Data safety в Play — человеческий чеклист, не этот SPEC.
  - Проверка публикации обязательна: после мержа в `main` workflow (`push` по путям
    `privacy-policy/**`) должен отработать, и опубликованная страница должна показывать новую дату.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Приватность и app-ads.txt под рекламу

  Scenario: Обе политики в приложении описывают рекламу
    Given собрана сборка с рекламным SDK
    When открывается политика в приложении на русском и на английском
    Then обе версии содержат раздел про рекламу и рекламный идентификатор
    And обе показывают одинаковую обновлённую дату

  Scenario: Опубликованная версия совпадает с версией в приложении
    Given изменения смержены в main и workflow отработал
    When открывается опубликованная страница политики
    Then её текст и дата совпадают с версией из ассетов приложения

  Scenario: app-ads.txt доступен по адресу, который проверяет AdMob
    Given app-ads.txt добавлен и опубликован
    When файл запрашивается по корневому адресу сайта разработчика из листинга Play
    Then он отдаётся и содержит строку издателя AdMob

  Scenario: Политика не выходит раньше SDK
    Given рекламный блок политики готов
    When планируется релиз
    Then он содержит и рекламный SDK, и обновлённую политику
```

## Gap / context
Черновик блока Advertising существует и помечен «Draft, not published» (G31), а действующие политики
описывают приложение без рекламы и без покупок. ADR-0010 (G28) требует выпустить рекламный блок в том
же релизе, что и SDK — не раньше и не позже. Отдельно вскрыто, что текущий workflow публикует
`privacy-policy/{en,ru}/index.html` в подпапку, поэтому наивно положенный рядом `app-ads.txt` окажется
по адресу, который краулер AdMob не проверяет.

## Implementation links
- commit: 5bb342a4 (impl), 0d49d0d6 (contract test)
- files: app/src/main/assets/privacy_policy_{en,ru}.html; privacy-policy/{en,ru}/index.html;
  privacy-policy/app-ads.txt; .github/workflows/privacy-policy-pages.yml;
  docs/legal/privacy-policy-monetization-draft.md;
  app/src/test/java/com/kshavrin/mymoney/PrivacyPolicyAdvertisingContractTest.kt
