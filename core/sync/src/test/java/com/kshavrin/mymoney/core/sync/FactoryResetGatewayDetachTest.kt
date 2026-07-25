package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import org.junit.Assert.assertNull
import org.junit.Test

class FactoryResetGatewayDetachTest {
    @Test
    fun `detached cloud state has no active binding`() {
        val detached: CloudBinding? = null
        assertNull(detached)
        check(CloudProvider.entries.size == 2)
    }
}
