package com.kshavrin.mymoney.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityCheckerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val checker = AndroidConnectivityChecker(context)

    @Test
    fun `isOnline returns a non-null Boolean using real ConnectivityManager`() {
        val result: Boolean? = checker.isOnline()

        assertNotNull(result)
    }

    @Test
    fun `isOnline returns false when ConnectivityManager is absent from context`() {
        val noServiceContext =
            object : android.content.ContextWrapper(context) {
                override fun getSystemService(name: String): Any? =
                    if (name == android.content.Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
            }
        val checkerWithoutCm = AndroidConnectivityChecker(noServiceContext)

        val result = checkerWithoutCm.isOnline()

        assert(!result) { "expected false when ConnectivityManager is null, got $result" }
    }
}
