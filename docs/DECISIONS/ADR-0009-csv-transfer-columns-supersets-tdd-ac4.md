# ADR-0009: CSV export superset adds two transfer columns beyond TDD §4.17 AC4

- Status: Accepted
- Date: 2026-07-18

## Context

Retrospective record. TDD §4.17 AC4 (line 1024) specifies the transaction CSV export as a
single file with exactly nine columns —
`id, kind, amount, currency, account, category, note, occurredAt, createdAt` — RFC 4180
escaped. ADR-0005 (line 58) recorded that CSV export (AC4) and factory reset (AC5) were
deferred beyond PHASE_12.

CSV export/import was subsequently delivered (import chain: `d80718a8`, `f1f210ad`,
`bd526f13`; export: `003ec467`; transfers: `29ed27c8`). The nine columns of AC4 cannot
represent a **transfer**, which has two sides: a source account/amount and a target
account/amount (the latter differs under a cross-currency transfer). Exporting a transfer
with only `account` + `amount` would silently lose the target side, and a round-trip
import could not reconstruct the transfer — it would degrade into an unpaired expense.

## Decision

`BackupRepositoryImpl.CSV_HEADER` uses an **eleven-column superset** of AC4:

```
id,kind,amount,currency,account,category,note,occurredAt,createdAt,to_account,to_amount
```

The first nine columns are exactly AC4. The two appended columns are:

- `to_account` — target account name for a `transfer` row; empty for expense/income.
- `to_amount` — target-side amount (plain `BigDecimal` string) for a cross-currency
  transfer; empty for expense/income and same-currency transfers where it equals `amount`.

Import accepts **both** shapes: a legacy nine-column file (AC4-exact; a transfer row in it
is rejected with an error, since it cannot be expressed) and the eleven-column file. The
superset is backward-compatible — the first nine columns are unchanged, so any consumer
expecting AC4 order still reads them correctly.

## Rejected alternatives

- **Strict nine columns (AC4 verbatim).** Rejected: transfers cannot round-trip; export
  would be lossy for any account with a transfer, which contradicts the SPEC-23 intent of
  full user data portability.
- **Encode the target inside `note` or a compound `account` field.** Rejected: non-standard,
  breaks RFC 4180 column semantics, and is not machine-parseable by other tools.

## Consequences

- The exported CSV is a strict superset of TDD §4.17 AC4; this ADR is the governing record
  for the two extra columns. The TDD AC4 line is left as the historical nine-column baseline;
  a future `[TDD-revision]` SPEC may fold the transfer columns into §4.17 AC4 if desired.
- Round-trip (export → import) preserves transfers including cross-currency target amounts,
  covered by `BackupCsvTransferTest.round_trip_export_then_import_restores_transfer_...`.
- Interop with Monefy's own CSV (nine-column family) remains via the legacy import path;
  `legacy_9_column_csv_imports_expense_and_income_correctly` and
  `transfer_row_in_legacy_9_column_file_is_rejected_with_an_error` pin that boundary.
