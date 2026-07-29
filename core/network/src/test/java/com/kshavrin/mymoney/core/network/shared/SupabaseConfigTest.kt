package com.kshavrin.mymoney.core.network.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    @Test
    fun `isConfigured returns true when url and anonKey are non-blank non-placeholder values`() {
        val config = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "eyJhbGciOiJIUzI1NiJ9.realkey",
        )
        assertTrue(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when url is empty`() {
        val config = SupabaseConfig(url = "", anonKey = "valid-anon-key")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when url is blank`() {
        val config = SupabaseConfig(url = "   ", anonKey = "valid-anon-key")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when anonKey is empty`() {
        val config = SupabaseConfig(url = "https://xyzxyz.supabase.co", anonKey = "")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when anonKey is blank`() {
        val config = SupabaseConfig(url = "https://xyzxyz.supabase.co", anonKey = "   ")
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when url is the default build placeholder`() {
        val config = SupabaseConfig(
            url = "PLACEHOLDER_SUPABASE_URL",
            anonKey = "valid-anon-key",
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when anonKey is the default build placeholder`() {
        val config = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "PLACEHOLDER_SUPABASE_ANON_KEY",
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when both url and anonKey are placeholders`() {
        val config = SupabaseConfig(
            url = "PLACEHOLDER_SUPABASE_URL",
            anonKey = "PLACEHOLDER_SUPABASE_ANON_KEY",
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when url starts with PLACEHOLDER_ regardless of suffix`() {
        val config = SupabaseConfig(
            url = "PLACEHOLDER_CUSTOM_ENVIRONMENT_URL",
            anonKey = "valid-anon-key",
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isConfigured returns false when anonKey starts with PLACEHOLDER_ regardless of suffix`() {
        val config = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "PLACEHOLDER_CUSTOM_KEY",
        )
        assertFalse(config.isConfigured)
    }

    @Test
    fun `isGoogleSignInConfigured returns true only when the base config and client id are valid`() {
        val config = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "valid-anon-key",
            googleWebClientId = "123.apps.googleusercontent.com",
        )

        assertTrue(config.isGoogleSignInConfigured)
    }

    @Test
    fun `isGoogleSignInConfigured rejects blank and placeholder client ids`() {
        val blank = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "valid-anon-key",
        )
        val placeholder = SupabaseConfig(
            url = "https://xyzxyz.supabase.co",
            anonKey = "valid-anon-key",
            googleWebClientId = "PLACEHOLDER_GOOGLE_CLIENT_ID",
        )

        assertFalse(blank.isGoogleSignInConfigured)
        assertFalse(placeholder.isGoogleSignInConfigured)
    }
}
