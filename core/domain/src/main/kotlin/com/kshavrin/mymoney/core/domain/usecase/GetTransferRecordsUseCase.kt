package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TransferRecord
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetTransferRecordsUseCase
    @Inject
    constructor(
        private val currencyRepository: CurrencyRepository,
        private val transactionRepository: TransactionRepository,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        suspend operator fun invoke(
            accountId: Long?,
            period: Period,
        ): List<TransferRecord> =
            withContext(defaultDispatcher) {
                val rows = transactionRepository.getTransfers(accountId, period)
                rows.map { row ->
                    val currency =
                        currencyRepository.findById(row.currencyId)
                            ?: throw IllegalStateException("Currency ${row.currencyId} not found")
                    TransferRecord(
                        id = row.id,
                        fromAccountName = row.fromAccountName,
                        toAccountName = row.toAccountName,
                        amount = Money(row.amount, currency),
                        toAmount = row.toAmount?.let { Money(it, currency) },
                        occurredAt = row.occurredAt,
                        note = row.note,
                    )
                }
            }
    }
