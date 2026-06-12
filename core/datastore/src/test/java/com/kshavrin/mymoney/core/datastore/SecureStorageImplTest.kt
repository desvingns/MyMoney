package com.kshavrin.mymoney.core.datastore

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecureStorageImplTest {

    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        tempFile = Files.createTempFile("secure_storage_settings", ".preferences_pb").toFile()
        tempFile.delete()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFile },
        )
    }

    @After
    fun tearDown() {
        tempFile.delete()
    }

    @Test
    fun `resetLockIfPinMissing clears biometric lock when the secure prefs lost the pin hash`() = runTest {
        dataStore.updateData { prefs ->
            androidx.datastore.preferences.core.mutablePreferencesOf().also { mutable ->
                prefs.toAppSettings()
                    .copy(biometricLockEnabled = true)
                    .writeTo(mutable)
            }
        }

        invokeResetLockIfPinMissing(
            prefs = FakeSharedPreferences(),
            dataStore = dataStore,
        )

        val settings = dataStore.data.first().toAppSettings()
        assertFalse(settings.biometricLockEnabled)
    }

    @Test
    fun `resetLockIfPinMissing leaves biometric lock enabled when the pin hash still exists`() = runTest {
        dataStore.updateData { prefs ->
            androidx.datastore.preferences.core.mutablePreferencesOf().also { mutable ->
                prefs.toAppSettings()
                    .copy(biometricLockEnabled = true)
                    .writeTo(mutable)
            }
        }

        invokeResetLockIfPinMissing(
            prefs = FakeSharedPreferences(
                mutableMapOf("pin_hash" to "hash-v2"),
            ),
            dataStore = dataStore,
        )

        val settings = dataStore.data.first().toAppSettings()
        assertTrue(settings.biometricLockEnabled)
    }

    private fun invokeResetLockIfPinMissing(
        prefs: SharedPreferences,
        dataStore: DataStore<Preferences>,
    ) {
        val companionField = SecureStorageImpl::class.java.getDeclaredField("Companion").apply {
            isAccessible = true
        }
        val companionInstance = companionField.get(null)
        val companionClass = SecureStorageImpl::class.java.declaredClasses
            .single { it.simpleName == "Companion" }
        val method = companionClass.getDeclaredMethod(
            "resetLockIfPinMissing",
            SharedPreferences::class.java,
            DataStore::class.java,
            kotlinx.coroutines.CoroutineDispatcher::class.java,
        ).apply {
            isAccessible = true
        }
        method.invoke(companionInstance, prefs, dataStore, Dispatchers.Unconfined)
    }

    private class FakeSharedPreferences(
        private val values: MutableMap<String, Any?> = mutableMapOf(),
    ) : SharedPreferences {

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            values[key] as? MutableSet<String> ?: defValues

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun edit(): SharedPreferences.Editor = Editor(values)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>,
        ) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = values
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                pending[key.orEmpty()] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) {
                    values.clear()
                }
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
                clearRequested = false
            }
        }
    }
}
