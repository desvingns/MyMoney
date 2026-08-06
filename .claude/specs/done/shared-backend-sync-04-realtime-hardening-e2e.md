# Shared Realtime, recovery hardening, security, and live E2E
Epic: shared-backend-sync
Order: 04 of 04
Status: done
Depends-on: shared-backend-sync-03-android-shared-mode
Date: 2026-07-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: Complete the remaining experimental Shared-sync scope with foreground Realtime, free-tier sleeping-project recovery, security verification, and a recorded multi-user end-to-end test.
LAYERS: [data] [presentation]
CHANGED_HINT: Supabase Realtime adapter, SharedSyncCoordinator extension points, Shared status UI, bounded wake-up/retry, security/integration/device tests, deployment runbook
TEST_TYPES: unit [integration] [compose-ui] [instrumented-compose-ui] [real-e2e]
CONSTRAINTS: Existing durable outbox/cursor, WorkManager/manual-sync fallback, and local safety-backup flows remain authoritative and are not redesigned; Realtime is an optimization, not the source of truth; free-tier sleeping state shows a clear server-starting status and retries with bounded exponential backoff without dropping local operations; feature remains behind debug/internal experimental gate; public rollout is out of scope until security, recovery, and multi-user E2E pass.
DESIGN_TOKENS: colorScheme.sharedSyncConnectedContainer, colorScheme.sharedSyncConnectedContent, colorScheme.sharedSyncStartingContainer, colorScheme.sharedSyncStartingContent, colorScheme.sharedSyncSleepingContainer, colorScheme.sharedSyncSleepingContent, colorScheme.sharedSyncRetryingContainer, colorScheme.sharedSyncRetryingContent, colorScheme.sharedSyncErrorContainer, colorScheme.sharedSyncErrorContent, colorScheme.sharedSyncStatusOutline
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

- commits: `437bb82b`, `e62eacbe`, `08a398b4`, `b28df581`, `6036e917`, `f37d56ac`, `a4e99bda`, `94f1818b`, `6d141c6e`, `456526e6`, `716216fd`, `a9c9a877`
- files: `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedRealtime.kt`; `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SharedOperationDto.kt`; `core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedJournalRpc.kt`; `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinator.kt`; `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/shared/SharedSyncCoordinatorImpl.kt`; `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncEvent.kt`; `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreen.kt`; `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncState.kt`; `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModel.kt`; `feature/cloudsync/src/main/res/values/strings.xml`; `feature/cloudsync/src/main/res/values-ru/strings.xml`; `supabase/migrations/0003_shared_realtime_security.sql`; `supabase/migrations/0004_private_workspace_realtime.sql`; `docs/SHARED_SYNC_REALTIME_RUNBOOK.md`; `app/src/androidTest/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncSharedCardUiTest.kt`; `app/src/test/java/com/kshavrin/mymoney/SharedRealtimeLifecycleContractTest.kt`; `app/src/test/java/com/kshavrin/mymoney/SharedRealtimeRunbookContractTest.kt`; `app/src/test/java/com/kshavrin/mymoney/SharedRealtimeSecurityContractTest.kt`; `core/network/src/test/java/com/kshavrin/mymoney/core/network/shared/SupabaseSharedTransportTest.kt`; `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncContentTest.kt`; `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreenContentTest.kt`; `feature/cloudsync/src/test/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModelTest.kt`
- verification: scoped JVM tests `370 passed / 0 failed / 0 skipped`; user-confirmed manual multi-user/device E2E on 2026-08-06.
