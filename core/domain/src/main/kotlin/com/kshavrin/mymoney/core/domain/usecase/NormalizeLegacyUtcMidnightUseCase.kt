package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

class NormalizeLegacyUtcMidnightUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(
        zone: ZoneId = ZoneId.systemDefault(),
        clock: Clock = Clock.systemUTC(),
    ): Int {
        val updates = transactionRepository.listForTimezoneNormalization()
            .mapNotNull { transaction ->
                val shifted = shiftUtcMidnightToLocalMidnight(transaction.occurredAt, zone)
                if (shifted == transaction.occurredAt) {
                    null
                } else {
                    transaction.id to shifted
                }
            }
            .toMap()

        if (updates.isNotEmpty()) {
            transactionRepository.updateOccurredAts(updates, clock.instant())
        }
        return updates.size
    }

    private fun shiftUtcMidnightToLocalMidnight(occurredAt: Instant, zone: ZoneId): Instant {
        val epochMillis = occurredAt.toEpochMilli()
        if (epochMillis % MILLIS_PER_DAY != 0L) {
            return occurredAt
        }
        return occurredAt
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
