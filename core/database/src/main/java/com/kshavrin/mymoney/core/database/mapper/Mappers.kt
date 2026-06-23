package com.kshavrin.mymoney.core.database.mapper

import com.kshavrin.mymoney.core.common.category.categoryIconDominantHex
import com.kshavrin.mymoney.core.common.category.categoryTextColorHex
import com.kshavrin.mymoney.core.database.entity.AccountEntity
import com.kshavrin.mymoney.core.database.entity.BudgetEntity
import com.kshavrin.mymoney.core.database.entity.CategoryEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyEntity
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import com.kshavrin.mymoney.core.database.entity.GoalEntity
import com.kshavrin.mymoney.core.database.entity.RecurringTemplateEntity
import com.kshavrin.mymoney.core.database.entity.SyncLogEntity
import com.kshavrin.mymoney.core.database.entity.TransactionEntity
import com.kshavrin.mymoney.core.database.projection.CategoryGroupRow
import com.kshavrin.mymoney.core.database.projection.CategorySummaryRow
import com.kshavrin.mymoney.core.database.projection.TransferRow
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import com.kshavrin.mymoney.core.domain.repository.TransferRow as DomainTransferRow

internal fun CurrencyEntity.toDomain(): Currency =
    Currency(
        id = id,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = decimalDigits,
        isActive = isActive,
        sortOrder = sortOrder,
    )

internal fun Currency.toEntity(): CurrencyEntity =
    CurrencyEntity(
        id = id,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = decimalDigits,
        isActive = isActive,
        sortOrder = sortOrder,
    )

internal fun CurrencyRateEntity.toDomain(): CurrencyRate =
    CurrencyRate(
        id = id,
        fromCurrencyId = fromCurrencyId,
        toCurrencyId = toCurrencyId,
        rate = rate,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

internal fun CurrencyRate.toEntity(): CurrencyRateEntity =
    CurrencyRateEntity(
        id = id,
        fromCurrencyId = fromCurrencyId,
        toCurrencyId = toCurrencyId,
        rate = rate,
        updatedAt = updatedAt.toEpochMilli(),
    )

internal fun AccountEntity.toDomain(): Account =
    Account(
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

internal fun Account.toEntity(): AccountEntity =
    AccountEntity(
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

internal fun GoalEntity.toDomain(): Goal =
    Goal(
        id = id,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        accountId = accountId,
        variant = GoalVariant.valueOf(variant),
        targetAmount = BigDecimal.valueOf(targetAmount),
        startingCapital = BigDecimal.valueOf(startingCapital),
        monthlyContribution = BigDecimal.valueOf(monthlyContribution),
        annualRatePercent = annualRatePercent?.let { BigDecimal.valueOf(it) },
        downPayment = downPayment?.toBigDecimal(),
        termMonths = termMonths,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        isArchived = isArchived,
        contributionBreakdown =
            contributionBreakdown
                ?.let { Json.decodeFromString<BreakdownDto>(it).toModel() }
                ?: ContributionBreakdown(),
    )

internal fun Goal.toEntity(): GoalEntity =
    GoalEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        accountId = accountId,
        variant = variant.name,
        targetAmount = targetAmount.toDouble(),
        startingCapital = startingCapital.toDouble(),
        monthlyContribution = monthlyContribution.toDouble(),
        annualRatePercent = annualRatePercent?.toDouble(),
        downPayment = downPayment?.toDouble(),
        termMonths = termMonths,
        termDate = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        isArchived = isArchived,
        contributionBreakdown = contributionBreakdown.toColumnOrNull(),
    )

@Serializable
private data class BreakdownItemDto(
    val name: String,
    val amount: String,
)

@Serializable
private data class BreakdownDto(
    val enabled: Boolean,
    val incomes: List<BreakdownItemDto>,
    val expenses: List<BreakdownItemDto>,
)

// Amount serialized as BigDecimal.toPlainString() (never a JSON number/Double) to round-trip exactly.
private fun BreakdownItemDto.toModel() = ContributionItem(name = name, amount = BigDecimal(amount))

private fun ContributionItem.toDto() = BreakdownItemDto(name = name, amount = amount.toPlainString())

private fun BreakdownDto.toModel() =
    ContributionBreakdown(
        enabled = enabled,
        incomes = incomes.map { it.toModel() },
        expenses = expenses.map { it.toModel() },
    )

private fun ContributionBreakdown.toColumnOrNull(): String? =
    if (enabled || incomes.isNotEmpty() || expenses.isNotEmpty()) {
        Json.encodeToString<BreakdownDto>(
            BreakdownDto(
                enabled = enabled,
                incomes = incomes.map { it.toDto() },
                expenses = expenses.map { it.toDto() },
            ),
        )
    } else {
        null
    }

internal fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        kind = CategoryKind.fromString(kind),
        iconKey = iconKey,
        colorHex = colorHex,
        textColor = textColor,
        sortOrder = sortOrder,
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

internal fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        kind = kind.name.lowercase(),
        iconKey = iconKey,
        colorHex = categoryIconDominantHex(iconKey),
        textColor = categoryTextColorHex(iconKey),
        sortOrder = sortOrder,
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = createdAt.toEpochMilli(),
    )

internal fun TransactionEntity.toDomain(): Transaction =
    Transaction(
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

internal fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
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

internal fun BudgetEntity.toDomain(): Budget =
    Budget(
        id = id,
        categoryId = categoryId,
        periodKind = periodKind,
        periodStart = Instant.ofEpochMilli(periodStart),
        amount = BigDecimal.valueOf(amount),
        currencyId = currencyId,
        alertThresholdPct = alertThresholdPct,
        isActive = isActive,
    )

internal fun Budget.toEntity(): BudgetEntity =
    BudgetEntity(
        id = id,
        categoryId = categoryId,
        periodKind = periodKind,
        periodStart = periodStart.toEpochMilli(),
        amount = amount.toDouble(),
        currencyId = currencyId,
        alertThresholdPct = alertThresholdPct,
        isActive = isActive,
    )

internal fun RecurringTemplateEntity.toDomain(): RecurringTemplate =
    RecurringTemplate(
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

internal fun RecurringTemplate.toEntity(): RecurringTemplateEntity =
    RecurringTemplateEntity(
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

internal fun SyncLogEntity.toDomain(): SyncLogEntry =
    SyncLogEntry(
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

internal fun SyncLogEntry.toEntity(): SyncLogEntity =
    SyncLogEntity(
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

internal fun CategorySummaryRow.toDomain(): CategorySummary =
    CategorySummary(
        categoryId = categoryId,
        categoryName = categoryName,
        colorHex = colorHex,
        total = BigDecimal.valueOf(total),
        iconKey = iconKey,
    )

internal fun CategoryGroupRow.toDomain(): CategoryGroup =
    CategoryGroup(
        categoryId = categoryId,
        name = name,
        iconKey = iconKey,
        colorHex = colorHex,
        kind = CategoryKind.fromString(kind),
        total = BigDecimal.valueOf(total),
        count = txCount,
    )

internal fun TransferRow.toDomain(): DomainTransferRow =
    DomainTransferRow(
        id = id,
        fromAccountName = fromAccountName,
        toAccountName = toAccountName,
        amount = BigDecimal.valueOf(amount),
        toAmount = toAmount?.let { BigDecimal.valueOf(it) },
        currencyId = currencyId,
        occurredAt = Instant.ofEpochMilli(occurredAt),
        note = note,
    )
