package com.kshavrin.mymoney.core.sync.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.sync.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AnalyticsGateway {
        private val analytics: FirebaseAnalytics? by lazy {
            if (BuildConfig.HAS_FIREBASE) {
                FirebaseAnalytics.getInstance(context)
            } else {
                null
            }
        }

        override fun log(event: AnalyticsEvent) {
            val firebaseAnalytics = analytics ?: return
            when (event) {
                AnalyticsEvent.SupportOpened ->
                    firebaseAnalytics.logEvent(EVENT_SUPPORT_OPENED, null)

                is AnalyticsEvent.SupportPurchaseStarted ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUPPORT_PURCHASE_STARTED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                        },
                    )

                is AnalyticsEvent.SupportPurchaseCompleted ->
                    firebaseAnalytics.logEvent(
                        EVENT_SUPPORT_PURCHASE_COMPLETED,
                        Bundle().apply {
                            putString(PARAM_PRODUCT_ID, event.productId)
                            putString(PARAM_OUTCOME, event.outcome)
                        },
                    )
            }
        }

        private companion object {
            const val EVENT_SUPPORT_OPENED = "support_opened"
            const val EVENT_SUPPORT_PURCHASE_STARTED = "support_purchase_started"
            const val EVENT_SUPPORT_PURCHASE_COMPLETED = "support_purchase_completed"
            const val PARAM_PRODUCT_ID = "product_id"
            const val PARAM_OUTCOME = "outcome"
        }
    }
