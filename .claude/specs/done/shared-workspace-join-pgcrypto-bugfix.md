# Shared workspace invite join fails with generic sync error

RUNTIME_BUG: true
Status: done
Date: 2026-08-06

## Symptom

On Pixel 8, joining another account's Supabase shared workspace by invite code ends with `Sync failed. Please try again.`.

## Reproduction

1. Sign in as a different account on Pixel 8.
2. Open Settings → Cloud sync → Shared.
3. Enter a valid invite code and choose either local-data policy.
4. Tap Join.

## Evidence

- Supabase API logs show `POST /rest/v1/rpc/join_workspace` returning HTTP 404.
- Direct RPC error body is SQLSTATE `42883`: `function digest(text, unknown) does not exist`.
- `pgcrypto` is installed in the `extensions` schema while `join_workspace` uses `set search_path = public` and calls unqualified `digest`.
- The Android transport maps this server-side RPC failure to the generic sync snackbar.

## Scope

- Qualify the `pgcrypto` call in the authoritative migration and add a forward migration for already-provisioned projects.
- Add a regression contract test that prevents an unqualified `digest` call in `join_workspace`.
- Preserve invite validation, membership limits, and data-import behavior.

## Acceptance criteria

- `join_workspace` calls `extensions.digest(p_token, 'sha256'::text)`.
- A fresh migration can be applied to a database where `0001` was applied manually and fixes the live function without replaying the baseline migration.
- Existing Android unit/contract tests and the relevant build pass.
- Pixel 8 reaches the join RPC without the `digest` SQLSTATE failure; invalid/expired invite errors remain distinct server responses.

## Verification

- Scoped app contract tests: `9 passed / 0 failed / 0 skipped`.
- User-confirmed Pixel 8 join flow and distinct invalid/expired invite responses on 2026-08-06.

## Implementation links

- commit: `ea914537`
- files: `supabase/migrations/0001_shared_workspaces.sql`; `supabase/migrations/20260806120000_fix_join_workspace_pgcrypto_digest.sql`; `app/src/test/java/com/kshavrin/mymoney/SharedWorkspaceJoinMigrationContractTest.kt`
