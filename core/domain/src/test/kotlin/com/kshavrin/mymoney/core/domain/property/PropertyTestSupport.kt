package com.kshavrin.mymoney.core.domain.property

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll

internal const val PROPERTY_ITERATIONS = 100

internal suspend fun <A> checkPropertyWithSeed(
    seed: Long,
    arb: Arb<A>,
    assertion: suspend (A) -> Unit,
) {
    try {
        checkAll(
            PropTestConfig(seed = seed, iterations = PROPERTY_ITERATIONS),
            arb,
        ) { value -> assertion(value) }
    } catch (failure: Throwable) {
        val error = AssertionError("Property failed; seed=$seed")
        error.initCause(failure)
        throw error
    }
}
