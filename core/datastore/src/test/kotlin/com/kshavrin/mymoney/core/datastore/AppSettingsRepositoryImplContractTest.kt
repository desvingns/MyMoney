package com.kshavrin.mymoney.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kshavrin.mymoney.core.testing.contract.AppSettingsRepositoryContract
import org.junit.After
import java.io.File
import java.nio.file.Files

class AppSettingsRepositoryImplContractTest : AppSettingsRepositoryContract() {
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach(File::delete)
    }

    override fun createRepository(): AppSettingsRepository {
        val file = Files.createTempFile("app-settings-contract", ".preferences_pb").toFile()
        file.delete()
        tempFiles += file
        return AppSettingsRepositoryImpl(PreferenceDataStoreFactory.create(produceFile = { file }))
    }
}
