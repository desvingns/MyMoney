package com.kshavrin.mymoney.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageTest {

    private lateinit var storage: SecureStorage

    @Before
    fun setUp() {
        storage = SecureStorageImpl(ApplicationProvider.getApplicationContext())
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
    }

    @Test
    fun dropbox_token_roundtrip() {
        storage.writeDropboxRefreshToken("token-abc-123")
        assertEquals("token-abc-123", storage.read().dropboxRefreshToken)
    }

    @Test
    fun gdrive_email_roundtrip() {
        storage.writeGdriveAccountEmail("user@example.com")
        assertEquals("user@example.com", storage.read().gdriveAccountEmail)
    }

    @Test
    fun pin_hash_roundtrip() {
        storage.writePinHash("hash-xyz")
        assertEquals("hash-xyz", storage.read().pinHash)
    }

    @Test
    fun clear_all_removes_each_field() {
        storage.writeDropboxRefreshToken("tok")
        storage.writeGdriveAccountEmail("email")
        storage.writePinHash("hash")
        storage.clearAll()
        val read = storage.read()
        assertNull(read.dropboxRefreshToken)
        assertNull(read.gdriveAccountEmail)
        assertNull(read.pinHash)
    }

    @Test
    fun null_write_removes_field() {
        storage.writeDropboxRefreshToken("tok")
        storage.writeDropboxRefreshToken(null)
        assertNull(storage.read().dropboxRefreshToken)
    }
}
