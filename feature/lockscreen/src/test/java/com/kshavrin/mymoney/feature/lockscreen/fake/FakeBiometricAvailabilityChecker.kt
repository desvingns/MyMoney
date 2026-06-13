package com.kshavrin.mymoney.feature.lockscreen.fake

import com.kshavrin.mymoney.feature.lockscreen.setup.BiometricAvailability
import com.kshavrin.mymoney.feature.lockscreen.setup.BiometricAvailabilityChecker

class FakeBiometricAvailabilityChecker(
    private var current: BiometricAvailability = BiometricAvailability.Available,
) : BiometricAvailabilityChecker {
    override fun availability(): BiometricAvailability = current

    fun setAvailability(availability: BiometricAvailability) {
        current = availability
    }
}
