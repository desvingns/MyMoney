package com.kshavrin.mymoney.core.testing.contract

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository

class FakeAppSettingsRepositoryContractTest : AppSettingsRepositoryContract() {
    override fun createRepository(): AppSettingsRepository = FakeAppSettingsRepository()
}
