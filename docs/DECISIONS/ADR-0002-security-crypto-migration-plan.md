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
itself. The future implementation will also put every `SecureStorage`
read, write, clear, migration, and reconciliation behind one process-wide
serialized gate. No caller may observe a primary store, or run a
connect/disconnect/PIN/lockout/clear mutation, until that gate has completed
migration or returned an explicit recoverable failure outcome. This gate
serializes work in one app process only. If a future design introduces
multi-process access, it must add and verify cross-process coordination before
relying on this protocol.

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
- **T0, after the preparation trigger is met:** within five business days,
  prepare the migration design, proposed TDD amendment, test fixtures, and a
  separately approved implementation SPEC. This preparation does not change
  production storage or release migration code.

The **preparation trigger** is documented release-block evidence that the
pinned `security-crypto:1.1.0-alpha07` cannot ship: an Android/AGP/targetSdk
incompatibility, a Google/Play policy block, or an unmitigated high-or-critical
security defect in the API actually used here. That evidence authorizes the
design, TDD-amendment, and implementation-SPEC preparation above. Deprecation
alone, a routine dependency refresh, or this ADR's scheduled review does not.

The separate **implementation authorization** is an approved TDD amendment
plus an approved implementation SPEC that incorporates the evidence and every
gate in this ADR. Only that authorization permits code migration or a release;
release-block evidence alone does not.

## Future migration and rollback contract

The migration release must be a dual-format release. It must not use the
legacy `createPrefs` recovery factory to import data. One process-wide
serialized gate owns the following protocol; every `SecureStorage` read,
write, and clear waits for its outcome. In particular, no
connect/disconnect/PIN/lockout/clear mutation may start while first-access
migration or reconciliation is pending.

### First access

1. Inside the gate, open the new Keystore-backed store and inspect its durable
   encrypted migration marker. A `complete(generation)` marker is primary only
   after the reconciliation rules below compare the full logical five-value
   snapshot with legacy storage; generation alone never establishes freshness.
2. If no complete target exists, open legacy preferences through a
   non-mutating compatibility reader and take one snapshot of all five fields.
   If this read throws, retain both legacy files, write no `complete` marker,
   report only a non-sensitive failure classification, and return an explicit
   recoverable failure outcome. It must not clear credentials, PIN state, or
   the biometric-lock setting.
3. Write an encrypted target `intent(generation = 0)` marker, then persist the
   snapshot as `staged(0)`. Reopen it, decrypt every field, and compare it
   field-for-field with the legacy snapshot, including a missing deadline,
   failed-attempt count, and nullable values. Only then write
   `complete(0)`. A process death leaves the gate to resume this same protocol
   on the next process start; it never invokes the deletion recovery path.

### Rollback-window mutations and reconciliation

The legacy XML and `.bak` remain untouched throughout the migration release
and its rollback window. An older application ignores the following additional
legacy metadata and therefore continues to read its current values.

An existing legacy file with no generation field is the initial
`legacy-generation = 0` baseline. That baseline is not a freshness proof:
an older app can change a value without adding the field, and `clearAll()` can
remove it. On every gate entry during the rollback window, the new reader must
therefore read the legacy snapshot and decrypt the target snapshot, then
compare all five logical values before treating target as primary. The
comparison includes each nullable field's absent/clear state, failed-attempt
count, and both present and absent lockout-deadline states.

1. For every mutation, the gate first takes a canonical, fully reconciled
   five-value pre-intent snapshot and allocates monotonic generation `g + 1`.
   It then durably writes encrypted target `intent(g + 1)` containing the
   expected target generation `g`, observed legacy generation (including an
   absent baseline), and an encrypted digest of that canonical snapshot. The
   digest includes all absent/clear semantics. It makes a crash detectable
   before either format changes, while generations remain diagnostic only.
2. The gate writes the complete new secure snapshot **legacy first**, including
   a non-secret legacy generation field, and verifies its committed value.
   It then writes and verifies the matching encrypted target snapshot with
   generation `g + 1`.
3. Only after both snapshots match does the gate atomically replace the target
   marker with `complete(g + 1)` and report success to the caller. A mutation
   cannot be reported successful after only one format has changed.
4. On the next first access, a new reader that sees `intent(g + 1)` must not
   use the target as primary. It reads both five-value snapshots, computes
   their canonical digests, and compares them with the encrypted expected
   pre-intent digest before considering the stored generations. If both
   snapshots match that digest, no source write completed and it discards the
   intent even when legacy generation is stale or absent. If legacy differs but
   target matches the expected digest, it adopts and verifies the legacy
   snapshot into target; if both snapshots match the same non-expected values,
   it verifies and commits them as the interrupted legacy-first mutation. A
   target-only change, or unequal snapshots with no safe legacy-first lineage,
   returns an explicit recoverable failure, preserves both sources, and blocks
   all secure-storage operations until the caller chooses recovery.
5. Even with `complete(g)`, a value mismatch is treated as a possible old-app
   write or clear, regardless of legacy-generation. During the rollback window
   legacy is the authoritative external snapshot: the gate first writes
   `intent(g + 1, legacy-adoption)`, copies and verifies all five legacy values
   into the encrypted target, then writes `complete(g + 1)`. Thus an old-app
   clear is safely adopted as five cleared values, rather than leaving a stale
   target primary. Legacy generation may remain stale or absent after this
   adoption; the next mutation's expected pre-intent digest, not generation,
   handles an interrupted write before the legacy update.
6. If the target cannot be opened, the gate uses the non-mutating legacy reader
   as the fallback source and leaves all files intact. Removing the AndroidX
   compatibility reader, its dependency, or the legacy files requires a later
   explicit TDD amendment and release decision; it is not part of this
   migration release.

This protocol protects a downgrade to the immediately previous app version,
an interrupted update, and a target-store defect without letting a new reader
serve a stale target. If neither format can be read because the device's key
material is irrecoverable, the app must fail closed and preserve the files for
diagnosis. Re-authentication, PIN reset, or secure-storage clear may be offered
only as an explicit user-confirmed recovery action; it must not be the
automatic result of a migration failure.

### Failure channel and user-confirmed recovery

The current `SecureStorage` contract returns `SecureSettings` from `read()`
and `Unit` from writes and clears, so it cannot represent the required
recoverable failure outcome. The future TDD amendment and implementation SPEC
must choose one compatible non-secret typed channel before code starts: either
a typed storage exception mapped at the repository/UI boundary, or an explicit
`Result`/status API change. It must carry a failure category only, never a
credential, PIN hash, email, ciphertext, or key material. The selected UI flow
must let the user retry while preserving both stores, then explicitly choose a
provider re-authentication, PIN reset/disable, or secure-storage clear only
after confirmation. This ADR selects neither API shape and makes no code change
to the current interface.

## Backup consequences

ADR-0001 establishes exclusion of the legacy XML and `.bak` as a backup
invariant. It does not establish coverage for the future target file. The
future implementation must inspect the then-current backup-rule sources,
retain the legacy exclusions, and add an explicit exclusion for
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
- Future unit and instrumentation tests that, after target `complete`, run an
  old-app fixture to change each of the five legacy fields separately and to
  call `clearAll()`. On reopening, the new reader must adopt each legacy value
  or absence before serving target data, regardless of legacy-generation.
- Failure-injection tests for a legacy open error, target write error,
  verification mismatch, process death after the target intent marker, after
  the legacy-first write, after the target snapshot write, and before the
  `complete` marker, plus target-open failure. Fresh-process recovery must
  prove the generation-and-value protocol reconciles before any target is
  primary, the legacy XML and `.bak` were not deleted, and no automatic
  re-login, PIN reset, or biometric-lock disable occurred.
- A fresh-process crash test for this exact sequence: adopt legacy values into
  `complete(g + 1)` while legacy generation remains stale or absent, begin the
  next mutation and persist `intent(g + 2)`, then die before the legacy write.
  Both prior snapshots must match the encrypted expected pre-intent digest, so
  recovery discards the unstarted intent and preserves `complete(g + 1)`.
- Concurrency tests that start reads and each mutation type together. They must
  prove one process-wide gate serializes migration, reconciliation, and all
  `SecureStorage` read/write/clear operations; no caller observes a pending or
  stale target, and no mutation starts before an explicit migration outcome.
  If multi-process access is introduced, separate tests must verify the
  selected cross-process coordinator before this gate is relied upon.
- Backup-rule tests or XML assertions against the actual release sources must
  prove both legacy and target stores are explicitly excluded from every
  applicable pre-Android-12, cloud-backup, and device-transfer rule.
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
