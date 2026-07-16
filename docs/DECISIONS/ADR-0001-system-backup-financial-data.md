# ADR-0001: System backup policy for financial data

- Status: Accepted
- Date: 2026-07-16

## Context

MyMoney is local-first and offers opt-in Dropbox or Google Drive snapshots rather
than automatic transaction uploads (TDD §1.4, lines 86–86; §2.4, lines 232–243).
The Android system backup configuration previously included `monefy.db` and its
WAL sidecars in cloud backup without app-level encryption. Secure preferences,
which hold the PIN and cloud credentials, were already excluded.

The user selected the privacy-preserving option to keep financial data available
for device-to-device transfer while withholding it from system cloud backup.

## Decision

Choose option 2: exclude `monefy.db`, `monefy.db-shm`, and `monefy.db-wal` from
system cloud backup. Keep those files in the Android 12+ `device-transfer` rule.

Keep `app_settings.preferences_pb` and non-secure shared preferences in their
current backup allowlists. Exclude `com.kshavrin.mymoney_secure.xml` and its
`.bak` sidecar from every backup mode; this is an invariant.

This decision does not add encryption before backup and does not change the
separate opt-in Dropbox or Google Drive sync feature.

## Rejected alternatives

- Keep the existing cloud backup policy. Rejected because it exposes an
  unencrypted financial SQLite snapshot to system cloud backup.
- Encrypt the database before system backup. Rejected for now because safe key
  lifecycle, restore, and migration design are not specified; implementing it
  would exceed this decision's scope.

## Consequences

- A system-cloud restore can retain settings but cannot restore financial
  history. Device-to-device transfer retains the Room database.
- The app's opt-in provider sync remains the route for cloud-hosted financial
  snapshots and is unaffected by this policy.
- Production Room schemas must still use explicit migrations; destructive
  fallback remains limited to the unreachable dev/QA sentinel 99 (TDD migration
  policy, lines 1949–1952).
