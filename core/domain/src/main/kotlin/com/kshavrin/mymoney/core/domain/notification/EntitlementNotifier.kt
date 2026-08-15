package com.kshavrin.mymoney.core.domain.notification

import com.kshavrin.mymoney.core.domain.model.EntitlementWarning

interface EntitlementNotifier {
    fun notify(warning: EntitlementWarning)
}
