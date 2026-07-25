# Shared Realtime, offline hardening, security, and live E2E
Epic: shared-backend-sync
Order: 04 of 04
Status: backlog
Depends-on: shared-backend-sync-03-android-shared-mode
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Complete experimental Shared sync with foreground Realtime, durable offline/background fallback, sleeping-project recovery, security verification, and a recorded multi-user end-to-end test.
LAYERS: [data] [presentation]
CHANGED_HINT: Supabase Realtime adapter, WorkManager scheduling/retry, Shared status UI, security/integration/device tests, deployment runbook
TEST_TYPES: unit [integration] [compose-ui] [instrumented-compose-ui] [real-e2e]
CONSTRAINTS: Realtime is an optimization, not the source of truth; WorkManager/manual sync and durable cursors recover every missed operation; free-tier sleeping state shows a clear server-starting status and retries with bounded exponential backoff without dropping local operations; local safety backups and manual export are the MVP recovery strategy; feature remains behind debug/internal experimental gate; public rollout is out of scope until security, recovery, and multi-user E2E pass.
=== END SPEC ===

## Acceptance

- Two different Google test users exchange recognizable operations in both directions in one workspace.
- A third non-member fails direct REST/RPC/Realtime reads and writes.
- Realtime disconnect/reconnect, process death, network loss, duplicate delivery, and a sleeping Supabase
  project recover through the durable cursor without loss or duplication.
- Invite/lifecycle and all three conflict classes pass end-to-end while unrelated operations continue.
- Join import/no-import, backup creation, restore, leave, removal, and final-owner deletion are recorded.
- The experimental gate remains off in public/release defaults.

## Implementation links

- commit: pending
- files: pending
