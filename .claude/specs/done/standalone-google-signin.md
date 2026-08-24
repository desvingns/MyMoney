# Лёгкий вход через Google для rewarded-ad блока, без CloudSync
Status: done
Date: 2026-08-17
Acceptance-matrix: dialog_state=idle,loading,success,error,cancelled
Risk-signals: auth, session, di-wiring, navigation, cross-layer, visual

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Логика получения Google ID-token (`CredentialManager`) переезжает из `CloudSyncScreen.kt`
  в переиспользуемый `GoogleIdTokenProvider` в `:core:network`; поверх него в `:feature:support`
  появляется `GoogleSignInDialog` + `GoogleSignInViewModel`, вызывающий
  `SharedAuth.signInWithGoogle` через новый `SignInWithGoogleUseCase` в `:core:domain`; кнопка
  «Войти» rewarded-ad блока на экране поддержки открывает этот диалог вместо навигации на
  `Destinations.CloudSync` и её модального `SharedSetupDialog`.
LAYERS: domain data presentation
CHANGED_HINT:
  - `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/GoogleIdTokenProvider.kt`
    (новый) — интерфейс `GoogleIdTokenProvider { suspend fun fetchIdToken(activity: Activity):
    Result<GoogleIdTokenResult> }` + `data class GoogleIdTokenResult(idToken: String, nonce: String)`.
  - `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/CredentialManagerGoogleIdTokenProvider.kt`
    (новый) — impl; портирует 1:1 (без изменения поведения, включая ретрай на `NoCredentialException`
    без `authorizedAccountsOnly` и отсутствие таймаута) тело `launchSharedGoogleSignIn()` +
    `generateGoogleNonce()`/`sharedGoogleCredentialNonce()`/`normalizeSharedGoogleWebClientId()`/
    `sharedGoogleWebClientIdOrNull()`/`sharedGoogleCredentialRequest()` из
    `CloudSyncScreen.kt:219-273,1507-1543` (G1 ниже). `CloudSyncScreen.kt` при этом **не
    редактируется** — держит свою собственную копию этого кода как есть.
  - `core/network/build.gradle.kts:54-76` (G2) — добавить `libs.androidx.credentials`,
    `libs.androidx.credentials.play.services.auth`, `libs.googleid` (те же алиасы, что уже
    использует `feature/cloudsync/build.gradle.kts:28-30`); из `feature/cloudsync/build.gradle.kts`
    **не убирать** — там своя рабочая копия кода, ей нужны свои зависимости.
  - `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedModule.kt:12-19`
    (G3) — добавить `@Binds @Singleton abstract fun bindGoogleIdTokenProvider(impl:
    CredentialManagerGoogleIdTokenProvider): GoogleIdTokenProvider`.
  - `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SignInWithGoogleUseCase.kt`
    (новый) — тонкая обёртка над `SharedAuth.signInWithGoogle(idToken, nonce)` по шаблону
    `ObserveAdRewardStateUseCase` (G8), но живёт в `:core:network` рядом с `SharedAuth`/
    `SupabaseSharedAuth` — **не** в `:core:domain` (см. D5' в Design notes: `:core:network` уже
    зависит от `:core:domain`, обратное ребро даёт цикл + Gradle не мэтчит JVM-вариант `:core:domain`
    с Android-вариантом `:core:network`, подтверждено реальным `compileKotlin`). `:core:domain` в
    этом SPEC не меняется вообще.
  - `feature/support/build.gradle.kts` (полный файл, 30 строк) — добавить
    `implementation(project(":core:network"))` (для прямой инъекции `GoogleIdTokenProvider` в
    `GoogleSignInViewModel`, см. D6 — тот же паттерн, что уже `AdGateway` в `RewardedAdViewModel`, G4).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInViewModel.kt`
    (новый) — `@HiltViewModel`; конструктор: `GoogleIdTokenProvider` (напрямую, G4/D6) +
    `SignInWithGoogleUseCase` (через UseCase, D5). Состояние `Idle | Loading | Success | Error`;
    метод `fun signIn(activity: Activity)`.
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInDialog.kt`
    (новый) — `GoogleSignInDialog(activity: Activity, onDismiss: () -> Unit, onSignedIn: () -> Unit,
    viewModel: GoogleSignInViewModel = hiltViewModel())` + `GoogleSignInDialogContent(...)` по
    конвенции `RateConfirmDialog`/`RateConfirmDialogContent` (`Name`/`NameContent` split, G7):
    `androidx.compose.ui.window.Dialog` обёртка, честные loading/error состояния (без вечных
    спиннеров, без ложного success), кнопка повтора при ошибке, отмена закрывает диалог без
    побочных эффектов.
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:39-54,241-246`
    (G6) — `RewardedAdSupportEntry` теряет параметр `onSignIn: () -> Unit`; заводит локальное
    `var showSignIn by remember { mutableStateOf(false) }`; `RewardedAdContent`'у передаётся
    `onSignIn = { showSignIn = true }`; при `showSignIn` и разрешимой `context.findActivity()`
    рендерится `GoogleSignInDialog(activity = ..., onDismiss = { showSignIn = false }, onSignedIn =
    { showSignIn = false; context.findActivity()?.let(viewModel::onRetry) })` — переиспользует уже
    существующий `onRetry(activity)` (`RewardedAdViewModel.kt:48-51`, G6) для перечитывания
    состояния после входа; новый метод во `RewardedAdViewModel` не нужен и не добавляется.
  - `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:252-254` — убрать
    `onSignIn = { navController.navigate(Destinations.CloudSync) }` из вызова
    `RewardedAdSupportEntry(...)` (параметр больше не существует).
  - `feature/support/src/main/res/values/strings.xml` + `values-ru/strings.xml` — новые строки
    диалога (заголовок, loading, ошибка, повтор, отмена); текст самой кнопки «Войти» в
    rewarded-ad блоке (`support_ads_sign_in_action`) не меняется — меняется только её обработчик.
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - `CloudSyncScreen.kt` и `CloudSyncViewModel.kt` не редактируются ни строкой — существующий
    вход на CloudSync и его `SharedSetupDialog` не меняют поведения.
  - `:feature:support` и весь новый код в `:core:network` (включая `SignInWithGoogleUseCase`) —
    не зависят и не вызывают `:core:sync`/`SharedSyncCoordinator`/`:feature:cloudsync` (forbidden
    edges, см. Design notes). `:core:domain` в этом SPEC не меняется вообще (D5'). Модульное
    правило проекта — `:feature:*` только на `:core:*`.
  - `Context.findActivity()` — приватный extension, дублируется в
    `feature/support/.../googlesignin/GoogleSignInDialog.kt` (или переиспользуется через параметр
    `activity`, если проще) — **не** выносится в общий `:core:ui` модуль (устоявшаяся в проекте
    конвенция дублирования, тот же паттерн уже в `CloudSyncScreen.kt`/`RewardedAdScreen.kt`, G6).
  - Честные loading/error состояния диалога: никогда не сообщать успех до реального ответа
    `SharedAuth.signInWithGoogle`, никаких бесконечных спиннеров.
  - Тесты — только Fakes (`GoogleIdTokenProvider`, `SharedAuth`, `SignInWithGoogleUseCase`), без
    мокинг-фреймворков; реальный `CredentialManager` в unit/Robolectric не вызывается.
  - Требуется ручная проверка на реальном устройстве (Pixel 5 API 34, реальный Google-аккаунт) —
    юнит/Robolectric не подтверждают настоящий OAuth-флоу (`external-oauth-verification`, второй
    мозг). Пункт входит в manual checklist Verifier'а.
  - Новые UI-строки — английский default + русский перевод в `values-ru/strings.xml` (конвенция
    проекта, работает существующий gate на пропущенный перевод).
  - a11y: touch-target ≥ 48dp у кнопок диалога, `contentDescription`/`Text` читаемы TalkBack.
=== END SPEC ===

## Design notes (архитектурные решения, зафиксированные в grill)

- **D5' (исправлено после первой попытки Developer'а — см. Implementation links):**
  Первоначальное решение D5 («UseCase в `:core:domain`») оказалось физически невозможным:
  `:core:network` **уже** объявляет `implementation(project(":core:domain"))`
  (`core/network/build.gradle.kts:55`), поэтому обратное ребро `:core:domain → :core:network` —
  цикл; вдобавок `:core:domain` (`mymoney.jvm.library`, `platform.type=jvm`) физически не может
  зависеть от Android-варианта `:core:network` (`androidJvm`) — подтверждено реальным падением
  `:core:domain:compileKotlin`. Решение: `SignInWithGoogleUseCase` остаётся тонкой UseCase-обёрткой
  (форма как у `ObserveAdRewardStateUseCase`, G8), но живёт **в `:core:network`**, рядом с
  `SharedAuth`/`SupabaseSharedAuth` — без единого нового ребра module-графа. `:core:domain` в этом
  SPEC не трогается вообще; правило «ViewModel → Repository только через UseCase» по-прежнему
  соблюдено (`GoogleSignInViewModel` вызывает `SignInWithGoogleUseCase`, не `SharedAuth` напрямую) —
  меняется только модуль, в котором класс физически лежит, не архитектурный слой отношений.
- **D6**: `GoogleIdTokenProvider` (требует живой `Activity` для `CredentialManager`) инжектится в
  `GoogleSignInViewModel` напрямую — так же, как `RewardedAdViewModel` сегодня инжектит `AdGateway`
  напрямую (G4). И `GoogleIdTokenProvider`, и `SignInWithGoogleUseCase` теперь оба живут в
  `:core:network`, но инъекция первого остаётся прямой (Activity-обвязка), а второго — через
  UseCase (чистый data-вызов), сохраняя смысл правила, а не только его module-геометрию.
- **State ownership**: `GoogleSignInViewModel` владеет только переходным состоянием входа
  (`Idle → Loading → Success | Error`), скоуп — жизненный цикл диалога. `RewardedAdSupportEntry`
  сам инициирует перечитывание через существующий `onRetry(activity)` после `onSignedIn` —
  отдельная реактивная модель сессии не заводится.
- **Timeout budget**: новый не вводится — портируемый код `getCredential()` сегодня не обёрнут в
  `withTimeoutOrNull`, порт сохраняет это поведение 1:1.

## Ключевые факты (verified, из grounding — Explore-агент + прямые Read/Grep 2026-08-17)

- **G1**: вся логика получения Google ID-token сегодня живёт только в `CloudSyncScreen.kt` —
  `launchSharedGoogleSignIn()` (219-273) + хелперы `generateGoogleNonce()` (1507-1510),
  `sharedGoogleCredentialNonce()` (1512-1516), `normalizeSharedGoogleWebClientId()` (1518-1522),
  `sharedGoogleWebClientIdOrNull()` (1524-1526), `sharedGoogleCredentialRequest()` (1528-1543).
  Использует `androidx.credentials.{CredentialManager, CustomCredential, GetCredentialRequest,
  NoCredentialException}` + `com.google.android.libraries.identity.googleid.{GetGoogleIdOption,
  GoogleIdTokenCredential}`; на `NoCredentialException` — ретрай с `authorizedAccountsOnly = false`
  (238-248); никакого `withTimeoutOrNull` вокруг вызова нет.
- **G2**: `core/network/build.gradle.kts:54-76` сегодня не тянет ни `androidx.credentials`, ни
  `googleid`, ни `Activity`/Compose — только Hilt, OkHttp, Retrofit, kotlinx.serialization,
  coroutines-core; но это уже `android.library` (есть `android {}` блок). Каталог уже содержит
  нужные алиасы (те же, что `feature/cloudsync/build.gradle.kts:28-30` использует):
  `libs.androidx.credentials`, `libs.androidx.credentials.play.services.auth`, `libs.googleid`
  (`gradle/libs.versions.toml:66-68`).
- **G3**: `SharedAuth.signInWithGoogle(googleIdToken, nonce): Result<SharedSession>` —
  `core/network/.../shared/SharedAuth.kt:37-40`; `currentSession(): SharedSession?` синхронный
  (:28), без Flow. Bind — `SharedModule.kt:17-19`, impl `SupabaseSharedAuth.kt:51-84`, зовёт
  `authSessionLifecycle.invalidate()` только при успехе (:81).
- **G4**: `RewardedAdViewModel` (`feature/support/.../rewardedad/RewardedAdViewModel.kt:29-36`) уже
  сейчас инжектит `AdGateway` (`:core:ads`) напрямую в конструктор, без UseCase — прецедент для
  прямой инъекции Android-SDK-обёртки, требующей `Activity`, в ViewModel.
- **G5**: `:core:domain/build.gradle.kts` — `mymoney.jvm.library` (чистый JVM, без Android),
  зависит только от `:core:common`; ноль текущих упоминаний `SharedAuth` где-либо в
  `:core:domain`/`:feature:*`.
- **G6**: `RewardedAdScreen.kt:39-54,241-246` — `RewardedAdSupportEntry(onSignIn: () -> Unit,
  viewModel = hiltViewModel())`; `onWatch`/`onRetry` уже резолвят `Activity` через собственный
  private `Context.findActivity()` (241-246) и зовут `viewModel::onRetry`/`viewModel::onWatchAd`.
  `onRetry(activity)` (`RewardedAdViewModel.kt:48-51`) уже перечитывает состояние через
  `loadBlock()` → `refreshRewardState()`, включая переход из `Unauthenticated` — готовый механизм
  обновления после успешного входа, новый метод во ViewModel не нужен.
- **G7**: единственный модальный паттерн в `core/designsystem` — `RateConfirmDialog.kt:78-95`
  (обёртка `androidx.compose.ui.window.Dialog`) + `RateConfirmDialogContent` (113-215,
  `Name`/`NameContent` split, задокументированная в файле конвенция 62-69). `ModalBottomSheet`
  нигде в `core/designsystem`/`core/ui` не встречается.
- **G8**: шаблон UseCase, оборачивающего один repository-вызов —
  `core/domain/.../usecase/ObserveAdRewardStateUseCase.kt:9-21` (тонкая делегирующая обёртка,
  конструктор `@Inject`).

## Acceptance

```gherkin
Feature: Лёгкий вход через Google из rewarded-ad блока
  Без навигации на CloudSync и без модалки настройки shared-воркспейса.

  Scenario: Успешный вход
    Given пользователь не авторизован и видит блок «Посмотреть рекламу» в состоянии Unauthenticated
    When он нажимает «Войти» и подтверждает аккаунт в системном диалоге CredentialManager
    Then открывается новый GoogleSignInDialog, а не экран CloudSync
    And после успешного SharedAuth.signInWithGoogle диалог закрывается
    And блок rewarded-ad сам перечитывает состояние и выходит из Unauthenticated — без визита на CloudSync

  Scenario: Ошибка входа
    Given пользователь открыл GoogleSignInDialog
    When CredentialManager или signInWithGoogle возвращают ошибку
    Then диалог показывает честное сообщение об ошибке и кнопку повтора, а не бесконечный спиннер
    And блок rewarded-ad остаётся в Unauthenticated

  Scenario: Отмена
    Given пользователь открыл GoogleSignInDialog
    When он закрывает диалог до завершения входа
    Then диалог закрывается без побочных эффектов
    And состояние rewarded-ad блока не меняется

  Scenario: CloudSync не тронут
    Given пользователь открывает экран Cloud sync напрямую из настроек
    Then кнопка «Sign in» там по-прежнему ведёт на SharedSetupDialog как раньше — поведение не изменилось
```

## Gap / context

Единственная сегодня существующая точка входа в это ТЗ — кнопка «Войти» в rewarded-ad блоке —
насильно тащит пользователя через полноценную настройку shared-воркспейса ради одной лишь
аутентификации. Этот SPEC закрывает именно её.

## Implementation links
- commit: `0270d465`
- files: `core/network/build.gradle.kts`, `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/GoogleIdTokenProvider.kt`, `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/CredentialManagerGoogleIdTokenProvider.kt`, `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SignInWithGoogleUseCase.kt`, `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedModule.kt`, `feature/support/build.gradle.kts`, `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInViewModel.kt`, `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInDialog.kt`, `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt`, `feature/support/src/main/res/values*/strings.xml`, `app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt`, plus tests: `core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SignInWithGoogleUseCaseTest.kt`, `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInViewModelTest.kt`, `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/googlesignin/GoogleSignInDialogContentTest.kt`
- verification: deterministic reviewer pass, semantic review pass (5/5 coverage), independent critic pass (risk downgraded high→standard, 1 non-blocking warning on compose-ui coverage completeness), runner `2349 passed / 0 failed / 0 skipped`, detekt/lint ok, verifier pass
- mid-run correction: initial attempt to put `SignInWithGoogleUseCase` in `:core:domain` hit a real Gradle cycle (`:core:network` already depends on `:core:domain`) + JVM/Android variant mismatch; SPEC corrected to keep the UseCase in `:core:network` — see "Design notes" above (D5')
- outstanding: real-device OAuth verification (Pixel 5 API 34, real Google account) not yet performed this session — see manual checklist; independent critic flagged a non-blocking compose-ui coverage gap for the success/cancelled-from-loading matrix cells (optional follow-up)
