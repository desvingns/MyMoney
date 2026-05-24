package com.kshavrin.mymoney.feature.transactionslist.util

import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Collects the first emission of a [PagingData] flow into a plain list, on the JVM, without the
 * `androidx.paging:paging-testing` artifact (which is not on this project's offline classpath).
 *
 * Drives a [PagingDataPresenter] — the same primitive `LazyPagingItems` / `asSnapshot` wrap — over
 * the first [PagingData] the flow emits, then reads its settled snapshot. Intended for use inside
 * `runTest` with the project's [MainDispatcherRule] (an UnconfinedTestDispatcher set as Main), so
 * the presenter's internal differ work runs inline.
 */
suspend fun <T : Any> Flow<PagingData<T>>.firstSnapshot(scope: CoroutineScope): List<T> {
    val presenter = object : PagingDataPresenter<T>() {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) = Unit
    }
    val firstPage = first()
    val job = scope.launch { presenter.collectFrom(firstPage) }

    // collectFrom presents the page through the (Main-bound) differ; give it a bounded number of
    // turns to settle. A genuinely empty page settles immediately at size 0, so cap the spins to
    // avoid blocking on the empty case.
    var spins = 0
    while (presenter.size == 0 && spins < 50) {
        yield()
        spins++
    }
    // PagingData.from(...) carries no placeholders, so every slot is a real item.
    val snapshot = presenter.snapshot().filterNotNull()
    job.cancel()
    return snapshot
}
