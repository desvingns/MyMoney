package com.kshavrin.mymoney.core.database.mapper

import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.BudgetEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import com.kshavrin.mymoney.core.database.entity.RecurringTemplateEntity
import com.kshavrin.mymoney.core.database.entity.SyncLogEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.projection.CategorySummaryRow
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import java.math.BigDecimal
import java.time.Instant

internal fun CurrencyEntity.toDomain(): Currency = Currency(
    id = id,
    code = code,
    symbol = symbol,
    name = name,
    decimalDigits = decimalDigits,
    isActive = isActive,
    sortOrder = sortOrder,
)

internal fun Currency.toEntity(): CurrencyEntity = CurrencyEntity(
    id = id,
    code = code,
    symbol = symbol,
    name = name,
    decimalDigits = decimalDigits,
    isActive = isActive,
    sortOrder = sortOrder,
)

internal fun CurrencyRateEntity.toDomain(): CurrencyRate = CurrencyRate(
    id = id,
    fromCurrencyId = fromCurrencyId,
    toCurrencyId = toCurrencyId,
    rate = rate,
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun CurrencyRate.toEntity(): CurrencyRateEntity = CurrencyRateEntity(
    id = id,
    fromCurrencyId = fromCurrencyId,
    toCurrencyId = toCurrencyId,
    rate = rate,
    updatedAt = updatedAt.toEpochMilli(),
)

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    currencyId = currencyId,
    initialBalance = BigDecimal.valueOf(initialBalance),
    type = AccountType.fromString(type),
    colorHex = colorHex,
    iconKey = iconKey,
    isDefault = isDefault,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isArchived = isArchived,
)

internal fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    currencyId = currencyId,
    initialBalance = initialBalance.toDouble(),
    type = type.name.lowercase(),
    colorHex = colorHex,
    iconKey = iconKey,
    isDefault = isDefault,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    isArchived = isArchived,
)

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = CategoryKind.fromString(kind),
    iconKey = iconKey,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    kind = kind.name.lowercase(),
    iconKey = iconKey,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isDefault = isDefault,
    isArchived = isArchived,
    createdAt = createdAt.toEpochMilli(),
)

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    kind = TransactionKind.fromString(kind),
    amount = BigDecimal.valueOf(amount),
    currencyId = currencyId,
    accountId = accountId,
    categoryId = categoryId,
    note = note,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    isDeleted = isDeleted,
    toAccountId = toAccountId,
    toAmount = toAmount?.let { BigDecimal.valueOf(it) },
    exchangeRate = exchangeRate,
)

internal fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    kind = kind.name.lowercase(),
    amount = amount.toDouble(),
    currencyId = currencyId,
    accountId = accountId,
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    isDeleted = isDeleted,
    toAccountId = toAccountId,
    toAmount = toAmount?.toDouble(),
    exchangeRate = exchangeRate,
)

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    periodKind = periodKind,
    periodStart = Instant.ofEpochMilli(periodStart),
    amount = BigDecimal.valueOf(amount),
    currencyId = currencyId,
    alertThresholdPct = alertThresholdPct,
    isActive = isActive,
)

internal fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    categoryId = categoryId,
    periodKind = periodKind,
    periodStart = periodStart.toEpochMilli(),
    amount = amount.toDouble(),
    currencyId = currencyId,
    alertThresholdPct = alertThresholdPct,
    isActive = isActive,
)

internal fun RecurringTemplateEntity.toDomain(): RecurringTemplate = RecurringTemplate(
    id = id,
    baseKind = TransactionKind.fromString(baseKind),
    amount = BigDecimal.valueOf(amount),
    currencyId = currencyId,
    accountId = accountId,
    categoryId = categoryId,
    toAccountId = toAccountId,
    note = note,
    recurrenceKind = recurrenceKind,
    interval = interval,
    byDay = byDay,
    startsAt = Instant.ofEpochMilli(startsAt),
    endsAt = endsAt?.let { Instant.ofEpochMilli(it) },
    nextRunAt = Instant.ofEpochMilli(nextRunAt),
    isActive = isActive,
)

internal fun RecurringTemplate.toEntity(): RecurringTemplateEntity = RecurringTemplateEntity(
    id = id,
    baseKind = baseKind.name.lowercase(),
    amount = amount.toDouble(),
    currencyId = currencyId,
    accountId = accountId,
    categoryId = categoryId,
    toAccountId = toAccountId,
    note = note,
    recurrenceKind = recurrenceKind,
    interval = interval,
    byDay = byDay,
    startsAt = startsAt.toEpochMilli(),
    endsAt = endsAt?.toEpochMilli(),
    nextRunAt = nextRunAt.toEpochMilli(),
    isActive = isActive,
)

internal fun SyncLogEntity.toDomain(): SyncLogEntry = SyncLogEntry(
    id = id,
    target = target,
    event = event,
    entityKind = entityKind,
    entityId = entityId,
    performedAt = Instant.ofEpochMilli(performedAt),
    status = status,
    payloadHash = payloadHash,
    errorMessage = errorMessage,
)

internal fun SyncLogEntry.toEntity(): SyncLogEntity = SyncLogEntity(
    id = id,
    target = target,
    event = event,
    entityKind = entityKind,
    entityId = entityId,
    performedAt = performedAt.toEpochMilli(),
    status = status,
    payloadHash = payloadHash,
    errorMessage = errorMessage,
)

internal fun CategorySummaryRow.toDomain(): CategorySummary = CategorySummary(
    categoryId = categoryId,
    categoryName = categoryName,
    colorHex = colorHex,
    total = BigDecimal.valueOf(total),
)
