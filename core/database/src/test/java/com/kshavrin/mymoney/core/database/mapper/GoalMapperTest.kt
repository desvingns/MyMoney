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

class GoalMapperTest {
    private val createdAt = Instant.parse("2026-06-01T10:00:00Z")
    private val updatedAt = Instant.parse("2026-06-02T12:00:00Z")
    private val termMonthsValue = 240

    private fun savingsEntity() =
        GoalEntity(
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
            downPayment = null,
            termMonths = null,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli(),
            isArchived = false,
        )

    private fun creditEntity() =
        GoalEntity(
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
            downPayment = 2_000_000.0,
            termMonths = termMonthsValue,
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
    fun `SAVINGS entity toDomain yields null annualRatePercent downPayment and termMonths`() {
        val domain = savingsEntity().toDomain()

        assertNull(domain.annualRatePercent)
        assertNull(domain.downPayment)
        assertNull(domain.termMonths)
    }

    @Test
    fun `CREDIT entity toDomain converts annualRatePercent Double to BigDecimal`() {
        val domain = creditEntity().toDomain()

        assertEquals(GoalVariant.CREDIT, domain.variant)
        assertEquals(0, BigDecimal.valueOf(12.5).compareTo(domain.annualRatePercent))
    }

    @Test
    fun `CREDIT entity toDomain converts downPayment Double to BigDecimal and preserves termMonths`() {
        val domain = creditEntity().toDomain()

        assertEquals(0, BigDecimal.valueOf(2_000_000.0).compareTo(domain.downPayment))
        assertEquals(termMonthsValue, domain.termMonths)
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
        assertNull(roundTripped.downPayment)
        assertNull(roundTripped.termMonths)
        assertEquals(original.createdAt, roundTripped.createdAt)
        assertEquals(original.updatedAt, roundTripped.updatedAt)
        assertEquals(original.isArchived, roundTripped.isArchived)
    }

    @Test
    fun `CREDIT domain toEntity round-trips with non-null annualRatePercent downPayment and termMonths`() {
        val original = creditEntity()
        val roundTripped = original.toDomain().toEntity()

        assertEquals(original.annualRatePercent!!, roundTripped.annualRatePercent!!, 0.0)
        assertEquals(original.downPayment!!, roundTripped.downPayment!!, 0.0)
        assertEquals(termMonthsValue, roundTripped.termMonths)
        assertEquals("CREDIT", roundTripped.variant)
    }

    @Test
    fun `toEntity then toDomain preserves createdAt and updatedAt epoch-milli`() {
        val goal =
            Goal(
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
                downPayment = null,
                termMonths = null,
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

    // --- ContributionBreakdown mapping ---

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
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes =
                    listOf(
                        ContributionItem("Salary", BigDecimal("120000.55")),
                        ContributionItem("Bonus", BigDecimal("3000.00")),
                    ),
                expenses =
                    listOf(
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
        val breakdown =
            ContributionBreakdown(
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

    @Test
    fun `BigDecimal scale is preserved exactly through toPlainString round-trip`() {
        // "3000.00" must survive as scale=2 after serialization — compareTo alone would pass
        // with BigDecimal("3000") (scale=0), but the column stores the plain string so scale
        // must survive if the original string representation was "3000.00".
        val amount = BigDecimal("3000.00")
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes = listOf(ContributionItem("Salary", amount)),
            )

        val restoredAmount =
            goalWithBreakdown(breakdown)
                .toEntity()
                .toDomain()
                .contributionBreakdown.incomes[0]
                .amount

        assertEquals("3000.00", restoredAmount.toPlainString())
        assertEquals(2, restoredAmount.scale())
    }

    @Test
    fun `high-precision BigDecimal amount is not corrupted by Double conversion`() {
        // Double cannot represent 123456789.99 exactly; serializing via toPlainString() bypasses
        // that loss entirely. We verify the value matches the original plain string precisely.
        val amount = BigDecimal("123456789.99")
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes = listOf(ContributionItem("Precise", amount)),
            )

        val restoredAmount =
            goalWithBreakdown(breakdown)
                .toEntity()
                .toDomain()
                .contributionBreakdown.incomes[0]
                .amount

        assertEquals("123456789.99", restoredAmount.toPlainString())
        assertEquals(0, amount.compareTo(restoredAmount))
    }

    @Test
    fun `negative amount is preserved exactly through round-trip`() {
        // SPEC: negative amounts are allowed in breakdown items (net monthly can be negative).
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                expenses = listOf(ContributionItem("Overspend", BigDecimal("-500.50"))),
            )

        val restored = goalWithBreakdown(breakdown).toEntity().toDomain().contributionBreakdown

        assertEquals(1, restored.expenses.size)
        assertEquals("-500.50", restored.expenses[0].amount.toPlainString())
        assertEquals(0, BigDecimal("-500.50").compareTo(restored.expenses[0].amount))
    }

    @Test
    fun `blank item name is preserved through round-trip`() {
        // SPEC: blank/optional names — an empty string name must survive serialization unchanged.
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes = listOf(ContributionItem("", BigDecimal("1000.00"))),
            )

        val restored = goalWithBreakdown(breakdown).toEntity().toDomain().contributionBreakdown

        assertEquals(1, restored.incomes.size)
        assertEquals("", restored.incomes[0].name)
        assertEquals(0, BigDecimal("1000.00").compareTo(restored.incomes[0].amount))
    }

    @Test
    fun `breakdown with only expenses and no incomes round-trips correctly`() {
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes = emptyList(),
                expenses =
                    listOf(
                        ContributionItem("Rent", BigDecimal("30000.00")),
                        ContributionItem("Utilities", BigDecimal("5000.00")),
                    ),
            )

        val restored = goalWithBreakdown(breakdown).toEntity().toDomain().contributionBreakdown

        assertEquals(true, restored.enabled)
        assertTrue(restored.incomes.isEmpty())
        assertEquals(2, restored.expenses.size)
        assertEquals("Rent", restored.expenses[0].name)
        assertEquals(0, BigDecimal("30000.00").compareTo(restored.expenses[0].amount))
        assertEquals("Utilities", restored.expenses[1].name)
        assertEquals(0, BigDecimal("5000.00").compareTo(restored.expenses[1].amount))
    }

    @Test
    fun `multiple incomes and multiple expenses round-trip preserving order`() {
        val breakdown =
            ContributionBreakdown(
                enabled = true,
                incomes =
                    listOf(
                        ContributionItem("Job A", BigDecimal("80000.00")),
                        ContributionItem("Job B", BigDecimal("40000.00")),
                        ContributionItem("Freelance", BigDecimal("15000.50")),
                    ),
                expenses =
                    listOf(
                        ContributionItem("Mortgage", BigDecimal("55000.00")),
                        ContributionItem("Car", BigDecimal("12000.00")),
                    ),
            )

        val restored = goalWithBreakdown(breakdown).toEntity().toDomain().contributionBreakdown

        assertEquals(3, restored.incomes.size)
        assertEquals("Job A", restored.incomes[0].name)
        assertEquals("Job B", restored.incomes[1].name)
        assertEquals("Freelance", restored.incomes[2].name)
        assertEquals(0, BigDecimal("15000.50").compareTo(restored.incomes[2].amount))
        assertEquals(2, restored.expenses.size)
        assertEquals("Mortgage", restored.expenses[0].name)
        assertEquals("Car", restored.expenses[1].name)
    }

    @Test
    fun `disabled empty breakdown is the only combination that produces null column`() {
        // All other combinations — enabled with empty rows, disabled with rows, enabled with rows
        // — must all produce a non-null column. Only ContributionBreakdown() produces null.
        assertNull(goalWithBreakdown(ContributionBreakdown(enabled = false)).toEntity().contributionBreakdown)

        assertTrue(
            goalWithBreakdown(ContributionBreakdown(enabled = true))
                .toEntity()
                .contributionBreakdown != null,
        )
        assertTrue(
            goalWithBreakdown(
                ContributionBreakdown(enabled = false, incomes = listOf(ContributionItem("X", BigDecimal("1")))),
            ).toEntity().contributionBreakdown != null,
        )
    }

    private fun goalWithBreakdown(breakdown: ContributionBreakdown) =
        Goal(
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
            downPayment = null,
            termMonths = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isArchived = false,
            contributionBreakdown = breakdown,
        )
}
