package com.kshavrin.mymoney.core.database.projection

import androidx.room.ColumnInfo

data class TransferRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "fromAccountName") val fromAccountName: String,
    @ColumnInfo(name = "toAccountName") val toAccountName: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "toAmount") val toAmount: Double?,
    @ColumnInfo(name = "currencyId") val currencyId: Long,
    @ColumnInfo(name = "occurredAt") val occurredAt: Long,
    @ColumnInfo(name = "note") val note: String?,
)
