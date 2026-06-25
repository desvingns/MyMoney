package com.kshavrin.mymoney.core.domain.sync

sealed interface MergeResult {
    data class Resolved(
        val op: Operation,
    ) : MergeResult

    data class Tombstone(
        val uuid: String,
    ) : MergeResult

    data object None : MergeResult
}

object OperationMerger {
    fun resolve(ops: List<Operation>): MergeResult {
        val winner =
            ops
                .distinctBy { op -> op.opId }
                .maxWithOrNull(
                    compareBy<Operation> { op -> op.updatedAt.toEpochMilli() }
                        .thenBy { op -> op.deviceId },
                )
                ?: return MergeResult.None

        return when (winner.opType) {
            OpType.Upsert -> MergeResult.Resolved(winner)
            OpType.Delete -> MergeResult.Tombstone(winner.entityUuid)
        }
    }
}
