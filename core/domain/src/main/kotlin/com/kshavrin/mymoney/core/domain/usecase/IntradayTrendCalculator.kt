package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

const val INTRADAY_SLOT_COUNT = 12
private const val SLOT_HOURS = 2
private const val MONEY_SCALE = 2

class IntradayTrendCalculator
    @Inject
    constructor() {
        fun buildIntradaySeries(
            transactions: List<Transaction>,
            day: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<BigDecimal> {
            val nets = Array(INTRADAY_SLOT_COUNT) { BigDecimal.ZERO }
            val counts = IntArray(INTRADAY_SLOT_COUNT)

            transactions.forEach { transaction ->
                val signed = signedAmount(transaction) ?: return@forEach
                val occurred = transaction.occurredAt.atZone(zone)
                if (occurred.toLocalDate() != day) return@forEach
                val slot = occurred.hour / SLOT_HOURS
                nets[slot] = nets[slot].add(signed)
                counts[slot]++
            }

            val lastActiveSlot = counts.indexOfLast { it > 0 }
            if (lastActiveSlot < 0) return emptyList()

            var cumulative = BigDecimal.ZERO
            return (0..lastActiveSlot).map { slot ->
                cumulative = cumulative.add(nets[slot])
                cumulative.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            }
        }

        private fun signedAmount(transaction: Transaction): BigDecimal? =
            when (transaction.kind) {
                TransactionKind.Income -> transaction.amount
                TransactionKind.Expense -> transaction.amount.negate()
                TransactionKind.Transfer -> null
            }
    }
