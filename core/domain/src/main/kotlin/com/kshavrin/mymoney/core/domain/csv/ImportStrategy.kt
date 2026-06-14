package com.kshavrin.mymoney.core.domain.csv

sealed interface ImportDataStrategy {
    data object ReplaceAll : ImportDataStrategy

    data object Append : ImportDataStrategy

    data object AppendDedup : ImportDataStrategy
}

sealed interface ImportCategoryStrategy {
    data object ReplaceCurrent : ImportCategoryStrategy

    data object Append : ImportCategoryStrategy

    data class AppendManualMerge(
        val mappings: List<CategoryMergeMapping>,
    ) : ImportCategoryStrategy
}

sealed interface MergeAction {
    data class CreateNew(
        val name: String,
    ) : MergeAction

    data class MergeInto(
        val targetCategoryName: String,
        val targetId: Long?,
        val resultName: String,
    ) : MergeAction
}

data class CategoryMergeMapping(
    val importCategoryName: String,
    val action: MergeAction,
)

sealed interface OrphanDecision {
    data object KeepCategory : OrphanDecision

    data object DeleteTransactions : OrphanDecision
}

data class ImportPlan(
    val dataStrategy: ImportDataStrategy,
    val categoryStrategy: ImportCategoryStrategy,
    val orphanDecisions: Map<String, OrphanDecision> = emptyMap(),
)
