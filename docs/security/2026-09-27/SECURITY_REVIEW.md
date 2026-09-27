# MyMoney and mobile-pipeline security review

Date: 2026-09-27. Review type: repository security assessment, with offline reproductions.

Subsequent user-selected fixes and current verification are documented in [REMEDIATION.md](REMEDIATION.md). The findings below describe the reviewed pre-fix snapshot.

**Outcome: 7 actionable findings: 2 high-priority issues (P1) and 5 medium-priority issues (P2). No P0 issue was established within this scope.** The most consequential findings concern the development machine and publication workflow, rather than a demonstrated cross-user leak from the Android app.

## Scope and evidence boundaries

- MyMoney: `D:/Pet/MyMoney`, HEAD `62050b85df399992d23da75051ff335ce7be1df4`, current working tree.
- mobile-pipeline: `D:/Pet/mobile-pipeline`, HEAD changed concurrently during the audit from `b7e5718841a28b23925dda19c587fb90d6add173` to `38c5919c35d58fd9ef0ed98aa7a2d2245b0ce98d`; reviewed security helper content was unchanged. Root VERSION `1.18.0`, including existing uncommitted changes. Existing source edits were preserved.
- Installed Codex mp-dev `1.17.2`: the proposal helper was inspected and contains the same unrestricted staging and token-in-URL patterns as the repository-generated helpers. This was a scoped comparison, not a complete audit of every installed cache.
- Reviewed Android manifest and backup rules; PIN hashing, secure storage and lock lifecycle; HTTP/Supabase authentication; final relevant SQL definitions and grants across forward migrations; Google Play and AdMob handlers; CSV/DB import-export; pipeline bootstrap/render, proposal publishing, Telegram delivery, crawl primitives, hooks and GitHub workflows.
- Graphify queries provided navigation; findings were checked against source. The mobile-pipeline graph gave limited context for Bash, so those scripts were inspected directly.
- A post-artifact AST graph update was attempted, then stopped after a bounded wait while scanning the large workspace. No updated graph is claimed; application and pipeline source were not modified by this audit.
- No deployed Supabase queries or mutations, production API attacks, Google Play requests, real Telegram sends, Git pushes, phone commands or instrumented UI tests were performed. SQL and RTDN findings are source-confirmed, not live exploit demonstrations.
- This is not a penetration test, full history secret audit, release APK reverse engineering, or complete resolved Gradle dependency audit.

Priority convention: P1 should be addressed before routine use of the affected publication path; P2 requires correction but has narrower impact or additional exploitation conditions. These are review priorities, not CVSS scores.

## Findings

| ID | Priority | Component | Finding |
|---|---|---|---|
| MP-01 | P1 | Proposal publishing | A staged patch can modify and execute the generator before PR review |
| MP-02 | P1 | Direct proposal publishing | Unrelated local files are staged, committed and eligible for push |
| MP-03 | P2 | Proposal authentication | A GitHub token is placed into the URL of an arbitrary origin host |
| MP-04 | P2 | Reference-app crawl | Text and package arguments can inject commands into the device shell |
| MM-01 | P2 | Supporter backend | Client-authored purchase rows grant paid supporter status without server verification |
| MM-03 | P2 | Google Play RTDN | Voided purchase notifications are marked processed without entitlement reconciliation |
| MM-04 | P2 | CSV export | User-controlled text can become spreadsheet formulas |

### MP-01 — Proposed patch executes on the developer machine before PR review

Source: [proposal helper](D:/Pet/mobile-pipeline/templates/common/scripts/{{PREFIX}}-propose-improvement.sh:21), lines 21–32; [batch helper](D:/Pet/mobile-pipeline/templates/common/scripts/{{PREFIX}}-improve-drain.sh:71), lines 71–97. The [improve agent](D:/Pet/mobile-pipeline/templates/common/agents/{{PREFIX}}-improve.md:48) requires patches to change only `templates/`, but the scripts do not enforce that contract.

The helper checks patch applicability, applies every path in the patch, then executes the now-modified `lib/build-marketplace.sh`. An unexpected or malicious queued patch can therefore modify that script and run commands with the developer's filesystem and environment access. The ordinary approval asks to open a PR based on a proposal summary; the later PR/CI review happens after this execution.

Condition: an untrusted or compromised proposal reaches the queue and the user authorizes publication. This is not a demonstrated unauthenticated remote entry point. The problem is that publication also executes changes outside the promised template boundary.

Offline reproduction used the exact helper prefix through regeneration, in a disposable Git fixture. A patch targeting `lib/build-marketplace.sh` wrote the harmless marker `PATCH_EXECUTED`. The test stopped before commit or push.

Recommended correction: enforce normalized patch target allowlists before apply; reject changes outside `templates/`, traversal, unexpected modes and symlink changes. Regenerate using trusted generator code from the base revision in an isolated checkout. Do not execute newly proposed code in the credential-bearing publication process. Apply the guard to direct and batch helpers and regenerate both plugin trees.

Acceptance: a patch touching `lib/build-marketplace.sh`, workflow code or another non-template path is rejected before apply; no marker executes. Valid template edits still produce the intended review branch.

### MP-02 — Direct proposal can publish unrelated local files

Source: [proposal helper](D:/Pet/mobile-pipeline/templates/common/scripts/{{PREFIX}}-propose-improvement.sh:34), lines 34–44; [ignore rules](D:/Pet/mobile-pipeline/.gitignore:47). The batch helper has a clean-worktree preflight; the direct helper does not.

After applying a proposal, `git add -A` stages the entire worktree. The subsequent commit and push can include unrelated edits, private notes and secret configuration variants. The current repository already has unrelated edits, so this is a practical publication boundary problem. `.env` is ignored, but `.env.backup`/`.env.production` are not covered by that rule.

Condition: the direct improvement flow runs in a dirty working tree, with files not protected by ignore rules. No existing private file was actually pushed during this review.

Offline reproduction applied the repository's actual ignore rules to a fixture and ran the exact staging operation. Both `.env.backup` containing a fake credential and an unrelated note entered the index.

Recommended correction: refuse a dirty worktree except for explicitly named proposal artifacts, or use an isolated checkout. Stage an explicit set of reviewed template, generated and changelog paths; verify the index before commit. Add secret-file variant ignore rules as defense in depth. Restrict staging in the batch helper too: a patch can introduce unrelated paths after its initial clean check.

Acceptance: unrelated tracked changes and untracked files remain outside the resulting commit; secret variants are rejected or ignored; only reviewed outputs are pushed.

### MP-03 — GitHub credential is forwarded to a non-GitHub remote

Source: [proposal helper](D:/Pet/mobile-pipeline/templates/common/scripts/{{PREFIX}}-propose-improvement.sh:40), lines 40–42; [batch helper](D:/Pet/mobile-pipeline/templates/common/scripts/{{PREFIX}}-improve-drain.sh:113), lines 113–115.

The helpers strip the URL scheme from `origin`, then prepend `https://x-access-token:${GITHUB_TOKEN}@`. There is no hostname or repository identity check. If origin points to an attacker-controlled HTTPS Git host, that host can request authentication and receive the GitHub token. The credential also appears in the constructed process argument.

Condition: `GITHUB_TOKEN` is set and the selected pipeline checkout has an unexpected/non-GitHub origin. This is a conditional credential-scoping defect, not evidence that the current origin or token has been compromised.

Offline reproduction mocked `git remote get-url` with `https://audit.example.invalid/owner/repo.git` and a fake token. The exact URL construction embedded that token for `audit.example.invalid`. No push or authentication request was sent.

Recommended correction: parse and verify an approved HTTPS hostname and expected repository before credential use; use a hostname-scoped credential helper/`gh` authentication rather than URL interpolation. Fail closed for unsupported schemes and unexpected hosts.

Acceptance: a non-approved remote is rejected before any authenticated network call; fake-token traces contain no token-bearing push URL.

### MP-04 — ADB command injection through entered text and package names

Source: [text input](D:/Pet/mobile-pipeline/templates/spec/skills/app-spec-creator/scripts/crawl/input.sh:95), lines 95–98; [package operations](D:/Pet/mobile-pipeline/templates/spec/skills/app-spec-creator/scripts/crawl/app-control.sh:53), lines 53, 64 and 82; [ADB wrapper](D:/Pet/mobile-pipeline/templates/spec/skills/app-spec-creator/scripts/crawl/_crawl-lib.sh:61). The generated Claude and Codex mp-spec crawl helpers repeat the text-input pattern.

`input.sh` replaces spaces with `%s` and passes the text as a quoted local argument to `adb shell`. Local quoting does not protect the remote shell: ADB joins the arguments into a command string without escaping. Semicolons, command substitutions, quotes, tabs and newlines can be interpreted on the connected Android device. Package arguments in `app-control.sh` are also passed without grammar validation or remote escaping.

Condition: attacker-controlled text reaches a crawl command and a device is connected. Execution is with the device's ADB shell privileges, not automatically host or Android root privileges. An APK screenshot by itself does not trigger the issue; its content would have to influence the entered argument.

Offline reproduction used the same space replacement and locally simulated the AOSP argument-join behavior. A harmless `printf` marker executed after a semicolon/tab payload. No ADB connection was used. [AOSP source](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/client/commandline.cpp) explicitly documents and implements the unescaped join in `adb_shell`.

Recommended correction: quote text for the Android POSIX shell before composing the remote command; handle control characters deliberately. Validate package identifiers against a strict Android package grammar. Keep these checks at the shared helper boundary and propagate generated copies.

Acceptance: metacharacters are delivered as literal data or rejected; package strings with shell syntax fail before ADB; no injected marker executes.

### MM-01 — Fake supporter purchases grant supporter status

Source: [migration](D:/Pet/MyMoney/supabase/migrations/20260813090000_supporter_purchases.sql:19), lines 19–24 and 26–52; [Android client](D:/Pet/MyMoney/core/network/src/main/java/com/kshavrin/mymoney/core/network/shared/SupabaseSupporterApi.kt:32). No later migration in this checkout replaces the grant or purchase trigger.

Authenticated users may insert into `supporter_purchases`. RLS checks only that `user_id` equals the caller. `product_id`, `purchase_token` and `purchased_at` are supplied by the client. The `SECURITY DEFINER` insert trigger grants a `supporters` row using that token, without querying Google Play. Any authenticated caller can fabricate a unique token and receive supporter status and a purchase count.

Impact is integrity of the paid supporter badge/history and monetization data. This finding does not assert that the separate Plus subscription can be obtained through this table.

Confirmation: migration/grant/trigger source and client request path were inspected. No fake row was inserted into a real database. [Google's billing security guidance](https://developer.android.com/google/play/billing/security) requires verification with the provider before granting purchased benefits; uniqueness of a client-supplied token alone is not verification.

Recommended correction: revoke direct authenticated INSERT and route purchase submission through a server verifier. Check package, allowed product, purchase state and token ownership/idempotency using Google Play; only a trusted server path should write the verified purchase and grant supporter status. Review historical records before treating them as verified evidence.

Acceptance: a random token, wrong product and another user's purchase fail; a valid verified purchase grants once; duplicate callbacks cannot inflate counts.

### MM-03 — Voided purchase is acknowledged without revoking benefits

Source: [RTDN handler](D:/Pet/MyMoney/supabase/functions/google-play-rtdn/index.ts:283), lines 283–312; notification classification/token extraction at lines 73 and 79–83.

The parser recognizes `voidedPurchaseNotification` and extracts its token. The handler only reconciles subscription notifications, records one-time product notifications, and handles tests. A voided notification falls through with `action: event_recorded`, gets marked `processed`, and returns HTTP 200 without changing entitlements or recomputing workspace billing state.

Condition: a refund/revocation/chargeback is represented by this notification and no separate successfully processed subscription event compensates for it. If a subscription revocation event also arrives and succeeds, that path can revoke access. The voided path itself cannot. No periodic Voided Purchases reconciliation was found in the reviewed code.

Confirmation is static handler analysis; live Google delivery timing was not tested. [Google's voided-purchase guidance](https://developer.android.com/google/play/billing/security#detect_and_handle_voided_purchases) describes revocation/clawback processing for refunded and charged-back purchases.

Recommended correction: reconcile the affected purchase in a dedicated voided branch and apply entitlement/workspace changes idempotently. Account for partial-quantity refunds and product types; do not indiscriminately revoke every partial refund. Record processing success only after the required state change succeeds, with retries and reconciliation for missed events.

Acceptance: a full revoked subscription represented solely by a voided notification removes access; duplicate delivery does not double-apply; failed reconciliation remains retryable; unrelated purchases remain unaffected.

### MM-04 — CSV export retains spreadsheet formula prefixes

Source: [export fields](D:/Pet/MyMoney/core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt:199), account/category/note fields in the export; [CSV escaping](D:/Pet/MyMoney/core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/BackupRepositoryImpl.kt:1114), lines 1114–1119.

`csvField` escapes commas, double quotes and line breaks, but leaves leading formula characters unchanged. Account/category names or notes containing `=1+1` are exported with that exact prefix. When a compatible spreadsheet opens the file, it can interpret attacker-authored text as a formula; external requests or other dangerous behavior depend on the formula and spreadsheet settings.

Condition: attacker-controlled text is imported or shared into financial records, then an exported CSV is opened in a spreadsheet. CSV creation itself does not execute commands inside MyMoney.

Offline reproduction faithfully translated the small Kotlin function and confirmed that `=1+1` survives unchanged. No spreadsheet, URL or command was opened. See [OWASP CSV Injection](https://community.owasp.org/attacks/CSV_Injection): ordinary CSV quoting does not ensure literal spreadsheet cells.

Recommended correction: define a spreadsheet-safe export for textual columns, or export an XLSX with explicit text cell types. Preserve intentional negative numeric values and canonical import round trips; blindly prefixing every CSV field would alter data semantics. Validate the chosen policy in the spreadsheet applications the product supports.

Acceptance: formula-like names/notes remain literal in supported spreadsheet exports, while numeric money/date fields and import/export round trips retain their intended values.

## Additional hardening and operational observations

- **Purchase-to-account binding:** `bind-google-play-purchase/index.ts`, lines 101–132, rejects a token already bound to another user but assigns an unbound verified token to whichever authenticated user submits it first. The subscription billing flow does not set an obfuscated account ID. Consider a server-verifiable user binding and `linkedPurchaseToken` reconciliation. This is conditional hardening: token acquisition by an attacker was not demonstrated, and a verified purchase token currently functions as possession proof. It is not counted as an independently proven bypass.
- **Telegram recipient authority:** the delivery helper accepts `TG_TARGET` from a project `.env` before machine-wide configuration. A project can therefore select a different recipient while using a globally stored session. For a workflow explicitly described as sending to yourself, force `me` or require an explicit approved target. No Telegram send or concrete malicious project configuration was tested.
- **CI/supply chain:** workflows generally reference third-party actions by version tags, rather than immutable commit hashes; mobile-pipeline `validate-plugins.yml` does not declare explicit token permissions. Pin actions and declare minimal permissions. No malicious action version, exploitable dependency CVE or workflow compromise was established. [GitHub security guidance](https://docs.github.com/en/actions/reference/security/secure-use) covers immutable action references and credential exposure.
- No evidence of a permissive custom TLS trust manager/hostname verifier was found in inspected app source. The general debug BODY logger is not used by Supabase: the shared transport has a separately qualified OkHttp client without that interceptor. It was therefore not reported as a Supabase token leak.
- SecureStorage recovery disables the local lock when its PIN is missing; database backup exports are plaintext. These deserve explicit threat-model/product review, but they are documented recovery/export behavior rather than evidence of a remote bypass in this assessment.

## Controls that were observed

- PIN uses versioned PBKDF2-HMAC-SHA256, a random 16-byte salt, 600,000 iterations and constant-time comparison; legacy rehashing and stored attempt/lockout state exist.
- Sensitive token/PIN storage uses EncryptedSharedPreferences and a Keystore-backed AES-GCM master key. Backup rules explicitly exclude that preference file and its backup copy. Ordinary cloud backup does not include the financial database; device transfer is explicitly configured.
- Shared HTTP calls use a distinct client without the debug BODY logger. Authentication uses a real `getUser` call in Edge functions; bearer presence alone is not trusted.
- Google Play RTDN verifies signed Google OIDC claims including issuer, audience, service-account email and email verification. AdMob verifies provider signatures and a signed expiring reward token. Public webhook `verify_jwt = false` is therefore not itself an authentication finding.
- Relevant shared-workspace RPCs have explicit membership checks and execute grants/revokes. Later migrations harden search paths and exposed columns; baseline migration code was not incorrectly treated as the final deployed function definition.
- Sentry screenshots/view hierarchy, traces, profiling and automatic sessions are disabled in application initialization.

These observations establish the presence of controls, not a guarantee that every runtime path or deployed configuration is safe.

## Checks and reproducibility

- Secret-pattern scan: 1,645 tracked text files in MyMoney and 764 in mobile-pipeline, each at most 2 MB. Rules covered recognizable GitHub tokens, AWS access key IDs, private-key headers and literal Telegram sessions. Only one private-key-header match occurred: a string used to parse PEM input in `google-play.ts`, not a private key. This limited scan found no literal credential under those rules; it does not establish that history, ignored local configuration or every secret format is clean.
- OSV `querybatch`: 122 unique package/version pairs from six selected direct Maven dependencies, two direct Edge-function npm imports, and the three pipeline npm lockfiles. No advisory matches were returned. Transitive resolved Gradle dependencies, Kotlin platform variants, installed system tools and undisclosed advisories were not comprehensively covered.
- Offline checks: five reproductions (patch execution, broad staging, credential URL construction, simulated remote shell injection, CSV prefix retention) plus two static backend contract checks. The first harness run failed on the shell payload setup; it was corrected and the complete final run passed. This is not an application/unit/instrumented test run.
- [Evidence JSON](D:/Pet/MyMoney/docs/security/2026-09-27/evidence.json) distinguishes `REPRODUCED` from `STATIC_CONFIRMED`.
- [Reproduction script](D:/Pet/MyMoney/docs/security/2026-09-27/verify_review.py) uses fake credentials, Git fixtures and local shell simulation. It does not contact a real service or device.
- Fixtures, including the incomplete first attempt, are preserved under `D:/Pet/archive/mobile-pipeline/2026-09-27/security-review/` and indexed in `D:/Pet/archive/INDEX.md`. No source file was deleted or repaired by the audit.

## Recommended order

1. Repair MP-01 and MP-02 together: trusted generator execution, isolated publication checkout, enforced path/index scope.
2. Repair MP-03 and MP-04 and propagate the canonical fixes into generated Claude/Codex plugin trees and installed copies.
3. Move supporter purchase verification to the server and add voided-purchase reconciliation.
4. Define and verify spreadsheet-safe export behavior.
5. In a separate verification pass, inspect the actually deployed Supabase policies/functions and release artifact, then run abuse cases against an isolated backend test project. Repository code alone cannot certify deployed infrastructure.
