# Security remediation — 2026-09-27

Scope: the four issues selected by the user from SECURITY_REVIEW.md. The original review is a
historical assessment of the earlier HEADs; this document records subsequent implementation.

## Changes

| Finding | Remediation |
|---|---|
| MP-02 | Both proposal helpers reject unrelated pre-existing staged/modified/untracked files. Publication stages explicit patch targets, bounded generated-plugin metadata and lifecycle receipts, never the complete worktree. Secret file types are excluded; `.env.*` is ignored. |
| MP-03 | Tokens remain in the environment and are served by a credential helper only for HTTPS github.com. The actual push URL is checked, multiple URLs are rejected and HTTP redirects are disabled. Non-GitHub HTTP origins do not receive the token. |
| MM-01 | Authenticated clients cannot insert purchase rows or invoke the verified-write RPC. The new authenticated Edge Function validates the receipt with Google Play product purchases V2, accepts only PURCHASED coffee_small/coffee_large, and derives owner/time/product from authentication and provider data. The database serializes token ownership and grants Supporter only after verification. |
| MM-04 | Every nonempty free-text CSV field is quoted and prefixed with an apostrophe. The additive text_encoding column identifies apostrophe-v1; only that version is decoded on import. Literal apostrophes, whitespace, separators and newlines are restored exactly. Legacy 9/11-column inputs remain supported. |

Proposal patches are additionally restricted to non-secret files below templates/, closing the
specific MP-01 generator-replacement reproduction. Approved template edits remain executable
pipeline inputs and require the existing user review gate.

## Deployment and compatibility

- mobile-pipeline release: 1.18.1. Both Claude and Codex trees are regenerated from canonical templates.
- The currently installed Codex mp-dev 1.17.2 proposal helpers were hotfixed from canonical sources;
  original files and before/after hashes are preserved in the shared archive. No other installed
  plugin behavior or manifest version was changed.
- Supabase migration verified_supporter_purchases is applied to shwzjlkhlpgbmzgnxhxi.
- verify-supporter-purchase is deployed ACTIVE with gateway JWT validation and independent getUser authentication.
- Existing unverified purchase rows are retained, hidden from client reads and excluded from grants.
  Client refresh automatically submits the server-owned legacy receipts for verification.
- Older Android clients that POST directly to the table now receive a denial; install the updated
  client to synchronize receipts. No historical purchase rows were deleted.
- CSV adds a twelfth column. Updated MyMoney imports this version plus legacy exports. Editing or
  resaving CSV in a spreadsheet can alter its text typing; protection applies to the fresh export.

## Evidence

- Deno: 7 handler/receipt tests PASS, including forged fields, pending/foreign purchases, consumed
  receipts, outage, legacy refresh, idempotent retry and an ownership race. Edge type-check PASS.
- PGlite/PostgreSQL: migration execution, INSERT/RPC denial, legacy quarantine, verified grant,
  duplicate token, cross-owner conflict and RLS isolation PASS.
- Live database: authenticated INSERT=false, authenticated verified RPC=false, service_role RPC=true.
- Live Edge gateway: unauthenticated fake receipt returns HTTP 401.
- Canonical and installed proposal helpers: regression tests PASS, including secret capture and
  arbitrary host/pushurl forwarding. Queue lifecycle/generator-failure tests PASS.
- Android JVM, static analysis and generated runtime checks: see completion receipt below.

No real Google Play purchase or refund was performed. The Edge provider seam is tested with
controlled responses; the live check validates access control, not successful paid delivery.
Pixel 5/API 34 was absent at discovery; no device result is claimed without a subsequent receipt.


## Completion receipt

- JVM: core:domain 450, core:network 130, core:sync 225 tests; 805 passed, zero failures/errors/skips.
- The database Android test source compiles, including encoded-formula transfer round-trip coverage.
- Detekt on all four affected modules PASS; focused ktlint check PASS.
- Edge tests: 7/7 PASS, with an additional authenticated-owner assertion for account-switch races.
- Canonical, installed, Claude-generated and Codex-generated publication security checks PASS.
- Proposal lifecycle, bootstrap, GitHub push contract and graph structural checks PASS.
- Both generated proposal adapters exactly match the canonical sources.
- Server function version 2 ACTIVE, verify_jwt=true; final source includes the owner assertion.
- Both AST graphs were refreshed. MyMoney: 17851 nodes/36022 edges; pipeline: 3502/5299.
- No device test or real paid-purchase success is included in this receipt.
