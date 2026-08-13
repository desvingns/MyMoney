package com.kshavrin.mymoney.core.sync.analytics

import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpAnalyticsGateway
    @Inject
    constructor() : AnalyticsGateway {
        override fun log(event: AnalyticsEvent) = Unit
    }
