package com.kshavrin.mymoney.core.database.mapper

import com.kshavrin.mymoney.core.database.entity.GoalEntity
import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class GoalMapperTest {

    private val createdAt = Instant.parse("2026-06-01T10:00:00Z")
    private val updatedAt = Instant.parse("2026-06-02T12:00:00Z")
    private val termLocalDate = LocalDate.of(2046, 1, 1)
    private val termEpochDay = termLocalDate.toEpochDay()

    private fun savingsEntity() = GoalEntity(
        id = 7L,
        name = "Car",
        iconKey = "ic_goal_car",
        colorHex = "#7AC794",
        accountId = 3L,
        variant = "SAVINGS",
        targetAmount = 500_000.50,
        startingCapital = 10_000.0,
        monthlyContribution = 20_000.0,
        annualRatePercent = null,
        termDate = null,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        isArchived = false,
    )

    private fun creditEntity() = GoalEntity(
        id = 12L,
        name = "Mortgage",
        iconKey = "ic_goal_house",
        colorHex = "#4A90D9",
        accountId = 1L,
        variant = "CREDIT",
        targetAmount = 10_000_000.0,
        startingCapital = 2_000_000.0,
        monthlyContribution = 85_000.0,
        annualRatePercent = 12.5,
        termDate = termEpochDay,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        isArchived = true,
    )

    @Test
    fun `SAVINGS entity toDomain preserves all scalar fields`() {
        val entity = savingsEntity()
        val domain = entity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("Car", domain.name)
        assertEquals("ic_goal_car", domain.iconKey)
        assertEquals("#7AC794", domain.colorHex)
        assertEquals(3L, domain.accountId)
        assertEquals(GoalVariant.SAVINGS, domain.variant)
        assertEquals(0, BigDecimal.valueOf(500_000.50).compareTo(domain.targetAmount))
        assertEquals(0, BigDecimal.valueOf(10_000.0).compareTo(domain.startingCapital))
        assertEquals(0, BigDecimal.valueOf(20_000.0).compareTo(domain.monthlyContribution))
        assertEquals(createdAt, domain.createdAt)
        assertEquals(updatedAt, domain.updatedAt)
        assertEquals(false, domain.isArchived)
    }

    @Test
    fun `SAVINGS entity toDomain yields null annualRatePercent and null termDate`() {
        val domain = savingsEntity().toDomain()

        assertNull(domain.annualRatePercent)
        assertNull(domain.termDate)
    }

    @Test
    fun `CREDIT entity toDomain converts annualRatePercent Double to BigDecimal`() {
        val domain = creditEntity().toDomain()

        assertEquals(GoalVariant.CREDIT, domain.variant)
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(domain.annualRatePercent))
    }

    @Test
    fun `CREDIT entity toDomain converts termDate epoch-day Long to LocalDate`() {
        val domain = creditEntity().toDomain()

        assertEquals(termLocalDate, domain.termDate)
    }

    @Test
    fun `CREDIT entity toDomain preserves isArchived true`() {
        val domain = creditEntity().toDomain()

        assertEquals(true, domain.isArchived)
        assertEquals(12L, domain.id)
    }

    @Test
    fun `SAVINGS domain toEntity round-trips with null nullable fields`() {
        val original = savingsEntity()
        val roundTripped = original.toDomain().toEntity()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.iconKey, roundTripped.iconKey)
        assertEquals(original.colorHex, roundTripped.colorHex)
        assertEquals(original.accountId, roundTripped.accountId)
        assertEquals(original.variant, roundTripped.variant)
        assertEquals(original.targetAmount, roundTripped.targetAmount, 0.0)
        assertEquals(original.startingCapital, roundTripped.startingCapital, 0.0)
        assertEquals(original.monthlyContribution, roundTripped.monthlyContribution, 0.0)
        assertNull(roundTripped.annualRatePercent)
        assertNull(roundTripped.termDate)
        assertEquals(original.createdAt, roundTripped.createdAt)
        assertEquals(original.updatedAt, roundTripped.updatedAt)
        assertEquals(original.isArchived, roundTripped.isArchived)
    }

    @Test
    fun `CREDIT domain toEntity round-trips with non-null annualRatePercent and termDate`() {
        val original = creditEntity()
        val roundTripped = original.toDomain().toEntity()

        assertEquals(original.annualRatePercent!!, roundTripped.annualRatePercent!!, 0.0)
        assertEquals(termEpochDay, roundTripped.termDate)
        assertEquals("CREDIT", roundTripped.variant)
    }

    @Test
    fun `toEntity then toDomain preserves createdAt and updatedAt epoch-milli`() {
        val goal = Goal(
            id = 0L,
            name = "Trip",
            iconKey = "ic_goal_plane",
            colorHex = "#FF5733",
            accountId = 2L,
            variant = GoalVariant.SAVINGS,
            targetAmount = BigDecimal("150000.00"),
            startingCapital = BigDecimal("5000.00"),
            monthlyContribution = BigDecimal("10000.00"),
            annualRatePercent = null,
            termDate = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isArchived = false,
        )

        val restored = goal.toEntity().toDomain()

        assertEquals(createdAt, restored.createdAt)
        assertEquals(updatedAt, restored.updatedAt)
    }

    @Test
    fun `variant string SAVINGS maps to GoalVariant SAVINGS`() {
        assertEquals(GoalVariant.SAVINGS, savingsEntity().toDomain().variant)
    }

    @Test
    fun `variant string CREDIT maps to GoalVariant CREDIT`() {
        assertEquals(GoalVariant.CREDIT, creditEntity().toDomain().variant)
    }

    @Test
    fun `default empty breakdown maps to null column`() {
        val goal = goalWithBreakdown(ContributionBreakdown())

        assertNull(goal.toEntity().contributionBreakdown)
    }

    @Test
    fun `null breakdown column maps back to default ContributionBreakdown`() {
        val restored = savingsEntity().copy(contributionBreakdown = null).toDomain()

        assertEquals(ContributionBreakdown(), restored.contributionBreakdown)
    }

    @Test
    fun `enabled breakdown with empty rows serializes to non-null column`() {
        val goal = goalWithBreakdown(ContributionBreakdown(enabled = true))

        assertTrue(goal.toEntity().contributionBreakdown != null)
    }

    @Test
    fun `non-empty breakdown round-trips preserving rows names and BigDecimal amounts`() {
        val breakdown = ContributionBreakdown(
            enabled = true,
            incomes = listOf(
                ContributionItem("Salary", BigDecimal("120000.55")),
                ContributionItem("Bonus", BigDecimal("3000.00")),
            ),
            expenses = listOf(
                ContributionItem("Rent", BigDecimal("45000.10")),
            ),
        )

        val restored = goalWithBreakdown(breakdown).toEntity().toDomain().contributionBreakdown

        assertEquals(true, restored.enabled)
        assertEquals(2, restored.incomes.size)
        assertEquals("Salary", restored.incomes[0].name)
        assertEquals(0, BigDecimal("120000.55").compareTo(restored.incomes[0].amount))
        assertEquals("Bonus", restored.incomes[1].name)
        assertEquals(0, BigDecimal("3000.00").compareTo(restored.incomes[1].amount))
        assertEquals(1, restored.expenses.size)
        assertEquals("Rent", restored.expenses[0].name)
        assertEquals(0, BigDecimal("45000.10").compareTo(restored.expenses[0].amount))
    }

    @Test
    fun `disabled breakdown with rows still serializes and round-trips`() {
        val breakdown = ContributionBreakdown(
            enabled = false,
            incomes = listOf(ContributionItem("Side gig", BigDecimal("7500.00"))),
        )

        val entity = goalWithBreakdown(breakdown).toEntity()
        assertTrue(entity.contributionBreakdown != null)

        val restored = entity.toDomain().contributionBreakdown
        assertEquals(false, restored.enabled)
        assertEquals(1, restored.incomes.size)
        assertEquals("Side gig", restored.incomes[0].name)
        assertEquals(0, BigDecimal("7500.00").compareTo(restored.incomes[0].amount))
    }

    private fun goalWithBreakdown(breakdown: ContributionBreakdown) = Goal(
        id = 1L,
        name = "Trip",
        iconKey = "ic_goal_plane",
        colorHex = "#FF5733",
        accountId = 2L,
        variant = GoalVariant.SAVINGS,
        targetAmount = BigDecimal("150000.00"),
        startingCapital = BigDecimal("5000.00"),
        monthlyContribution = BigDecimal("10000.00"),
        annualRatePercent = null,
        termDate = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isArchived = false,
        contributionBreakdown = breakdown,
    )
}
