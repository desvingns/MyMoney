package com.kshavrin.mymoney.feature.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPageTest {
    @Test
    fun `first onboarding page is not the last page`() {
        assertFalse(isLastOnboardingPage(0))
    }

    @Test
    fun `middle onboarding page is not the last page`() {
        assertFalse(isLastOnboardingPage(1))
    }

    @Test
    fun `final onboarding page is the last page`() {
        assertTrue(isLastOnboardingPage(3))
    }
}
