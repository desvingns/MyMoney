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
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MoneyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun goal(name: String, createdAt: Long): GoalEntity = GoalEntity(
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
    fun upsert_then_observeActive_returns_it() = runTest {
        val id = db.goalDao().upsert(goal("Car", 0L))
        db.goalDao().observeActive().test {
            val active = awaitItem()
            assertEquals(1, active.size)
            assertEquals(id, active.first().id)
            assertEquals("Car", active.first().name)
        }
    }

    @Test
    fun findById_returns_stored_goal() = runTest {
        val id = db.goalDao().upsert(goal("House", 0L))
        val read = db.goalDao().findById(id)
        assertNotNull(read)
        assertEquals("House", read!!.name)
    }

    @Test
    fun findById_unknown_returns_null() = runTest {
        assertNull(db.goalDao().findById(999L))
    }

    @Test
    fun archive_hides_goal_from_observeActive() = runTest {
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
    fun observeActive_orders_by_created_at_desc() = runTest {
        val older = db.goalDao().upsert(goal("Older", 100L))
        val newer = db.goalDao().upsert(goal("Newer", 200L))
        db.goalDao().observeActive().test {
            val active = awaitItem()
            assertEquals(listOf(newer, older), active.map { it.id })
        }
    }

    @Test
    fun observeActive_empty_returns_empty() = runTest {
        db.goalDao().observeActive().test {
            assertTrue(awaitItem().isEmpty())
        }
    }
}
