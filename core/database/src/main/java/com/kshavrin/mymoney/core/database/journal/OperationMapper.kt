package com.kshavrin.mymoney.core.database.journal

import com.kshavrin.mymoney.core.database.entity.OperationEntity
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.OpType
import com.kshavrin.mymoney.core.domain.sync.Operation
import java.time.Instant

fun OperationEntity.toDomain(): Operation =
    Operation(
        opId = opId,
        deviceId = deviceId,
        entityKind = EntityKind.valueOf(entityKind),
        entityUuid = entityUuid,
        opType = OpType.valueOf(opType),
        payload = payload,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
