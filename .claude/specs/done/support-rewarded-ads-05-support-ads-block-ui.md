# Блок «Посмотреть рекламу» на экране поддержки: все обязательные состояния
Epic: support-rewarded-ads
Order: 05 of 06
Status: done
Depends-on: 03, 04, plus-subscription-gating
Date: 2026-08-12
Acceptance-matrix: ui_state=unauthenticated,available_with_progress,no_fill,region_unavailable,offline,plus_active,pending_confirmation,interrupted
Risk-signals: auth, entitlement, server-authoritative, offline, navigation, cross-layer

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На экране «Поддержать проект» появляется блок награждаемой рекламы: объяснение правила «5 роликов — 24 часа Plus», серверный прогресс «просмотрено 3 из 5» и кнопка запуска. Блок видим всегда, включая действующих подписчиков Plus, и честно объясняет каждое своё состояние: неавторизованному предлагает войти, при активном Plus объясняет, что просмотр засчитается как поддержка, но время не добавится и счётчик не вырастет, при отсутствии заполнения гасит кнопку с текстом «сейчас нет доступных роликов», при серии отказов объясняет недоступность рекламы в регионе, а после досмотра показывает «ждём подтверждения сервера», пока сервер не подтвердит. Слова «начислено» до подтверждения нет ни в одном состоянии.
LAYERS: presentation
CHANGED_HINT:
  - feature/support/src/main/kotlin/com/kshavrin/mymoney/feature/support/RewardedAdBlock.kt — НОВЫЙ: composable блока, все состояния из `AdAvailability` (SPEC 03) × `AdRewardState` (SPEC 04) (assumption: точный путь появится вместе с модулем из plus-subscription-gating)
  - feature/support/src/main/kotlin/com/kshavrin/mymoney/feature/support/SupportViewModel.kt — расширение существующего VM: события «тап посмотреть», «повторить», «войти», действия навигации; UDF-форма по образцу `SettingsViewModel` (G4: `feature/settings/src/main/java/com/kshavrin/mymoney/feature/settings/root/SettingsViewModel.kt:21-156`)
  - feature/support/src/main/kotlin/com/kshavrin/mymoney/feature/support/SupportScreen.kt — врезка блока в контент экрана; Route/Content-разделение по образцу `SettingsRootScreen.kt:50-98` и `:85-268` (G4)
  - feature/support/src/main/res/values/strings.xml — EN-строки всех состояний, ключи с префиксом `support_ads_` (G8: `feature/settings/src/main/res/values/strings.xml:2,12,23`)
  - feature/support/src/main/res/values-ru/strings.xml — RU-перевод один в один по ключам (иначе lint `MissingTranslation` = error, G36)
  - feature/support/build.gradle.kts — зависимости на `:core:ads` и `:core:domain`
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Cross-epic:** все перечисленные файлы создаёт эпик `plus-subscription-gating` (модуля
    `:feature:support` сейчас нет, G1). SPEC нельзя брать, пока тот эпик не закрыт; при старте
    сверить фактические имена файлов и путей, а не полагаться на предположенные выше.
  - Блок остаётся видимым для действующих подписчиков Plus — скрывать его запрещено.
  - Тексты состояний обязаны различать «нет роликов сейчас» и «реклама недоступна в вашем регионе»;
    ни то ни другое не подаётся как ошибка приложения (G26).
  - Ни одно состояние не сообщает о начислении до подтверждения сервером (ADR-0010 D5, G22).
  - Индикатор загрузки ограничен по времени — состояние «крутится вечно» запрещено (H4). При этом
    вердикт «недоступно в регионе» не персистится, поэтому одна попытка на холодный старт — это
    ожидаемое, принятое поведение (D5), а не баг.
  - Хардкод пользовательских строк запрещён — только ресурсы, EN + RU (G8).
  - `:feature:*` не зависит от `:feature:*` — только на `:core:*` (правило проекта).
  - Тесты ViewModel с `savedStateHandle.toRoute<…>()` — только под `@RunWith(RobolectricTestRunner)`
    + `@Config(sdk=[34])` (G38).
  - Только Fakes: `FakeAdRewardRepository` из SPEC 04 плюс фейковый `AdGateway` (G39).
  - a11y: кнопка и прогресс получают внятные семантики; прогресс читается как текст, а не только
    как полоска.
DESIGN_TOKENS: colorScheme.rewardAdProgressIndicator, colorScheme.rewardAdProgressTrack
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Блок награждаемой рекламы в разделе поддержки

  Scenario: Неавторизованному предлагают войти
    Given пользователь не авторизован
    When открывается раздел поддержки
    Then блок объясняет, что для награды нужен вход
    And показывает действие входа вместо кнопки просмотра

  Scenario: Прогресс показывается с сервера
    Given сервер сообщает, что засчитано 3 из 5
    When открывается раздел поддержки
    Then блок показывает «просмотрено 3 из 5»

  Scenario: Реклама недоступна в регионе
    Given гейтвей сообщает о недоступности рекламы в регионе
    When открывается раздел поддержки
    Then блок показывает объяснение недоступности
    And не показывает ни индикатор загрузки, ни сообщение об ошибке

  Scenario: Нет доступных роликов
    Given гейтвей сообщает об отсутствии заполнения
    When открывается раздел поддержки
    Then кнопка просмотра неактивна
    And показан текст о том, что роликов сейчас нет и стоит попробовать позже

  Scenario: Plus уже активен
    Given у пользователя активен Plus
    When открывается раздел поддержки
    Then блок остаётся видимым
    And объясняет, что просмотр засчитается как поддержка, но время не добавится и счётчик не вырастет

  Scenario: Ожидание подтверждения не врёт
    Given пользователь досмотрел ролик
    When сервер ещё не подтвердил награду
    Then блок сообщает, что просмотр засчитается после подтверждения сервером
    And не сообщает о начислении

  Scenario: Просмотр прерван
    Given пользователь закрыл ролик до конца
    When он возвращается на экран
    Then прогресс не изменился
    And сообщения об ошибке нет

  Scenario: Оффлайн
    Given сети нет
    When пользователь нажимает «посмотреть рекламу»
    Then блок объясняет отсутствие сети и предлагает повторить
```

## Gap / context
Требование пользователя: главный сценарий тестовой аудитории — пользователь в РФ, для которого
недоступность рекламы постоянна (G26). Блок обязан объяснять это состояние, а не показывать ошибку
или вечный спиннер. Всё остальное — честное отображение серверного состояния из SPEC 04 и
состояний гейтвея из SPEC 03.

## Implementation links
- commit: `9ea63800` (feature) + `be3f5beb` (layer-boundary repair) + `4dd41972` (semantic-review
  repair) + `6e6070f6` (tests) + `034b578f` (independent-critic repair)
- files: `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdState.kt`,
  `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdViewModel.kt`,
  `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt`,
  `feature/support/src/main/res/values/strings.xml`, `feature/support/src/main/res/values-ru/strings.xml`,
  `feature/support/build.gradle.kts`, `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt`,
  `core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/ObserveAdRewardStateUseCase.kt`,
  `core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/ObserveAdRewardStateUseCaseTest.kt`,
  `core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAdRewardRepository.kt`,
  `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/FakeAdGateway.kt`,
  `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdViewModelTest.kt`,
  `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreenContentTest.kt`,
  `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/PaywallStringsTest.kt`
