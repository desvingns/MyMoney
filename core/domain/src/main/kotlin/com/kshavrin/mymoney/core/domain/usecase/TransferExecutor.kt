package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject

class TransferExecutor
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val currencyRateRepository: CurrencyRateRepository,
        private val transactionRepository: TransactionRepository,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        suspend fun execute(
            sourceAccountId: Long,
            targetAccountId: Long,
            amount: BigDecimal,
            note: String?,
            occurredAt: Instant,
            now: Instant,
            // One-shot rate confirmed/edited in the transfer rate dialog (D5). When set for a
            // cross-currency transfer it overrides the stored rate for THIS operation only and is
            // never written to CurrencyRateRepository. [overrideToAmount] is the already-rounded
            // target amount (G18); [overrideRate] is the cross-rate persisted on the Transaction.
            overrideToAmount: BigDecimal? = null,
            overrideRate: BigDecimal? = null,
        ): TransferResult =
            withContext(defaultDispatcher) {
                require(sourceAccountId != targetAccountId) { "source and target accounts must differ" }
                require(amount.signum() > 0) { "amount must be > 0" }

                val source =
                    accountRepository.findById(sourceAccountId)
                        ?: return@withContext TransferResult.Failure.SourceMissing(sourceAccountId)
                val target =
                    accountRepository.findById(targetAccountId)
                        ?: return@withContext TransferResult.Failure.TargetMissing(targetAccountId)

                val sameCurrency = source.currencyId == target.currencyId
                val (toAmount, exchangeRate) =
                    when {
                        sameCurrency -> amount to null
                        overrideToAmount != null && overrideRate != null ->
                            overrideToAmount to overrideRate.toDouble()
                        else -> {
                            val rate =
                                currencyRateRepository.findRate(source.currencyId, target.currencyId)
                                    ?: return@withContext TransferResult.Failure.RateMissing(source.currencyId, target.currencyId)
                            val converted = amount.multiply(BigDecimal.valueOf(rate.rate))
                            converted to rate.rate
                        }
                    }

                val transaction =
                    Transaction(
                        id = 0L,
                        kind = TransactionKind.Transfer,
                        amount = amount,
                        currencyId = source.currencyId,
                        accountId = sourceAccountId,
                        categoryId = null,
                        note = note,
                        occurredAt = occurredAt,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        toAccountId = targetAccountId,
                        toAmount = toAmount,
                        exchangeRate = exchangeRate,
                    )
                val id = transactionRepository.upsert(transaction)
                TransferResult.Success(transaction.copy(id = id))
            }
    }

sealed class TransferResult {
    data class Success(
        val transaction: Transaction,
    ) : TransferResult()

    sealed class Failure : TransferResult() {
        data class SourceMissing(
            val accountId: Long,
        ) : Failure()

        data class TargetMissing(
            val accountId: Long,
        ) : Failure()

        data class RateMissing(
            val fromCurrencyId: Long,
            val toCurrencyId: Long,
        ) : Failure()
    }
}
