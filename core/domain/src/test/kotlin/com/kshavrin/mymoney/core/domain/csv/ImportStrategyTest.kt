package com.kshavrin.mymoney.core.domain.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Light construction/equality tests for the sealed strategy types and ImportPlan.
 * These are data holders — the goal is to confirm the sealed hierarchy is wired correctly
 * and that data-class equality works as expected, not to test business logic.
 */
class ImportStrategyTest {
    // -------------------------------------------------------------------------
    // ImportDataStrategy
    // -------------------------------------------------------------------------

    @Test
    fun `ReplaceAll equals itself`() {
        assertEquals(ImportDataStrategy.ReplaceAll, ImportDataStrategy.ReplaceAll)
    }

    @Test
    fun `Append equals itself`() {
        assertEquals(ImportDataStrategy.Append, ImportDataStrategy.Append)
    }

    @Test
    fun `AppendDedup equals itself`() {
        assertEquals(ImportDataStrategy.AppendDedup, ImportDataStrategy.AppendDedup)
    }

    @Test
    fun `ReplaceAll and Append are not equal`() {
        assertNotEquals(ImportDataStrategy.ReplaceAll, ImportDataStrategy.Append)
    }

    // -------------------------------------------------------------------------
    // ImportCategoryStrategy
    // -------------------------------------------------------------------------

    @Test
    fun `ReplaceCurrent equals itself`() {
        assertEquals(ImportCategoryStrategy.ReplaceCurrent, ImportCategoryStrategy.ReplaceCurrent)
    }

    @Test
    fun `category Append equals itself`() {
        assertEquals(ImportCategoryStrategy.Append, ImportCategoryStrategy.Append)
    }

    @Test
    fun `AppendManualMerge with same mappings are equal`() {
        val mapping =
            CategoryMergeMapping(
                importCategoryName = "Cafes",
                action =
                    MergeAction.MergeInto(
                        targetCategoryName = "Кафе",
                        targetId = 3L,
                        resultName = "Кафе",
                    ),
            )
        val s1 = ImportCategoryStrategy.AppendManualMerge(listOf(mapping))
        val s2 = ImportCategoryStrategy.AppendManualMerge(listOf(mapping))
        assertEquals(s1, s2)
    }

    @Test
    fun `AppendManualMerge with different mappings are not equal`() {
        val s1 =
            ImportCategoryStrategy.AppendManualMerge(
                listOf(CategoryMergeMapping("A", MergeAction.CreateNew("A"))),
            )
        val s2 =
            ImportCategoryStrategy.AppendManualMerge(
                listOf(CategoryMergeMapping("B", MergeAction.CreateNew("B"))),
            )
        assertNotEquals(s1, s2)
    }

    // -------------------------------------------------------------------------
    // MergeAction
    // -------------------------------------------------------------------------

    @Test
    fun `CreateNew with same name is equal`() {
        assertEquals(MergeAction.CreateNew("Транспорт"), MergeAction.CreateNew("Транспорт"))
    }

    @Test
    fun `MergeInto with same fields is equal`() {
        val a = MergeAction.MergeInto(targetCategoryName = "Food", targetId = 1L, resultName = "Food")
        val b = MergeAction.MergeInto(targetCategoryName = "Food", targetId = 1L, resultName = "Food")
        assertEquals(a, b)
    }

    @Test
    fun `MergeInto with null targetId is equal to another with null targetId`() {
        val a = MergeAction.MergeInto(targetCategoryName = "Food", targetId = null, resultName = "Food")
        val b = MergeAction.MergeInto(targetCategoryName = "Food", targetId = null, resultName = "Food")
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // OrphanDecision
    // -------------------------------------------------------------------------

    @Test
    fun `KeepCategory equals itself`() {
        assertEquals(OrphanDecision.KeepCategory, OrphanDecision.KeepCategory)
    }

    @Test
    fun `DeleteTransactions equals itself`() {
        assertEquals(OrphanDecision.DeleteTransactions, OrphanDecision.DeleteTransactions)
    }

    @Test
    fun `KeepCategory and DeleteTransactions are not equal`() {
        assertNotEquals(OrphanDecision.KeepCategory, OrphanDecision.DeleteTransactions)
    }

    // -------------------------------------------------------------------------
    // ImportPlan
    // -------------------------------------------------------------------------

    @Test
    fun `ImportPlan with matching fields are equal`() {
        val plan1 =
            ImportPlan(
                dataStrategy = ImportDataStrategy.AppendDedup,
                categoryStrategy = ImportCategoryStrategy.Append,
                orphanDecisions = mapOf("OldCat" to OrphanDecision.KeepCategory),
            )
        val plan2 =
            ImportPlan(
                dataStrategy = ImportDataStrategy.AppendDedup,
                categoryStrategy = ImportCategoryStrategy.Append,
                orphanDecisions = mapOf("OldCat" to OrphanDecision.KeepCategory),
            )
        assertEquals(plan1, plan2)
    }

    @Test
    fun `ImportPlan defaults to empty orphanDecisions`() {
        val plan =
            ImportPlan(
                dataStrategy = ImportDataStrategy.ReplaceAll,
                categoryStrategy = ImportCategoryStrategy.ReplaceCurrent,
            )
        assertEquals(emptyMap<String, OrphanDecision>(), plan.orphanDecisions)
    }

    @Test
    fun `ImportPlan with different dataStrategy are not equal`() {
        val plan1 = ImportPlan(ImportDataStrategy.ReplaceAll, ImportCategoryStrategy.Append)
        val plan2 = ImportPlan(ImportDataStrategy.AppendDedup, ImportCategoryStrategy.Append)
        assertNotEquals(plan1, plan2)
    }
}
