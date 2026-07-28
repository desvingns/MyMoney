# Android Shared mode, join/import, backups, and membership lifecycle
Epic: shared-backend-sync
Order: 03 of 04
Status: active
Depends-on: shared-backend-sync-02-operation-api-and-conflicts
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add Shared as a mutually exclusive Android sync mode with Google sign-in, workspace create/join flows, safe local-data import choices, internal backups, conflict resolution UI, and leave/removal lifecycle handling.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: SyncTarget/binding orchestration, :core:database internal backup/restore, :core:sync Shared repository, :feature:cloudsync Shared/workspace/conflict flows
TEST_TYPES: unit [dao] [compose-ui] [instrumented-compose-ui]
CONSTRAINTS: Dropbox, Google Drive, and Shared are mutually exclusive; one active workspace and one active local database; no workspaceId/profile column across domain entities; before join the user explicitly chooses import or no import, with no-import selected by default; both paths create an internal safety backup before active data changes; no-import replaces active local finance data with Shared data; import publishes local Transaction/Account/Category operations; leaving or removal cuts remote access but preserves the current shared data as a personal local copy; any member may resolve conflicts; author appears only in conflict UI.
=== END SPEC ===

## Acceptance

- Switching into Shared cannot silently publish personal data or keep a personal provider syncing.
- Join-without-import creates a restorable backup before loading Shared data.
- Join-with-import preserves local financial entities and publishes them through the Shared journal.
- Internal backup list/restore is usable after failed join, leave, removal, and provider migration.
- Removed users receive access-denied state, stop background work, and retain a personal local copy.
- Conflict UI resolves either version while unrelated sync continues.

## Implementation links

- commit: pending
- files: pending
