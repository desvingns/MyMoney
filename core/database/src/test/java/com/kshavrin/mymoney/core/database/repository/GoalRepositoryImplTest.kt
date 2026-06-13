package com.kshavrin.mymoney.core.database.repository

import app.cash.turbine.test
import com.kshavrin.mymoney.core.database.dao.GoalDao
import com.kshavrin.mymoney.core.database.entity.GoalEntity
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalRepositoryImplTest {
    private inner class FakeGoalDao : GoalDao() {
        private val store = MutableStateFlow<Map<Long, GoalEntity>>(emptyMap())
        private var nextId = 1L

        override suspend fun upsert(e: GoalEntity): Long {
            val id = if (e.id == 0L) nextId++ else e.id
            store.value = store.value + (id to e.copy(id = id))
            return id
        }

        override fun observeActive(): Flow<List<GoalEntity>> =
            store.map { m -> m.values.filter { !it.isArchived }.sortedByDescending { it.createdAt } }

        override suspend fun findById(id: Long): GoalEntity? = store.value[id]

        override suspend fun archive(
            id: Long,
            now: Long,
        ) {
            store.value =
                store.value.mapValues { (k, v) ->
                    if (k == id) v.copy(isArchived = true, updatedAt = now) else v
                }
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeGoalDao
    private lateinit var repo: GoalRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeGoalDao()
        repo = GoalRepositoryImpl(fakeDao, dispatcher)
    }

    private fun savingsGoal(
        id: Long = 0L,
        name: String = "Car",
        createdAt: Instant = Instant.parse("2026-06-01T10:00:00Z"),
    ) = Goal(
        id = id,
        name = name,
        iconKey = "ic_goal_car",
        colorHex = "#7AC794",
        accountId = 3L,
        variant = GoalVariant.SAVINGS,
        targetAmount = BigDecimal("500000.00"),
        startingCapital = BigDecimal("10000.00"),
        monthlyContribution = BigDecimal("20000.00"),
        annualRatePercent = null,
        downPayment = null,
        termMonths = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        isArchived = false,
    )

    @Test
    fun `observeActive emits domain goals after upsert`() =
        runTest(dispatcher) {
            val id = repo.upsert(savingsGoal())

            repo.observeActive().test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals(id, items.first().id)
                assertEquals("Car", items.first().name)
                assertEquals(GoalVariant.SAVINGS, items.first().variant)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeActive emits empty list when no goals exist`() =
        runTest(dispatcher) {
            repo.observeActive().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `findById returns domain goal for existing id`() =
        runTest(dispatcher) {
            val id = repo.upsert(savingsGoal(name = "House"))

            val found = repo.findById(id)

            assertNotNull(found)
            assertEquals("House", found!!.name)
            assertEquals(id, found.id)
        }

    @Test
    fun `findById returns null for unknown id`() =
        runTest(dispatcher) {
            assertNull(repo.findById(999L))
        }

    @Test
    fun `archive hides goal from observeActive but findById still returns it`() =
        runTest(dispatcher) {
            val id = repo.upsert(savingsGoal(name = "Vacation"))
            repo.archive(id)

            repo.observeActive().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }

            val found = repo.findById(id)
            assertNotNull(found)
            assertTrue(found!!.isArchived)
        }

    @Test
    fun `upsert with existing id updates the stored goal`() =
        runTest(dispatcher) {
            val id = repo.upsert(savingsGoal(name = "Old"))
            repo.upsert(savingsGoal(id = id, name = "Updated"))

            val found = repo.findById(id)
            assertEquals("Updated", found!!.name)
        }

    @Test
    fun `observeActive orders goals by createdAt descending`() =
        runTest(dispatcher) {
            val older = Instant.parse("2026-05-01T00:00:00Z")
            val newer = Instant.parse("2026-06-01T00:00:00Z")

            val olderId = repo.upsert(savingsGoal(name = "Older", createdAt = older))
            val newerId = repo.upsert(savingsGoal(name = "Newer", createdAt = newer))

            repo.observeActive().test {
                val items = awaitItem()
                assertEquals(listOf(newerId, olderId), items.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `upsert returns assigned id greater than zero for new goal`() =
        runTest(dispatcher) {
            val id = repo.upsert(savingsGoal())
            assertTrue(id > 0L)
        }
}
