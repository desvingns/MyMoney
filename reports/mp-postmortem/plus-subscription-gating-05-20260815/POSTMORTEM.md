# MP Dev postmortem: `plus-subscription-gating-05`

Status: SPEC complete, pushed to `main`, report awaiting external analysis.

Project: `D:\Pet\MyMoney`

SPEC: `.claude/specs/done/plus-subscription-gating-05-cloudsync-gating-and-warning-banners.md`

Epic/order: `plus-subscription-gating`, SPEC `05 of 10`

Correlation ID: `20260814T000000Z-mp-feature-plus-subscription-gating-05`

Report created: 2026-08-15

## Required handling of this report

This folder is intentionally separate from the implementation artifacts so that a stronger model can analyze the pipeline failure modes without reopening the SPEC. After the analysis is complete, move the whole folder, preserving its name, to:

`D:\Pet\MyMoney\archive\mp-postmortem\plus-subscription-gating-05-20260815\`

Do not delete the folder or its contents. The repository rule is archive-by-move.

## Executive summary

The SPEC looked like a UI gate, but its acceptance surface crossed presentation, entitlement state, navigation, domain interfaces, HTTP/RPC transport, WebSocket/realtime error handling, sync lifecycle cleanup, Supabase column grants, localization, and multiple test layers. The implementation itself was not the only source of delay. The dominant delay came from the pipeline discovering cross-layer omissions incrementally through long, serial semantic-review cycles.

The strongest evidence-backed causes are:

1. The initial risk route was `high`, so the pipeline used the powerful developer, repeated semantic review, an architect capsule, an independent critic, and the full verifier.
2. Semantic review found several new batches of state-machine and lifecycle defects after each repair. The batches were not all visible in the first acceptance/test matrix.
3. Semantic-review agents often took roughly 8–15 minutes of estimated runtime and sometimes stopped producing a result until interrupted. The pipeline had no effective heartbeat/timeout escalation.
4. The first scoped runner was given pure-JVM modules whose task names did not exist for the Android runner script. It reported a compile/config failure even though the appropriate explicit Gradle tasks passed.
5. Deterministic review repeatedly flagged `runBlocking` in test clock seams. The actual fix was an allowlisted `mp-real-io:` marker, but this consumed two repair/review loops.
6. The first full verifier found a server-schema omission that Android tests could not detect: the client selected `billing_state` and `billing_state_until`, while the least-privilege Supabase grant exposed only the old columns.
7. The verifier then found that three new use cases had no dedicated tests. Adding those tests exposed a pre-existing compile defect in `EntitlementStateMachineTest`, which required one more repair.

The result was correct and fully verified, but the pipeline behaved as a long discovery loop rather than as a bounded implementation plan. This is the main issue for pipeline improvement.

## What the SPEC required

The required behavior was:

- Free users attempting Shared create/setup/enabling are routed to the Plus paywall.
- Participants do not pay and do not receive a renewal/paywall offer; the owner-pays rule is shown both for create and join.
- Trial, Grace, and expiry warnings are separate from generic technical errors.
- Grace makes the shared workspace read-only for all participants; participants see no purchase CTA.
- A server `entitlement_required` response is authoritative even if local state still says Plus.
- The same entitlement refusal behavior works through HTTP/RPC and Supabase realtime paths.
- Auth or membership loss fails closed and clears stale Shared state, while transient Network/Server failures retain recoverable local state.
- English and Russian strings are present and `:feature:cloudsync` does not depend on `:core:billing`.

The apparent “simple” UI request therefore contained at least four state machines: entitlement/warning state, owner/participant role state, server error classification, and local Shared binding/realtime cleanup.

## Final implementation and evidence

Final implementation commits, in chronological order:

| Commit | Purpose |
|---|---|
| `08ec42be` | Shared entitlement warning/read-only design tokens |
| `fdb88364` | Initial CloudSync gating, navigation, transport, realtime, and UI implementation |
| `070ca33e` | Use-case boundaries replacing feature ViewModel repository injection |
| `83347850` | First semantic repair batch: stale fixtures, server billing state, realtime terminal mapping, fail-closed behavior, participant copy, cancellation |
| `090c4f26` | Allowlisted real-clock test seams |
| `e31728ce` | Realtime payload typing, warning precedence, entitlement refresh, fail-closed coordinator default |
| `a74ed6ea` | More allowlisted realtime test-clock seams |
| `afc52d65` | Warning/error precedence and stale writable-state repair after the approved architect capsule |
| `b7ac3d4b` | Preserve entitlement refusals through realtime close/publish paths and update stale app fixture |
| `a9f2dee8` | Auth cleanup, participant generic errors, primitive realtime payload handling, owner-pays UI assertions |
| `fb9adff5` | Shared exact entitlement parser for HTTP/RPC/realtime, realtime auth cleanup, restore handling |
| `a3a8311c` | Preserve generic error, suppress participant join refusal paywall, clean ownership Auth loss |
| `c5611a88` | Regression tests for warning/error, participant refusal, and ownership cleanup |
| `ac6f7534` | Supabase authenticated SELECT grant for billing columns plus pgTAP schema contract |
| `8fc58ad2` | Dedicated tests for the three new use cases |
| `bb7ac5f3` | Minimal compile repair for missing `Instant` date helpers in an existing entitlement state-machine test |
| `54d75581` | SPEC close-out, PROGRESS update, and move to `done/` |

Verification evidence:

- Deterministic Android reviewer: pass after final union.
- Scoped Android runner: `547 passed / 0 failed / 0 skipped`.
- Full Android runner: `2219 passed / 0 failed / 0 skipped`; detekt and lint were `ok`.
- Explicit `:core:domain:test :core:sync:test`: BUILD SUCCESSFUL after the final test additions.
- Independent critic: pass with no findings.
- Final full verifier: pass with no findings.
- Final verifier confirmed navigation wiring, Hilt graph, EN/RU strings, test inventory, client projection/schema grant alignment, strict entitlement mapping, and pgTAP contract presence.
- The Supabase migration and pgTAP contract were statically validated. No live Supabase CLI/project application was available in this run.
- The SPEC was pushed with `git push origin HEAD` to `main`.

The final verifier left four manual checklist items for device/user-flow confirmation: Free-owner paywall, warning CTA behavior, participant read-only behavior, and both owner-pays explanations. This SPEC was not an explicit visual task, so the documented Pixel 5 visual gate was not required and those manual items were not claimed as executed.

## Timeline and observed cost

The telemetry source is `selfimprove/runs/2026-08.jsonl`, lines 177–193 for this correlation ID. Its `usage_source` is `estimated`, so the durations are useful for diagnosis but are not a precise wall-clock trace.

### Telemetry sequence

| Time (UTC) | Stage | Result | Estimated duration / effect |
|---|---|---|---|
| 20:59 | Deterministic reviewer | Fail: 4 violations | 12 s |
| 21:21 | Reviewer retry | Pass | 16 s |
| 21:21 | Semantic reviewer | Fail: 8 findings | 700 s; first large semantic batch |
| 21:47 | Scoped runner | Fail: `268 passed`, Gradle config/task failure | 40 s; wrong task shape for pure JVM modules |
| 21:49 | Corrected scoped runner | Pass: `520 passed` | 80 s |
| 21:49 | Reviewer | Fail: 4 test-clock violations | 18 s |
| 21:51 | Reviewer retry | Pass | 17 s |
| 22:03 | Semantic reviewer | Fail: 4 findings | 500 s |
| 22:21 | Scoped runner | Pass: `526 passed` | 90 s |
| 22:22 | Reviewer | Fail: 6 test-clock violations | 16 s |
| 22:25 | Reviewer retry | Pass | 16 s |
| 22:43 | Semantic reviewer | Fail: 3 findings; architect capsule required | 700 s |
| 03:26 | Approved capsule developer repair | Pass | 900 s; includes a long cross-layer repair |
| 03:26 | Post-capsule reviewer | Pass | 14 s |
| 03:42 | Semantic reviewer | Fail: 3 findings | 650 s |
| 03:50 | Developer repair and reviewer | Pass | 700 s + 14 s |

The telemetry excerpt ends before the later manual repairs. Git timestamps show the complete implementation/close-out window from `08ec42be` at `2026-08-14T22:37:40+02:00` through `54d75581` at `2026-08-15T08:42:45+02:00`, approximately ten hours. The user’s “about five hours” is therefore compatible with a narrower active-work estimate, but not with the complete wall-clock commit window. This discrepancy itself is an observability problem: the pipeline does not clearly separate agent execution, queueing, user approval wait, idle polling, and manual orchestration.

### Later findings not fully represented in the telemetry excerpt

After the recorded post-capsule events, fresh semantic reviews found and repaired additional batches:

- `STATE-007`, `TESTS-011`, `STATE-008`: entitlement refusal could be dropped on publish, a stale Android fixture omitted known access, and primitive realtime close/error classification was incomplete.
- Auth cleanup and participant generic-error issues remained in some paths; `a9f2dee8` repaired them.
- HTTP/RPC primitive/nested entitlement parsing, realtime startup Auth cleanup, and stale refusal restoration were found by another fresh review; `fb9adff5` repaired them.
- A final semantic batch found generic-error clearing, participant join paywall exposure, and ownership lookup Auth cleanup; `a3a8311c` repaired them.
- The first verifier found `SERVER-001`: Supabase `workspaces` billing columns were absent from the authenticated column grant. `ac6f7534` added the migration and pgTAP contract.
- The next verifier found three missing dedicated use-case tests. `8fc58ad2` added them.
- The explicit core test run then exposed missing `Instant.plusDays`/`minusDays` helpers in an existing state-machine test. `bb7ac5f3` fixed the compile gap.

## Failure taxonomy

### 1. Scope and task-resolution failure

The runner script initially received `:core:common` and `:core:domain` as if they supported the Android `testDebugUnitTest` task. They are pure JVM modules with different task names. The script emitted a structured failure that looked like a code/config problem. The correct explicit Gradle command later passed.

This should be prevented by resolving module type and available test tasks before invoking the runner. A runner must distinguish “tests failed” from “the requested task does not exist”.

### 2. Review policy and implementation policy mismatch

The powerful developer role was instructed not to write tests. The tester role ran later. Meanwhile, the verifier expected dedicated tests for every newly introduced use case. This guarantees at least one extra loop for a cross-layer feature that introduces use-case boundaries.

The role contract should either:

- require a test-plan manifest from the developer that the tester must satisfy, or
- let the developer add production-adjacent unit tests while keeping broader test ownership with the tester.

### 3. Semantic review discovered the state machine in fragments

The first semantic batch had eight findings, then four, then three, then three, then additional batches after the architect capsule. The findings were valid and materially improved correctness, but the pipeline paid the cost repeatedly because it did not start with a complete truth table for:

- owner vs participant,
- Free/Trial/Active/Grace/Expired,
- active vs inactive workspace,
- local entitlement vs server refusal,
- warning visibility vs generic error visibility,
- Auth vs Network/Server failure,
- RPC vs realtime error shapes.

A preflight state/transition matrix would likely have converted several rounds into one implementation batch.

### 4. Deterministic reviewer false-positive/rework loop

The reviewer repeatedly flagged `runBlocking` in test clock seams. Those calls were intentional real-IO/test-clock boundaries and were ultimately accepted through the project’s `mp-real-io:` marker. The same class of issue consumed multiple reviewer passes.

The allowlist should be part of the reviewer’s first-pass policy, or the reviewer should report it as a warning when the required marker is present rather than as a new violation.

### 5. Server contract was checked too late

The client projection was implemented correctly, but the database column privilege surface still exposed only the pre-entitlement columns. Android unit tests and lint could not catch that. The verifier found it late.

Every client projection change that crosses PostgREST should be paired at intake with a schema contract check:

`client selected columns ↔ migration columns ↔ authenticated grants ↔ RLS policy ↔ pgTAP assertion`.

### 6. Agent waiting and timeout behavior

Several semantic/verifier agents did not return for many minutes. The orchestration had to poll repeatedly, send an interrupt, and sometimes close/restart the agent. This increases wall time and makes the true execution time hard to measure.

The pipeline needs a bounded wait policy with explicit states: queued, running, no-output, interrupted, completed, and discarded. The report should record queue time and execution time separately.

### 7. Dirty-worktree and artifact boundaries

The worktree already contained many unrelated modified/untracked files. The implementation preserved them and staged only SPEC-owned paths, which was correct. However, this increases the cost of every broad status/diff/verifier scan and makes scope attribution harder.

The pipeline should maintain a machine-readable changed-file manifest for the active SPEC and make reviewers consume that manifest before scanning the whole worktree.

## Root-cause hypotheses

These are hypotheses for the stronger model to validate, not established facts.

### High confidence

- The pipeline lacks an acceptance-matrix compiler that expands a SPEC into roles, states, transport paths, schema grants, and required tests.
- The runner lacks a task resolver for JVM vs Android modules.
- The verifier’s dedicated-test rule is not fed back into the tester’s initial plan.
- Client/server schema privilege checks are not part of the initial implementation contract.
- Semantic review is serial and broad, so each fix can reveal another unmodeled transition at high latency.

### Medium confidence

- The high-risk route may be over-triggered by broad signals such as “entitlement”, “DI graph”, and “navigation” even when the implementation surface is known. The signals were not wrong, but their combined consequence was expensive.
- Fresh semantic reviewers repeat too much unchanged context instead of reviewing only the delta from the previous finding ledger.
- Deterministic reviewer rules do not share the same exception/allowlist semantics as the developer and tester contracts.

### Low confidence / needs measurement

- Some wall time may be caused by model queueing rather than reasoning. Current telemetry uses estimated durations and cannot prove the split.
- The initial user-visible five-hour estimate may exclude overnight gaps, approval wait, or time spent in another host/session.
- The graphify hook/background rebuild may contribute contention. In this run, the final `graphify update .` refused to overwrite because the new graph had 16,401 nodes versus 16,753 in the existing graph and reported missing chunk files; it was not forced.

## Recommended pipeline changes

### P0: add before the next implementation

1. Generate an acceptance matrix from the SPEC before developer work. For each scenario record role, local state, server state, transport path, expected UI state, action availability, error/banner precedence, and test file.
2. Generate a changed-symbol/test manifest. Every new production class/use case must have either a named dedicated test or an explicit verifier-approved exception before the first runner.
3. Resolve Gradle test tasks per module. Never pass pure JVM modules to an Android-only runner task. Emit a distinct `task_not_found` result instead of `compile/config failure`.
4. Parse all client projection strings and compare their selected columns to migration definitions and role grants. Fail preflight before implementation if the schema contract is incomplete.
5. Add a test of the participant/owner paywall truth table before UI implementation, including server refusal while local Plus is stale.

### P1: reduce review loops

1. Give semantic review a stable finding ledger and ask each subsequent pass to review only the changed paths plus all previously failing transitions.
2. Add hard agent heartbeat/timeout thresholds. After a bounded no-output period, capture the agent state, interrupt once, and fall back to a smaller evidence prompt.
3. Make reviewer allowlists/data contracts available before first review, especially for explicit test clock seams.
4. Let the tester compile its newly added tests or at minimum run a compile-only task; test execution can remain with runner.
5. Run the schema verifier before the full Android runner when a feature changes server migrations or PostgREST projections.

### P2: improve measurement

1. Record `queued_ms`, `running_ms`, `polling_ms`, `human_wait_ms`, and `repair_cycle` separately for every agent.
2. Record one event for each spawn, interrupt, close, retry, and user approval. The current JSONL records only selected pass/fail events.
3. Add `spec_file`, `changed_manifest_hash`, and `test_manifest_hash` to every event.
4. Emit a machine-generated postmortem bundle automatically when a high-risk SPEC exceeds a time or repair-cycle budget.
5. Report “code time”, “agent time”, “orchestration time”, and “external wait” separately instead of one approximate duration.

## What went well

- The active SPEC invariant was honored: exactly one SPEC was selected and completed.
- The implementation was kept on the dependency-safe layer boundary; no feature-to-`core:billing` dependency was introduced.
- Semantic review caught real lifecycle and authority bugs that ordinary UI tests would miss.
- The server grant omission was fixed before close-out rather than being hidden by a green Android runner.
- Dedicated tests were added after the verifier found their absence.
- Unrelated dirty-worktree changes were preserved and not staged.
- The SPEC was moved to `done/`, PROGRESS was updated, and the next Codex session was created automatically with the same continuation prompt.

## Residual risks and follow-up

- The Supabase migration was added and statically tested but was not applied to a live Supabase project in this session.
- The four manual UI checklist items remain follow-up evidence, not failures of the non-visual SPEC gate.
- The graphify incremental update refused to overwrite because of a graph/chunk mismatch. Do not force it without deciding whether the missing chunks are recoverable.
- The working tree still contains unrelated pre-existing modifications and untracked artifacts; they must remain separate from this report and SPEC history.

## Suggested analysis questions for the stronger model

1. Which of the semantic findings could have been predicted from a generated state/transition matrix?
2. Which verifier findings indicate a missing pipeline contract rather than an implementation mistake?
3. How much time was spent in model execution versus polling, queueing, interrupt/restart, and user approval?
4. Should high-risk routing be split into independent risk dimensions with bounded escalation instead of one expensive route?
5. What is the minimum preflight that would have caught the runner task mismatch, missing dedicated use-case tests, and Supabase grant omission?
6. Can the next SPEC consume this report automatically without loading the whole project history?

