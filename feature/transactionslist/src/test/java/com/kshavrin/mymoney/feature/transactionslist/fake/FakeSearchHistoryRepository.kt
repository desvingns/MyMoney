package com.kshavrin.mymoney.feature.transactionslist.fake

import com.kshavrin.mymoney.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Fake at the repository boundary for the S08 search ViewModel tests.
 *
 * - `seed(...)` feeds the chip history that [observe] emits, in order.
 * - [add] records each (query, now) pair so history-write tests can assert exactly what was
 *   persisted (and that a blank query is never recorded by the ViewModel).
 * - [pruneToLimit] bumps a call counter so the "commit -> add then prune" wiring can be asserted
 *   without a real Room max-20-distinct store.
 */
class FakeSearchHistoryRepository : SearchHistoryRepository {

    data class AddCall(val query: String, val now: Instant)

    val addCalls: MutableList<AddCall> = mutableListOf()
    var pruneCount: Int = 0
        private set

    private val history = MutableStateFlow<List<String>>(emptyList())

    fun seed(vararg queries: String) {
        history.value = queries.toList()
    }

    override fun observe(): Flow<List<String>> = history.asStateFlow()

    override suspend fun add(query: String, now: Instant) {
        addCalls.add(AddCall(query, now))
    }

    override suspend fun pruneToLimit() {
        pruneCount++
    }
}
