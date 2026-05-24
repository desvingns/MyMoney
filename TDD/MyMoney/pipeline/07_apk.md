# APK Analysis — com.monefy.app.lite

> Ground-truth source. Overrides screenshot-based guesses where overlapping.

## Tooling
- Strategy: **B** (aapt2 + Expand-Archive; apktool not installed)
- Tools used: aapt2 37.0.0 (C:\Users\k.shavrin\AppData\Local\Android\Sdk\build-tools\37.0.0\aapt2.exe), PowerShell Expand-Archive
- Tools missing: apktool, jadx
- Split APKs processed: base (`app.apk`) + `split_config.ru.apk` for Russian strings

## Manifest

| Field | Value |
|---|---|
| package | com.monefy.app.lite |
| versionName | 1.22.10 |
| versionCode | 2228 |
| minSdkVersion | 21 |
| targetSdkVersion | 36 |
| compileSdkVersion | 36 (platform 16) |
| sharedUserId | com.monefy.app.sharedid |
| application/name | com.monefy.application.ClearCashApplication_ |
| application/label | @0x7f13014a → "Monefy" (string/monefy_app_name) |
| application/theme | @0x7f140148 → style/MonefyAppTheme.NoActionBar |
| application/icon | @0x7f100000 → mipmap/ic_launcher |
| backupAgent | com.monefy.helpers.PreferencesBackupAgent |
| theme_parent | **Theme.MaterialComponents.DayNight.DarkActionBar** |
| launch_theme | MonefyAppTheme.SplashTheme (parent: MonefyAppTheme) |

### Permissions (12)

| Permission | Dangerous? |
|---|---|
| com.android.vending.BILLING | no |
| android.permission.INTERNET | no |
| android.permission.USE_FINGERPRINT | no (deprecated, superseded by USE_BIOMETRIC) |
| android.permission.WAKE_LOCK | no |
| android.permission.ACCESS_NETWORK_STATE | no |
| android.permission.READ_PHONE_STATE | **yes** |
| android.permission.POST_NOTIFICATIONS | **yes** (runtime, Android 13+) |
| android.permission.RECEIVE_BOOT_COMPLETED | no |
| android.permission.FOREGROUND_SERVICE | no |
| com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE | no |
| com.google.android.gms.permission.AD_ID | no |
| android.permission.USE_BIOMETRIC | no |
| android.hardware.fingerprint (feature, not required) | — |

### Activities (16)

| Activity | Exported | Notes |
|---|---|---|
| com.monefy.activities.main.MainActivity_ | **true** | MAIN/LAUNCHER; also DRIVE_OPEN filter; app shortcuts |
| com.monefy.activities.transaction.NewTransactionActivity_ | false | parent: MainActivity_ |
| com.monefy.activities.transfer.ManageTransferActivity_ | false | parent: MainActivity_ |
| com.monefy.activities.password_settings.PasswordSettingsActivity_ | false | |
| com.monefy.activities.password_settings.ChangeSecurityQuestionActivity_ | false | |
| com.monefy.activities.password_settings.NewPasswordActivity_ | false | noHistory |
| com.monefy.activities.password_settings.EnterPasswordActivity_ | false | noHistory |
| com.monefy.activities.category.EditCategoryActivity_ | false | label: "Edit category" |
| com.monefy.activities.account.EditAccountActivity_ | false | label: "Edit account" |
| com.monefy.activities.currency.CurrencyActivity_ | false | label: "Edit currency" |
| com.monefy.activities.category.AddCategoryActivity_ | false | label: "Add category" |
| com.monefy.activities.account.AddAccountActivity_ | false | label: "Add account" |
| com.monefy.activities.crash.CrashActivity | false | label: "Application error" |
| com.monefy.activities.buy.BuyMonefyActivity_ | false | label: "Buy Monefy Premium" |
| com.monefy.activities.buy.BuyMonefySpecialOfferActivity_ | false | label: "Buy Monefy Premium Spefial Offer" [sic] |
| com.monefy.activities.onboarding.OnboardingActivity_ | false | noHistory; theme: OnboardingTheme |
| com.monefy.activities.widget.settings.SmallWidgetSettingsActivity_ | false | APPWIDGET_CONFIGURE |
| com.monefy.activities.widget.settings.BigWidgetSettingsActivity_ | false | APPWIDGET_CONFIGURE |
| com.dropbox.core.android.AuthActivity | **true** | Dropbox OAuth; scheme=@string/DROPBOX_DB_SCHEME |

### Services (10)

- com.monefy.activities.widget.services.WidgetCategoriesUpdateService (BIND_REMOTEVIEWS)
- com.monefy.activities.widget.services.BigWidgetUpdateServiceSDK26 (BIND_JOB_SERVICE)
- com.monefy.activities.widget.services.SmallWidgetUpdateServiceSDK26 (BIND_JOB_SERVICE)
- com.monefy.activities.widget.services.BigWidgetUpdateServicePreSDK26
- com.monefy.activities.widget.services.SmallWidgetUpdateServicePreSDK26
- com.monefy.sync.SyncServicePreSDK26
- com.monefy.sync.SyncServiceSDK26 (BIND_JOB_SERVICE)
- androidx.work.impl.background.systemalarm.SystemAlarmService
- androidx.work.impl.background.systemjob.SystemJobService (exported=true)
- androidx.work.impl.foreground.SystemForegroundService

### Receivers (7+)

- com.monefy.activities.widget.WidgetProvider (APPWIDGET_UPDATE)
- com.monefy.activities.widget.CollectionWidgetProvider (APPWIDGET_UPDATE; label: "Monefy - categories collection")
- androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver
- androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryChargingProxy
- androidx.work.impl.background.systemalarm.ConstraintProxy$BatteryNotLowProxy
- (remaining WorkManager constraint proxies)

### Providers (3)

- androidx.core.content.FileProvider (authorities: com.monefy.app.lite.provider)
- br.com.mauker.materialsearchview.db.HistoryProvider (syncable)
- androidx.startup.InitializationProvider (WorkManager, EmojiCompat, Lifecycle, ProfileInstaller startup)

### Deep links

| Activity | Scheme | Notes |
|---|---|---|
| com.dropbox.core.android.AuthActivity | db-wxbzuly0x7v23t8 (Dropbox OAuth callback) | action VIEW, BROWSABLE |
| com.monefy.activities.main.MainActivity_ | com.google.android.apps.drive.DRIVE_OPEN | Google Drive file open integration |

No custom `monefy://` deep links in manifest. Dropbox scheme is the only URL-based intent filter.

## Exact palette (light)

Named color chain: `colorPrimary` → `@color/green_2` → **#ff7ac794**

| Token (resource name) | Hex (ARGB) | Hex (RGB) | Semantic role |
|---|---|---|---|
| green_0 | #ffd7f3e1 | #d7f3e1 | pressed/highlight tint |
| green_1 | #ffa9e0bb | #a9e0bb | active/colorActive |
| green_2 | #ff7ac794 | #7ac794 | **colorPrimary**, action bar, accent |
| green_3 | #ff50ab6f | #50ab6f | **colorPrimaryDark**, control border |
| red | #fff66561 | #f66561 | **colorAccent** (= colorError), expense color |
| black | #ff000000 | #000000 | pure black |
| black_0 | #1e000000 | (12% black) | disabled alpha |
| black_1 | #42000000 | (26% black) | mid alpha |
| black_2 | #6a000000 | (42% black) | |
| black_3 | #ae000000 | (68% black) | **primaryTextColor** (light) |
| black_4 | #ff000000 | #000000 | colorBlackWhite (light) |
| white_3 | #ffffffff | #ffffff | colorOnPrimary, colorOnError, colorOnSecondary |
| white_ish | #fffefefe | #fefefe | list text color in dark drawer |
| white_opaque | ~#ffffffff | #ffffff | box stroke color |
| main_background (light) | #fff2fff7 | #f2fff7 | colorSurface, windowBackground (very light green-tinted white) |
| transparent | #00ffffff | — | status bar tint |
| biometric_error_color | #ffff5722 | #ff5722 | biometric error |
| action_bar_menu_item | #aaffffff | — | menu icon tint |

## Exact palette (dark) — values-night present: yes

Dark mode resolves via `(night)` config qualifiers in resources.arsc (no separate values-night/ folder in base.apk, resolved via aapt2 config dump).

| Token | Night value → resolves to |
|---|---|
| main_background (night) | @color/material_gray_800 → **#ff424242** |
| action_bar_background_real (night) | @color/material_gray_700 → **#ff616161** |
| colorBlackWhite (night) | @color/white_3 → **#ffffffff** |
| colorPressed (night) | @color/material_gray_600 (not resolved to hex in dump) |
| colorActive (night) | @color/material_gray_700 → **#ff616161** |
| primaryTextColor (night) | @color/white_3 → **#ffffffff** |
| colorChartInnerCircleForeground (night) | @color/material_gray_800 → **#ff424242** |

## Exact dimensions

| Name | Value | Category |
|---|---|---|
| action_bar_height | 38dp | component (custom, not standard 56dp) |
| action_bar_title_font_size | 18sp | typography |
| action_bar_menu_icon_size | 32dp | icon |
| balance_text_size | 14sp | typography |
| balance_button_height | 40dp | component |
| balance_margin | 8dp | spacing |
| backspace_button_width | 40dp | component |
| category_name_font_size_grid | 12sp | typography |
| category_name_text_size | 28sp | typography (large category labels) |
| corner_radius | 3dp | corner radius (very subtle) |
| dialog_margin | 24dp | spacing |
| double_margin | 24dp | spacing |
| drawer_width | 224dp | component |
| edit_category_buttons_height | 40dp | component |
| extra_large_font_size | 22sp | typography |
| extra_small_font_size | 8sp | typography |
| green_text_view_height | 38dp | component |
| half_margin | 8dp | spacing |
| icon_internal_padding | 4dp | spacing |
| keyboard_button_margin | 3dp | spacing |
| large_font_size | 20sp | typography |
| list_icon_size | 30dp | icon |
| list_item_icon_margin | 4dp | spacing |
| design_fab_size_normal | 56dp | FAB |
| design_fab_size_mini | 40dp | FAB mini |
| design_appbar_elevation | 4dp | elevation |
| default_letter_spacing | 0sp | typography |

## Strings catalog

- Default locale: base APK (English only; no locale qualifier in base.apk)
- Total string resources (default): **566**
- App name: **"Monefy"** (`string/monefy_app_name`)
- Note: `string/app_name` = "Material Search Library" (third-party library artifact, not the app's display name)
- Locales from split APKs: base.apk has no locale configs — all non-English strings are in split_config.{locale}.apk
- Russian split (split_config.ru.apk): **486** string resources, full coverage

### Key business strings — English (default)

| Key | Value |
|---|---|
| monefy_app_name | "Monefy" |
| balance | "Balance" |
| income_title | "INCOMES" |
| expense_title | "EXPENSES" |
| add_transaction_button_hint | "Tap to add a new expense record" |
| add_transaction_icon_hint | "Or tap the category icon to add a record faster" |
| add_transfer_hint | "Tap the 'Transfer' button to move money between accounts" |
| empty_view_title | "There are no records for this period yet" |
| empty_result_text_view_message | "No records have been found." |
| budget_mode_enabled | "Budget mode" |
| recurring_records_hint | "Recurring records are now available in Monefy Premium!" |
| buy_monefy_pro_header | "Monefy Premium" |
| buy_monefy_pro_button_title | "Buy" |
| buypro_remove_all_ads | "Remove all ads" |
| buypro_unlock_everything | "Unlock everything in the app" |
| buypro_your_categories | "Create your own categories" |
| buypro_become_budgeting_hero | "Become your own budgeting hero" |
| buypro_claim_offer | "Claim my offer" |
| buypro_offer_ends_in | "Offer ends in %s" |
| cancel_anytime | "Cancel anytime" |
| passcode_dark_mode_and_more | "Passcode, dark mode and more!" |
| passcode_protection_header | "Passcode Protection" |
| sync_in_progress | "Synchronization..." |
| dropbox_sync_text | "Allows you to use Monefy on multiple devices or to share the finance tracking with your significant other. The data will be stored in your Google Drive or Dropbox account." |
| delete_database_message | "Your data will be deleted completely including the data in Google Drive or Dropbox. Do you want to continue?" |
| delete_application_data_to_unlock | "Do you want to delete application data and unlock Monefy?" |
| delete_category_account_explanation | "All associated records will be removed. You can merge or disable it instead" |
| currency_no_currency_rates | "You have not added exchange rates yet." |
| account_initial_balance | "Balance '%s'" |
| accounts_have_to_be_different | "Accounts have to be different" |

## Drawables

- Total assets (all drawable dirs): **372** (357 XML vectors + 15 PNG/WebP rasters)
- Mipmap assets: **12** (across hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi + anydpi-v26)
- Density buckets: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi, anydpi-v26 (adaptive icon), night-v8
- Launcher icon: `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive) + PNG fallbacks
- Night mode drawables: `res/drawable-night-v8/` present

### Notable app drawables

| Name | Purpose |
|---|---|
| monefy_icon.xml | App icon vector |
| monefy_loading_image.xml | Splash / loading illustration |
| monefy_active_background.xml | Selectableitem background |
| ic_launcher_foreground.xml | Adaptive icon foreground |
| onboarding_hero_1..4.xml | Onboarding full-page illustrations (4 screens) |
| onboarding_step_1..4.xml | Onboarding step indicators |
| add_new_expense_transaction_button.xml | FAB-style expense button |
| add_new_income_transaction_button.xml | FAB-style income button |
| ic_add_new_expense_transaction_button[_empty/_pressed].xml | Expense button states |
| ic_add_new_income_transaction_button[_empty/_pressed].xml | Income button states |
| balance_bg.xml | Balance panel background (positive) |
| balance_bg_negative.xml | Balance panel background (negative balance) |
| gradient_background.xml | Onboarding background gradient |
| chart_category_summary_background.xml | Donut chart background |
| ic_currency.xml | Currency icon |
| ic_add_exchange_rate.xml | Exchange rate add icon |
| ic_repeat_*.xml | Recurring transaction icons |
| ic_calendar_wrapper.xml | Date picker wrapper |
| ic_history_white.xml | History/transactions icon |

Category icons are stored as **named colors** (e.g., `color/aircraft`, `color/beer`, `color/basketball`, `color/baby`, `color/banknotes`, etc.) — these are accent colors per category, NOT separate icon drawables. The actual category icons appear to be drawn programmatically using the Monefy custom view system.

## UI framework

- Layout XML files: **192** files in `res/layout/` (plus layout-land, layout-v21, etc.)
- No `androidx.compose` entries in META-INF/androidx
- Font: `res/font/roboto_regular` referenced in theme
- Architecture annotations: `androidannotations-api.properties` present (AndroidAnnotations framework — `_` suffix on class names confirms this)
- **Guess: XML Views** (confidence: 0.97)
- Implication: 192 layout files means full View-based UI; the `_` suffix on Activity names is the AndroidAnnotations code-generation pattern

## Libraries detected

From META-INF `*.version` and `*.properties` files — no jadx needed:

**Core AndroidX:**
- androidx.room (Room database ORM)
- androidx.lifecycle (LiveData + ViewModel)
- androidx.work (WorkManager — background sync scheduler)
- androidx.datastore + datastore-preferences (settings storage)
- androidx.biometric (biometric authentication)
- androidx.constraintlayout
- androidx.viewpager2
- androidx.recyclerview
- androidx.fragment, activity, drawerlayout, viewpager

**DI / Architecture:**
- com.google.dagger (Dagger — NOT Hilt; pre-Hilt injection)
- androidannotations-api (AndroidAnnotations — code generation, explains `_` suffixed class names)

**Google / Firebase:**
- firebase-analytics + firebase-core (Firebase Analytics)
- firebase-config (Remote Config)
- firebase-abt (A/B Testing)
- firebase-installations
- play-services-ads-identifier (AdMob/ad ID)
- play-services-auth + auth-api-phone (Google Sign-In)
- play-services-location (location services)
- play-services-measurement (analytics measurement)
- play-services-fido (FIDO/passkeys)
- Google Play Billing **7.0.0** (`billing.properties`)

**Error tracking:**
- io.sentry (Sentry SDK — DSN present in manifest meta-data: `https://...@o155653.ingest.us.sentry.io/4508238736916480`)

**Sync:**
- Dropbox SDK (com.dropbox.core.android.AuthActivity in manifest; DROPBOX_APP_KEY in strings)
- Google Drive API (drive.v3.json present; DRIVE_OPEN intent filter on MainActivity)
- google-http-client (Google API HTTP client)

**Async:**
- kotlinx-coroutines-core + coroutines-android + coroutines-play-services

**Other:**
- br.com.mauker.materialsearchview (MaterialSearchView — search history provider in manifest)
- com.j256.ormlite (ORM Lite — visible in `com/j256` extracted folder; legacy SQLite ORM)
- protolite-well-known-types (Protocol Buffers)
- transport-api (Firebase transport)

**NOT detected:** Retrofit, OkHttp (no META-INF entry; though `okhttp3/` folder exists in APK root — OkHttp bundled), Glide, Coil, Lottie, Hilt, Koin, Compose.

`okhttp3/` directory present at APK root → **OkHttp3 confirmed** (bundled as resource folder rather than META-INF version file).

## Architecture guess

- **AndroidAnnotations** (`_`-suffixed activities) + **Dagger** (manual modules) + **Room** + **LiveData** + **OrmLite**
- Pattern: **MVP with AndroidAnnotations** (the `_` suffix is the AA-generated subclass pattern, typical of Activity/Fragment-centric MVP architecture popular ~2015–2019)
- Data layer: dual ORM — Room (newer code) + OrmLite (legacy, `com.j256` present)
- Background: WorkManager + two legacy service variants (pre/post SDK 26) for widget sync
- DI: Dagger (not Hilt — no `dagger.hilt` in META-INF)
- Architecture guess: **MVP-AndroidAnnotations** (older codebase; gradual migration towards MVVM/LiveData visible in lifecycle dependencies)

## Endpoints extracted

From manifest meta-data:
- `https://8798feb6bdfcd4ab961ecd986fc80d90@o155653.ingest.us.sentry.io/4508238736916480` (Sentry DSN — not an API endpoint)

From strings (DROPBOX_APP_KEY = `wxbzuly0x7v23t8` → Dropbox OAuth).

No plain HTTP(S) API endpoints found in extractable string resources. API calls are likely hardcoded in DEX (not accessible without jadx).

## Versioning / signing

- Signed: Yes — `META-INF/BNDLTOOL.RSA` + `BNDLTOOL.SF` (bundle tool signature)
- This is a base split from an Android App Bundle (`.aab` → Play-generated APK splits), hence `BNDLTOOL` signing
- APK file size: **23.1 MB** (base split only; arm64 + ru locale are separate splits)
- `stamp-cert-sha256` present (Play store delivery stamp)

## Split APK notes

This `app.apk` is the **base split** from a Play-generated split-APK bundle:
- `split_config.arm64_v8a.apk` — native libraries for ARM64
- `split_config.ru.apk` — Russian locale resources (486 strings)
- `split_config.xxhdpi.apk` — high-density raster assets
- Base APK has NO locale-qualified resources — all translations in locale splits

## Overrides applied

These fields take precedence over `screenshot-style-analyzer` and `screenshot-business-analyzer` output:

- `palette.*` — exact hex from resources.arsc (`green_2=#7ac794`, `red=#f66561`, `main_background=#f2fff7`, dark background `#424242`)
- `typography.scale_sp` — `balance_text_size=14sp`, `large_font_size=20sp`, `extra_large_font_size=22sp`, `action_bar_title_font_size=18sp`, `category_name_font_size_grid=12sp`, `category_name_text_size=28sp`
- `corner_radius_dp` — `corner_radius=3dp` (very small, nearly square buttons)
- `implied_permissions` — replaced by exact manifest permissions list above
- `implied_sdks` — replaced by `libraries_detected` from META-INF
- `deep_links` — only Dropbox OAuth callback + Google Drive OPEN; no custom app scheme
- `theme_parent` — `Theme.MaterialComponents.DayNight.DarkActionBar` (confirmed; NOT Material3)
- `ui_framework` — XML Views confirmed (192 layout files, AndroidAnnotations, no Compose)
- `app_name` — "Monefy" (from `string/monefy_app_name`; not `string/app_name` which is a library artifact)
- `version` — 1.22.10 / versionCode 2228
- `min_sdk` — 21 (Android 5.0 Lollipop), `target_sdk` — 36
