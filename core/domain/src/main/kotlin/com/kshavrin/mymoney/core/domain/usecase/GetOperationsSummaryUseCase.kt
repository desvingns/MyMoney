package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.CategoryRecordGroup
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import javax.inject.Inject

class GetOperationsSummaryUseCase
    @Inject
    constructor(
        private val getCategoryRecords: GetCategoryRecordsUseCase,
        private val getTransferRecords: GetTransferRecordsUseCase,
    ) {
        suspend operator fun invoke(
            accountId: Long,
            period: Period,
            categoryId: Long? = null,
        ): List<SummaryRecord> {
            val operations = getCategoryRecords(accountId, period, categoryId).toOperationRecords()
            if (categoryId != null) return operations.sortedForSummary()

            val transfers = getTransferRecords(accountId, period).map(SummaryRecord::Transfer)
            return (operations + transfers).sortedForSummary()
        }

        suspend fun forAccounts(
            accounts: List<Account>,
            currency: Currency,
            period: Period,
            categoryId: Long? = null,
        ): List<SummaryRecord> {
            val operations =
                getCategoryRecords
                    .forAccounts(accounts, currency, period, categoryId)
                    .toOperationRecords()
            if (categoryId != null) return operations.sortedForSummary()

            val transfers =
                accounts
                    .filterNot(Account::isArchived)
                    .flatMap { account -> getTransferRecords(account.id, period) }
                    .distinctBy { transfer -> transfer.id }
                    .map(SummaryRecord::Transfer)
            return (operations + transfers).sortedForSummary()
        }

        private fun List<CategoryRecordGroup>.toOperationRecords(): List<SummaryRecord.Operation> =
            flatMap { group -> group.transactions.map(SummaryRecord::Operation) }

        private fun List<SummaryRecord>.sortedForSummary(): List<SummaryRecord> =
            sortedWith(
                compareByDescending<SummaryRecord> { record -> record.timestamp }
                    .thenByDescending { record -> record.id }
                    .thenBy { record -> record.typeSortKey },
            )

        private val SummaryRecord.typeSortKey: Int
            get() =
                when (this) {
                    is SummaryRecord.Operation -> 0
                    is SummaryRecord.Transfer -> 1
                }
    }
