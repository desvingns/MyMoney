# Эпик: audit3-lock-security — экран блокировки и защита данных
Epic: audit3-lock-security
Order: 00 of 04 (overview)
Status: done
Completed: 2026-06-14 (all SPECs shipped to main; epic closed during backlog housekeeping)
Depends-on: —
Date: 2026-06-10

## Цель

Закрыть критические дыры безопасности из аудита (`docs/audit/2026-06-10-project-audit.md`):
(C2) вечный локаут — необработанные коды ошибок биометрии + возможность включить лок без PIN;
(C3) Auto Backup переносит EncryptedSharedPreferences → краш-луп на новом устройстве;
(H5) флеш контента до появления лока на холодном старте + отсутствие FLAG_SECURE;
(M10) PIN без анти-brute-force и со слабым KDF (10k итераций).

## Заблокированные решения (из grill)

- **D3:** версионированный формат хэша `v2:<iters>:<saltB64>:<hashB64>`; legacy `salt:hash`
  верифицируется с 10 000 и лениво перехэшируется на 600 000 при первом успешном вводе.
- **D3b:** троттлинг — после 5 неверных попыток задержка 30с, ×2 за каждые следующие 5;
  счётчик и deadline в SecureStorage (переживают рестарт процесса).
- PIN обязателен ДО включения лока; «Отмена» биометрии → PIN-fallback.
- **O3 (assumption):** monefy.db в бэкап ВКЛЮЧАЕТСЯ, secure-prefs исключаются безусловно.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `audit3-lock-security-01-biometric-errors-pin-first.md` | — | presentation | все коды ошибок; PIN до включения лока; pinFallback saveable |
| 02 | `audit3-lock-security-02-pin-hardening.md` | 01 | domain+data | формат v2 + 600k + lazy re-hash + троттлинг |
| 03 | `audit3-lock-security-03-flag-secure-cold-start.md` | — | presentation | FLAG_SECURE + splash до известности lock-state |
| 04 | `audit3-lock-security-04-backup-rules-recovery.md` | — | data | реальные backup-правила + recovery SecureStorage |

## Почему такой порядок

01 устраняет тупики UI (включая мёртвую PIN-ветку), 02 строится на его PIN-флоу и правит тот же
`LockOverlay.kt` (клэш ⇒ последовательность). 03 и 04 независимы. `SecureStorageImpl.kt` (04)
затем правится в `audit5-donut-perf-02` (lazy-init) — 04 первым.

## Ключевые факты (verified, из grounding)

- G1: обрабатываются только `ERROR_LOCKOUT`/`ERROR_LOCKOUT_PERMANENT` — `feature/lockscreen/.../overlay/LockOverlay.kt:171-172`; `BackHandler {}` :71; `pinFallback by remember { mutableStateOf(false) }` (не saveable) :67.
- G2: лок включается ДО создания PIN — `BiometricSetupViewModel.kt:76` (`update { copy(biometricLockEnabled = true) }`); `PinSetupDismissed` :58-59 лишь прячет диалог.
- G3: PIN-ветка мертва при pinHash=null — `LockOverlay.kt:145-149` (verifyPin → false навсегда).
- G4: `PinHasher`: хранение `Base64(salt) + ":" + Base64(dk)`, PBKDF2WithHmacSHA256, ITERATIONS=10_000 — `PinHasher.kt:11-13,44-47`; счётчик итераций НЕ зашит в строку.
- G5: `SecureStorage`: `read().pinHash`, `writePinHash(hash: String?)`, `clearAll()` — `core/datastore/.../SecureStorageImpl.kt:30,50-55`; конструктор создаёт EncryptedSharedPreferences без обработки ошибок :17-28.
- G6: `allowBackup="true"` — `app/src/main/AndroidManifest.xml:13`; `backup_rules.xml` — полностью закомментированный шаблон; `data_extraction_rules.xml` — шаблон с literal TODO.
- G7: `MainActivity.setContent` :45-61 без `setKeepOnScreenCondition`; `LockController.shouldShowLock` init=false :33-34, выставляется асинхронно :42.
- G8: FLAG_SECURE в проекте отсутствует.
- G9: строки — `feature/lockscreen/src/main/res/values{,-ru}/strings.xml` (EN+RU обязательно).

## Implementation links
- (заполняется по мере выполнения)
