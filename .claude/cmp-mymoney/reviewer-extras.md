# cmp-reviewer-android — MyMoney extras

Read this **after** `.claude/agents/cmp-reviewer-android.md`. The base agent checks Clean Architecture layer boundaries (`presentation/` may import `domain/`, never `data/`; `domain/` is pure Kotlin; etc.). These MyMoney-specific checks run **in addition**.

## TDD / AS conformance pass

For each changed file, in addition to layer checks:

1. **TDD line citation present.** If the SPEC's `CHANGED_HINT` cites TDD line ranges, confirm the implementation matches the cited section. If a file implements behaviour described in TDD §X.Y lines A–B, the code should not contradict that section.
2. **AS-1…AS-15 respected.** Cross-reference the changed files against the AS list (TDD §14.1, lines 2727–2750). If the file touches a screen / behaviour covered by an AS-x, confirm the AS is honoured. Pay special attention to:
   - **AS-12** (range picker, not single-day) — when reviewing date-picker code, the mode must include `CustomRange(start, end)`.
   - **AS-14** (donut labels ≥3 %) — when reviewing donut-chart code, the threshold must be 3 %, not 5 %.
   - **AS-5** (biometric overlay) — biometric lock must be a Composable overlay, not a NavHost destination.
   - **AS-7** (single-row transfer) — transfers must be one `TransactionEntity` row, not two linked.
   - **AS-9** (swipe-delete with UNDO) — soft-delete + 5-second Snackbar + 30-day worker.
3. **Monefy deviations are intentional.** If the implementation looks like Monefy v1.0 in a way that contradicts AS-12 or AS-14 → block the chain. Cite the AS.

## Module boundary checks

In addition to layer boundaries, check Gradle-module boundaries:

- `:feature:*` may depend on `:core:*` and `:core:domain`. **Never** `:feature:*` → `:feature:*`. A new Compose screen that imports another `:feature:*` package is a violation.
- `:core:domain` must have no Android dependencies (pure Kotlin, JVM-only). Imports of `android.*`, `androidx.*` are violations.
- `:core:database` exposes DAOs and entities; consumers go through `:core:domain` repository interfaces, never DAO directly.

## Money / time type checks

- `BigDecimal` for money in `domain/` and `presentation/`. `Double` for money is allowed **only** inside `:core:database` entity classes and DAO signatures.
- TypeConverters in `:core:database/converter/` convert at the boundary. If a `BigDecimal ↔ Double` conversion happens outside `:core:database` → violation.
- `LocalDate` / `Instant` in `domain/`; `Long` epoch-millis in Room entities. Same boundary rule.

## Hilt + dispatcher checks

- No `Dispatchers.IO` / `Dispatchers.Default` / `Dispatchers.Main` direct usage inside a class body. Must be injected via `@Named` `CoroutineDispatcher` from a `DispatchersModule`. **Hard fail** on direct usage — the test isolation requirement breaks otherwise.
- `@HiltViewModel` annotated VMs must have an `@Inject` constructor; no `@Provides` for them.
- Repository implementations must be `@Singleton` unless explicitly documented otherwise.

## Result pattern + Sentry

- Domain functions returning a value should return `kotlin.Result<T>` (not raw `T` with thrown exceptions). Exception: pure computational helpers with no failure modes (e.g. `formatAmount(BigDecimal): String`).
- Repository implementations should catch domain failures and remap to `SyncException(SyncError)` for sync ops. For other ops, propagate as `Result.failure(e)`.
- Throwables should reach Sentry. If a `catch (e: Throwable)` block swallows without `Sentry.captureException(e)` or rethrow → violation, unless the catch comment explains why (and even then, prefer the explicit `Sentry.captureException(e)`).

## String localization

- No hardcoded user-facing strings in Kotlin. Every user-visible string must be `stringResource(R.string.…)` or `context.getString(R.string.…)`.
- New `R.string.foo` entries must exist in both `res/values/strings.xml` (English) and `res/values-ru/strings.xml` (Russian per TDD §10). If only EN is present, flag as "missing RU translation — defer to PHASE_15 polish, but document in the PR description."

## Comments policy enforcement

- Multi-line comment blocks or multi-paragraph KDoc on internal functions / classes → violation. Keep KDoc only on `@PublicApi` surfaces.
- Comments that narrate WHAT the code does (e.g. `// Increment counter`, `// Return the result`) → violation.
- Comments referencing the current PR / task / commit hash → violation (this rots; belongs in commit message, not code).
- Single-line comments explaining a non-obvious WHY → fine.

## Output format

Standard reviewer output: `{"pass": bool, "violations": [...]}`. Each violation entry should cite the file:line and the rule. Example:

```json
{
  "pass": false,
  "violations": [
    {"file": "feature/dashboard/.../DonutChart.kt", "line": 47, "rule": "AS-14", "detail": "Label threshold is 0.05f but AS-14 requires 0.03f"},
    {"file": "core/database/.../TransactionEntity.kt", "line": 23, "rule": "Comments policy", "detail": "Multi-line KDoc on internal data class"}
  ]
}
```
