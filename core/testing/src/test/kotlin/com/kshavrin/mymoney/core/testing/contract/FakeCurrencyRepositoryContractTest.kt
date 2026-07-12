package com.kshavrin.mymoney.core.testing.contract

import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository

class FakeCurrencyRepositoryContractTest : CurrencyRepositoryContract() {
    override fun createRepository(): CurrencyRepository = FakeCurrencyRepository()
}
