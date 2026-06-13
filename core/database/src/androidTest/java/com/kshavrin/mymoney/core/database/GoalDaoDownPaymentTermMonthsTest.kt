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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class GoalDaoDownPaymentTermMonthsTest {
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

    private fun creditGoalEntity(name: String = "Mortgage") =
        GoalEntity(
            name = name,
            iconKey = "ic_goal_house",
            colorHex = "#4A90D9",
            accountId = 1L,
            variant = "CREDIT",
            targetAmount = 10_000_000.0,
            startingCapital = 2_000_000.0,
            monthlyContribution = 85_000.0,
            annualRatePercent = 12.5,
            downPayment = 2_000_000.0,
            termMonths = 240,
            termDate = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
            isArchived = false,
        )

    private fun savingsGoalEntity(name: String = "Car") =
        GoalEntity(
            name = name,
            iconKey = "ic_goal_savings",
            colorHex = "#7AC794",
            accountId = 2L,
            variant = "SAVINGS",
            targetAmount = 500_000.0,
            startingCapital = 10_000.0,
            monthlyContribution = 20_000.0,
            annualRatePercent = null,
            downPayment = null,
            termMonths = null,
            termDate = null,
            createdAt = 2_000L,
            updatedAt = 2_000L,
            isArchived = false,
        )

    // --- CREDIT goal: downPayment and termMonths round-trip ---

    @Test
    fun credit_goal_downPayment_survives_round_trip_via_findById() =
        runTest {
            val id = db.goalDao().upsert(creditGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            // Money is Double in Room; compare as BigDecimal to avoid floating-point equality traps
            assertEquals(0, BigDecimal.valueOf(2_000_000.0).compareTo(BigDecimal.valueOf(read!!.downPayment!!)))
        }

    @Test
    fun credit_goal_termMonths_survives_round_trip_via_findById() =
        runTest {
            val id = db.goalDao().upsert(creditGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertEquals(240, read!!.termMonths)
        }

    @Test
    fun credit_goal_annualRatePercent_survives_round_trip_via_findById() =
        runTest {
            val id = db.goalDao().upsert(creditGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertEquals(0, BigDecimal.valueOf(12.5).compareTo(BigDecimal.valueOf(read!!.annualRatePercent!!)))
        }

    @Test
    fun credit_goal_downPayment_and_termMonths_survive_round_trip_via_observeActive() =
        runTest {
            db.goalDao().upsert(creditGoalEntity())
            db.goalDao().observeActive().test {
                val active = awaitItem()
                assertEquals(1, active.size)
                val entity = active.first()
                assertEquals(0, BigDecimal.valueOf(2_000_000.0).compareTo(BigDecimal.valueOf(entity.downPayment!!)))
                assertEquals(240, entity.termMonths)
            }
        }

    @Test
    fun credit_goal_upsert_update_overwrites_downPayment_and_termMonths() =
        runTest {
            val id = db.goalDao().upsert(creditGoalEntity())
            db.goalDao().upsert(
                creditGoalEntity().copy(
                    id = id,
                    downPayment = 3_500_000.0,
                    termMonths = 180,
                ),
            )
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertEquals(0, BigDecimal.valueOf(3_500_000.0).compareTo(BigDecimal.valueOf(read!!.downPayment!!)))
            assertEquals(180, read.termMonths)
        }

    @Test
    fun credit_goal_termDate_is_not_written_by_toEntity_and_reads_back_as_null() =
        runTest {
            // The mapper sets termDate = null for all new saves (legacy column, no longer used).
            val id = db.goalDao().upsert(creditGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertNull(read!!.termDate)
        }

    // --- SAVINGS goal: downPayment and termMonths are null ---

    @Test
    fun savings_goal_downPayment_is_null_after_round_trip() =
        runTest {
            val id = db.goalDao().upsert(savingsGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertNull(read!!.downPayment)
        }

    @Test
    fun savings_goal_termMonths_is_null_after_round_trip() =
        runTest {
            val id = db.goalDao().upsert(savingsGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertNull(read!!.termMonths)
        }

    @Test
    fun savings_goal_targetAmount_survives_round_trip_as_BigDecimal() =
        runTest {
            val id = db.goalDao().upsert(savingsGoalEntity())
            val read = db.goalDao().findById(id)

            assertNotNull(read)
            assertEquals(0, BigDecimal.valueOf(500_000.0).compareTo(BigDecimal.valueOf(read!!.targetAmount)))
        }

    @Test
    fun savings_and_credit_goals_coexist_and_are_distinguishable_by_variant() =
        runTest {
            val savingsId = db.goalDao().upsert(savingsGoalEntity("VacationFund"))
            val creditId = db.goalDao().upsert(creditGoalEntity("HomeLoan"))

            val savings = db.goalDao().findById(savingsId)
            val credit = db.goalDao().findById(creditId)

            assertNotNull(savings)
            assertNotNull(credit)
            assertEquals("SAVINGS", savings!!.variant)
            assertNull(savings.downPayment)
            assertNull(savings.termMonths)
            assertEquals("CREDIT", credit!!.variant)
            assertEquals(0, BigDecimal.valueOf(2_000_000.0).compareTo(BigDecimal.valueOf(credit.downPayment!!)))
            assertEquals(240, credit.termMonths)
        }
}
