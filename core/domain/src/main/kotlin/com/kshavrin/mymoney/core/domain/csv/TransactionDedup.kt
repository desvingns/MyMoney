package com.kshavrin.mymoney.core.domain.csv

import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.math.BigDecimal
import java.time.Instant

/**
 * Canonical identity of a transaction for import deduplication (D3). Two rows are the same
 * transaction only when all six fields match. Account and category names are normalized through
 * [MonefyCsvImportParser.normalizeName] so casing/whitespace variants collapse; the amount is held
 * as a scale-normalized [BigDecimal] so 100 and 100.00 hash and compare equal (BigDecimal.equals
 * would split them).
 */
data class TransactionDedupKey(
    val accountKey: String,
    val occurredAt: Instant,
    val amount: BigDecimal,
    val categoryKey: String,
    val kind: TransactionKind,
    val note: String?,
) {
    companion object {
        fun of(
            accountName: String,
            occurredAt: Instant,
            amount: BigDecimal,
            categoryName: String,
            kind: TransactionKind,
            note: String?,
        ): TransactionDedupKey =
            TransactionDedupKey(
                accountKey = MonefyCsvImportParser.normalizeName(accountName),
                occurredAt = occurredAt,
                amount = normalizeAmount(amount),
                categoryKey = MonefyCsvImportParser.normalizeName(categoryName),
                kind = kind,
                note = note,
            )

        // strip trailing zeros so 100 == 100.00; stripTrailingZeros leaves ZERO as 0E-N, so pin it.
        private fun normalizeAmount(amount: BigDecimal): BigDecimal =
            if (amount.signum() == 0) BigDecimal.ZERO else amount.stripTrailingZeros()
    }
}

data class DedupOutcome<T>(
    val unique: List<T>,
    val duplicates: List<T>,
)

/**
 * Removes duplicates from [items]: rows whose key already appears earlier in [items] (intra-import
 * dupes) and rows whose key is in [existingKeys] (already-persisted matches) go to
 * [DedupOutcome.duplicates]; the first occurrence of each otherwise-new key stays in
 * [DedupOutcome.unique]. Pure — reads nothing from the database.
 */
fun <T> dedupTransactions(
    items: List<T>,
    existingKeys: Set<TransactionDedupKey>,
    keyOf: (T) -> TransactionDedupKey,
): DedupOutcome<T> {
    val seen = existingKeys.toMutableSet()
    val unique = mutableListOf<T>()
    val duplicates = mutableListOf<T>()
    for (item in items) {
        if (seen.add(keyOf(item))) {
            unique += item
        } else {
            duplicates += item
        }
    }
    return DedupOutcome(unique = unique, duplicates = duplicates)
}
