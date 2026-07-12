package com.kshavrin.mymoney.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.testing.contract.CurrencyRepositoryContract
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrencyRepositoryImplContractTest : CurrencyRepositoryContract() {
    private lateinit var database: MoneyDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    override fun createRepository(): CurrencyRepository =
        CurrencyRepositoryImpl(
            dao = database.currencyDao(),
            ioDispatcher = Dispatchers.Unconfined,
        )
}
