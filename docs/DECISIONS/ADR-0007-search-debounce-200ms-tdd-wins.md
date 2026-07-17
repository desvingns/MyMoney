# ADR-0007: Search debounce is 200 ms and matches note + category name

- Status: Accepted
- Date: 2026-05-24

## Context

Retrospective record (2026-07-17) from PROGRESS.md decision-log line 69,
PHASE_11 Notes, commit acf62a7.

TDD §4.9 specifies the S08 search screen. The `Loading` state description at line 779
notes the query is "debounced 200 ms". Acceptance criterion AC1 at line 784 reads:
"Typing triggers a 200 ms debounced query that searches `transaction.note LIKE %?% OR
category.name LIKE %?%` (case-insensitive)." TDD §11.4 US-12 AC1 (line 2476) confirms:
"Search debounces 200 ms, queries `note` and `category.name`."

The PHASE_11 phase file disagreed on two points: it specified 300 ms debounce and a
`Flow<PagingData>` result set with amount-parsing in the search predicate. Neither matches
the TDD. The project rule is that TDD is the source of truth; the phase file is
implementation guidance subordinate to the TDD.

The existing `TransactionRepository.searchByNote(query: String)` returns a `List` capped
at 200 items, which satisfies §4.9 without introducing Paging 3 complexity not called for
by the spec.

## Decision

Implement S08 search with a 200 ms `debounce` (matching TDD §4.9 AC1 and §11.4 US-12
AC1), querying `transaction.note` and `category.name` via the existing `searchByNote`
(`List`, cap 200). Do not implement the phase file's 300 ms debounce, amount-parsing
predicate, or `Flow<PagingData>` result type.

## Rejected alternatives

- 300 ms debounce (phase file): rejected because it contradicts TDD §4.9 AC1 and §11.4
  US-12 AC1, which both specify 200 ms. The TDD is the authoritative spec.
- `Flow<PagingData>` result type with amount-parsing (phase file): rejected because §4.9
  specifies only note and category-name matching; amount-parsing and pagination are not
  in scope and would exceed the specified behaviour.

## Consequences

- Search latency matches the TDD-specified 200 ms debounce across all implementations.
- The `searchByNote` DAO method (List, cap 200) is reused without schema or DAO changes.
- If a future version requires amount-based search or paginated results, a new decision
  and TDD amendment are needed before implementation.
