# Онбординг-тур подсветками — epic overview
Epic: onboarding-spotlight-tour
Order: 00 of 05
Status: done
Completed: 2026-09-21
Depends-on: —
Date: 2026-09-20

## Цель
Заменить 4-слайдовый онбординг (`OnboardingScreen`, HorizontalPager) на **тур с подсветками прямо на
настоящем дашборде**: после первого запуска открывается сам экран S01, экран слегка затемняется, а вырез
(кружок для круглых кнопок, контур по границам для остального) подсвечивает элемент, о котором говорит
карточка-подсказка. Слева внизу — «Пропустить всё», справа внизу — «Далее» (на последнем шаге «Готово»).
После последнего шага или «Пропустить всё» пользователь остаётся на обычном дашборде с закрытыми панелями.
В scope: 4 шага тура, удаление старого онбординга, тесты и проверка на устройстве. Вне scope: повтор тура из
Настроек, обучение на других экранах, аналитика тура, изменение Splash и содержимого боковых панелей.

## Шаги тура (итоговая раскладка)
1. **Расходы · переводы · доходы** — вырез вокруг всего ряда из трёх FAB (один блок).
2. **Левая панель** — сначала кружок на кнопке меню (панель закрыта), затем панель сама выезжает и
   подсвечивается целиком: период, свой диапазон дат, выбор счетов.
3. **Правая панель · Категории** — сначала кружок на кнопке ⋮ (панель закрыта), затем панель выезжает и
   подсвечивается пункт «Категории».
4. **Поддержать проект** — та же открытая правая панель, подсвечен пункт «Поддержать проект».
После «Готово» / «Пропустить всё» / системного Back панели закрываются, тур записан как пройденный.

## Заблокированные решения (grill 2026-09-20)
- D1: старый 4-слайдовый онбординг удаляется полностью; гид — тур поверх настоящего S01.
- D2: лёгкое затемнение + вырез; «Пропустить всё» слева внизу, «Далее» справа внизу; конец тура = обычный дашборд.
- D3: 4 шага (см. выше); ряд FAB — один блок; правая панель — пункты по очереди в одной открытой панели.
- D4: шаг с боковой панелью — один шаг (кнопка → панель открывается сама), а не два отдельных шага.
- D5: форма выреза — круг для круглых иконок-кнопок, скруглённый контур по границам для ряда FAB, панели и пунктов списка.
- D6: тап по подсвеченному элементу выполняет его РЕАЛЬНОЕ действие; тур ставится на паузу, пока пользователь на другом экране, и продолжается после возврата.
- D7: повтора тура из Настроек нет; флаг `SHOW_ONBOARDING` работает как раньше (debug — тура нет; пользователи с `onboardingCompletedAt != null` тур не видят).
- D8 (assumption): после реального действия шаг считается просмотренным — тур продолжается со СЛЕДУЮЩЕГО шага; тап по «Поддержать» и возврат завершают тур.
- D9 (assumption): состояние боковых панелей определяется шагом/фазой (`drawersFor`): при входе в шаг и при возобновлении хост приводит панели к нужному виду (`CategoriesClicked`/`SupportClicked` вызывают `closeDrawers()`, G14).
- D10 (assumption): тапы по затемнению вне выреза игнорируются; системный Back = «Пропустить всё».
- D11 (assumption): `onboardingCompletedAt` записывается только на «Готово» / «Пропустить всё» / Back; текущий шаг лежит в `SavedStateHandle` (переживает смерть процесса); «смахнули из Recents» — тур начинается с шага 1.
- D12 (assumption): Splash (и `InitialDataSeeder`) остаётся; из графа убирается только `Destinations.Onboarding`; Splash → Dashboard; тур показывается, когда на дашборде `onboardingCompletedAt == null`.
- D13 (assumption): универсальный оверлей — в `:core:designsystem` (пакет `spotlight`); шаги, состояние и тексты — в `:feature:dashboard` (никаких зависимостей `:feature:*` → `:feature:*`).
- D14 (assumption): на последнем шаге кнопка «Готово»/«Done»; на карточке подпись «N из 4»; тексты в SPEC-03.
- D15: удаляемые файлы переносятся в `archive/` (репозиторий, git-ignored), а не удаляются; список отдаётся пользователю для ручной очистки (AGENTS.md).
- D16 (assumption, проверить на устройстве): открытая панель физически закрывает свою кнопку в топ-баре (`DashboardDrawerOverlay.kt:97-107`), поэтому шаг с панелью двухфазный: фаза A — панель закрыта, вырез-кружок на кнопке; фаза B — панель открывается сама (через `TOUR_PANEL_OPEN_DELAY_MS` = 900 мс или сразу по тапу на кнопку — это и есть реальное действие) и вырез переезжает на панель/пункт. Шаг 4 идёт без фазы A (панель уже открыта).

## SPECs (run via /mp --feature --next in Order)
| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `onboarding-spotlight-tour-01-spotlight-overlay-component.md` | — | presentation | Универсальный оверлей-подсветка в `:core:designsystem` |
| 02 | `onboarding-spotlight-tour-02-tour-state-and-viewmodel.md` | — | domain, presentation | Модель шагов/фаз, состояние, `DashboardViewModel`, пауза/возобновление |
| 03 | `onboarding-spotlight-tour-03-tour-ui-integration.md` | 01, 02 | presentation | Метки целей, оверлей в `DashboardContent`, строки EN/RU, a11y |
| 04 | `onboarding-spotlight-tour-04-remove-old-onboarding.md` | 03 | presentation | Splash → Dashboard, архивирование старого онбординга, правка тестов |
| 05 | `onboarding-spotlight-tour-05-tour-device-verification.md` | 04 | tests | Инструментальный журнейный тест, a11y-гейт, проверка на Pixel_5 API 34 |

## Why this ordering
01 и 02 независимы (разные модули), 03 связывает их и требует обоих. Старый онбординг (04) удаляется только
после того, как новый тур работает, — иначе новый пользователь остался бы без гида. 05 — сквозная проверка
на устройстве после того, как весь поток собран. Same-file clash: 02 правит `DashboardViewModel.kt`/
`DashboardState.kt`, 03 — `DashboardScreen.kt` и компоненты; общих файлов нет, но 03 зависит от API из 02.
Внимание: рабочее дерево содержит незакоммиченные правки `core/ui/.../theme/Spacing.kt` (посторонняя задача) —
SPEC-01 добавляет токены в этот же файл; не перезаписывать чужие изменения.

## Key facts (verified)
- G1 — маршрутизация первого запуска Decision → Splash → Onboarding → Dashboard: `app/.../navigation/DecisionRouterViewModel.kt:29-56`, `MyMoneyNavHost.kt:39-62`.
- G3 — `AppSettings.onboardingCompletedAt: Long?` (`core/datastore/.../model/AppSettings.kt:16`); `AppSettingsRepository.update { }` (`AppSettingsRepository.kt`).
- G4 — `SHOW_ONBOARDING`: debug=false / release=true (`app/build.gradle.kts:372,381`), `@ShowOnboarding` (`OnboardingFlagModule.kt:13-21`).
- G7 — `DashboardContent` — внешний `Box` со Scaffold и двумя `DashboardDrawerOverlay`; оверлей, добавленный последним, рисуется поверх всего (`DashboardScreen.kt:185-291`).
- G8 — кнопки топ-бара: гамбургер `DashboardScreen.kt:541` (иконка меняется на ArrowBack при открытой панели), ⋮ `:563`; без testTag.
- G9 — `ThreeFabLayout` (Expense/Transfer/Income), без testTag: `components/ThreeFabLayout.kt:35`.
- G10 — панели — самописный `DashboardDrawerOverlay` (scrim + Surface 0.62 ширины, `statusBarsPadding`), не M3 `ModalNavigationDrawer`: `components/DashboardDrawerOverlay.kt:41-110`.
- G11 — `leftDrawerOpen/rightDrawerOpen`: `DashboardState.kt:52-53`; события `LeftDrawerToggled`/`RightDrawerToggled`/`DrawerDismissed`: `DashboardViewModel.kt:1080-1093`.
- G12 — `RightDrawerContent` (7 пунктов, testTag `right_drawer_categories`, `right_drawer_support`): `components/RightDrawerContent.kt:59-64,89-93,134-140`.
- G14 — `CategoriesClicked`/`SupportClicked` вызывают `closeDrawers()` → после возврата панель закрыта: `DashboardViewModel.kt:1098-1125`; FAB-клики панели не закрывают: `:1095-1097`.
- G16 — `DashboardViewModel` не имеет `AppSettingsRepository` и `SavedStateHandle`: `DashboardViewModel.kt:57-69`; конструируется вручную в `DashboardViewModelTest.kt` и `app/src/androidTest/.../ImportFocusColdStartRegressionTest.kt`.
- G17 — подсветок/coach-mark утилит в проде нет; пакеты `core/designsystem`: amountfield … drawer … pill, sound.
- G18/G19 — тесты, жёстко завязанные на онбординг (`DestinationsTest.kt:111,299`, `L10nParityTest.kt:121`, Kover-порог `:feature:onboarding`=13 в `build.gradle.kts:29,42,142` и `KoverCoverageCiContractTest.kt`); 4 журнейных androidTest ждут `onboarding_skip`, хотя в debug онбординг не показывается (pre-existing red, см. память проекта).

## Открытые вопросы (assumptions)
- O1: точные тексты карточек RU/EN — черновик в SPEC-03, финализируется там.
- O2: сила затемнения — по умолчанию ≈ 0.72 alpha от тёмного нейонового фона, токен `spotlightScrim`.
- O3: TDD (`TDD/MyMoney/MyMoney_TDD.md`, S00/S11, §3.2 стр. ~366, §4.0 стр. ~518) описывает pager-онбординг — правка текста TDD и запись решения (AS-NN) предложена в SPEC-04, требует ревью пользователя.

## Чеклист для человека (после SPEC-05)
1. Чистая установка **release**-сборки на Pixel_5 API 34: дашборд открывается, появляется тур, шаг 1 подсвечивает ряд из трёх FAB.
2. «Далее» → шаг 2: кружок на кнопке меню, затем панель выезжает и подсвечена целиком; «Далее» → шаг 3 (кнопка ⋮ → панель, «Категории»); «Далее» → шаг 4 («Поддержать проект»); «Готово» → обычный дашборд, панели закрыты.
3. «Пропустить всё» на любом шаге → дашборд без затемнения; повторный запуск приложения тур не показывает.
4. Тап по «+» на шаге 1 → открывается ввод дохода; вернуться назад → тур продолжается на шаге 2.
5. TalkBack: карточка озвучивается, «Далее»/«Пропустить всё» доступны; шрифт 200% — текст не обрезан.

## Implementation links
- commit: d13cbda4, 725fd8f8, 1162e893, 559073e3, 40fb08c3, 64329e6b, 19fca24e, 89ae6de2, 348a7977, 6938b95a
- files: see the five SPEC files in done/
