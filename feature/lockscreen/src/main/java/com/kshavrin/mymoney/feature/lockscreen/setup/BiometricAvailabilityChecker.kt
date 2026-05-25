package com.kshavrin.mymoney.feature.lockscreen.setup

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAvailability {
    Available,
    NoHardware,
    NotEnrolled,
}

interface BiometricAvailabilityChecker {
    fun availability(): BiometricAvailability
}

@Singleton
class AndroidBiometricAvailabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAvailabilityChecker {
    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricAvailability.NoHardware

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
            else -> BiometricAvailability.Available
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricAvailabilityModule {

    @Binds
    @Singleton
    abstract fun bindBiometricAvailabilityChecker(
        impl: AndroidBiometricAvailabilityChecker,
    ): BiometricAvailabilityChecker
}
