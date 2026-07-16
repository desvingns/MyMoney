# ADR-0002: Planned migration from EncryptedSharedPreferences

- Status: Accepted -- planned; implementation is deferred
- Date: 2026-07-16

## Context

The current TDD pins `androidx.security:security-crypto:1.1.0-alpha07` and
assigns sensitive credentials to `EncryptedSharedPreferences` (TDD §2.1, line
18; §2.4, lines 229-236; §8, line 2207). AndroidX deprecated the crypto APIs
in favour of platform APIs and direct Android Keystore use. The Android
reference also says encrypted preferences must be excluded from Auto Backup,
because a restored preference file can outlive its encryption key.

Sources: [AndroidX Security release notes](https://developer.android.com/jetpack/androidx/releases/security),
[EncryptedSharedPreferences reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).

The module ownership remains `:core:datastore` (TDD §2.2, lines 163-164). The
future migration must preserve the cloud-connection and disconnect behaviour
defined for Dropbox and Google Drive (TDD §4.16, lines 986-991). This ADR is a
plan only: it changes no Kotlin, Gradle dependency, resource, backup rule, or
TDD version today.

### Current inventory

`SecureStorageImpl` currently opens the shared-preferences name
`com.kshavrin.mymoney_secure`, which Android stores as
`shared_prefs/com.kshavrin.mymoney_secure.xml`; a `.bak` sidecar can also
exist. It creates a `MasterKey` with AES-256-GCM, encrypts preference names
with AES-256-SIV and values with AES-256-GCM. The source contains no secret
values or cryptographic key material; the identifiers below are storage-field
names only.

| Protected value | Legacy field name | Representation and absent state |
|---|---|---|
| Dropbox refresh token | `dropbox_refresh_token` | nullable string; absent means disconnected |
| Google Drive account email | `gdrive_account_email` | nullable string; absent means disconnected |
| PIN hash | `pin_hash` | nullable string; absence disables the PIN fallback |
| Failed PIN attempts | `failed_pin_attempts` | integer; absent reads as `0` |
| PIN lockout deadline | `pin_lockout_deadline_epoch_ms` | nullable epoch-millis; key absence is distinct from a stored deadline |

The original TDD inventory lists the first three secure fields (TDD §7.3,
lines 1683-1688); the implementation now also persists the two lockout fields.
The TDD's on-device layout names the encrypted-preferences location (TDD §8.3,
lines 2047-2055), while the current application-id filename is the
`com.kshavrin` name above.

There is an important existing failure path. If opening legacy preferences
throws `GeneralSecurityException` or `IOException`, `createPrefs` reports the
failure, calls `recoverPrefs`, and tries `Context.deleteSharedPreferences`. If
that returns false, it directly deletes both the XML and XML `.bak` files,
then recreates the preferences. When no PIN hash remains, it also turns off the
biometric-lock setting in the ordinary settings DataStore. That recovery path
can discard all five legacy values. It must never be used by the future
migration reader.

## Decision

Choose **a dedicated DataStore Preferences file containing per-value Android
Keystore ciphertext**, rather than placing protected values directly in an
Android Keystore entry or continuing to write `EncryptedSharedPreferences`.

The planned target is an app-private
`files/datastore/secure_settings.preferences_pb`, separate from the existing
non-sensitive `app_settings.preferences_pb`. `:core:datastore` will preserve
the `SecureStorage` interface. Before a value reaches that DataStore it will
be encrypted with the platform `AndroidKeyStore` API using a versioned,
app-specific AES/GCM key alias and a fresh nonce for every write. Its envelope
will contain a format version, nonce, ciphertext and authentication tag; the
logical field identifier will be authenticated additional data so ciphertext
cannot be swapped between fields. Only ciphertext and non-sensitive format
metadata may be persisted. The key remains in Android Keystore and must not
require user authentication, because background sync and lockout bookkeeping
need non-interactive access.

All five values, including the zero-or-absent and nullable semantics in the
table, are one secure record set. A versioned encrypted migration-state record
will distinguish `staged` from `complete`. DataStore's serialized update is
the commit point for a record set; it is not being treated as encryption by
itself.

This is the selected target architecture, not an authorization to implement
it now. Before implementation, the TDD must be amended and approved to replace
the encrypted-preferences specification in §2.1 line 18, §2.4 lines 229-236,
§7.3 lines 1683-1688, §8.3 lines 2047-2055, and §8 line 2207. The amendment
must document the fifth-field inventory, target file, backup exclusions,
compatibility importer, and removal criteria. No dependency or version may
diverge from the current TDD before that amendment is accepted.

## Rejected alternatives

- **Keep `EncryptedSharedPreferences`.** Rejected because the exact AndroidX
  API family in use is deprecated and is no longer the recommended platform
  direction.
- **Plain DataStore Preferences.** Rejected because DataStore does not encrypt
  its payload; putting these values there would make the protected values
  readable in app-private storage.
- **Store the five values directly in Android Keystore.** Rejected because
  Android Keystore is the key-management boundary, not a typed, transactional
  credential store for this record set. It still needs an encrypted payload
  store and migration metadata.
- **A private encrypted file or `EncryptedFile`.** Rejected because it loses
  the selected structured, serialized DataStore update model; the latter also
  belongs to the deprecated AndroidX crypto API family.
- **Use system backup as a migration or recovery channel.** Rejected because
  restored ciphertext cannot rely on the original device's Keystore key and
  because credentials and PIN material must not enter system backup.

## Dated plan and execution trigger

- **2026-07-16:** record this architecture and migration plan only.
- **2026-10-01:** conduct the first scheduled dependency/target-platform
  review against the trigger below. Subsequent reviews happen with each
  targetSdk or AGP upgrade proposal.
- **T0, only after the trigger is met:** create a separately approved
  implementation SPEC and TDD amendment. Within five business days, perform
  the migration design review and test-fixture preparation; ship only in the
  next release train after every gate in this ADR is green.

The trigger is deliberately narrow: execute this migration only when an
approved TDD amendment exists **and** there is documented evidence that the
pinned `security-crypto:1.1.0-alpha07` blocks a production release -- an
Android/AGP/targetSdk incompatibility, a Google/Play policy block, or an
unmitigated high-or-critical security defect in the API actually used here.
Deprecation alone, a routine dependency refresh, or this ADR's scheduled
review does not execute migration work.

## Future migration and rollback contract

The migration release must be a dual-format release. It must not use the
legacy `createPrefs` recovery factory to import data.

1. On first secure-storage access, open the new Keystore-backed store. If its
   state is `complete`, read it as the primary source and do not overwrite it.
2. Otherwise, open the legacy preferences through a non-mutating compatibility
   reader. Read all five fields as one snapshot, preserving the distinctions in
   the inventory table. If this read throws, retain both legacy files, write no
   `complete` marker, report only a non-sensitive failure classification, and
   use a user-confirmed recovery path rather than clearing credentials or PIN
   state automatically.
3. Encrypt the complete snapshot and write it with state `staged` in one
   serialized DataStore update. Reopen it, decrypt every field, and compare it
   field-for-field with the legacy snapshot, including a missing deadline,
   failed-attempt count, and nullable values.
4. Mark the target `complete` only after that verification. A process death at
   any earlier point leaves either the legacy source intact or a `staged`
   target that the next launch verifies or replaces from the intact source.
   Repeating these steps is therefore idempotent and never clears a source.
5. Keep the legacy preference XML and `.bak` untouched throughout the
   migration release and its rollback window. The new implementation must fall
   back to the non-mutating legacy reader if a completed target cannot be
   opened. Removing the AndroidX compatibility reader, its dependency, or the
   legacy files requires a later explicit TDD amendment and release decision;
   it is not part of this migration release.

During that rollback window, regular secure-storage mutations must be
dual-written and verified in both formats. A mutation is reported successful
only after both writes have completed; if the second write fails, persist only
non-sensitive retry state, keep both last-known-valid sources, and do not
silently report a successful connect, disconnect, PIN change, or lockout
update. This keeps a supported downgrade from reading stale credentials or PIN
state while retaining a recoverable source for the next launch.

This contract protects a downgrade to the immediately previous app version,
an interrupted update, and a target-store defect. If neither format can be
read because the device's key material is irrecoverable, the app must fail
closed and preserve the files for diagnosis. Re-authentication, PIN reset, or
secure-storage clear may be offered only as an explicit user-confirmed
recovery action; it must not be the automatic result of a migration failure.

## Backup consequences

ADR-0001 already requires excluding the legacy XML and `.bak` from cloud
backup and device-to-device transfer. This matches Android's warning that an
encrypted-preferences file should not be backed up without its key. The future
implementation must add an explicit exclusion for
`datastore/secure_settings.preferences_pb` in both `backup_rules.xml` and the
cloud-backup and device-transfer sections of `data_extraction_rules.xml`
**before** the target store can receive a value. It must retain the legacy
exclusions until the legacy format is formally retired.

No secure record is restored through Android system backup or device transfer.
That is intentional: a target device creates its own Keystore key, while cloud
connections and PIN state remain local to the device rather than becoming a
silent cross-device credential transfer.

## Required implementation and release gates

No gate below is satisfied by this documentation-only ADR. The future SPEC
must require all of them:

- Accepted TDD amendment and security review of key generation, AES/GCM
  envelope versioning, error handling, and absence of sensitive logs/Sentry
  payloads.
- Unit tests for each of the five values, null/absent semantics, authenticated
  field binding, ciphertext-only persistence, staged-to-complete idempotence,
  and explicit clear/disconnect behaviour.
- Android instrumentation upgrade tests created from a real legacy
  `EncryptedSharedPreferences` fixture, then reopened through the new store.
  They must prove exact preservation of a connected Dropbox token, Drive
  email, PIN hash, failed-attempt count, and both present and absent lockout
  deadlines without asserting or logging their plaintext values.
- Failure-injection tests for a legacy open error, target write error,
  verification mismatch, process death before `complete`, and target-open
  failure. Each must prove that the legacy XML and `.bak` were not deleted and
  that no automatic re-login, PIN reset, or biometric-lock disable occurred.
- Backup-rule tests or XML assertions proving both legacy and target stores are
  excluded from pre-Android-12 backup, cloud backup, and device transfer.
- A release upgrade/downgrade smoke test on a real API-31+ device, followed by
  staged rollout telemetry that records only migration outcome categories. A
  migration failure, unexpected clear, or secure-storage reset stops rollout
  and keeps the compatibility fallback available.

## Consequences

The persistent writer and primary reader will move to supported platform
cryptography while preserving the current `SecureStorage` callers in Dropbox,
Google Drive, and the lock screen. The migration costs a temporary
dual-format compatibility path and more device-level test coverage, but it
avoids silently losing user credentials or PIN state. Until the narrow trigger
and the proposed TDD amendment are both satisfied, the pinned dependency and
current implementation remain unchanged.
