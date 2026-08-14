package com.kshavrin.mymoney.core.ads.admob

import com.google.android.gms.ads.AdRequest
import com.kshavrin.mymoney.core.ads.AdAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdErrorMapperTest {
    private val mapper = AdErrorMapper()

    @Test
    fun `maps no fill to no fill and identifies it as streak input`() {
        assertEquals(AdAvailability.NoFill, mapper.map(AdRequest.ERROR_CODE_NO_FILL))
        assertTrue(mapper.isNoFill(AdRequest.ERROR_CODE_NO_FILL))
    }

    @Test
    fun `maps network errors to offline without treating them as no fill`() {
        assertEquals(AdAvailability.Offline, mapper.map(AdRequest.ERROR_CODE_NETWORK_ERROR))
        assertFalse(mapper.isNoFill(AdRequest.ERROR_CODE_NETWORK_ERROR))
    }

    @Test
    fun `maps unknown errors to no fill as the deterministic fallback`() {
        assertEquals(AdAvailability.NoFill, mapper.map(errorCode = Int.MIN_VALUE, errorMessage = "unexpected"))
        assertFalse(mapper.isNoFill(Int.MIN_VALUE))
    }
}
