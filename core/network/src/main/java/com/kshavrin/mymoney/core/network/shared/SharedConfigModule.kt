package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedConfigModule {
    @Provides
    @Singleton
    fun provideSupabaseConfig(): SupabaseConfig =
        SupabaseConfig(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
        )

    @Provides
    fun provideSecureRandom(): SecureRandom = SecureRandom()
}
