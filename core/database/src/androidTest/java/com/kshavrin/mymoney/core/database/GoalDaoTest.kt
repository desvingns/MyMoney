package com.kshavrin.mymoney.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.kshavrin.mymoney.core.database.entity.GoalEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalDaoTest {
    private lateinit var db: MoneyDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun goal(
        name: String,
        createdAt: Long,
    ): GoalEntity =
        GoalEntity(
            name = name,
            iconKey = "ic_goal_savings",
            colorHex = "#7AC794",
            accountId = 1L,
            variant = "SAVINGS",
            targetAmount = 1000.0,
            startingCapital = 100.0,
            monthlyContribution = 50.0,
            annualRatePercent = null,
            termDate = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            isArchived = false,
        )

    @Test
    fun upsert_then_observeActive_returns_it() =
        runTest {
            val id = db.goalDao().upsert(goal("Car", 0L))
            db.goalDao().observeActive().test {
                val active = awaitItem()
                assertEquals(1, active.size)
                assertEquals(id, active.first().id)
                assertEquals("Car", active.first().name)
            }
        }

    @Test
    fun findById_returns_stored_goal() =
        runTest {
            val id = db.goalDao().upsert(goal("House", 0L))
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertEquals("House", read!!.name)
        }

    @Test
    fun findById_unknown_returns_null() =
        runTest {
            assertNull(db.goalDao().findById(999L))
        }

    @Test
    fun archive_hides_goal_from_observeActive() =
        runTest {
            val id = db.goalDao().upsert(goal("Vacation", 0L))
            db.goalDao().archive(id, 123L)
            db.goalDao().observeActive().test {
                assertTrue(awaitItem().isEmpty())
            }
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertTrue(read!!.isArchived)
            assertEquals(123L, read.updatedAt)
        }

    @Test
    fun observeActive_orders_by_created_at_desc() =
        runTest {
            val older = db.goalDao().upsert(goal("Older", 100L))
            val newer = db.goalDao().upsert(goal("Newer", 200L))
            db.goalDao().observeActive().test {
                val active = awaitItem()
                assertEquals(listOf(newer, older), active.map { it.id })
            }
        }

    @Test
    fun observeActive_empty_returns_empty() =
        runTest {
            db.goalDao().observeActive().test {
                assertTrue(awaitItem().isEmpty())
            }
        }

    // --- contribution_breakdown column ---

    @Test
    fun upsert_with_null_breakdown_persists_null_and_findById_reads_it_back() =
        runTest {
            val id = db.goalDao().upsert(goal("NullBreakdown", 0L).copy(contributionBreakdown = null))
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertNull(read!!.contributionBreakdown)
        }

    @Test
    fun upsert_with_non_null_breakdown_json_persists_and_findById_reads_it_back_intact() =
        runTest {
            val json = """{"enabled":true,"incomes":[{"name":"Salary","amount":"120000.55"}],"expenses":[]}"""
            val id = db.goalDao().upsert(goal("WithBreakdown", 0L).copy(contributionBreakdown = json))
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertEquals(json, read!!.contributionBreakdown)
        }

    @Test
    fun upsert_update_replaces_null_breakdown_with_non_null() =
        runTest {
            val id = db.goalDao().upsert(goal("UpdateBreakdown", 0L).copy(contributionBreakdown = null))
            val json = """{"enabled":true,"incomes":[],"expenses":[{"name":"Rent","amount":"45000.10"}]}"""
            db.goalDao().upsert(goal("UpdateBreakdown", 0L).copy(id = id, contributionBreakdown = json))
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertEquals(json, read!!.contributionBreakdown)
        }

    @Test
    fun upsert_update_clears_breakdown_from_non_null_to_null() =
        runTest {
            val json = """{"enabled":false,"incomes":[{"name":"Side","amount":"5000.00"}],"expenses":[]}"""
            val id = db.goalDao().upsert(goal("ClearBreakdown", 0L).copy(contributionBreakdown = json))
            db.goalDao().upsert(goal("ClearBreakdown", 0L).copy(id = id, contributionBreakdown = null))
            val read = db.goalDao().findById(id)
            assertNotNull(read)
            assertNull(read!!.contributionBreakdown)
        }

    @Test
    fun observeActive_emits_goal_with_breakdown_json_preserved() =
        runTest {
            val json = """{"enabled":true,"incomes":[{"name":"A","amount":"1.00"}],"expenses":[]}"""
            db.goalDao().upsert(goal("StreamBreakdown", 0L).copy(contributionBreakdown = json))
            db.goalDao().observeActive().test {
                val active = awaitItem()
                assertEquals(1, active.size)
                assertEquals(json, active.first().contributionBreakdown)
            }
        }
}
